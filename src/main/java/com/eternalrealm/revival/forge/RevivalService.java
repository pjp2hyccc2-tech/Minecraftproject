package com.eternalrealm.revival.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.forge.DungeonLevels;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.ConsumableEffect;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.forge.ConsumableItem;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.revival.RevivalRules;
import com.eternalrealm.revival.RevivalTips;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiData;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.Waystones;
import com.eternalrealm.world.forge.AstoriaDimension;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.Capital;
import com.eternalrealm.world.forge.HubLayout;
import com.eternalrealm.world.forge.WaystoneService;
import com.eternalrealm.world.gen.HubSpot;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The revival system on the server ({@link RevivalRules}): in アストリア and in dungeon instances a lethal hit does not
 * kill a player but makes them fall (戦闘不能). The fallen player lies where they fell — untouchable, ignored by monsters
 * and bosses, unable to act — and gets the revival screen ({@link #CHANNEL}): revive on the spot with a 世界樹の葉,
 * return to the save point (automatically when the countdown ends), or wait for an ally's leaf or resurrection.
 * Nearby clients get the list of fallen bodies ({@link #BODIES_CHANNEL}) to draw them lying on the ground.
 *
 * <p>Deaths that bypass invulnerability ({@code /kill}, the void) and deaths elsewhere stay vanilla deaths. The
 * fallen state is marked in the player's persistent data, so a crash while fallen cannot leave anyone untouchable.</p>
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RevivalService {
    private static final Logger LOG = LogUtils.getLogger();
    /** Revival screen of the fallen player: {@code state} "down" (with the screen data) or "up". */
    public static final String CHANNEL = "revival";
    /** Fallen bodies in the receiver's level: entity ids and player ids. */
    public static final String BODIES_CHANNEL = "revival_bodies";
    public static final String RETURN_ACTION = "revival.return";
    public static final String LEAF_ACTION = "revival.leaf";
    /** Persistent-data marker (place id) while fallen. */
    private static final String MARK = ModInfo.MOD_ID + "_fallen";
    private static final int RESEND_TICKS = 100;
    private static final int BODIES_REFRESH_TICKS = 100;
    private static final double ALLY_MESSAGE_RANGE = 64.0;

    /** One fallen player. */
    private static final class Down {
        final RevivalRules.Place place;
        final ResourceKey<Level> dimension;
        final Vec3 anchor;
        final long since;
        final Component killer;
        long expLost;

        Down(RevivalRules.Place place, ResourceKey<Level> dimension, Vec3 anchor, long since, Component killer) {
            this.place = place;
            this.dimension = dimension;
            this.anchor = anchor;
            this.since = since;
            this.killer = killer;
        }
    }

    private static final Map<UUID, Down> DOWN = new HashMap<>();
    /** Revived players stay untouchable until this server tick. */
    private static final Map<UUID, Long> GRACE = new HashMap<>();
    private static final Map<ResourceKey<Level>, String> LAST_BODIES = new HashMap<>();
    private static MinecraftServer owner;

    private RevivalService() {
    }

    /** Registers the screen's actions and the leaf hook (once, during mod construction). */
    public static void register() {
        UiActions.register(RETURN_ACTION, (player, context, args) -> requestReturn(player));
        UiActions.register(LEAF_ACTION, (player, context, args) -> requestLeaf(player));
        ConsumableItem.setReviveHook(RevivalService::leafOnAlly);
    }

    /** Whether the player is fallen right now (server thread). */
    public static boolean isDown(UUID playerId) {
        return DOWN.containsKey(playerId);
    }

    public static boolean isDown(Entity entity) {
        return entity instanceof ServerPlayer && DOWN.containsKey(entity.getUUID());
    }

    // ------------------------------------------------------------------ falling

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        if (DOWN.containsKey(player.getUUID())) {
            // a real death while fallen (/kill, the void): the vanilla respawn takes over
            DOWN.remove(player.getUUID());
            player.getPersistentData().remove(MARK);
            syncBodies(player.server);
            UiData.send(player, CHANNEL, upMessage("died"));
            return;
        }
        if (player.isCreative() || player.isSpectator() || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        RevivalRules.Place place = placeOf(player);
        if (place == null) {
            return;
        }
        event.setCanceled(true);
        try {
            fall(player, event.getSource(), place);
        } catch (RuntimeException e) {
            LOG.error("Making {} fall failed; reviving them at once", player.getGameProfile().getName(), e);
            DOWN.remove(player.getUUID());
            player.getPersistentData().remove(MARK);
            restoreAbilities(player);
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Where the player is, for the revival rules; null outside the game's dimensions. */
    private static RevivalRules.Place placeOf(ServerPlayer player) {
        if (DungeonLevels.isDungeonLevel(player.level())) {
            return RevivalRules.Place.DUNGEON;
        }
        if (player.level().dimension() == AstoriaDimension.LEVEL) {
            return FieldLayout.inCapital(player.getX(), player.getZ()) ? RevivalRules.Place.CAPITAL
                    : RevivalRules.Place.FIELD;
        }
        return null;
    }

    private static void fall(ServerPlayer player, DamageSource source, RevivalRules.Place place) {
        MinecraftServer server = player.server;
        long now = server.getTickCount();
        UUID id = player.getUUID();
        player.setHealth(1.0F); // the cancelled death must leave the body alive
        player.stopUsingItem();
        player.stopRiding();
        player.setSprinting(false);
        player.clearFire();
        player.removeAllEffects();
        player.setDeltaMovement(Vec3.ZERO);
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        GRACE.remove(id);
        Down down = new Down(place, player.level().dimension(), player.position(), now, killerOf(source));
        DOWN.put(id, down);
        player.getPersistentData().putString(MARK, place.id());

        // monsters look for someone else
        ServerLevel level = player.serverLevel();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0),
                mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
        }
        ServerContext context = ServerContext.current().orElse(null);
        if (context != null) {
            context.combat().service().get(id).ifPresent(Combatant::cancelAction);
            context.game().rankings().onDeath(player);
            context.game().dungeons().onPlayerDeath(player);
            if (RevivalRules.costsExperience(place)) {
                double fraction = com.eternalrealm.config.ERServerConfig.DEATH_EXP_PENALTY.get();
                down.expLost = fraction > 0.0 ? context.playerData().applyDeathPenalty(id, fraction) : 0L;
            }
        }
        // the fall: a toll, souls leaving the body, a word to the allies around
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS,
                0.7F, 0.6F);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.4, player.getZ(), 24, 0.5, 0.3, 0.5,
                0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.2, player.getZ(), 12, 0.6, 0.1,
                0.6, 0.01);
        Component word = Component.translatable("message.eternalrealm.revival.ally_down", player.getDisplayName())
                .withStyle(ChatFormatting.RED);
        for (ServerPlayer other : level.players()) {
            if (other != player && (other.distanceToSqr(player) < ALLY_MESSAGE_RANGE * ALLY_MESSAGE_RANGE
                    || context != null && context.game().parties().sameParty(other.getUUID(), id))) {
                other.displayClientMessage(word, false);
            }
        }
        sendScreen(player, down, context, now);
        syncBodies(server);
    }

    private static Component killerOf(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker != null) {
            return attacker.getDisplayName();
        }
        Entity direct = source.getDirectEntity();
        if (direct != null) {
            return direct.getDisplayName();
        }
        return Component.translatable("revival.eternalrealm.killer.unknown");
    }

    // ------------------------------------------------------------------ the screen

    private static void sendScreen(ServerPlayer player, Down down, ServerContext context, long now) {
        int fallenFor = (int) Math.max(0, now - down.since);
        JsonObject data = new JsonObject();
        data.addProperty("state", "down");
        data.addProperty("place", down.place.id());
        data.addProperty("killer", Component.Serializer.toJson(down.killer));
        data.addProperty("destination", Component.Serializer.toJson(destinationName(player, down, context)));
        data.addProperty("unlock", Math.max(0, RevivalRules.RETURN_UNLOCK_TICKS - fallenFor) / 20.0);
        data.addProperty("auto", Math.max(0, RevivalRules.autoReturnTicks(down.place) - fallenFor) / 20.0);
        data.addProperty("leaves", countLeaves(player));
        data.addProperty("exp", down.expLost);
        data.addProperty("since", down.since);
        JsonArray tips = new JsonArray();
        for (RevivalTips.Tip tip : RevivalTips.choose(situation(player, down, context))) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", tip.kind().translationKey());
            entry.addProperty("icon", tip.kind().icon());
            JsonArray args = new JsonArray();
            tip.args().forEach(args::add);
            entry.add("args", args);
            tips.add(entry);
        }
        data.add("tips", tips);
        UiData.send(player, CHANNEL, data);
    }

    private static JsonObject upMessage(String way) {
        JsonObject data = new JsonObject();
        data.addProperty("state", "up");
        data.addProperty("way", way);
        return data;
    }

    private static RevivalTips.Situation situation(ServerPlayer player, Down down, ServerContext context) {
        CharacterSnapshot snapshot = context == null ? null : context.playerData().snapshot(player.getUUID()).orElse(null);
        int level = snapshot == null ? 1 : snapshot.level();
        int recommended = 0;
        boolean boss = false;
        int partySize = 1;
        if (context != null) {
            if (down.place == RevivalRules.Place.DUNGEON) {
                recommended = context.game().dungeons().recommendedLevel(player.getUUID());
                boss = context.game().dungeons().inBossFight(player.getUUID());
            } else {
                recommended = context.game().world().areaOf(player).map(AreaDefinition::minLevel).orElse(0);
            }
            partySize = context.game().parties().service().partyOf(player.getUUID())
                    .map(party -> party.memberIds().size()).orElse(1);
        }
        int potions = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof ConsumableItem item
                    && item.consumable().effect() instanceof ConsumableEffect.Heal) {
                potions += stack.getCount();
            }
        }
        int unenhanced = 0;
        for (ItemStack stack : player.getInventory().armor) {
            unenhanced += EquipmentStacks.read(stack).filter(data -> data.enhancement() == 0).isPresent() ? 1 : 0;
        }
        unenhanced += EquipmentStacks.read(player.getMainHandItem()).map(EquipmentData::enhancement)
                .filter(enhancement -> enhancement == 0).isPresent() ? 1 : 0;
        return new RevivalTips.Situation(level, recommended, snapshot == null ? 0 : snapshot.unspentStatPoints(),
                snapshot == null ? 0 : snapshot.unspentSkillPoints(), potions, unenhanced, partySize, down.place, boss);
    }

    // ------------------------------------------------------------------ coming back

    private static void requestReturn(ServerPlayer player) {
        Down down = DOWN.get(player.getUUID());
        if (down == null) {
            return;
        }
        int fallenFor = (int) (player.server.getTickCount() - down.since);
        if (!RevivalRules.canReturn(fallenFor)) {
            return;
        }
        returnToSavePoint(player, down, RevivalRules.Way.RETURN);
    }

    private static void requestLeaf(ServerPlayer player) {
        Down down = DOWN.get(player.getUUID());
        if (down == null) {
            return;
        }
        if (!takeLeaf(player)) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.revival.no_leaf")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        DOWN.remove(player.getUUID());
        stand(player, RevivalRules.Way.LEAF_SELF, null, 0.0);
    }

    /** The leaf hook of {@link ConsumableItem}: {@code helper} used a 世界樹の葉 on {@code target}. */
    private static boolean leafOnAlly(ServerPlayer helper, ServerPlayer target) {
        if (helper == target || DOWN.containsKey(helper.getUUID())) {
            return false;
        }
        if (!DOWN.containsKey(target.getUUID())) {
            helper.displayClientMessage(Component.translatable("message.eternalrealm.revival.not_down")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (helper.distanceTo(target) > RevivalRules.ALLY_REACH + 1.0) {
            return false;
        }
        DOWN.remove(target.getUUID());
        stand(target, RevivalRules.Way.LEAF_ALLY, helper, 0.0);
        helper.displayClientMessage(Component.translatable("message.eternalrealm.revival.you_revived",
                target.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    /**
     * A resurrection spell (priest skills): revives the fallen {@code target} where they lie with {@code fraction} of
     * their health. False if the target is not fallen.
     */
    public static boolean resurrect(ServerPlayer caster, ServerPlayer target, double fraction) {
        if (!DOWN.containsKey(target.getUUID()) || DOWN.containsKey(caster.getUUID())) {
            return false;
        }
        DOWN.remove(target.getUUID());
        stand(target, RevivalRules.Way.RESURRECTION, caster, fraction);
        return true;
    }

    /** Fallen players near {@code at} (for area resurrections), nearest first. */
    public static List<ServerPlayer> fallenNear(ServerLevel level, Vec3 at, double radius) {
        List<ServerPlayer> found = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (DOWN.containsKey(player.getUUID()) && player.position().distanceTo(at) <= radius) {
                found.add(player);
            }
        }
        found.sort((a, b) -> Double.compare(a.position().distanceToSqr(at), b.position().distanceToSqr(at)));
        return found;
    }

    private static void returnToSavePoint(ServerPlayer player, Down down, RevivalRules.Way way) {
        DOWN.remove(player.getUUID());
        ServerContext context = ServerContext.current().orElse(null);
        boolean moved = false;
        try {
            moved = switch (down.place) {
                case DUNGEON -> context != null && context.game().dungeons().reviveAtRevivalPoint(player);
                case FIELD -> toWaystone(player, context);
                case CAPITAL -> false;
            };
            if (!moved) {
                moved = toCapital(player);
            }
        } catch (RuntimeException e) {
            LOG.warn("Sending {} to their save point failed", player.getGameProfile().getName(), e);
        }
        stand(player, way, null, 0.0);
        if (moved) {
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 1.0,
                    0.4, 0.05);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.PLAYERS, 0.9F, 1.3F);
        }
    }

    /** To the nearest recorded waystone of the field the player fell in. */
    private static boolean toWaystone(ServerPlayer player, ServerContext context) {
        Optional<FieldLayout.Waystone> stone = savePoint(player, context);
        if (stone.isEmpty() || player.level().dimension() != AstoriaDimension.LEVEL) {
            return false;
        }
        WaystoneService.Arrival arrival = WaystoneService.arrival(player.serverLevel(), stone.get());
        player.teleportTo(player.serverLevel(), arrival.position().x, arrival.position().y, arrival.position().z,
                arrival.yaw(), 0.0F);
        player.fallDistance = 0.0F;
        return true;
    }

    private static Optional<FieldLayout.Waystone> savePoint(ServerPlayer player, ServerContext context) {
        if (context == null) {
            return Optional.empty();
        }
        Set<String> recorded = WaystoneService.recorded(context, player.getUUID());
        int field = FieldLayout.areaAt(player.getX(), player.getZ());
        FieldLayout.Waystone best = null;
        double bestDistance = Double.MAX_VALUE;
        for (FieldLayout.Waystone waystone : Waystones.all()) {
            if (waystone.field() != field || !recorded.contains(waystone.id())) {
                continue;
            }
            double dx = waystone.x() - player.getX();
            double dz = waystone.z() - player.getZ();
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = waystone;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean toCapital(ServerPlayer player) {
        ServerLevel astoria = AstoriaTravel.astoria(player.server).orElse(null);
        if (astoria == null) {
            return false;
        }
        HubLayout layout = Capital.layout(astoria);
        AstoriaTravel.teleport(player, astoria, layout.respawnPoint(), layout.yaw(HubSpot.RESPAWN));
        return true;
    }

    private static Component destinationName(ServerPlayer player, Down down, ServerContext context) {
        return switch (down.place) {
            case DUNGEON -> Component.translatable("revival.eternalrealm.destination.dungeon");
            case CAPITAL -> Component.translatable("revival.eternalrealm.destination.capital");
            case FIELD -> savePoint(player, context)
                    .<Component>map(stone -> Component.translatable("revival.eternalrealm.destination.waystone",
                            Component.translatable(Waystones.translationKey(stone.id()))))
                    .orElse(Component.translatable("revival.eternalrealm.destination.capital"));
        };
    }

    /** Back on their feet: health, combat state, a short grace, light and sound, the screen closes. */
    private static void stand(ServerPlayer player, RevivalRules.Way way, ServerPlayer helper, double resurrection) {
        MinecraftServer server = player.server;
        player.getPersistentData().remove(MARK);
        ServerContext.current().ifPresent(context -> context.combat().onRespawn(player, true));
        double fraction = RevivalRules.healthAfter(way, resurrection);
        player.setHealth((float) Math.max(1.0, player.getMaxHealth() * fraction));
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        GRACE.put(player.getUUID(), (long) server.getTickCount() + RevivalRules.GRACE_TICKS);
        ServerLevel level = player.serverLevel();
        if (way.inPlace()) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 60,
                    0.5, 0.8, 0.5, 0.35);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.5, player.getZ(), 20,
                    0.6, 0.6, 0.6, 0.0);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE,
                    SoundSource.PLAYERS, 0.55F, 1.25F);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        Component message = helper == null ? Component.translatable(way.translationKey())
                : Component.translatable(way.translationKey(), helper.getDisplayName());
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GREEN), false);
        UiData.send(player, CHANNEL, upMessage(way.name().toLowerCase(java.util.Locale.ROOT)));
        syncBodies(server);
    }

    private static boolean takeLeaf(ServerPlayer player) {
        Item leaf = ERGearItems.consumable(Consumable.YGGDRASIL_LEAF);
        if (player.getAbilities().instabuild) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == leaf) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static int countLeaves(ServerPlayer player) {
        Item leaf = ERGearItems.consumable(Consumable.YGGDRASIL_LEAF);
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() == leaf) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void restoreAbilities(ServerPlayer player) {
        player.getAbilities().invulnerable = player.isCreative() || player.isSpectator();
        player.onUpdateAbilities();
    }

    // ------------------------------------------------------------------ every tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null) {
            return;
        }
        if (owner != server) {
            owner = server;
            DOWN.clear();
            GRACE.clear();
            LAST_BODIES.clear();
        }
        long now = server.getTickCount();
        if (!DOWN.isEmpty()) {
            ServerContext context = ServerContext.current().orElse(null);
            for (Map.Entry<UUID, Down> entry : new ArrayList<>(DOWN.entrySet())) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                Down down = entry.getValue();
                if (player == null || player.isRemoved() || !player.isAlive()) {
                    DOWN.remove(entry.getKey());
                    continue;
                }
                try {
                    tickDown(player, down, context, now);
                } catch (RuntimeException e) {
                    LOG.warn("Revival tick of {} failed", player.getGameProfile().getName(), e);
                }
            }
        }
        if (!GRACE.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> iterator = GRACE.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                if (entry.getValue() > now) {
                    continue;
                }
                iterator.remove();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null && !DOWN.containsKey(entry.getKey())) {
                    restoreAbilities(player);
                }
            }
        }
        if (now % BODIES_REFRESH_TICKS == 7) {
            syncBodies(server);
        }
    }

    private static void tickDown(ServerPlayer player, Down down, ServerContext context, long now) {
        if (player.isCreative() || player.isSpectator()) {
            DOWN.remove(player.getUUID());
            stand(player, RevivalRules.Way.RETURN, null, 0.0);
            return;
        }
        if (player.level().dimension() != down.dimension || player.position().distanceTo(down.anchor) > RevivalRules.MOVED_AWAY) {
            // moved on purpose (the dungeon closed, a command): that ends the fall
            DOWN.remove(player.getUUID());
            stand(player, RevivalRules.Way.RETURN, null, 0.0);
            return;
        }
        Vec3 at = player.position();
        double dx = at.x - down.anchor.x;
        double dz = at.z - down.anchor.z;
        if (dx * dx + dz * dz > RevivalRules.ANCHOR_SLACK * RevivalRules.ANCHOR_SLACK
                || Math.abs(at.y - down.anchor.y) > 1.2) {
            player.connection.teleport(down.anchor.x, down.anchor.y, down.anchor.z, player.getYRot(), player.getXRot());
        }
        if (player.getHealth() > 1.0F) {
            player.setHealth(1.0F);
        }
        if (!player.getAbilities().invulnerable) {
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
        }
        int fallenFor = (int) (now - down.since);
        if (RevivalRules.autoReturnDue(down.place, fallenFor)) {
            returnToSavePoint(player, down, RevivalRules.Way.AUTO_RETURN);
            return;
        }
        if (fallenFor % 12 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.3, player.getZ(), 2,
                    0.35, 0.1, 0.35, 0.02);
        }
        if (fallenFor > 0 && fallenFor % RESEND_TICKS == 0) {
            sendScreen(player, down, context, now);
        }
    }

    /**
     * Sends each level's fallen bodies to its players: whenever a level's list changed, and again now and then while
     * someone lies there (players who just arrived).
     */
    private static void syncBodies(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            JsonArray ids = new JsonArray();
            JsonArray uuids = new JsonArray();
            for (ServerPlayer player : level.players()) {
                if (DOWN.containsKey(player.getUUID())) {
                    ids.add(player.getId());
                    uuids.add(player.getUUID().toString());
                }
            }
            JsonObject data = new JsonObject();
            data.add("ids", ids);
            data.add("uuids", uuids);
            String json = data.toString();
            String last = LAST_BODIES.get(level.dimension());
            boolean empty = ids.isEmpty();
            boolean changed = last == null ? !empty : !last.equals(json);
            if (!changed && empty) {
                continue;
            }
            LAST_BODIES.put(level.dimension(), json);
            for (ServerPlayer player : level.players()) {
                UiData.send(player, BODIES_CHANNEL, data);
            }
        }
    }

    // ------------------------------------------------------------------ what a fallen player cannot do

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isDown(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickup(EntityItemPickupEvent event) {
        if (isDown(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ joining, leaving, stopping

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Down down = DOWN.get(player.getUUID());
        if (down != null) {
            // logging out does not revive on the spot: off to the save point, as if the countdown had ended
            returnToSavePoint(player, down, RevivalRules.Way.AUTO_RETURN);
        }
        if (GRACE.remove(player.getUUID()) != null) {
            restoreAbilities(player);
        }
        syncBodies(player.server);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getPersistentData().contains(MARK)) {
            // the server went down while this player was fallen: stand up, full health
            player.getPersistentData().remove(MARK);
            player.setHealth(player.getMaxHealth());
            restoreAbilities(player);
        } else if (player.getAbilities().invulnerable && !player.isCreative() && !player.isSpectator()) {
            restoreAbilities(player);
        }
        String bodies = LAST_BODIES.get(player.level().dimension());
        if (bodies != null) {
            UiData.send(player, BODIES_CHANNEL, com.google.gson.JsonParser.parseString(bodies));
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String bodies = LAST_BODIES.get(player.level().dimension());
            if (bodies != null) {
                UiData.send(player, BODIES_CHANNEL, com.google.gson.JsonParser.parseString(bodies));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (UUID id : new ArrayList<>(DOWN.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.getPersistentData().remove(MARK);
                player.setHealth(player.getMaxHealth());
                restoreAbilities(player);
            }
        }
        for (UUID id : GRACE.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                restoreAbilities(player);
            }
        }
        DOWN.clear();
        GRACE.clear();
        LAST_BODIES.clear();
    }
}
