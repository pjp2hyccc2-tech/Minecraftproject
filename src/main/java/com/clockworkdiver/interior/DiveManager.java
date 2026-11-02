package com.clockworkdiver.interior;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.block.MechanismBlockEntity;
import com.clockworkdiver.block.ToothSocketBlock;
import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.ClockType;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.combat.Quality;
import com.clockworkdiver.combat.TickStrike;
import com.clockworkdiver.data.PlayerProgress;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.entity.CogGremlin;
import com.clockworkdiver.entity.DustMoth;
import com.clockworkdiver.entity.RustMite;
import com.clockworkdiver.entity.TimeEater;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.registry.ModBlocks;
import com.clockworkdiver.registry.ModDimensions;
import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.registry.ModSounds;
import com.clockworkdiver.util.DelayedTasks;
import com.clockworkdiver.util.Teleport;
import com.clockworkdiver.workshop.Objectives;
import com.clockworkdiver.workshop.WorkshopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Runs the repair dives: builds the clock interior, spawns the vermin, tracks the repair and sends the player back. */
public final class DiveManager {
    public static final int SLOT_SPACING = 1024;
    private static final Map<UUID, DiveSession> SESSIONS = new HashMap<>();

    private DiveManager() {}

    public static BlockPos zoneOrigin(int slot) {
        return new BlockPos(slot * SLOT_SPACING, 100, 0);
    }

    @Nullable
    public static DiveSession session(Player player) {
        return SESSIONS.get(player.getUUID());
    }

    public static Collection<DiveSession> sessions() {
        return SESSIONS.values();
    }

    public static void clearAll() {
        SESSIONS.clear();
    }

    // ------------------------------------------------------------------ start

    /** Called when the player picks a faulty part at the bench. */
    public static void startDive(ServerPlayer player, int defectId) {
        if (SESSIONS.containsKey(player.getUUID())) return;
        PlayerProgress progress = WorldProgress.of(player);
        ClockData clock = progress.activeClock;
        if (clock == null || !progress.accepted) return;
        Defect defect = clock.defect(defectId);
        if (defect == null || defect.repaired) return;
        defect.found = true;
        begin(player, progress, clock, defect, false);
    }

    /** Debug and self test entry: dive straight into a chamber of the given kind. */
    public static boolean startDebugDive(ServerPlayer player, DefectType type, boolean tutorial) {
        if (SESSIONS.containsKey(player.getUUID())) return false;
        ClockData clock = new ClockData();
        clock.type = ClockType.POCKET_WATCH;
        clock.tutorial = tutorial;
        Defect defect = new Defect(0, type, type.parts[0], 0.0f, 0.0f);
        defect.found = true;
        clock.defects.add(defect);
        return begin(player, WorldProgress.of(player), clock, defect, true);
    }

    private static boolean begin(ServerPlayer player, PlayerProgress progress, ClockData clock, Defect defect, boolean debug) {
        ServerLevel interior = player.server.getLevel(ModDimensions.INTERIOR);
        if (interior == null) {
            player.displayClientMessage(Component.translatable("message.clockworkdiver.no_dimension"), false);
            return false;
        }
        BlockPos origin = zoneOrigin(progress.slot);
        ZoneLayout layout;
        try {
            layout = ZoneBuilder.build(interior, origin, defect.type, clock.difficulty(), clock.type.period, clock.tutorial,
                    player.getRandom());
        } catch (Exception e) {
            ClockworkDiver.LOGGER.error("Could not build the clock interior", e);
            player.displayClientMessage(Component.translatable("message.clockworkdiver.build_failed"), false);
            return false;
        }
        DiveSession s = new DiveSession(player.getUUID(), defect, clock, origin, layout);
        s.debug = debug;
        s.anchor = interior.getGameTime();
        switch (s.type) {
            case RUST, TEETH -> s.total = layout.targets.size();
            case SCREW -> s.total = layout.screwMaxHeight - 4;
            case SPRING -> s.total = 100;
            case NEST -> s.total = 1;
        }
        SESSIONS.put(player.getUUID(), s);
        WorkshopManager.rememberReturn(player, progress);
        ensureItem(player, ModItems.TINKER_WRENCH.get(), "message.clockworkdiver.got_wrench");
        selectItem(player, ModItems.TINKER_WRENCH.get());
        TickStrike.reset(player);
        player.setHealth(player.getMaxHealth());
        Teleport.to(player, interior, layout.spawn.x, layout.spawn.y, layout.spawn.z, layout.spawnYaw, 0.0f);
        DelayedTasks.schedule(player.server, 8, () -> {
            if (SESSIONS.get(player.getUUID()) != s) return;
            sendState(player, s, true);
            ModNetwork.send(player, new S2C.Transition(S2C.TRANSITION_ARRIVE, DefectType.partKey(s.part), s.type.nameKey()));
            TickStrike.sync(player, null);
            Objectives.sync(player);
            diveHints(player, progress, s);
        });
        return true;
    }

    private static void diveHints(ServerPlayer player, PlayerProgress progress, DiveSession s) {
        List<String> keys = new ArrayList<>();
        if (progress.markSeen(PlayerProgress.HINT_DIVE)) {
            keys.add("hint.clockworkdiver.dive.0");
            keys.add("hint.clockworkdiver.dive.1");
            keys.add("hint.clockworkdiver.dive.2");
        }
        int flag = switch (s.type) {
            case RUST -> PlayerProgress.HINT_RUST;
            case TEETH -> PlayerProgress.HINT_TEETH;
            case SCREW -> PlayerProgress.HINT_SCREW;
            case SPRING -> PlayerProgress.HINT_SPRING;
            case NEST -> PlayerProgress.HINT_NEST;
        };
        if (progress.markSeen(flag)) {
            keys.add("hint.clockworkdiver." + s.type.id + ".0");
            keys.add("hint.clockworkdiver." + s.type.id + ".1");
        }
        if (!keys.isEmpty()) ModNetwork.send(player, new S2C.Hint(keys));
    }

    public static void sendState(ServerPlayer player, DiveSession s, boolean inDive) {
        PlayerProgress progress = WorldProgress.of(player);
        ModNetwork.send(player, new S2C.DiveState(inDive, s.type.id, DefectType.partKey(s.part), s.period, s.anchor,
                progress.lensLevel, s.tutorial));
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (DiveSession s : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(s.playerId);
            if (player == null) {
                SESSIONS.remove(s.playerId);
                cleanupZone(server, s);
                continue;
            }
            s.ticks++;
            if (!ModDimensions.isInterior(player.level())) {
                if (s.ticks > 80) {
                    SESSIONS.remove(s.playerId);
                    cleanupZone(server, s);
                    sendState(player, s, false);
                    TickStrike.clear(player);
                    Objectives.sync(player);
                }
                continue;
            }
            try {
                tickSession(player, s);
            } catch (Exception e) {
                ClockworkDiver.LOGGER.error("Clockwork Diver dive tick failed", e);
            }
        }
    }

    private static void tickSession(ServerPlayer player, DiveSession s) {
        ServerLevel level = player.serverLevel();
        s.phaseTicks++;
        if (s.actionCooldown > 0) s.actionCooldown--;
        if (s.warnCooldown > 0) s.warnCooldown--;

        if (player.getY() < s.origin.getY() - 10) {
            Teleport.to(player, level, s.layout.spawn.x, s.layout.spawn.y, s.layout.spawn.z, s.layout.spawnYaw, 0.0f);
            player.hurt(player.damageSources().fall(), 2.0f);
        }
        for (BlockPos v : s.layout.vents) {
            double dx = player.getX() - (v.getX() + 0.5);
            double dz = player.getZ() - (v.getZ() + 0.5);
            double dy = player.getY() - (v.getY() + 1.0);
            if (dx * dx + dz * dz < 1.7 && dy > -0.5 && dy < 3.0) {
                TickStrike.addSteam(player, 1.0f);
                break;
            }
        }

        switch (s.phase) {
            case DiveSession.INTRO -> {
                if (s.phaseTicks >= 30) {
                    s.phase = DiveSession.ACTIVE;
                    s.phaseTicks = 0;
                    activate(player, s, level);
                }
            }
            case DiveSession.ACTIVE -> {
                switch (s.type) {
                    case RUST -> tickRust(player, s, level);
                    case TEETH -> tickTeeth(player, s, level);
                    case SCREW -> tickScrew(s, level, player);
                    case SPRING -> tickSpring(player, s, level);
                    case NEST -> tickNest(player, s, level);
                }
            }
            case DiveSession.COMPLETE -> {
                if (s.phaseTicks >= 70) exit(player, s, true);
                return;
            }
            default -> {
            }
        }
        int obj = Objects.hash(s.objectiveKey(), s.done, s.total);
        if (obj != s.lastObjective) {
            s.lastObjective = obj;
            Objectives.sync(player);
        }
    }

    private static void activate(ServerPlayer player, DiveSession s, ServerLevel level) {
        switch (s.type) {
            case RUST -> {
                for (int i = 0; i < (s.tutorial ? 1 : 2); i++) spawnNear(level, s, ModEntities.RUST_MITE.get(), player);
            }
            case TEETH -> {
                int n = Math.min(s.total, s.tutorial ? 2 : 3);
                for (int i = 0; i < n; i++) spawnGremlin(level, s, player, true);
            }
            case SCREW -> {
                for (int i = 0; i < (s.tutorial ? 1 : 2); i++) spawnFlying(level, s, player);
            }
            case SPRING -> {
                for (int i = 0; i < 2; i++) spawnNear(level, s, ModEntities.RUST_MITE.get(), player);
            }
            case NEST -> {
            }
        }
    }

    // ------------------------------------------------------------------ rust

    private static void tickRust(ServerPlayer player, DiveSession s, ServerLevel level) {
        if (s.ticks % 5 == 0) {
            int remaining = remainingRust(s, level).size();
            s.done = s.total - remaining;
            if (remaining == 0) {
                complete(player, s, level);
                return;
            }
        }
        if (--s.spawnTimer <= 0) {
            s.spawnTimer = s.tutorial ? 150 : Math.max(60, 120 - s.difficulty * 12);
            int cap = s.tutorial ? 3 : 4 + s.difficulty;
            if (count(level, s, RustMite.class) < cap) {
                List<BlockPos> left = remainingRust(s, level);
                if (!left.isEmpty()) {
                    BlockPos t = left.get(level.random.nextInt(left.size()));
                    spawn(level, s, ModEntities.RUST_MITE.get(), Vec3.atBottomCenterOf(t).add(0.0, 1.0, 0.0), player);
                }
            }
        }
    }

    private static List<BlockPos> remainingRust(DiveSession s, ServerLevel level) {
        List<BlockPos> left = new ArrayList<>();
        for (BlockPos t : s.layout.targets) {
            if (level.getBlockState(t).is(ModBlocks.RUST_CLUSTER.get())) left.add(t);
        }
        return left;
    }

    public static void onRustBroken(ServerPlayer player, BlockPos pos) {
        DiveSession s = session(player);
        ServerLevel level = player.serverLevel();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.RUST_CLUSTER.get().defaultBlockState()),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0f, 1.4f);
        if (s == null || s.type != DefectType.RUST || s.phase != DiveSession.ACTIVE) return;
        TickStrike.addSteam(player, 4.0f);
        if (player.getRandom().nextFloat() < (s.tutorial ? 0.25f : 0.35f) && count(level, s, RustMite.class) < 8) {
            spawn(level, s, ModEntities.RUST_MITE.get(), Vec3.atBottomCenterOf(pos).add(0.0, 0.2, 0.0), player);
        }
    }

    // ------------------------------------------------------------------ teeth

    private static void tickTeeth(ServerPlayer player, DiveSession s, ServerLevel level) {
        if (s.ticks % 5 == 0) {
            int filled = 0;
            for (BlockPos t : s.layout.targets) {
                BlockState st = level.getBlockState(t);
                if (st.is(ModBlocks.TOOTH_SOCKET.get()) && st.getValue(ToothSocketBlock.FILLED)) filled++;
            }
            s.done = filled;
            if (filled >= s.total) {
                complete(player, s, level);
                return;
            }
        }
        if (--s.spawnTimer <= 0) {
            s.spawnTimer = 80;
            List<CogGremlin> gremlins = level.getEntitiesOfClass(CogGremlin.class, s.layout.bounds, Entity::isAlive);
            int carriers = 0;
            for (CogGremlin g : gremlins) if (g.hasTooth()) carriers++;
            int held = countItem(player, ModItems.GEAR_TOOTH.get());
            int dropped = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, s.layout.bounds,
                    e -> e.getItem().is(ModItems.GEAR_TOOTH.get()))) {
                dropped += item.getItem().getCount();
            }
            if (s.done + carriers + held + dropped < s.total && gremlins.size() < 3) {
                spawnGremlin(level, s, player, true);
            } else if (!s.tutorial && gremlins.size() < 2 && count(level, s, RustMite.class) < 1 + s.difficulty / 2) {
                spawnNear(level, s, ModEntities.RUST_MITE.get(), player);
            }
        }
    }

    public static void onSocketFilled(ServerPlayer player, BlockPos pos) {
        DiveSession s = session(player);
        if (s == null || s.type != DefectType.TEETH) return;
        ServerLevel level = player.serverLevel();
        TickStrike.addSteam(player, 10.0f);
        if (s.layout.mainMechanism != null && level.getBlockEntity(s.layout.mainMechanism) instanceof MechanismBlockEntity wheel) {
            wheel.missing = Math.max(0, wheel.missing - 1);
            wheel.sync();
        }
    }

    // ------------------------------------------------------------------ screw

    private static void tickScrew(DiveSession s, ServerLevel level, ServerPlayer player) {
        if (--s.spawnTimer <= 0) {
            s.spawnTimer = Math.max(80, 150 - s.difficulty * 12);
            int cap = s.tutorial ? 2 : 2 + s.difficulty / 2;
            if (count(level, s, DustMoth.class) < cap) spawnFlying(level, s, player);
        }
    }

    public static void onScrewTurn(ServerPlayer player, BlockPos pos) {
        DiveSession s = session(player);
        if (s == null || s.type != DefectType.SCREW || s.phase != DiveSession.ACTIVE) return;
        if (s.actionCooldown > 0) return;
        s.actionCooldown = 6;
        ServerLevel level = player.serverLevel();
        if (player.getY() < s.origin.getY() + s.screwHeight + 0.5) {
            player.displayClientMessage(Component.translatable("message.clockworkdiver.screw_climb"), true);
            return;
        }
        Quality q = TickStrike.consumeQuality(player, s);
        if (q == Quality.PERFECT) {
            s.done++;
            s.screwPhase++;
            s.screwHeight--;
            ZoneBuilder.rebuildScrew(level, s.origin, s.screwHeight, s.screwPhase);
            level.playSound(null, pos, ModSounds.RATCHET.get(), SoundSource.BLOCKS, 1.2f, 0.8f);
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 0.6f);
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.2, player.getZ(), 30, 2.0, 0.2, 2.0, 0.3);
            TickStrike.onPerfectAction(player);
            player.displayClientMessage(Component.translatable("message.clockworkdiver.screw_turned", s.done, s.total), true);
            if (s.done >= s.total) complete(player, s, level);
        } else if (q == Quality.GOOD) {
            level.playSound(null, pos, ModSounds.RATCHET.get(), SoundSource.BLOCKS, 0.7f, 0.6f);
            player.displayClientMessage(Component.translatable("message.clockworkdiver.screw_close"), true);
        } else {
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8f, 1.4f);
            Vec3 away = player.position().subtract(Vec3.atCenterOf(s.origin)).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 0.01) away = new Vec3(1.0, 0.0, 0.0);
            away = away.normalize().scale(1.3);
            player.setDeltaMovement(away.x, 0.6, away.z);
            player.hurtMarked = true;
            player.displayClientMessage(Component.translatable("message.clockworkdiver.screw_slip"), true);
        }
    }

    // ------------------------------------------------------------------ spring

    private static void tickSpring(ServerPlayer player, DiveSession s, ServerLevel level) {
        if (s.ticks % 20 == 0 && s.layout.crank != null) {
            AABB near = new AABB(s.layout.crank).inflate(3.5, 2.0, 3.5);
            int n = level.getEntitiesOfClass(Monster.class, near, Entity::isAlive).size();
            if (n > 0 && s.progress > 0) {
                s.progress = Math.max(0.0f, s.progress - 2.0f * n);
                syncSpring(level, s);
                if (s.warnCooldown == 0) {
                    player.displayClientMessage(Component.translatable("message.clockworkdiver.spring_drain"), true);
                    s.warnCooldown = 80;
                }
            }
        }
        if (s.waveIndex < 3 && s.progress >= (s.waveIndex + 1) * 25.0f) {
            spawnWave(player, s, level, s.waveIndex);
            s.waveIndex++;
        }
        if (--s.spawnTimer <= 0) {
            s.spawnTimer = 220;
            if (count(level, s, Monster.class) < 2) spawnNear(level, s, ModEntities.RUST_MITE.get(), player);
        }
        s.done = (int) s.progress;
        if (s.progress >= 100.0f) complete(player, s, level);
    }

    private static void spawnWave(ServerPlayer player, DiveSession s, ServerLevel level, int index) {
        int mites = 2 + index + s.difficulty / 2;
        for (int i = 0; i < mites; i++) spawnNear(level, s, ModEntities.RUST_MITE.get(), player);
        if (index >= 1) spawnGremlin(level, s, player, false);
        if (index >= 2) spawnFlying(level, s, player);
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.0f, 1.4f);
        player.displayClientMessage(Component.translatable("message.clockworkdiver.wave", index + 1), true);
    }

    public static void onCrankWind(ServerPlayer player, BlockPos pos) {
        DiveSession s = session(player);
        if (s == null || s.type != DefectType.SPRING || s.phase != DiveSession.ACTIVE) return;
        if (s.actionCooldown > 0) return;
        s.actionCooldown = 5;
        ServerLevel level = player.serverLevel();
        Quality q = TickStrike.consumeQuality(player, s);
        float gain = q == Quality.PERFECT ? 9.0f : q == Quality.GOOD ? 5.0f : 3.0f;
        s.progress = Math.min(100.0f, s.progress + gain);
        syncSpring(level, s);
        level.playSound(null, pos, ModSounds.RATCHET.get(), SoundSource.BLOCKS, 1.0f, 0.8f + s.progress / 100.0f * 0.8f);
        level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, q == Quality.PERFECT ? 18 : 6,
                0.3, 0.3, 0.3, 0.2);
        if (q == Quality.PERFECT) TickStrike.onPerfectAction(player);
    }

    private static void syncSpring(ServerLevel level, DiveSession s) {
        if (s.layout.mainMechanism != null && level.getBlockEntity(s.layout.mainMechanism) instanceof MechanismBlockEntity m) {
            m.progress = s.progress / 100.0f;
            m.sync();
        }
    }

    // ------------------------------------------------------------------ boss

    private static void tickNest(ServerPlayer player, DiveSession s, ServerLevel level) {
        if (!s.bossSpawned && s.phaseTicks >= 20) spawnBoss(player, s, level);
        if (s.bossSpawned && s.ticks % 10 == 0) {
            Entity boss = s.bossId == null ? null : level.getEntity(s.bossId);
            if (boss == null || !boss.isAlive()) {
                s.done = 1;
                complete(player, s, level);
                return;
            }
        }
        if (s.difficulty >= 3 && --s.spawnTimer <= 0) {
            s.spawnTimer = 300;
            if (count(level, s, DustMoth.class) < 2) spawnFlying(level, s, player);
        }
    }

    private static void spawnBoss(ServerPlayer player, DiveSession s, ServerLevel level) {
        TimeEater boss = ModEntities.TIME_EATER.get().create(level);
        if (boss == null) return;
        boss.moveTo(s.origin.getX() + 0.5, s.origin.getY() + 0.1, s.origin.getZ() + 0.5, 0.0f, 0.0f);
        AttributeInstance hp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(150.0 + 25.0 * s.difficulty);
        boss.setHealth(boss.getMaxHealth());
        boss.setTarget(player);
        level.addFreshEntity(boss);
        s.bossId = boss.getUUID();
        s.bossSpawned = true;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, boss.getX(), boss.getY() + 1.0, boss.getZ(), 60, 1.2, 1.0, 1.2, 0.05);
        level.playSound(null, boss.blockPosition(), ModSounds.BOSS_ROAR.get(), SoundSource.HOSTILE, 2.0f, 0.8f);
        title(player, "title.clockworkdiver.boss", "title.clockworkdiver.boss.sub");
    }

    /** The Time Eater dropped below half health: the clock's beat speeds up. */
    public static void onBossEnraged(TimeEater boss) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        for (DiveSession s : SESSIONS.values()) {
            if (!s.layout.bounds.contains(boss.position()) || s.enraged) continue;
            s.enraged = true;
            s.period = Math.max(8, Math.round(s.period * 0.65f));
            s.anchor = level.getGameTime();
            for (BlockPos mp : s.layout.mechanisms) {
                if (level.getBlockEntity(mp) instanceof MechanismBlockEntity m) {
                    m.period = s.period;
                    m.running = true;
                    m.sync();
                }
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(s.playerId);
            if (player != null) {
                ModNetwork.send(player, new S2C.TempoSync(s.period, s.anchor));
                title(player, "title.clockworkdiver.enraged", "title.clockworkdiver.enraged.sub");
            }
        }
    }

    // ------------------------------------------------------------------ completion

    private static void complete(ServerPlayer player, DiveSession s, ServerLevel level) {
        if (s.phase == DiveSession.COMPLETE) return;
        s.phase = DiveSession.COMPLETE;
        s.phaseTicks = 0;
        s.done = Math.max(s.done, s.total);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, s.layout.bounds, Entity::isAlive)) {
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            mob.discard();
        }
        for (BlockPos mp : s.layout.mechanisms) {
            if (level.getBlockEntity(mp) instanceof MechanismBlockEntity m) {
                m.running = true;
                m.missing = 0;
                if (m.kind == MechanismBlockEntity.SPRING) m.progress = 1.0f;
                m.sync();
            }
        }
        level.playSound(null, player.blockPosition(), ModSounds.REPAIRED.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 60, 1.5, 1.0, 1.5, 0.3);
        ModNetwork.send(player, new S2C.Transition(S2C.TRANSITION_REPAIRED, "title.clockworkdiver.repaired", DefectType.partKey(s.part)));
        Objectives.sync(player);
    }

    /** Ends the dive and sends the player back to the bench at normal size. */
    public static void exit(ServerPlayer player, DiveSession s, boolean success) {
        SESSIONS.remove(s.playerId);
        MinecraftServer server = player.server;
        PlayerProgress progress = WorldProgress.of(player);
        if (success && !s.debug && progress.activeClock != null) {
            Defect d = progress.activeClock.defect(s.defectId);
            if (d != null) {
                d.repaired = true;
                d.found = true;
            }
            WorkshopManager.syncClockItem(player, progress.activeClock);
        }
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.BLINDNESS);
        player.clearFire();
        TickStrike.clear(player);
        sendState(player, s, false);
        ModNetwork.send(player, new S2C.Transition(success ? S2C.TRANSITION_RETURN : S2C.TRANSITION_FAILED,
                success ? "title.clockworkdiver.return" : "title.clockworkdiver.failed",
                success ? "title.clockworkdiver.return.sub" : "title.clockworkdiver.failed.sub"));
        WorkshopManager.returnToBench(player, !s.debug || progress.activeClock != null);
        DelayedTasks.schedule(server, 40, () -> cleanupZone(server, s));
    }

    public static void abort(ServerPlayer player) {
        DiveSession s = session(player);
        if (s == null) return;
        player.displayClientMessage(Component.translatable("message.clockworkdiver.aborted"), false);
        exit(player, s, false);
    }

    /** Fainting inside a clock throws the player out instead of killing them. */
    public static boolean onPlayerDeath(ServerPlayer player) {
        DiveSession s = session(player);
        if (s == null || !ModDimensions.isInterior(player.level())) return false;
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.displayClientMessage(Component.translatable("message.clockworkdiver.fainted"), false);
        exit(player, s, false);
        return true;
    }

    public static void onLogout(ServerPlayer player) {
        DiveSession s = SESSIONS.remove(player.getUUID());
        if (s != null) cleanupZone(player.server, s);
    }

    /** Debug command: finish the current repair instantly. */
    public static boolean forceComplete(ServerPlayer player) {
        DiveSession s = session(player);
        if (s == null || s.phase == DiveSession.COMPLETE) return false;
        ServerLevel level = player.serverLevel();
        if (s.bossId != null) {
            Entity boss = level.getEntity(s.bossId);
            if (boss != null) boss.discard();
        }
        complete(player, s, level);
        return true;
    }

    private static void cleanupZone(MinecraftServer server, DiveSession s) {
        ServerLevel interior = server.getLevel(ModDimensions.INTERIOR);
        if (interior == null) return;
        for (Entity e : interior.getEntitiesOfClass(Entity.class, s.layout.bounds, e -> !(e instanceof Player))) {
            e.discard();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void title(ServerPlayer player, String title, String subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 12));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(subtitle)));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title)));
    }

    private static int count(ServerLevel level, DiveSession s, Class<? extends Mob> type) {
        return level.getEntitiesOfClass(type, s.layout.bounds, Entity::isAlive).size();
    }

    public static int countItem(Player player, Item item) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) n += st.getCount();
        }
        return n;
    }

    public static void ensureItem(ServerPlayer player, Item item, String messageKey) {
        if (countItem(player, item) > 0) return;
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.displayClientMessage(Component.translatable(messageKey), false);
    }

    /** Puts the item in the player's hand: selects its hotbar slot, or swaps it into the selected slot. */
    public static void selectItem(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        if (inv.getSelected().is(item)) return;
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (inv.getItem(i).is(item)) {
                inv.selected = i;
                player.connection.send(new ClientboundSetCarriedItemPacket(i));
                return;
            }
        }
        for (int i = Inventory.getSelectionSize(); i < inv.items.size(); i++) {
            if (inv.items.get(i).is(item)) {
                ItemStack held = inv.items.get(inv.selected);
                inv.items.set(inv.selected, inv.items.get(i));
                inv.items.set(i, held);
                inv.setChanged();
                return;
            }
        }
    }

    private static Vec3 randomSpawn(DiveSession s, RandomSource random) {
        List<Vec3> spots = s.layout.enemySpawns;
        if (spots.isEmpty()) return Vec3.atBottomCenterOf(s.origin);
        return spots.get(random.nextInt(spots.size()));
    }

    @Nullable
    private static <T extends Mob> T spawn(ServerLevel level, DiveSession s, EntityType<T> type, Vec3 pos, @Nullable ServerPlayer target) {
        T mob = type.create(level);
        if (mob == null) return null;
        mob.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360.0f, 0.0f);
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && s.difficulty > 0) {
            hp.setBaseValue(hp.getBaseValue() * (1.0 + 0.15 * s.difficulty));
        }
        mob.setHealth(mob.getMaxHealth());
        if (target != null) mob.setTarget(target);
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.4, pos.z, 10, 0.3, 0.3, 0.3, 0.02);
        return mob;
    }

    private static void spawnNear(ServerLevel level, DiveSession s, EntityType<? extends Mob> type, ServerPlayer target) {
        spawn(level, s, type, randomSpawn(s, level.random), target);
    }

    private static void spawnFlying(ServerLevel level, DiveSession s, ServerPlayer target) {
        spawn(level, s, ModEntities.DUST_MOTH.get(), randomSpawn(s, level.random).add(0.0, 7.0, 0.0), target);
    }

    private static void spawnGremlin(ServerLevel level, DiveSession s, ServerPlayer target, boolean withTooth) {
        CogGremlin g = spawn(level, s, ModEntities.COG_GREMLIN.get(), randomSpawn(s, level.random), target);
        if (g != null && withTooth) {
            g.setHasTooth(true);
            // The Chrono Spectacles show which gremlin carries a tooth.
            g.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 10, 0, false, false));
        }
    }
}
