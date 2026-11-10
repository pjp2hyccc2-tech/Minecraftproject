package com.eternalrealm.world.forge;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.Contributions;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.ClearRank;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.Materials;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.monster.MonsterScaling;
import com.eternalrealm.monster.MonsterStats;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiData;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.FieldThreats;
import com.eternalrealm.world.field.Miasma;
import com.eternalrealm.world.field.Wanderers;
import com.eternalrealm.world.field.Waystones;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The 放浪する大物 on the server ({@link Wanderers}): the schedule says when a field's wanderer appears and where;
 * everyone is told (field, nearest waystone, direction, coordinates) and the board and map show it. A beacon of motes
 * marks its spot; when a player comes within {@link Wanderers#WAKE_RADIUS} its ground is cleared (trees and plants
 * trampled, the ground levelled only where it is rough) and an open-world boss fight starts that everyone close by
 * joins, tuned for at least two fighters. Nobody coming within its stay, the field's calamity rising, or its fighters
 * leaving it for {@link Wanderers#ABANDON_TICKS}: it goes. Defeated, every fighter with a fair share
 * ({@link FieldThreats#REWARD_SHARE}) is rewarded (the hero more) and sees the result screen.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WandererService {
    private static final Logger LOG = LogUtils.getLogger();
    /** Tag and persistent-data key (its field) of a wanderer's body. */
    static final String TAG = ModInfo.MOD_ID + "_wanderer";
    private static final int CLEAR_COLUMNS_PER_TICK = 160;
    /** Players this close see the beacon over a waiting wanderer's spot. */
    private static final double BEACON_RANGE = 192.0;
    /** Ground rougher than this (blocks between the lowest and highest sample) is levelled before the fight. */
    private static final int ROUGH = 4;
    private static final long TIME_LIMIT_TICKS = 6L * 60L * 20L;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    /** A wanderer that is out: waiting at its spot, clearing its ground, or being fought. */
    private static final class Out {
        final Wanderers.Wanderer wanderer;
        final Wanderers.Spot spot;
        Clearing clearing;
        BossFight fight;
        long fightStart;
        long lastEngaged;

        Out(Wanderers.Wanderer wanderer, Wanderers.Spot spot) {
            this.wanderer = wanderer;
            this.spot = spot;
        }

        int field() {
            return wanderer.field();
        }
    }

    /** The ground being cleared before the fight. */
    private static final class Clearing {
        final List<int[]> columns = new ArrayList<>();
        final int groundY;
        final boolean level;
        int index;

        Clearing(int groundY, boolean level) {
            this.groundY = groundY;
            this.level = level;
        }

        boolean done() {
            return index >= columns.size();
        }
    }

    private static MinecraftServer owner;
    private static Wanderers.Schedule schedule;
    /** Where each field's wanderers appear, found off the server thread when the server starts. */
    private static final Map<Integer, List<Wanderers.Spot>> SPOTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Out> OUT = new HashMap<>();

    private WandererService() {
    }

    // ------------------------------------------------------------------ the clock

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null) {
            return;
        }
        ServerLevel astoria = server.getLevel(AstoriaDimension.LEVEL);
        if (astoria == null) {
            return;
        }
        long now = server.getTickCount();
        if (owner != server) {
            start(server);
        }
        try {
            for (Out out : List.copyOf(OUT.values())) {
                if (out.fight != null) {
                    out.fight.tick(now);
                } else if (out.clearing != null) {
                    clearSlice(astoria, out, now);
                }
            }
            if (now % 20 == 0) {
                tickFields(server, astoria, now);
            }
        } catch (RuntimeException e) {
            LOG.error("Field wanderers tick failed", e);
        }
    }

    private static void start(MinecraftServer server) {
        owner = server;
        OUT.clear();
        SPOTS.clear();
        schedule = new Wanderers.Schedule(RANDOM, SPOTS::get);
        List<Wanderers.Box> boxes = Wanderers.boxes(FieldPieces.data());
        CompletableFuture.runAsync(() -> {
            long started = System.nanoTime();
            for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
                SPOTS.put(field, Wanderers.findSpots(field, boxes));
            }
            LOG.info("Field wanderers: places found in {} ms", (System.nanoTime() - started) / 1_000_000L);
        }, Util.backgroundExecutor()).exceptionally(failure -> {
            LOG.error("Field wanderers: no places found, no wanderer will appear", failure);
            return null;
        });
    }

    private static void tickFields(MinecraftServer server, ServerLevel astoria, long now) {
        Set<Integer> occupied = new HashSet<>();
        for (ServerPlayer player : astoria.players()) {
            int field = FieldThreatService.fieldOf(player);
            if (!player.isSpectator() && field > 0) {
                occupied.add(field);
            }
        }
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            Miasma.Phase phase = FieldThreatService.phase(field);
            boolean calamityUp = phase == Miasma.Phase.OMEN || phase == Miasma.Phase.MANIFEST;
            for (Wanderers.Event event : schedule.tick(field, now, occupied.contains(field), calamityUp)) {
                handle(server, astoria, event);
            }
        }
        for (Out out : List.copyOf(OUT.values())) {
            tickOut(astoria, out, now);
        }
    }

    private static void handle(MinecraftServer server, ServerLevel astoria, Wanderers.Event event) {
        FieldThreatService.boardChanged();
        if (event instanceof Wanderers.Event.Appeared appeared) {
            Out out = new Out(appeared.wanderer(), appeared.spot());
            OUT.put(out.field(), out);
            announce(server, astoria, out);
        } else if (event instanceof Wanderers.Event.Left left) {
            Out out = OUT.remove(left.wanderer().field());
            if (out != null && out.fight != null) {
                discard(astoria, out.fight);
            }
            FieldThreatService.broadcast(server, Component.translatable(left.calamity() ? Wanderers.FLEES
                    : Wanderers.LEAVES, name(left.wanderer()), fieldName(left.wanderer().field()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Tells everyone where the wanderer is: its field, the nearest waystone, the direction and the coordinates. */
    private static void announce(MinecraftServer server, ServerLevel astoria, Out out) {
        int x = out.spot.x();
        int z = out.spot.z();
        Optional<FieldLayout.Waystone> landmark = Wanderers.landmark(out.field(), x, z);
        Component waystone = landmark.<Component>map(w -> Component.translatable(Waystones.translationKey(w.id()))
                .withStyle(ChatFormatting.AQUA)).orElse(Component.literal("-"));
        Component direction = Component.translatable(Wanderers.directionKey(landmark.map(w -> Wanderers.direction(w.x(),
                w.z(), x, z)).orElse("n")));
        long distance = landmark.map(w -> Math.round(Math.hypot(w.x() - x, w.z() - z) / 10.0) * 10L).orElse(0L);
        FieldThreatService.broadcast(server, Component.translatable(Wanderers.APPEARS, fieldName(out.field()),
                name(out.wanderer), level(out.wanderer), waystone, direction, distance, x, z)
                .withStyle(ChatFormatting.GOLD));
        for (ServerPlayer player : astoria.players()) {
            if (FieldThreatService.fieldOf(player) == out.field()) {
                player.playNotifySound(SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.6F, 0.7F);
            }
        }
    }

    private static void tickOut(ServerLevel astoria, Out out, long now) {
        if (out.fight == null) {
            if (out.clearing != null) {
                return;
            }
            if (now % 40 == 0) {
                beacon(astoria, out);
            }
            BlockPos at = new BlockPos(out.spot.x(), 64, out.spot.z());
            if (FieldThreatService.nearestPlayer(astoria, out.spot.x(), out.spot.z(), Wanderers.WAKE_RADIUS) != null
                    && astoria.hasChunkAt(at)) {
                schedule.engaged(out.field()); // it no longer leaves on its own
                out.clearing = planClearing(astoria, out);
                astoria.playSound(null, out.spot.x(), out.clearing.groundY + 1, out.spot.z(), SoundEvents.RAVAGER_ROAR,
                        SoundSource.HOSTILE, 2.0F, 0.6F);
            }
            return;
        }
        if (out.fight.isOver()) {
            if (!out.fight.isDefeated()) {
                walkOff(astoria, out, now); // the body is gone without a defeat (unloaded with its chunk)
            }
            return;
        }
        Mob body = out.fight.body();
        boolean engaged = false;
        double join = FieldThreats.JOIN_RADIUS * FieldThreats.JOIN_RADIUS;
        for (ServerPlayer player : astoria.players()) {
            if (player.isSpectator() || player.distanceToSqr(body) > join) {
                continue;
            }
            out.fight.join(player);
            engaged = engaged || !com.eternalrealm.revival.forge.RevivalService.isDown(player.getUUID());
        }
        if (engaged) {
            out.lastEngaged = now;
        } else if (now - out.lastEngaged > Wanderers.ABANDON_TICKS && !out.fight.isDefeated()) {
            walkOff(astoria, out, now);
        }
    }

    /** A column of motes over a waiting wanderer's spot, seen from afar. */
    private static void beacon(ServerLevel astoria, Out out) {
        double x = out.spot.x() + 0.5;
        double z = out.spot.z() + 0.5;
        BlockPos at = new BlockPos(out.spot.x(), 64, out.spot.z());
        double y = astoria.hasChunkAt(at) ? astoria.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, out.spot.x(),
                out.spot.z()) : 64.0;
        for (ServerPlayer player : astoria.players()) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz > BEACON_RANGE * BEACON_RANGE) {
                continue;
            }
            astoria.sendParticles(player, ParticleTypes.END_ROD, true, x, y + 12.0, z, 30, 0.4, 10.0, 0.4, 0.02);
            astoria.sendParticles(player, ParticleTypes.FLAME, true, x, y + 1.0, z, 12, 2.5, 0.3, 2.5, 0.01);
        }
    }

    // ------------------------------------------------------------------ the ground and the fight

    /**
     * Clears the wanderer's ground: trees, plants and anything standing well above the ground go, puddles are filled;
     * rough ground (a rare spot the terrain did not keep flat) is levelled to its median height like a calamity's lair.
     */
    private static Clearing planClearing(ServerLevel astoria, Out out) {
        int radius = FieldThreats.LAIR_RADIUS + 2;
        int x0 = out.spot.x();
        int z0 = out.spot.z();
        List<Integer> heights = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 3) {
            for (int dz = -radius; dz <= radius; dz += 3) {
                if (dx * dx + dz * dz <= radius * radius) {
                    heights.add(groundAt(astoria, x0 + dx, z0 + dz));
                }
            }
        }
        heights.sort(Integer::compare);
        int groundY = heights.get(heights.size() / 2);
        // the spread without the extremes (a boulder or a pit is cleared anyway)
        int low = heights.get(heights.size() / 10);
        int high = heights.get(heights.size() - 1 - heights.size() / 10);
        Clearing clearing = new Clearing(groundY, high - low > ROUGH);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    clearing.columns.add(new int[]{x0 + dx, z0 + dz, dx * dx + dz * dz});
                }
            }
        }
        clearing.columns.sort((a, b) -> Integer.compare(a[2], b[2]));
        return clearing;
    }

    /** The top block of the ground at (x, z): below trees, plants and water. */
    private static int groundAt(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        int bottom = level.getMinBuildHeight();
        while (pos.getY() > bottom) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !clearable(state)) {
                return pos.getY();
            }
            pos.move(0, -1, 0);
        }
        return pos.getY();
    }

    /** Trees, plants, mushrooms and fluids: what a wanderer tramples. */
    private static boolean clearable(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS) || state.canBeReplaced() || !state.getFluidState().isEmpty()
                || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BAMBOO) || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.BEE_NEST);
    }

    private static void clearSlice(ServerLevel astoria, Out out, long now) {
        Clearing clearing = out.clearing;
        int groundY = clearing.groundY;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int n = 0; n < CLEAR_COLUMNS_PER_TICK && !clearing.done(); n++) {
            int[] column = clearing.columns.get(clearing.index++);
            int x = column[0];
            int z = column[1];
            int top = astoria.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            for (int y = Math.min(top + 1, groundY + 40); y > groundY; y--) {
                pos.set(x, y, z);
                BlockState state = astoria.getBlockState(pos);
                if (!state.isAir() && (clearing.level || y > groundY + 3 || clearable(state))) {
                    astoria.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            for (int y = groundY; y >= groundY - (clearing.level ? 6 : 3); y--) {
                pos.set(x, y, z);
                BlockState state = astoria.getBlockState(pos);
                boolean hole = !state.getFluidState().isEmpty() || clearing.level && (state.isAir() || state.canBeReplaced());
                if (hole) {
                    BlockState fill = y == groundY && clearing.level
                            ? FieldThreatService.scarBlock(out.field(), x, z, false) : Blocks.DIRT.defaultBlockState();
                    astoria.setBlock(pos, fill, Block.UPDATE_CLIENTS);
                }
            }
        }
        if (!clearing.done()) {
            if (now % 5 == 0) {
                astoria.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, out.spot.x() + 0.5, groundY + 1.5,
                        out.spot.z() + 0.5, 6, 6.0, 0.5, 6.0, 0.02);
            }
            return;
        }
        out.clearing = null;
        startFight(astoria, out, now);
    }

    private static void startFight(ServerLevel astoria, Out out, long now) {
        BossDefinition definition = Bosses.byId(out.wanderer.boss()).orElse(null);
        if (definition == null) {
            LOG.error("Field wanderer {} has no boss {}", out.wanderer.id(), out.wanderer.boss());
            walkOff(astoria, out, now);
            return;
        }
        BlockPos spawn = FieldThreatService.surface(astoria, out.spot.x(), out.spot.z());
        Vec3 center = Vec3.atBottomCenterOf(spawn);
        Set<UUID> participants = new HashSet<>();
        double join = FieldThreats.JOIN_RADIUS * FieldThreats.JOIN_RADIUS;
        for (ServerPlayer player : astoria.players()) {
            if (!player.isSpectator() && player.distanceToSqr(center) < join) {
                participants.add(player.getUUID());
            }
        }
        if (participants.isEmpty()) {
            walkOff(astoria, out, now); // whoever woke it left at once
            return;
        }
        // tuned for at least two fighters, like a calamity: whoever is around joins on the way
        Set<UUID> tuning = new HashSet<>(participants);
        while (tuning.size() < 2) {
            tuning.add(UUID.randomUUID());
        }
        Optional<BossFight> started = BossFight.start(astoria, definition, Difficulty.NORMAL, spawn, center,
                FieldThreats.LAIR_RADIUS, tuning, killed -> {
                }, defeated -> wandererDown(astoria, out, defeated));
        if (started.isEmpty()) {
            LOG.error("Field wanderer {}: no body for {}", out.wanderer.id(), definition.id());
            walkOff(astoria, out, now);
            return;
        }
        BossFight fight = started.get();
        Mob body = fight.body();
        body.setCustomName(Component.translatable(Wanderers.NAMEPLATE, name(out.wanderer))
                .withStyle(ChatFormatting.GOLD));
        body.addTag(TAG);
        body.getPersistentData().putInt(TAG, out.field());
        out.fight = fight;
        out.fightStart = now;
        out.lastEngaged = now;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(astoria);
        if (bolt != null) {
            bolt.moveTo(center);
            bolt.setVisualOnly(true);
            astoria.addFreshEntity(bolt);
        }
        astoria.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 1.0, center.z, 6, 3.0, 0.5, 3.0, 0.0);
        FieldThreatService.boardChanged();
    }

    /** The wanderer leaves: its fight (if any) ends and the body fades. */
    private static void walkOff(ServerLevel astoria, Out out, long now) {
        if (OUT.get(out.field()) == out) {
            OUT.remove(out.field());
        }
        schedule.ended(out.field(), now);
        if (out.fight != null) {
            discard(astoria, out.fight);
        }
        FieldThreatService.broadcast(astoria.getServer(), Component.translatable(Wanderers.WALKS_OFF, name(out.wanderer),
                fieldName(out.field())).withStyle(ChatFormatting.GRAY));
        FieldThreatService.boardChanged();
    }

    private static void discard(ServerLevel astoria, BossFight fight) {
        Entity body = fight.body();
        fight.end();
        if (body.isAlive()) {
            astoria.sendParticles(ParticleTypes.CLOUD, body.getX(), body.getY() + 2.0, body.getZ(), 60, 1.5, 2.0, 1.5,
                    0.05);
            body.discard();
        }
    }

    // ------------------------------------------------------------------ rewards

    private static void wandererDown(ServerLevel astoria, Out out, BossFight fight) {
        MinecraftServer server = astoria.getServer();
        long now = server.getTickCount();
        int field = out.field();
        if (OUT.get(field) == out) {
            OUT.remove(field);
        }
        schedule.ended(field, now);
        FieldThreatService.boardChanged();
        Contributions contributions = fight.contributions();
        Optional<Contributions.Line> hero = contributions.hero();
        FieldThreatService.broadcast(server, Component.translatable(Wanderers.DOWN, fieldName(field),
                name(out.wanderer), hero.map(line -> Component.literal(line.name()).withStyle(ChatFormatting.GOLD))
                        .orElse(Component.literal("-"))).withStyle(ChatFormatting.YELLOW));
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        BossDefinition definition = Bosses.byId(out.wanderer.boss()).orElse(null);
        MonsterTemplate base = FieldThreatService.rewardTemplate(definition, field);
        MonsterStats stats = base == null ? null : MonsterScaling.stats(base, Math.max(base.levels().min(),
                Math.min(base.levels().max(), definition.level())));
        long durationTicks = Math.max(0L, (fight.killTick() < 0 ? now : fight.killTick()) - out.fightStart);
        int falls = contributions.byDamage().stream().mapToInt(Contributions.Line::falls).sum();
        ClearRank rank = ClearRank.of(durationTicks, TIME_LIMIT_TICKS, falls);
        JsonArray party = new JsonArray();
        for (Contributions.Line line : contributions.byDamage()) {
            CharacterSnapshot snapshot = context.playerData().snapshot(line.id()).orElse(null);
            JsonObject row = new JsonObject();
            row.addProperty("id", line.id().toString());
            row.addProperty("name", line.name());
            row.addProperty("job", line.job());
            row.addProperty("level", snapshot == null ? 0 : snapshot.level());
            row.addProperty("damage", Math.round(line.damage()));
            row.addProperty("share", Math.round(line.share() * 1000.0) / 1000.0);
            row.addProperty("taken", Math.round(line.taken()));
            row.addProperty("falls", line.falls());
            row.addProperty("hero", hero.map(best -> best.id().equals(line.id())).orElse(false));
            party.add(row);
        }
        for (Contributions.Line line : contributions.byDamage()) {
            ServerPlayer player = server.getPlayerList().getPlayer(line.id());
            boolean isHero = hero.map(best -> best.id().equals(line.id())).orElse(false);
            if (player == null || line.share() < FieldThreats.REWARD_SHARE && !isHero) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            FieldThreatService.addMaterial(items, com.eternalrealm.monster.Materials.fieldUncommon(field),
                    isHero ? 4 : 3);
            FieldThreatService.addMaterial(items, com.eternalrealm.monster.Materials.fieldElite(field), isHero ? 2 : 1);
            FieldThreatService.addMaterial(items, Materials.ENHANCEMENT_STONE, 1 + rank.bonusTier());
            // a wanderer's equipment floor stays below a calamity's (whose hero always gets RARE or better)
            if (isHero || RANDOM.nextDouble() < 0.5) {
                ItemRarity floor = isHero && RANDOM.nextDouble() < 0.35 ? ItemRarity.RARE : ItemRarity.UNCOMMON;
                items.add(FieldThreatService.equipment(context, player, field, floor));
            }
            if (RANDOM.nextDouble() < (isHero ? 0.3 : 0.2)) {
                items.add(new ItemStack(ERGearItems.consumable(Consumable.EVERBLOOM_PETAL)));
            }
            long exp = stats == null ? 0L : Math.round(stats.exp() * (isHero ? 15.0 : 10.0));
            long gold = stats == null ? 0L : Math.round(stats.goldMax() * (isHero ? 7.5 : 5.0));
            FieldThreatService.give(context, player, items, exp, gold);
            JsonObject data = new JsonObject();
            data.addProperty("kind", "wanderer");
            data.addProperty("dungeon", out.wanderer.translationKey());
            data.addProperty("difficulty", "wanderer");
            data.addProperty("difficultyKey", FieldThreatService.fieldKey(field));
            data.addProperty("time", durationTicks / 20.0);
            data.addProperty("limit", 0);
            data.addProperty("falls", falls);
            data.addProperty("rank", rank.name());
            data.addProperty("me", player.getUUID().toString());
            data.addProperty("heroMe", isHero);
            data.add("party", party);
            JsonArray rewards = new JsonArray();
            items.forEach(stack -> rewards.add(stack.save(new CompoundTag()).toString()));
            data.add("rewards", rewards);
            data.addProperty("exp", exp);
            data.addProperty("gold", gold);
            data.addProperty("cards", 0);
            data.addProperty("exit", 0);
            UiData.send(player, com.eternalrealm.dungeon.forge.DungeonResults.CHANNEL, data);
        }
    }

    // ------------------------------------------------------------------ the board

    /**
     * Adds the wanderers of {@code field} to its board entry: {@code "wanderers": [{id, name, level, out, x, z,
     * fighting, bx, bz, leaves}]} (the position and times only for the one that is out).
     */
    static void addToBoard(JsonObject entry, int field, long now) {
        JsonArray list = new JsonArray();
        Out out = OUT.get(field);
        for (Wanderers.Wanderer wanderer : Wanderers.of(field)) {
            JsonObject w = new JsonObject();
            w.addProperty("id", wanderer.id());
            w.addProperty("name", wanderer.translationKey());
            w.addProperty("level", level(wanderer));
            boolean isOut = out != null && out.wanderer.equals(wanderer);
            w.addProperty("out", isOut);
            if (isOut) {
                w.addProperty("x", out.spot.x());
                w.addProperty("z", out.spot.z());
                boolean fighting = out.clearing != null || out.fight != null && !out.fight.isOver();
                w.addProperty("fighting", fighting);
                if (out.fight != null && !out.fight.isOver()) {
                    w.addProperty("bx", Math.round(out.fight.body().getX()));
                    w.addProperty("bz", Math.round(out.fight.body().getZ()));
                }
                w.addProperty("leaves", schedule == null ? 0.0 : schedule.leavesIn(field, now) / 20.0);
            }
            list.add(w);
        }
        entry.add("wanderers", list);
    }

    // ------------------------------------------------------------------ helpers

    private static Component name(Wanderers.Wanderer wanderer) {
        return Component.translatable(wanderer.translationKey()).withStyle(ChatFormatting.GOLD);
    }

    private static Component fieldName(int field) {
        return Component.translatable(FieldThreatService.fieldKey(field)).withStyle(ChatFormatting.AQUA);
    }

    private static int level(Wanderers.Wanderer wanderer) {
        return Bosses.byId(wanderer.boss()).map(BossDefinition::level).orElse(0);
    }

    /** Wanderers saved with their chunks by an earlier run (or after their fight was lost) are not tracked: they go. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide() || !entity.getTags().contains(TAG)) {
            return;
        }
        for (Out out : OUT.values()) {
            if (out.fight != null && out.fight.body() == entity) {
                return;
            }
        }
        // the tag is added after the body joined the level, so a fresh body is never caught here
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel astoria = event.getServer().getLevel(AstoriaDimension.LEVEL);
        if (astoria != null && owner == event.getServer()) {
            for (Out out : OUT.values()) {
                if (out.fight != null) {
                    out.fight.end();
                    out.fight.body().discard();
                }
            }
        }
        OUT.clear();
        schedule = null;
        owner = null;
    }

    /**
     * Operators: {@code /eternalrealm threat wanderer <id>} brings a wanderer out now at one of its places, {@code ...
     * <id> here} where the operator stands (in Astoria).
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        List<String> ids = Wanderers.all().stream().map(Wanderers.Wanderer::id).toList();
        event.getDispatcher().register(Commands.literal("eternalrealm")
                .then(Commands.literal("threat").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("wanderer")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ids, builder))
                                        .executes(ctx -> summon(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                                false))
                                        .then(Commands.literal("here").executes(ctx -> summon(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"), true)))))));
    }

    private static int summon(CommandSourceStack source, String id, boolean here) {
        MinecraftServer server = source.getServer();
        ServerLevel astoria = server.getLevel(AstoriaDimension.LEVEL);
        Optional<Wanderers.Wanderer> wanderer = Wanderers.byId(id);
        if (astoria == null || owner != server || schedule == null) {
            source.sendFailure(Component.literal("Astoria is not running"));
            return 0;
        }
        if (wanderer.isEmpty()) {
            source.sendFailure(Component.literal("Unknown wanderer " + id));
            return 0;
        }
        Wanderers.Spot spot;
        if (here) {
            if (source.getLevel() != astoria) {
                source.sendFailure(Component.literal("Stand in Astoria"));
                return 0;
            }
            Vec3 position = source.getPosition();
            spot = new Wanderers.Spot((int) Math.floor(position.x), (int) Math.floor(position.z));
        } else {
            List<Wanderers.Spot> places = SPOTS.get(wanderer.get().field());
            if (places == null || places.isEmpty()) {
                source.sendFailure(Component.literal("The places of field " + wanderer.get().field()
                        + " are not known yet"));
                return 0;
            }
            spot = places.get(RANDOM.nextInt(places.size()));
        }
        Optional<Wanderers.Event.Appeared> appeared = schedule.force(wanderer.get(), spot, server.getTickCount());
        if (appeared.isEmpty()) {
            source.sendFailure(Component.literal("Field " + wanderer.get().field() + " already has a wanderer out"));
            return 0;
        }
        handle(server, astoria, appeared.get());
        source.sendSuccess(() -> Component.literal(id + " is out at " + spot.x() + ", " + spot.z()), true);
        return 1;
    }
}
