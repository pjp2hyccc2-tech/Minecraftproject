package com.eternalrealm.world.forge;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.eternalrealm.boss.Contributions;
import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.ExperienceSource;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.ClearRank;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.EquipmentGenerator;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.Materials;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.Inventories;
import com.eternalrealm.job.JobType;
import com.eternalrealm.monster.MonsterRole;
import com.eternalrealm.monster.MonsterScaling;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterStats;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.LootDrops;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.monster.forge.MonsterTags;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiData;
import com.eternalrealm.world.field.CalamityGrowth;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.FieldThreats;
import com.eternalrealm.world.field.LordRoster;
import com.eternalrealm.world.field.LordTurns;
import com.eternalrealm.world.field.Miasma;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The threats of the fields on the server ({@link FieldThreats}, {@link Miasma}).
 *
 * <ul>
 *     <li><b>ヌシ</b>: at most two lords of a field are out at a time ({@link LordRoster}); every 20 minutes the
 *     line-up changes: those out and not beaten leave (after their fight, if players are fighting them) and the
 *     resting ones come out, so every change brings a lord that was not there; a beaten lord stays down until the
 *     next change. A lord that is out appears when a player comes near its home, much tougher than its kin
 *     (more health than the attribute cap allows is carried by a toughness factor), roams its territory and goes to
 *     sleep when nobody is around. It fights like a small boss: all three
 *     special moves of its body, helpers called at 70% and 30% health and a rage from 50% ({@link LordTurns}), and
 *     the players fighting it see its health, the move it winds up and the damage ranking on the boss HUD. Everyone
 *     who did a fair share of the damage gets the lord's rewards; the best contributor is the hero (殊勲).</li>
 *     <li><b>瘴気</b>: monsters slain in a field fill its gauge. An overflowing gauge brings an omen (warning to
 *     everyone, darker sky in the field), then the field's <b>災厄</b> manifests: when a player reaches its lair the lair
 *     is scarred flat and a boss fight starts that anyone close by joins. Subdued, it purifies the field (more
 *     experience for a while); left alone, it departs.</li>
 * </ul>
 * Every few seconds the players in アストリア get the board ({@link #BOARD_CHANNEL}): every field's miasma and phase,
 * its lords, its calamity and its wanderers ({@link WandererService}), for the map, the 手配書 and the gauge under the
 * minimap.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FieldThreatService {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String BOARD_CHANNEL = "threat_board";
    /** Short result of a lord hunt for each contributor (a toast). */
    public static final String RESULT_CHANNEL = "threat_result";
    private static final String LORD_TAG = ModInfo.MOD_ID + "_lord";
    private static final String LORD_ID = "er_lord";
    private static final String TOUGHNESS = "er_toughness";
    /** Persistent data of a monster that answered a lord's call: the lord's id. */
    public static final String HELPER_OF = "er_lord_helper";
    private static final String CALAMITY_TAG = ModInfo.MOD_ID + "_calamity";
    private static final double LORD_DAMAGE = 1.35;
    /** Players this close to a lord in a fight see its HUD (those who hurt it, a little farther). */
    private static final double HUD_RANGE = 28.0;
    private static final double HUD_RANGE_FIGHTER = 48.0;
    private static final int HUD_INTERVAL = 4;
    private static final double HEALTH_CAP = 1000.0;
    private static final int SCAR_COLUMNS_PER_TICK = 160;
    private static final int BOARD_TICKS = 100;
    private static final int SAVE_TICKS = 20 * 60;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private static final class LordState {
        final FieldThreats.Lord lord;
        UUID body;
        long dueAt;
        long lastNearby;
        int level;
        Contributions contributions = new Contributions();
        /** The turns of this appearance taken so far (helpers called, rage). */
        java.util.EnumSet<LordTurns.Turn> turns = LordTurns.none();
        boolean enraged;
        /** In the field's line-up of this turn ({@link LordRoster}). */
        boolean out;
        /** Beaten during this turn: it stays down until the next change. */
        boolean beaten;
        /** Its turn is over but players are fighting it: it leaves when the fight is over. */
        boolean leaving;
        /** When a player last hit it (a tick; -1: not in this appearance). */
        long lastHit = -1L;
        /** Players shown this lord's HUD (they get its end when they leave or it falls). */
        final Set<UUID> hud = new HashSet<>();

        LordState(FieldThreats.Lord lord) {
            this.lord = lord;
        }
    }

    private static final class CalamityState {
        final FieldThreats.Calamity calamity;
        BossFight fight;
        Scar scar;
        long fightStart;
        /** The level this appearance fights at (its boss level plus every defeat so far, CalamityGrowth). */
        int level;

        CalamityState(FieldThreats.Calamity calamity) {
            this.calamity = calamity;
        }
    }

    /** The lair being levelled before the calamity rises from it. */
    private static final class Scar {
        final List<int[]> columns = new ArrayList<>();
        final int groundY;
        final int field;
        int index;

        Scar(int groundY, int field) {
            this.groundY = groundY;
            this.field = field;
        }

        boolean done() {
            return index >= columns.size();
        }
    }

    private static MinecraftServer owner;
    private static Miasma miasma = new Miasma();
    /** How often each field's calamity was subdued: it comes back a level higher every time. */
    private static CalamityGrowth growth = new CalamityGrowth();
    private static final Map<String, LordState> LORDS = new HashMap<>();
    private static final Map<Integer, CalamityState> CALAMITIES = new HashMap<>();
    private static boolean boardDirty = true;
    /** The tick of the next change of the lords' line-up. */
    private static long nextRotation;

    private FieldThreatService() {
    }

    /** Experience multiplier for a monster killed at {@code victim}'s place (purified fields). */
    public static double expMultiplier(Entity victim) {
        if (victim == null || victim.level().dimension() != AstoriaDimension.LEVEL
                || FieldLayout.inCapital(victim.getX(), victim.getZ())) {
            return 1.0;
        }
        return miasma.expMultiplier(FieldLayout.areaAt(victim.getX(), victim.getZ()));
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
            start(server, astoria, now);
        }
        try {
            for (CalamityState state : List.copyOf(CALAMITIES.values())) {
                if (state.fight != null) {
                    state.fight.tick(now);
                } else if (state.scar != null) {
                    scarSlice(astoria, state, now);
                }
            }
            if (now % HUD_INTERVAL == 0) {
                lordHuds(astoria);
            }
            if (now % 20 == 0) {
                tickLords(astoria, now);
                tickCalamities(astoria, now);
                handle(server, astoria, miasma.tick(now), now);
            }
            if (now % BOARD_TICKS == 0 || boardDirty && now % 10 == 0) {
                sendBoard(astoria, now);
            }
            if (now % SAVE_TICKS == 0) {
                ThreatData.get(astoria).store(miasma.toJson());
                ThreatData.get(astoria).storeGrowth(growth.toJson());
            }
        } catch (RuntimeException e) {
            LOG.error("Field threats tick failed", e);
        }
    }

    private static void start(MinecraftServer server, ServerLevel astoria, long now) {
        owner = server;
        miasma = Miasma.fromJson(ThreatData.get(astoria).json());
        growth = CalamityGrowth.fromJson(ThreatData.get(astoria).growthJson());
        LORDS.clear();
        CALAMITIES.clear();
        for (FieldThreats.Lord lord : FieldThreats.lords()) {
            LordState state = new LordState(lord);
            state.dueAt = now + FieldThreats.firstAppearanceTicks(lord, RANDOM);
            LORDS.put(lord.id(), state);
        }
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            for (String id : LordRoster.first(lordIds(field), RANDOM)) {
                LORDS.get(id).out = true;
            }
        }
        nextRotation = now + LordRoster.ROTATION_TICKS;
        boardDirty = true;
    }

    private static List<String> lordIds(int field) {
        List<String> ids = new ArrayList<>();
        for (FieldThreats.Lord lord : FieldThreats.lordsOf(field)) {
            ids.add(lord.id());
        }
        return ids;
    }

    // ------------------------------------------------------------------ lords

    private static void tickLords(ServerLevel astoria, long now) {
        if (now >= nextRotation) {
            rotate(astoria, now);
            nextRotation = now + LordRoster.ROTATION_TICKS;
        }
        for (LordState state : LORDS.values()) {
            FieldThreats.Lord lord = state.lord;
            Entity body = body(astoria, state);
            if (body == null) {
                state.leaving = false;
                if (state.out && !state.beaten && now >= state.dueAt
                        && bodies(astoria, lord.field()) < LordRoster.PER_FIELD
                        && nearestPlayer(astoria, lord.x(), lord.z(), FieldThreats.WAKE_RADIUS) != null
                        && astoria.hasChunkAt(new BlockPos(lord.x(), 64, lord.z()))) {
                    spawnLord(astoria, state, now);
                }
                continue;
            }
            if (state.leaving && !LordRoster.engaged(state.lastHit, now)) {
                leave(astoria, state, body);
                continue;
            }
            if (nearestPlayer(astoria, body.getX(), body.getZ(), FieldThreats.SLEEP_RADIUS) != null) {
                state.lastNearby = now;
            } else if (now - state.lastNearby > FieldThreats.SLEEP_TICKS) {
                body.discard(); // asleep: it comes back, healed, when someone wanders by (if it is still out)
                endHud(astoria, state, body.getUUID());
                state.body = null;
                state.leaving = false;
                state.dueAt = now;
                state.contributions = new Contributions();
                boardDirty = true;
                continue;
            }
            // an aura that tells it apart from its kin
            astoria.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, body.getX(), body.getY() + 0.2, body.getZ(), 3,
                    body.getBbWidth() * 0.6, 0.05, body.getBbWidth() * 0.6, 0.01);
            if (now % 60 == 0) {
                astoria.sendParticles(ParticleTypes.DRAGON_BREATH, body.getX(), body.getY() + body.getBbHeight() * 0.5,
                        body.getZ(), 6, 0.4, 0.5, 0.4, 0.01);
            }
        }
    }

    /** The body of a lord in アストリア, if it is there and alive (a gone body is forgotten). */
    private static Entity body(ServerLevel astoria, LordState state) {
        Entity body = state.body == null ? null : astoria.getEntity(state.body);
        if (body != null && (!body.isAlive() || body.isRemoved())) {
            state.body = null;
            return null;
        }
        return body;
    }

    /** Lords of {@code field} whose body is in the field now (out, or leaving after a fight). */
    private static int bodies(ServerLevel astoria, int field) {
        int count = 0;
        for (LordState state : LORDS.values()) {
            if (state.lord.field() == field && body(astoria, state) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * A change of the line-up ({@link LordRoster}): in every field the lords whose turn is over and who were not
     * beaten leave (those in a fight when it is over), the newcomers may appear at once, the beaten ones are up again.
     */
    private static void rotate(ServerLevel astoria, long now) {
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            List<String> ids = lordIds(field);
            List<String> out = new ArrayList<>();
            for (String id : ids) {
                if (LORDS.get(id).out) {
                    out.add(id);
                }
            }
            List<String> next = LordRoster.next(ids, out, RANDOM);
            for (String id : ids) {
                LordState state = LORDS.get(id);
                boolean stays = next.contains(id);
                if (state.out && !stays) {
                    Entity body = body(astoria, state);
                    if (body != null) {
                        if (LordRoster.engaged(state.lastHit, now)) {
                            state.leaving = true;
                        } else {
                            leave(astoria, state, body);
                        }
                    }
                }
                if (stays && (!state.out || state.beaten)) {
                    state.dueAt = now;
                }
                state.out = stays;
                state.beaten = false;
            }
        }
        boardDirty = true;
    }

    /** Tests: the lords of {@code field} in the current line-up. */
    public static List<String> outForTest(int field) {
        List<String> out = new ArrayList<>();
        for (String id : lordIds(field)) {
            LordState state = LORDS.get(id);
            if (state != null && state.out) {
                out.add(id);
            }
        }
        return out;
    }

    /** Tests: a change of the line-up now (the next one is a full turn later). */
    public static void rotateForTest(ServerLevel astoria) {
        long now = astoria.getServer().getTickCount();
        rotate(astoria, now);
        nextRotation = now + LordRoster.ROTATION_TICKS;
    }

    /** A lord whose turn is over goes away (nobody is fighting it). */
    private static void leave(ServerLevel astoria, LordState state, Entity body) {
        Component message = Component.translatable("threat.eternalrealm.lord_leaves",
                Component.translatable(state.lord.translationKey()).withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : astoria.players()) {
            if (player.distanceToSqr(body) < 128.0 * 128.0) {
                player.displayClientMessage(message, false);
            }
        }
        astoria.sendParticles(ParticleTypes.LARGE_SMOKE, body.getX(), body.getY() + 1.0, body.getZ(), 40, 0.8, 1.0,
                0.8, 0.03);
        astoria.sendParticles(ParticleTypes.SOUL, body.getX(), body.getY() + 0.5, body.getZ(), 20, 0.6, 0.6, 0.6,
                0.04);
        endHud(astoria, state, body.getUUID());
        body.discard();
        state.body = null;
        state.leaving = false;
        state.lastHit = -1L;
        state.contributions = new Contributions();
        boardDirty = true;
    }

    /**
     * Tests: the body of lord {@code lordId} made and tracked like a real appearance, but at {@code pos} in
     * {@code level} (no announcement, no territory). Empty for an unknown lord.
     */
    public static Optional<Mob> spawnLordForTest(ServerLevel level, String lordId, BlockPos pos) {
        FieldThreats.Lord lord = FieldThreats.lord(lordId).orElse(null);
        MonsterTemplate template = lord == null ? null : Monsters.byId(lord.template()).orElse(null);
        if (template == null) {
            return Optional.empty();
        }
        LordState state = LORDS.computeIfAbsent(lordId, id -> new LordState(lord));
        int lordLevel = Math.min(MonsterScaling.MAX_LEVEL, template.levels().max() + FieldThreats.LORD_LEVEL_BONUS);
        Optional<Mob> made = MonsterFactory.create(level, new MonsterSpawn(template, lordLevel), false);
        made.ifPresent(mob -> {
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            mob.getPersistentData().putString(LORD_ID, lord.id());
            mob.getPersistentData().putDouble(TOUGHNESS, 1.0);
            mob.addTag(LORD_TAG);
            if (mob instanceof com.eternalrealm.entity.ERMonster monster) {
                monster.setSpecialMoves(com.eternalrealm.monster.SpecialMoves.LORD_MOVES);
            }
            armSignature(mob, lord);
            state.turns = LordTurns.none();
            state.enraged = false;
            state.contributions = new Contributions();
            state.level = lordLevel;
            state.body = mob.getUUID();
            level.addFreshEntity(mob);
        });
        return made;
    }

    /**
     * A lord whose body has a weapon hand carries its signature weapon of the armory (the lord's rewards may hand it
     * out); lords on beast, golem, flyer or archer bodies keep their natural weapons.
     */
    private static void armSignature(Mob mob, FieldThreats.Lord lord) {
        if (com.eternalrealm.item.Signatures.lordWieldsWeapon(lord.id())) {
            com.eternalrealm.item.Signatures.lordWeapon(lord.id())
                    .ifPresent(design -> com.eternalrealm.item.forge.ArmoryStacks.arm(mob, design));
        }
    }

    /** Tests: whether the lord {@code lordId} has flown into its rage in its current appearance. */
    public static boolean enragedForTest(String lordId) {
        LordState state = LORDS.get(lordId);
        return state != null && state.enraged;
    }

    private static void spawnLord(ServerLevel astoria, LordState state, long now) {
        FieldThreats.Lord lord = state.lord;
        MonsterTemplate template = Monsters.byId(lord.template()).orElse(null);
        if (template == null) {
            state.dueAt = Long.MAX_VALUE;
            LOG.warn("Field lord {} names unknown monster template {}", lord.id(), lord.template());
            return;
        }
        int level = Math.min(MonsterScaling.MAX_LEVEL, template.levels().max() + FieldThreats.LORD_LEVEL_BONUS);
        Optional<Mob> made = MonsterFactory.create(astoria, new MonsterSpawn(template, level), true);
        if (made.isEmpty()) {
            state.dueAt = now + 20L * 60L;
            return;
        }
        Mob mob = made.get();
        BlockPos ground = surface(astoria, lord.x(), lord.z());
        mob.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, RANDOM.nextFloat() * 360.0F, 0.0F);
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        double wanted = mob.getMaxHealth() * lord.healthMultiplier();
        double capped = Math.min(wanted, HEALTH_CAP);
        if (health != null) {
            health.setBaseValue(capped);
        }
        mob.setHealth(mob.getMaxHealth());
        mob.getPersistentData().putString(LORD_ID, lord.id());
        mob.getPersistentData().putDouble(TOUGHNESS, capped / wanted);
        mob.addTag(LORD_TAG);
        MonsterTags.scaleDamage(mob, LORD_DAMAGE);
        if (mob instanceof PathfinderMob walker) {
            walker.restrictTo(ground, lord.territory());
        }
        mob.setPersistenceRequired();
        if (mob instanceof com.eternalrealm.entity.ERMonster monster) {
            monster.setSpecialMoves(com.eternalrealm.monster.SpecialMoves.LORD_MOVES); // all three moves of its body
        }
        state.turns = LordTurns.none();
        state.enraged = false;
        state.lastHit = -1L;
        mob.setCustomName(Component.translatable("threat.eternalrealm.lord.nameplate", level,
                Component.translatable(lord.translationKey())).withStyle(ChatFormatting.LIGHT_PURPLE));
        mob.setCustomNameVisible(true);
        armSignature(mob, lord);
        state.body = mob.getUUID(); // before it joins the level: the join check lets only tracked lords in
        if (!astoria.addFreshEntity(mob)) {
            state.body = null;
            state.dueAt = now + 20L * 30L;
            return;
        }
        state.level = level;
        state.lastNearby = now;
        state.contributions = new Contributions();
        boardDirty = true;
        astoria.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1.0, mob.getZ(), 40, 0.8, 1.0, 0.8, 0.05);
        astoria.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.3, mob.getZ(), 30, 1.0, 0.2, 1.0,
                0.02);
        astoria.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE,
                1.4F, 0.7F);
        Component message = Component.translatable("threat.eternalrealm.lord_appears",
                Component.translatable(lord.translationKey()).withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : astoria.players()) {
            if (player.distanceToSqr(mob) < 128.0 * 128.0) {
                player.displayClientMessage(message, false);
            }
        }
    }

    private static Optional<LordState> lordOf(Entity entity) {
        if (entity == null || !entity.getTags().contains(LORD_TAG)) {
            return Optional.empty();
        }
        LordState state = LORDS.get(entity.getPersistentData().getString(LORD_ID));
        return state != null && entity.getUUID().equals(state.body) ? Optional.of(state) : Optional.empty();
    }

    /** Hits on a lord: its toughness carries the health above the attribute cap; the attacker's share is noted. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !victim.getTags().contains(LORD_TAG)) {
            return;
        }
        double toughness = victim.getPersistentData().contains(TOUGHNESS)
                ? victim.getPersistentData().getDouble(TOUGHNESS) : 1.0;
        float amount = (float) (event.getAmount() * Math.max(0.05, Math.min(1.0, toughness)));
        event.setAmount(amount);
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            long now = player.server.getTickCount();
            lordOf(victim).ifPresent(state -> {
                state.lastHit = now;
                state.contributions.damage(player.getUUID(), player.getGameProfile().getName(), jobOf(player),
                        Math.min(amount, victim.getHealth()) / Math.max(0.05, toughness));
            });
        }
    }

    /**
     * What a lord's blows cost the players (for the hero); and a lord's turns when a blow takes its health past a mark.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            LivingEntity victim = event.getEntity();
            if (!victim.level().isClientSide() && victim.getTags().contains(LORD_TAG)) {
                lordOf(victim).ifPresent(state -> turns((ServerLevel) victim.level(), state, victim,
                        (victim.getHealth() - event.getAmount()) / Math.max(1.0F, victim.getMaxHealth())));
            }
            return;
        }
        Entity attacker = event.getSource().getEntity();
        lordOf(attacker).ifPresent(state -> state.contributions.taken(player.getUUID(), player.getGameProfile().getName(),
                jobOf(player), Math.min(event.getAmount(), player.getHealth())));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || victim.level().dimension() != AstoriaDimension.LEVEL
                || !MonsterTags.givesRewards(victim)) {
            return;
        }
        long now = victim.getServer() == null ? 0L : victim.getServer().getTickCount();
        int field = FieldLayout.inCapital(victim.getX(), victim.getZ()) ? 0
                : FieldLayout.areaAt(victim.getX(), victim.getZ());
        Optional<LordState> lord = lordOf(victim);
        int points;
        if (lord.isPresent()) {
            points = Miasma.LORD_KILL;
        } else {
            points = MonsterTags.template(victim).map(template -> template.role() == MonsterRole.ELITE
                    ? Miasma.ELITE_KILL : Miasma.NORMAL_KILL).orElse(Miasma.NORMAL_KILL);
        }
        if (field > 0 && victim.getServer() != null) {
            ServerLevel astoria = (ServerLevel) victim.level();
            handle(victim.getServer(), astoria, miasma.addKill(field, points, now), now);
            boardDirty = boardDirty || miasma.gauge(field) % 50 < points;
        }
        lord.ifPresent(state -> lordDown((ServerLevel) victim.level(), state, victim, event.getSource().getEntity(),
                now));
    }

    private static void lordDown(ServerLevel astoria, LordState state, LivingEntity body, Entity killer, long now) {
        endHud(astoria, state, body.getUUID());
        state.body = null;
        state.beaten = true; // down until the next change of the line-up
        state.leaving = false;
        state.lastHit = -1L;
        if (killer instanceof ServerPlayer player) {
            state.contributions.lastHit(player.getUUID());
        }
        boardDirty = true;
        Contributions contributions = state.contributions;
        state.contributions = new Contributions();
        Optional<Contributions.Line> hero = contributions.hero();
        Component lordName = Component.translatable(state.lord.translationKey()).withStyle(ChatFormatting.LIGHT_PURPLE);
        Component message = Component.translatable("threat.eternalrealm.lord_down", lordName,
                hero.map(line -> Component.literal(line.name()).withStyle(ChatFormatting.GOLD))
                        .orElse(Component.literal("-"))).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : astoria.players()) {
            if (player.distanceToSqr(body) < 160.0 * 160.0) {
                player.displayClientMessage(message, false);
            }
        }
        astoria.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, body.getX(), body.getY() + 1.0, body.getZ(), 50, 0.6,
                0.8, 0.6, 0.3);
        ServerContext context = ServerContext.current().orElse(null);
        MonsterTemplate template = Monsters.byId(state.lord.template()).orElse(null);
        if (context == null || template == null) {
            return;
        }
        MonsterStats stats = MonsterScaling.stats(template, Math.max(1, state.level));
        double total = contributions.totalDamage();
        List<Contributions.Line> lines = contributions.byDamage();
        for (Contributions.Line line : lines) {
            ServerPlayer player = astoria.getServer().getPlayerList().getPlayer(line.id());
            boolean isHero = hero.map(best -> best.id().equals(line.id())).orElse(false);
            if (player == null || total <= 0.0 || line.share() < FieldThreats.REWARD_SHARE && !isHero) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            int field = state.lord.field();
            addMaterial(items, com.eternalrealm.monster.Materials.fieldUncommon(field), 2 + RANDOM.nextInt(2));
            addMaterial(items, com.eternalrealm.monster.Materials.fieldElite(field), isHero ? 2 : 1);
            if (RANDOM.nextDouble() < (isHero ? 0.45 : 0.2)) {
                items.add(equipment(context, player, field, isHero ? ItemRarity.RARE : ItemRarity.UNCOMMON));
            }
            // the lord's signature weapon (armory)
            if (RANDOM.nextDouble() < com.eternalrealm.item.ArmoryDrops.lordWeaponChance(isHero)) {
                com.eternalrealm.item.Signatures.lordWeapon(state.lord.id()).ifPresent(design -> {
                    ItemStack weapon = com.eternalrealm.item.forge.ArmoryStacks.reward(player, design,
                            ItemRarity.RARE, RANDOM);
                    items.add(weapon);
                    com.eternalrealm.item.forge.ArmoryStacks.announce(player, weapon, design);
                });
            }
            // the lord's own trinket (名品の装身具)
            if (RANDOM.nextDouble() < com.eternalrealm.item.NamedTrinkets.lordDropChance(isHero)) {
                com.eternalrealm.item.NamedTrinkets.forLord(state.lord.id()).ifPresent(design -> {
                    ItemStack trinket = com.eternalrealm.item.forge.ArmoryStacks.reward(player, design,
                            ItemRarity.UNCOMMON, RANDOM);
                    items.add(trinket);
                    com.eternalrealm.item.forge.ArmoryStacks.announce(player, trinket, design);
                });
            }
            if (RANDOM.nextDouble() < 0.15) {
                items.add(new ItemStack(ERGearItems.consumable(Consumable.EVERBLOOM_PETAL)));
            }
            // about one kill in a thousand: one of the lord's relics (金枠)
            com.eternalrealm.item.forge.RelicDrops.roll(player, com.eternalrealm.item.Relics.SourceKind.LORD,
                    state.lord.id(), RANDOM).ifPresent(items::add);
            long exp = Math.round(stats.exp() * 2.0 * expMultiplier(body));
            long gold = stats.goldMax() * 2;
            give(context, player, items, exp, gold);
            JsonObject data = new JsonObject();
            data.addProperty("kind", "lord");
            data.addProperty("name", Component.Serializer.toJson(Component.translatable(state.lord.translationKey())));
            data.addProperty("rank", contributions.rank(line.id()));
            data.addProperty("share", Math.round(line.share() * 1000.0) / 1000.0);
            data.addProperty("hero", isHero);
            data.addProperty("exp", exp);
            data.addProperty("gold", gold);
            JsonArray rewards = new JsonArray();
            items.forEach(stack -> rewards.add(stack.save(new CompoundTag()).toString()));
            data.add("rewards", rewards);
            UiData.send(player, RESULT_CHANNEL, data);
        }
    }

    // ------------------------------------------------------------------ the lord fight

    /** The turns whose marks the lord's health fraction {@code health} has reached: calls for help, the rage. */
    private static void turns(ServerLevel level, LordState state, LivingEntity body, double health) {
        if (health <= 0.0) {
            return; // the killing blow
        }
        for (LordTurns.Turn turn : LordTurns.due(health, state.turns)) {
            state.turns.add(turn);
            Component name = Component.translatable(state.lord.translationKey()).withStyle(ChatFormatting.LIGHT_PURPLE);
            Component message = Component.translatable(turn.messageKey(), name).withStyle(ChatFormatting.RED);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(body) < 48.0 * 48.0) {
                    player.displayClientMessage(message, false);
                }
            }
            if (turn.calls()) {
                callHelpers(level, state, body);
            } else {
                rage(level, state, body);
            }
        }
    }

    /** Monsters of the lord's field answer its call and join its fight. */
    private static void callHelpers(ServerLevel level, LordState state, LivingEntity body) {
        com.eternalrealm.world.AreaDefinition area = com.eternalrealm.world.Areas.require(state.lord.field());
        com.eternalrealm.monster.MonsterTable table = Monsters.tableFor(area);
        LivingEntity target = body instanceof Mob mob ? mob.getTarget() : null;
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON,
                SoundSource.HOSTILE, 1.4F, 0.8F);
        for (int i = 0; i < LordTurns.HELPERS; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double distance = 2.5 + body.getBbWidth();
            int x = (int) Math.floor(body.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(body.getZ() + Math.sin(angle) * distance);
            BlockPos ground = surface(level, x, z);
            if (Math.abs(ground.getY() - body.getY()) > 4.0) {
                ground = body.blockPosition();
            }
            MonsterSpawn spawn = table.pick(area.levelRange(), RANDOM);
            BlockPos at = ground;
            MonsterFactory.spawn(level, spawn, at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                    (float) Math.toDegrees(angle)).ifPresent(helper -> {
                        helper.getPersistentData().putString(HELPER_OF, state.lord.id());
                        if (target != null && target.isAlive()) {
                            helper.setTarget(target);
                        }
                        level.sendParticles(ParticleTypes.SOUL, helper.getX(), helper.getY() + 0.5, helper.getZ(), 16,
                                0.4, 0.6, 0.4, 0.03);
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, helper.getX(), helper.getY() + 0.2, helper.getZ(),
                                10, 0.5, 0.1, 0.5, 0.02);
                    });
        }
    }

    /** The roar at half health: everyone close is thrown back, and the lord fights faster and harder from now on. */
    private static void rage(ServerLevel level, LordState state, LivingEntity body) {
        state.enraged = true;
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.8F,
                0.6F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, body.getX(), body.getY() + body.getBbHeight() + 0.3,
                body.getZ(), 8, 0.6, 0.3, 0.6, 0.0);
        level.sendParticles(ParticleTypes.LAVA, body.getX(), body.getY() + 0.5, body.getZ(), 24, 1.2, 0.4, 1.2, 0.0);
        level.sendParticles(ParticleTypes.SONIC_BOOM, body.getX(), body.getY() + body.getBbHeight() * 0.6, body.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        double radius = LordTurns.ROAR_RADIUS + body.getBbWidth() * 0.5;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - body.getX();
            double dz = player.getZ() - body.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < radius && !player.isSpectator() && !player.isCreative()) {
                double push = 1.1 * (1.0 - distance / radius) + 0.4;
                player.push(dx / Math.max(0.3, distance) * push, 0.35, dz / Math.max(0.3, distance) * push);
                player.hurtMarked = true;
            }
        }
        body.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60 * 10, 0, false, false));
        MonsterTags.scaleDamage(body, LordTurns.RAGE_DAMAGE);
    }

    /**
     * The boss HUD for the players fighting a lord: its true health (above the attribute cap), the special move it
     * winds up, its rage and the damage ranking. Sent every few ticks while a lord has someone to show it to.
     */
    private static void lordHuds(ServerLevel astoria) {
        for (LordState state : LORDS.values()) {
            Entity entity = state.body == null ? null : astoria.getEntity(state.body);
            if (!(entity instanceof Mob body) || !body.isAlive()) {
                continue;
            }
            boolean fighting = body.getTarget() instanceof ServerPlayer || !state.contributions.byDamage().isEmpty();
            Set<UUID> audience = new HashSet<>();
            if (fighting) {
                for (ServerPlayer player : astoria.players()) {
                    boolean fighter = state.contributions.byDamage().stream().anyMatch(l -> l.id().equals(player.getUUID()));
                    double range = fighter ? HUD_RANGE_FIGHTER : HUD_RANGE;
                    if (player.distanceToSqr(body) < range * range) {
                        audience.add(player.getUUID());
                    }
                }
            }
            for (UUID gone : List.copyOf(state.hud)) {
                if (!audience.contains(gone)) {
                    ServerPlayer player = astoria.getServer().getPlayerList().getPlayer(gone);
                    if (player != null) {
                        UiData.send(player, BossFight.HUD_CHANNEL, hudEnd(body.getUUID()));
                    }
                    state.hud.remove(gone);
                }
            }
            if (audience.isEmpty()) {
                continue;
            }
            JsonObject base = lordHud(state, body);
            List<Contributions.Line> ranking = state.contributions.byDamage();
            for (UUID id : audience) {
                ServerPlayer player = astoria.getServer().getPlayerList().getPlayer(id);
                if (player == null) {
                    continue;
                }
                JsonObject data = base.deepCopy();
                JsonArray rows = new JsonArray();
                int place = 0;
                for (Contributions.Line line : ranking) {
                    place++;
                    boolean me = line.id().equals(id);
                    if (place > BossFight.RANKING_ROWS && !me) {
                        continue;
                    }
                    JsonObject row = new JsonObject();
                    row.addProperty("pos", place);
                    row.addProperty("name", line.name());
                    row.addProperty("job", line.job());
                    row.addProperty("damage", Math.round(line.damage()));
                    row.addProperty("share", Math.round(line.share() * 1000.0) / 1000.0);
                    if (me) {
                        row.addProperty("me", true);
                    }
                    rows.add(row);
                }
                data.add("rank", rows);
                UiData.send(player, BossFight.HUD_CHANNEL, data);
                state.hud.add(id);
            }
        }
    }

    private static JsonObject lordHud(LordState state, Mob body) {
        double toughness = body.getPersistentData().contains(TOUGHNESS) ? body.getPersistentData().getDouble(TOUGHNESS)
                : 1.0;
        double scale = 1.0 / Math.max(0.05, Math.min(1.0, toughness));
        double max = body.getMaxHealth() * scale;
        double hp = Math.max(0.0, body.getHealth()) * scale;
        JsonObject data = new JsonObject();
        data.addProperty("id", body.getUUID().toString());
        data.addProperty("name", Component.Serializer.toJson(Component.translatable(state.lord.translationKey())));
        data.addProperty("boss", state.lord.id());
        data.addProperty("level", state.level);
        data.addProperty("difficulty", "lord");
        data.addProperty("difficultyKey", "threat.eternalrealm.hud.lord");
        data.addProperty("hp", Math.round(hp * 10.0) / 10.0);
        data.addProperty("max", Math.round(max * 10.0) / 10.0);
        data.addProperty("layers", Math.max(3, Math.min(10, (int) Math.round(max / 450.0))));
        data.addProperty("phase", 0);
        data.addProperty("phases", 1);
        data.addProperty("enraged", state.enraged);
        data.addProperty("enrage", -1.0);
        if (body instanceof com.eternalrealm.entity.ERMonster monster && monster.currentSpecialMove() != null
                && monster.specialTicksLeft() > 0) {
            com.eternalrealm.monster.SpecialMove move = monster.currentSpecialMove();
            JsonObject cast = new JsonObject();
            cast.addProperty("key", "special." + ModInfo.MOD_ID + "." + move.id());
            cast.addProperty("total", move.windupTicks() / 20.0);
            cast.addProperty("left", monster.specialTicksLeft() / 20.0);
            data.add("cast", cast);
        }
        return data;
    }

    private static JsonObject hudEnd(UUID body) {
        JsonObject end = new JsonObject();
        end.addProperty("id", body.toString());
        end.addProperty("end", true);
        return end;
    }

    /** The lord is gone (fallen or asleep): its HUD goes away for everyone who had it. */
    private static void endHud(ServerLevel level, LordState state, UUID body) {
        for (UUID id : state.hud) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                UiData.send(player, BossFight.HUD_CHANNEL, hudEnd(body));
            }
        }
        state.hud.clear();
    }

    // ------------------------------------------------------------------ miasma and calamities

    private static void handle(MinecraftServer server, ServerLevel astoria, List<Miasma.Event> events, long now) {
        for (Miasma.Event event : events) {
            boardDirty = true;
            int field = event.field();
            Component fieldName = Component.translatable(fieldKey(field)).withStyle(ChatFormatting.AQUA);
            Optional<FieldThreats.Calamity> calamity = FieldThreats.calamityOf(field);
            Component calamityName = calamity.<Component>map(c -> Component.translatable(c.translationKey())
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)).orElse(Component.literal("?"));
            switch (event.kind()) {
                case OMEN -> {
                    broadcast(server, Component.translatable("threat.eternalrealm.omen", fieldName)
                            .withStyle(ChatFormatting.DARK_PURPLE));
                    for (ServerPlayer player : astoria.players()) {
                        if (fieldOf(player) == field) {
                            player.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.6F, 0.6F);
                        }
                    }
                }
                case MANIFEST -> {
                    calamity.ifPresent(c -> CALAMITIES.put(field, new CalamityState(c)));
                    broadcast(server, Component.translatable("threat.eternalrealm.manifest", fieldName, calamityName,
                            calamity.map(c -> calamityLevel(c)).orElse(1)).withStyle(ChatFormatting.RED));
                    for (ServerPlayer player : astoria.players()) {
                        if (fieldOf(player) == field) {
                            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5F, 0.7F);
                        }
                    }
                }
                case DEPART -> {
                    CalamityState state = CALAMITIES.remove(field);
                    if (state != null && state.fight != null) {
                        Entity body = state.fight.body();
                        state.fight.end();
                        if (body.isAlive()) {
                            astoria.sendParticles(ParticleTypes.REVERSE_PORTAL, body.getX(), body.getY() + 2.0,
                                    body.getZ(), 120, 1.5, 2.5, 1.5, 0.1);
                            body.discard();
                        }
                    }
                    broadcast(server, Component.translatable("threat.eternalrealm.depart", calamityName, fieldName)
                            .withStyle(ChatFormatting.GRAY));
                }
                case PURIFIED -> broadcast(server, Component.translatable("threat.eternalrealm.purified", fieldName,
                        Miasma.PURIFIED_TICKS / 1200).withStyle(ChatFormatting.AQUA));
                case CALM -> {
                    for (ServerPlayer player : astoria.players()) {
                        if (fieldOf(player) == field) {
                            player.displayClientMessage(Component.translatable("threat.eternalrealm.calm", fieldName)
                                    .withStyle(ChatFormatting.GRAY), false);
                        }
                    }
                }
            }
        }
    }

    private static void tickCalamities(ServerLevel astoria, long now) {
        for (CalamityState state : List.copyOf(CALAMITIES.values())) {
            FieldThreats.Calamity calamity = state.calamity;
            if (state.fight == null) {
                if (state.scar == null && nearestPlayer(astoria, calamity.x(), calamity.z(), FieldThreats.JOIN_RADIUS) != null
                        && astoria.hasChunkAt(new BlockPos(calamity.x(), 64, calamity.z()))) {
                    state.scar = planScar(astoria, calamity);
                }
                continue;
            }
            if (state.fight.isOver()) {
                continue;
            }
            Entity body = state.fight.body();
            boolean engaged = false;
            for (ServerPlayer player : astoria.players()) {
                if (player.isSpectator() || player.distanceToSqr(body) > FieldThreats.JOIN_RADIUS * FieldThreats.JOIN_RADIUS) {
                    continue;
                }
                state.fight.join(player);
                engaged = engaged || !com.eternalrealm.revival.forge.RevivalService.isDown(player.getUUID());
            }
            if (engaged) {
                miasma.engaged(calamity.field(), now);
            }
            // the omen sky over the lair: dark motes rising
            if (now % 40 == 0) {
                astoria.sendParticles(ParticleTypes.SQUID_INK, calamity.x() + 0.5, body.getY() + 0.3, calamity.z() + 0.5,
                        20, FieldThreats.LAIR_RADIUS * 0.5, 0.2, FieldThreats.LAIR_RADIUS * 0.5, 0.01);
            }
        }
    }

    /** Levels the lair: the median ground height, cleared above and filled below, scarred blocks on top. */
    private static Scar planScar(ServerLevel astoria, FieldThreats.Calamity calamity) {
        int radius = FieldThreats.LAIR_RADIUS + 2;
        List<Integer> heights = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 3) {
            for (int dz = -radius; dz <= radius; dz += 3) {
                if (dx * dx + dz * dz <= radius * radius) {
                    heights.add(astoria.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, calamity.x() + dx,
                            calamity.z() + dz));
                }
            }
        }
        heights.sort(Integer::compare);
        int groundY = heights.get(heights.size() / 2) - 1;
        Scar scar = new Scar(groundY, calamity.field());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    scar.columns.add(new int[]{calamity.x() + dx, calamity.z() + dz, dx * dx + dz * dz});
                }
            }
        }
        // from the middle outwards, so the scar spreads
        scar.columns.sort((a, b) -> Integer.compare(a[2], b[2]));
        return scar;
    }

    private static void scarSlice(ServerLevel astoria, CalamityState state, long now) {
        Scar scar = state.scar;
        int inner = FieldThreats.LAIR_RADIUS * FieldThreats.LAIR_RADIUS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int n = 0; n < SCAR_COLUMNS_PER_TICK && !scar.done(); n++) {
            int[] column = scar.columns.get(scar.index++);
            int x = column[0];
            int z = column[1];
            int top = astoria.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            for (int y = Math.min(top + 1, scar.groundY + 28); y > scar.groundY; y--) {
                pos.set(x, y, z);
                if (!astoria.getBlockState(pos).isAir()) {
                    astoria.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            for (int y = scar.groundY - 1; y >= scar.groundY - 6; y--) {
                pos.set(x, y, z);
                BlockState below = astoria.getBlockState(pos);
                if (below.isAir() || !below.getFluidState().isEmpty() || below.canBeReplaced()) {
                    astoria.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            pos.set(x, scar.groundY, z);
            boolean rim = column[2] > inner;
            astoria.setBlock(pos, scarBlock(scar.field, x, z, rim), Block.UPDATE_CLIENTS);
        }
        if (!scar.done()) {
            if (now % 10 == 0) {
                astoria.playSound(null, state.calamity.x(), scar.groundY + 1, state.calamity.z(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.5F, 0.5F);
            }
            return;
        }
        state.scar = null;
        startFight(astoria, state, now);
    }

    static BlockState scarBlock(int field, int x, int z, boolean rim) {
        int hash = (x * 73856093) ^ (z * 19349663);
        int pick = Math.floorMod(hash, 7);
        if (rim) {
            return switch (field) {
                case 7 -> pick < 3 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                case 5, 10 -> pick < 3 ? Blocks.CUT_SANDSTONE.defaultBlockState()
                        : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                default -> pick < 3 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                        : pick < 5 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            };
        }
        Block[] blocks = switch (field) {
            case 2 -> new Block[]{Blocks.PODZOL, Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT};
            case 3 -> new Block[]{Blocks.GRAVEL, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF, Blocks.COARSE_DIRT};
            case 4, 9 -> new Block[]{Blocks.MUD, Blocks.PACKED_MUD, Blocks.COARSE_DIRT, Blocks.GRAVEL};
            case 5 -> new Block[]{Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.COARSE_DIRT, Blocks.GRAVEL};
            case 6 -> new Block[]{Blocks.SNOW_BLOCK, Blocks.GRAVEL, Blocks.STONE, Blocks.ANDESITE};
            case 7 -> new Block[]{Blocks.BLACKSTONE, Blocks.SOUL_SOIL, Blocks.BASALT, Blocks.SMOOTH_BASALT};
            case 10 -> new Block[]{Blocks.SAND, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.SANDSTONE};
            default -> new Block[]{Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.GRAVEL};
        };
        return blocks[Math.floorMod(hash >> 3, blocks.length)].defaultBlockState();
    }

    private static void startFight(ServerLevel astoria, CalamityState state, long now) {
        FieldThreats.Calamity calamity = state.calamity;
        BossDefinition definition = grown(calamity).orElse(null);
        if (definition == null) {
            handle(astoria.getServer(), astoria, miasma.departed(calamity.field(), now), now);
            return;
        }
        BlockPos spawn = surface(astoria, calamity.x(), calamity.z());
        Vec3 center = Vec3.atBottomCenterOf(spawn);
        Set<UUID> participants = new HashSet<>();
        for (ServerPlayer player : astoria.players()) {
            if (!player.isSpectator() && player.distanceToSqr(center) < FieldThreats.JOIN_RADIUS * FieldThreats.JOIN_RADIUS) {
                participants.add(player.getUUID());
            }
        }
        if (participants.isEmpty()) {
            return; // everyone left while the lair was scarred: it rises when someone comes back
        }
        // a field calamity is tuned for at least two fighters: whoever is around joins on the way
        Set<UUID> tuning = new HashSet<>(participants);
        while (tuning.size() < 2) {
            tuning.add(UUID.randomUUID());
        }
        Optional<BossFight> started = BossFight.start(astoria, definition, Difficulty.NORMAL, spawn, center,
                FieldThreats.LAIR_RADIUS, tuning, killed -> {
                }, defeated -> calamityDown(astoria, state));
        if (started.isEmpty()) {
            handle(astoria.getServer(), astoria, miasma.departed(calamity.field(), now), now);
            return;
        }
        BossFight fight = started.get();
        Mob body = fight.body();
        // the boss HUD puts the (grown) level in front of this name, so the nameplate itself carries none
        body.setCustomName(Component.translatable("threat.eternalrealm.calamity.nameplate",
                Component.translatable(calamity.translationKey())).withStyle(ChatFormatting.DARK_RED));
        state.level = definition.level();
        body.addTag(CALAMITY_TAG);
        body.getPersistentData().putInt(CALAMITY_TAG, calamity.field());
        state.fight = fight;
        state.fightStart = now;
        miasma.engaged(calamity.field(), now);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(astoria);
        if (bolt != null) {
            bolt.moveTo(center);
            bolt.setVisualOnly(true);
            astoria.addFreshEntity(bolt);
        }
        boardDirty = true;
    }

    private static void calamityDown(ServerLevel astoria, CalamityState state) {
        MinecraftServer server = astoria.getServer();
        long now = server.getTickCount();
        CALAMITIES.remove(state.calamity.field());
        handle(server, astoria, miasma.subdued(state.calamity.field(), now), now);
        BossFight fight = state.fight;
        Contributions contributions = fight.contributions();
        Optional<Contributions.Line> hero = contributions.hero();
        Component fieldName = Component.translatable(fieldKey(state.calamity.field())).withStyle(ChatFormatting.AQUA);
        Component calamityName = Component.translatable(state.calamity.translationKey())
                .withStyle(ChatFormatting.DARK_RED);
        broadcast(server, Component.translatable("threat.eternalrealm.subdued", fieldName, calamityName,
                hero.map(line -> Component.literal(line.name()).withStyle(ChatFormatting.GOLD))
                        .orElse(Component.literal("-"))).withStyle(ChatFormatting.GOLD));
        // every defeat makes it come back a level higher (saved at once: a crash must not undo a victory)
        growth.defeated(state.calamity.field());
        ThreatData.get(astoria).storeGrowth(growth.toJson());
        int next = calamityLevel(state.calamity);
        broadcast(server, Component.translatable(next > state.level ? "threat.eternalrealm.grown"
                : "threat.eternalrealm.grown_max", calamityName, next).withStyle(ChatFormatting.DARK_PURPLE));
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        BossDefinition definition = Bosses.byId(state.calamity.boss()).orElse(null);
        MonsterTemplate minion = rewardTemplate(definition, state.calamity.field());
        // the rewards of the level it fought at (a grown calamity pays more)
        int rewardLevel = Math.max(1, state.level > 0 ? state.level : definition == null ? 1 : definition.level());
        MonsterStats stats = minion == null ? null : MonsterScaling.stats(minion, Math.min(MonsterScaling.MAX_LEVEL,
                Math.max(minion.levels().min(), rewardLevel)));
        long durationTicks = Math.max(0L, (fight.killTick() < 0 ? now : fight.killTick()) - state.fightStart);
        int falls = contributions.byDamage().stream().mapToInt(Contributions.Line::falls).sum();
        ClearRank rank = ClearRank.of(durationTicks, 10L * 60L * 20L, falls);
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
            int field = state.calamity.field();
            List<ItemStack> items = new ArrayList<>();
            addMaterial(items, com.eternalrealm.monster.Materials.bossMaterial(field), isHero ? 2 : 1);
            addMaterial(items, Materials.ENHANCEMENT_STONE, 2 + rank.bonusTier());
            addMaterial(items, com.eternalrealm.monster.Materials.fieldElite(field), 1);
            if (isHero || RANDOM.nextDouble() < 0.5) {
                items.add(equipment(context, player, field, isHero ? ItemRarity.RARE : ItemRarity.UNCOMMON));
            }
            // the calamity's weapon and a piece of its armor set (armory)
            String calamity = state.calamity.id();
            if (RANDOM.nextDouble() < com.eternalrealm.item.ArmoryDrops.calamityWeaponChance(isHero)) {
                com.eternalrealm.item.Signatures.calamityWeapon(calamity).ifPresent(design -> {
                    ItemStack weapon = com.eternalrealm.item.forge.ArmoryStacks.reward(player, design,
                            ItemRarity.RARE, RANDOM);
                    items.add(weapon);
                    com.eternalrealm.item.forge.ArmoryStacks.announce(player, weapon, design);
                });
            }
            if (RANDOM.nextDouble() < com.eternalrealm.item.ArmoryDrops.calamityArmorChance(isHero)) {
                com.eternalrealm.item.ArmoryDrops.calamityPiece(calamity, RANDOM).ifPresent(design -> {
                    ItemStack piece = com.eternalrealm.item.forge.ArmoryStacks.reward(player, design,
                            ItemRarity.UNCOMMON, RANDOM);
                    items.add(piece);
                    com.eternalrealm.item.forge.ArmoryStacks.announce(player, piece, design);
                });
            }
            if (RANDOM.nextDouble() < 0.35) {
                items.add(new ItemStack(ERGearItems.consumable(Consumable.EVERBLOOM_PETAL)));
            }
            // about one kill in a hundred: one of the calamity's relics (金枠)
            com.eternalrealm.item.forge.RelicDrops.roll(player, com.eternalrealm.item.Relics.SourceKind.CALAMITY,
                    calamity, RANDOM).ifPresent(items::add);
            long exp = stats == null ? 0L : stats.exp() * 25L;
            long gold = stats == null ? 0L : stats.goldMax() * 12L;
            give(context, player, items, exp, gold);
            JsonObject data = new JsonObject();
            data.addProperty("kind", "calamity");
            data.addProperty("dungeon", state.calamity.translationKey());
            data.addProperty("difficulty", "calamity");
            data.addProperty("difficultyKey", fieldKey(field));
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
        boardDirty = true;
    }

    /** The level the calamity of its field fights at now: its boss level plus one for every defeat (at most 100). */
    static int calamityLevel(FieldThreats.Calamity calamity) {
        return Bosses.byId(calamity.boss()).map(definition -> growth.level(calamity.field(), definition.level()))
                .orElse(1);
    }

    /** The calamity's boss grown to its current level (health and damage, CalamityGrowth). */
    static Optional<BossDefinition> grown(FieldThreats.Calamity calamity) {
        return Bosses.byId(calamity.boss()).map(definition -> {
            int level = growth.level(calamity.field(), definition.level());
            return definition.atLevel(level, CalamityGrowth.healthFactor(definition.level(), level),
                    CalamityGrowth.damageFactor(definition.level(), level));
        });
    }

    /** How often the calamity of {@code field} was subdued (the board, tests). */
    public static int calamityDefeats(int field) {
        return growth.defeats(field);
    }

    // ------------------------------------------------------------------ rewards

    /**
     * The monster whose EXP and gold a boss's rewards scale from: its adds' template, or for the field bosses (which
     * summon none) the first lord of their field, at the boss's level.
     */
    static MonsterTemplate rewardTemplate(BossDefinition definition, int field) {
        if (definition == null) {
            return null;
        }
        return Monsters.byId(Bosses.minionTemplateId(definition.id()))
                .or(() -> FieldThreats.lordsOf(field).stream().findFirst().flatMap(lord -> Monsters.byId(lord.template())))
                .orElse(null);
    }

    static void addMaterial(List<ItemStack> items, String materialId, int count) {
        Item item = materialId == null ? null : ERGearItems.material(materialId);
        if (item != null && count > 0) {
            items.add(new ItemStack(item, count));
        }
    }

    static ItemStack equipment(ServerContext context, ServerPlayer player, int field, ItemRarity floor) {
        double luck = context.combat().service().get(player.getUUID()).map(Combatant::stats)
                .map(CombatStats::lootBonus).orElse(0.0);
        ItemRarity rarity = EquipmentGenerator.rollRarity(luck, RANDOM);
        if (rarity.rank() < floor.rank()) {
            rarity = floor;
        }
        ItemTier tier = ItemTier.IRON;
        int tierNumber = com.eternalrealm.world.Areas.byId(field).map(area -> area.tier()).orElse(1);
        for (ItemTier candidate : ItemTier.values()) {
            if (candidate.number() <= tierNumber && candidate.number() >= tier.number()) {
                tier = candidate;
            }
        }
        // now and then an accessory or a charm of the tier's grade instead (装身具)
        if (RANDOM.nextDouble() < com.eternalrealm.item.Trinkets.REWARD_SHARE) {
            int level = context.playerData().snapshot(player.getUUID()).map(CharacterSnapshot::level).orElse(1);
            return EquipmentStacks.create(EquipmentGenerator.roll(com.eternalrealm.item.Trinkets.forDrop(tier, level,
                    LootDrops.jobStats(player), RANDOM), rarity, RANDOM));
        }
        return EquipmentStacks.create(EquipmentGenerator.roll(LootDrops.baseFor(player, tier), rarity, RANDOM));
    }

    static void give(ServerContext context, ServerPlayer player, List<ItemStack> items, long exp, long gold) {
        if (exp > 0) {
            context.playerData().grantExperience(player.getUUID(), exp, ExperienceSource.BOSS);
        }
        if (gold > 0) {
            context.playerData().grantGold(player.getUUID(), gold);
        }
        for (ItemStack stack : items) {
            Inventories.give(player, stack.copy());
        }
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    // ------------------------------------------------------------------ the board

    private static void sendBoard(ServerLevel astoria, long now) {
        boardDirty = false;
        if (astoria.players().isEmpty()) {
            return;
        }
        JsonObject board = new JsonObject();
        JsonArray fields = new JsonArray();
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("field", field);
            entry.addProperty("phase", miasma.phase(field).id());
            entry.addProperty("gauge", miasma.gauge(field));
            entry.addProperty("remaining", miasma.remaining(field, now) / 20.0);
            int fieldNumber = field;
            FieldThreats.calamityOf(field).ifPresent(calamity -> {
                JsonObject c = new JsonObject();
                c.addProperty("id", calamity.id());
                c.addProperty("name", calamity.translationKey());
                c.addProperty("x", calamity.x());
                c.addProperty("z", calamity.z());
                CalamityState state = CALAMITIES.get(fieldNumber);
                c.addProperty("risen", state != null && state.fight != null && !state.fight.isOver());
                if (state != null && state.fight != null && !state.fight.isOver()) {
                    c.addProperty("bx", Math.round(state.fight.body().getX()));
                    c.addProperty("bz", Math.round(state.fight.body().getZ()));
                }
                c.addProperty("level", calamityLevel(calamity));
                c.addProperty("defeats", growth.defeats(fieldNumber));
                entry.add("calamity", c);
            });
            JsonArray lords = new JsonArray();
            entry.addProperty("rotation", Math.max(0L, nextRotation - now) / 20.0);
            for (FieldThreats.Lord lord : FieldThreats.lordsOf(field)) {
                LordState state = LORDS.get(lord.id());
                JsonObject l = new JsonObject();
                l.addProperty("id", lord.id());
                l.addProperty("name", lord.translationKey());
                l.addProperty("x", lord.x());
                l.addProperty("z", lord.z());
                Entity body = state == null || state.body == null ? null : astoria.getEntity(state.body);
                boolean alive = body != null && body.isAlive();
                l.addProperty("present", alive);
                if (alive) {
                    l.addProperty("bx", Math.round(body.getX()));
                    l.addProperty("bz", Math.round(body.getZ()));
                    l.addProperty("level", state.level);
                } else {
                    Monsters.byId(lord.template()).ifPresent(template -> l.addProperty("level",
                            Math.min(MonsterScaling.MAX_LEVEL, template.levels().max() + FieldThreats.LORD_LEVEL_BONUS)));
                }
                long due = state == null ? 0L : Math.max(0L, state.dueAt - now);
                l.addProperty("due", alive ? 0.0 : due / 20.0);
                l.addProperty("out", alive || state != null && state.out);
                l.addProperty("beaten", !alive && state != null && state.beaten);
                lords.add(l);
            }
            entry.add("lords", lords);
            WandererService.addToBoard(entry, field, now);
            fields.add(entry);
        }
        board.add("fields", fields);
        for (ServerPlayer player : astoria.players()) {
            UiData.send(player, BOARD_CHANNEL, board);
        }
    }

    /** Something on the board changed (a wanderer came or went): send it soon. */
    static void boardChanged() {
        boardDirty = true;
    }

    // ------------------------------------------------------------------ helpers

    static String fieldKey(int field) {
        return com.eternalrealm.world.Areas.byId(field).map(area -> area.translationKey()).orElse("?");
    }

    static int fieldOf(ServerPlayer player) {
        return FieldLayout.inCapital(player.getX(), player.getZ()) ? 0 : FieldLayout.areaAt(player.getX(), player.getZ());
    }

    static ServerPlayer nearestPlayer(ServerLevel level, double x, double z, double radius) {
        ServerPlayer best = null;
        double bestDistance = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            double distance = dx * dx + dz * dz;
            if (distance <= bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static String jobOf(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(snapshot -> JobType.fromStored(snapshot.jobId()).id()).orElse("none");
    }

    static void broadcast(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    /** Lords and calamities saved with their chunks by an earlier run are not tracked: they leave quietly. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (entity.getTags().contains(LORD_TAG) && lordOf(entity).isEmpty() && !isBeingSpawned(entity)) {
            event.setCanceled(true);
        } else if (entity.getTags().contains(CALAMITY_TAG) && !isTrackedCalamity(entity)) {
            event.setCanceled(true);
        }
    }

    private static boolean isBeingSpawned(Entity entity) {
        LordState state = LORDS.get(entity.getPersistentData().getString(LORD_ID));
        return state != null && entity.getUUID().equals(state.body);
    }

    private static boolean isTrackedCalamity(Entity entity) {
        for (CalamityState state : CALAMITIES.values()) {
            if (state.fight != null && state.fight.body() == entity) {
                return true;
            }
        }
        // the tag is added after the body joined the level, so a fresh body is never caught here
        return false;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel astoria = event.getServer().getLevel(AstoriaDimension.LEVEL);
        if (astoria != null && owner == event.getServer()) {
            ThreatData.get(astoria).store(miasma.toJson());
            ThreatData.get(astoria).storeGrowth(growth.toJson());
            for (CalamityState state : CALAMITIES.values()) {
                if (state.fight != null) {
                    state.fight.end();
                    state.fight.body().discard();
                }
            }
            for (LordState state : LORDS.values()) {
                Entity body = state.body == null ? null : astoria.getEntity(state.body);
                if (body != null) {
                    body.discard();
                }
            }
        }
        CALAMITIES.clear();
        LORDS.clear();
        BossFight.forgetAll();
        owner = null;
    }

    /**
     * The miasma gauges and how often each calamity was subdued, saved with アストリア
     * ({@code data/eternalrealm_field_threats.dat}).
     */
    public static final class ThreatData extends SavedData {
        private static final String NAME = ModInfo.MOD_ID + "_field_threats";
        private String json = "";
        private String growthJson = "";

        public static ThreatData get(ServerLevel astoria) {
            return astoria.getDataStorage().computeIfAbsent(ThreatData::load, ThreatData::new, NAME);
        }

        static ThreatData load(CompoundTag tag) {
            ThreatData data = new ThreatData();
            data.json = tag.getString("miasma");
            data.growthJson = tag.getString("calamities");
            return data;
        }

        String json() {
            return json;
        }

        String growthJson() {
            return growthJson;
        }

        void store(String value) {
            if (!value.equals(json)) {
                json = value;
                setDirty();
            }
        }

        void storeGrowth(String value) {
            if (!value.equals(growthJson)) {
                growthJson = value;
                setDirty();
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putString("miasma", json);
            tag.putString("calamities", growthJson);
            return tag;
        }
    }

    /**
     * Operator commands to try the threats without hours of hunting: {@code /eternalrealm threat miasma <field> <points>}
     * adds miasma (1000 starts the omen), {@code /eternalrealm threat lord <id>} makes a lord due now (it appears when
     * someone is near), {@code /eternalrealm threat status} prints every field.
     */
    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("eternalrealm")
                .then(net.minecraft.commands.Commands.literal("threat").requires(source -> source.hasPermission(2))
                        .then(net.minecraft.commands.Commands.literal("miasma")
                                .then(net.minecraft.commands.Commands.argument("field",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, FieldLayout.FIELD_COUNT))
                                        .then(net.minecraft.commands.Commands.argument("points",
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, Miasma.MAX))
                                                .executes(ctx -> {
                                                    MinecraftServer server = ctx.getSource().getServer();
                                                    ServerLevel astoria = server.getLevel(AstoriaDimension.LEVEL);
                                                    if (astoria == null || owner != server) {
                                                        ctx.getSource().sendFailure(Component.literal("Astoria is not running"));
                                                        return 0;
                                                    }
                                                    int field = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "field");
                                                    int points = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "points");
                                                    long now = server.getTickCount();
                                                    handle(server, astoria, miasma.addKill(field, points, now), now);
                                                    boardDirty = true;
                                                    ctx.getSource().sendSuccess(() -> Component.literal("field " + field + ": "
                                                            + miasma.phase(field).id() + " " + miasma.gauge(field) + "/" + Miasma.MAX), true);
                                                    return 1;
                                                }))))
                        .then(net.minecraft.commands.Commands.literal("lord")
                                .then(net.minecraft.commands.Commands.argument("id",
                                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                                            LordState state = LORDS.get(id);
                                            if (state == null) {
                                                ctx.getSource().sendFailure(Component.literal("Unknown lord " + id));
                                                return 0;
                                            }
                                            state.dueAt = 0L;
                                            boardDirty = true;
                                            ctx.getSource().sendSuccess(() -> Component.literal(id + " is due at "
                                                    + state.lord.x() + ", " + state.lord.z()), true);
                                            return 1;
                                        })))
                        .then(net.minecraft.commands.Commands.literal("status").executes(ctx -> {
                            long now = ctx.getSource().getServer().getTickCount();
                            for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
                                int f = field;
                                String level = FieldThreats.calamityOf(f).map(c -> " calamity Lv" + calamityLevel(c)
                                        + " (" + growth.defeats(f) + " defeats)").orElse("");
                                ctx.getSource().sendSuccess(() -> Component.literal("field " + f + ": "
                                        + miasma.phase(f).id() + " " + miasma.gauge(f) + "/" + Miasma.MAX + " ("
                                        + miasma.remaining(f, now) / 20 + "s)" + level), false);
                            }
                            return 1;
                        }))));
    }

    /** For the map and tests: the phase of a field right now. */
    public static Miasma.Phase phase(int field) {
        return miasma.phase(field);
    }

    static String id(int field) {
        return String.format(Locale.ROOT, "field_%d", field);
    }
}
