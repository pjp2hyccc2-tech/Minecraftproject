package com.clockworkdiver.interior;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.block.MechanismBlockEntity;
import com.clockworkdiver.block.ToothSocketBlock;
import com.clockworkdiver.block.TuningBellBlock;
import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.ClockType;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.clock.DiveMods;
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
import net.minecraft.world.level.block.Blocks;
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
    public static final String ELITE_TAG = "clockworkdiver_elite";
    /** Pitches of the four tuning bells: a major arpeggio. */
    private static final float[] BELL_PITCH = {0.707f, 0.891f, 1.059f, 1.414f};
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
        if (defect == null || defect.repaired || !defect.type.divable()) return;
        defect.found = true;
        begin(player, progress, clock, defect, false);
    }

    /** Debug and self test entry: dive straight into a chamber of the given kind. */
    public static boolean startDebugDive(ServerPlayer player, DefectType type, boolean tutorial) {
        return startDebugDive(player, type, tutorial, 0, 0, Defect.BOSS_NORMAL);
    }

    public static boolean startDebugDive(ServerPlayer player, DefectType type, boolean tutorial, int mods, int fragments, int boss) {
        if (SESSIONS.containsKey(player.getUUID()) || !type.divable()) return false;
        ClockData clock = new ClockData();
        clock.type = ClockType.POCKET_WATCH;
        clock.tutorial = tutorial;
        Defect defect = new Defect(0, type, type.parts[0], 0.0f, 0.0f);
        defect.found = true;
        defect.mods = mods;
        defect.timeLimit = DiveMods.has(mods, DiveMods.TIMED) ? 180 : 0;
        defect.fragCount = fragments;
        defect.boss = boss;
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
        int period = clock.type.period;
        if (DiveMods.has(defect.mods, DiveMods.FAST)) period = Math.max(8, Math.round(period * 0.72f));
        ZoneLayout layout;
        try {
            layout = ZoneBuilder.build(interior, origin, defect.type, clock.difficulty(), period, clock.tutorial, player.getRandom(),
                    defect.mods, defect.fragCount);
        } catch (Exception e) {
            ClockworkDiver.LOGGER.error("Could not build the clock interior", e);
            player.displayClientMessage(Component.translatable("message.clockworkdiver.build_failed"), false);
            return false;
        }
        DiveSession s = new DiveSession(player.getUUID(), defect, clock, origin, layout);
        s.debug = debug;
        s.period = period;
        s.offset = s.has(DiveMods.OFFBEAT) ? period / 2 : 0;
        s.anchor = interior.getGameTime();
        if (s.has(DiveMods.TIMED) && defect.timeLimit > 0) s.deadline = interior.getGameTime() + 40 + defect.timeLimit * 20L;
        switch (s.type) {
            case RUST, TEETH, DUST -> s.total = layout.targets.size();
            case SCREW -> s.total = layout.screwMaxHeight - 4;
            case SPRING -> s.total = 100;
            case NEST -> s.total = 1;
            case TUNING -> s.total = s.tutorial ? 2 : s.difficulty >= 4 ? 4 : 3;
            case CLUE -> s.total = 1;
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
            case DUST -> PlayerProgress.HINT_DUST;
            case TUNING -> PlayerProgress.HINT_TUNING;
            case CLUE -> 0;
        };
        if (flag != 0 && progress.markSeen(flag)) {
            keys.add("hint.clockworkdiver." + s.type.id + ".0");
            keys.add("hint.clockworkdiver." + s.type.id + ".1");
        }
        modHint(progress, keys, s.has(DiveMods.TIMED), PlayerProgress.HINT_TIMED, "timed");
        modHint(progress, keys, s.has(DiveMods.OFFBEAT), PlayerProgress.HINT_OFFBEAT, "offbeat");
        modHint(progress, keys, s.has(DiveMods.DARK), PlayerProgress.HINT_DARK, "dark");
        modHint(progress, keys, s.has(DiveMods.PRECISION), PlayerProgress.HINT_PRECISION, "precision");
        modHint(progress, keys, s.fragCount > 0, PlayerProgress.HINT_FRAGMENT, "fragment");
        if (!keys.isEmpty()) ModNetwork.send(player, new S2C.Hint(keys));
    }

    private static void modHint(PlayerProgress progress, List<String> keys, boolean applies, int flag, String id) {
        if (applies && progress.markSeen(flag)) keys.add("hint.clockworkdiver." + id + ".0");
    }

    public static void sendState(ServerPlayer player, DiveSession s, boolean inDive) {
        PlayerProgress progress = WorldProgress.of(player);
        ModNetwork.send(player, new S2C.DiveState(inDive, s.type.id, DefectType.partKey(s.part), s.period, s.anchor,
                progress.lensLevel, s.tutorial, s.mods, s.offset, s.deadline));
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
        if (s.ticks % 10 == 0 && s.has(DiveMods.ELITE)) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class, s.layout.bounds, m -> m.isAlive() && m.getTags().contains(ELITE_TAG))) {
                level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 3, 0.25, 0.3, 0.25, 0.01);
            }
        }
        if (s.ticks % 20 == 0 && s.has(DiveMods.DARK)) {
            // The spectacles outline every creature in the dark.
            for (Mob mob : level.getEntitiesOfClass(Mob.class, s.layout.bounds, m -> m.isAlive() && !m.hasEffect(MobEffects.GLOWING))) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 10, 0, false, false));
            }
        }
        if (s.deadline > 0 && s.phase != DiveSession.COMPLETE && !s.pendingComplete) {
            long left = s.deadline - level.getGameTime();
            if (left <= 0) {
                player.displayClientMessage(Component.translatable("message.clockworkdiver.time_up"), false);
                exit(player, s, false);
                return;
            }
            if (left == 20 * 60 || left == 20 * 30 || left == 20 * 10) {
                player.displayClientMessage(Component.translatable("message.clockworkdiver.time_left", left / 20), true);
                level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
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
                if (!s.pendingComplete) {
                    switch (s.type) {
                        case RUST -> tickRust(player, s, level);
                        case TEETH -> tickTeeth(player, s, level);
                        case SCREW -> tickScrew(s, level, player);
                        case SPRING -> tickSpring(player, s, level);
                        case NEST -> tickNest(player, s, level);
                        case DUST -> tickDust(player, s, level);
                        case TUNING -> tickTuning(player, s, level);
                        case CLUE -> complete(player, s, level);
                    }
                }
            }
            case DiveSession.COMPLETE -> {
                if (s.phaseTicks >= 70) exit(player, s, true);
                return;
            }
            default -> {
            }
        }
        int obj = Objects.hash(s.objectiveKey(), s.done, s.total, s.fragCollected);
        if (obj != s.lastObjective) {
            s.lastObjective = obj;
            Objectives.sync(player);
        }
    }

    private static void activate(ServerPlayer player, DiveSession s, ServerLevel level) {
        int extra = s.has(DiveMods.SWARM) ? 2 : 0;
        switch (s.type) {
            case RUST -> {
                for (int i = 0; i < (s.tutorial ? 1 : 2) + extra; i++) spawnNear(level, s, ModEntities.RUST_MITE.get(), player);
            }
            case TEETH -> {
                int n = Math.min(s.total, s.tutorial ? 2 : 3);
                for (int i = 0; i < n; i++) spawnGremlin(level, s, player, true);
                for (int i = 0; i < extra; i++) spawnNear(level, s, ModEntities.RUST_MITE.get(), player);
            }
            case SCREW -> {
                for (int i = 0; i < (s.tutorial ? 1 : 2) + extra; i++) spawnFlying(level, s, player);
            }
            case SPRING -> {
                for (int i = 0; i < 2 + extra; i++) spawnNear(level, s, ModEntities.RUST_MITE.get(), player);
            }
            case DUST -> {
                for (int i = 0; i < (s.tutorial ? 1 : 2) + extra; i++) spawnFlying(level, s, player);
            }
            case TUNING -> {
                s.tuneState = DiveSession.TUNE_PAUSE;
                s.tuneWait = 3;
                s.spawnTimer = 160;
                for (int i = 0; i < extra; i++) spawnFlying(level, s, player);
            }
            case NEST, CLUE -> {
            }
        }
    }

    private static int cap(DiveSession s, int base) {
        return s.has(DiveMods.SWARM) ? base + 2 + base / 2 : base;
    }

    private static int timer(DiveSession s, int base) {
        return s.has(DiveMods.SWARM) ? Math.max(20, (int) (base * 0.6f)) : base;
    }

    // ------------------------------------------------------------------ rust

    private static void tickRust(ServerPlayer player, DiveSession s, ServerLevel level) {
        if (s.ticks % 5 == 0) {
            int remaining = remaining(s, level, ModBlocks.RUST_CLUSTER.get().defaultBlockState()).size();
            s.done = s.total - remaining;
            if (remaining == 0) {
                complete(player, s, level);
                return;
            }
        }
        if (--s.spawnTimer <= 0) {
            s.spawnTimer = timer(s, s.tutorial ? 150 : Math.max(60, 120 - s.difficulty * 12));
            int cap = cap(s, s.tutorial ? 3 : 4 + s.difficulty);
            if (count(level, s, RustMite.class) < cap) {
                List<BlockPos> left = remaining(s, level, ModBlocks.RUST_CLUSTER.get().defaultBlockState());
                if (!left.isEmpty()) {
                    BlockPos t = left.get(level.random.nextInt(left.size()));
                    spawn(level, s, ModEntities.RUST_MITE.get(), Vec3.atBottomCenterOf(t).add(0.0, 1.0, 0.0), player);
                }
            }
        }
    }

    private static List<BlockPos> remaining(DiveSession s, ServerLevel level, BlockState kind) {
        List<BlockPos> left = new ArrayList<>();
        for (BlockPos t : s.layout.targets) {
            if (level.getBlockState(t).is(kind.getBlock())) left.add(t);
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
        if (player.getRandom().nextFloat() < (s.tutorial ? 0.25f : 0.35f) && count(level, s, RustMite.class) < cap(s, 8)) {
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
            s.spawnTimer = timer(s, 80);
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
            } else if (!s.tutorial && gremlins.size() < cap(s, 2) && count(level, s, RustMite.class) < cap(s, 1 + s.difficulty / 2)) {
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
            s.spawnTimer = timer(s, Math.max(80, 150 - s.difficulty * 12));
            int cap = cap(s, s.tutorial ? 2 : 2 + s.difficulty / 2);
            if (count(level, s, DustMoth.class) < cap) spawnFlying(level, s, player);
        }
    }

    public static void onScrewTurn(ServerPlayer player, BlockPos pos) {
        DiveSession s = session(player);
        if (s == null || s.type != DefectType.SCREW || s.phase != DiveSession.ACTIVE || s.pendingComplete) return;
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
            if (s.has(DiveMods.PRECISION) && s.done > 0) {
                // Precision work: the screw backs out a turn.
                s.done--;
                s.screwPhase--;
                s.screwHeight++;
                player.teleportTo(player.getX(), player.getY() + 1.1, player.getZ());
                ZoneBuilder.rebuildScrew(level, s.origin, s.screwHeight, s.screwPhase);
                player.displayClientMessage(Component.translatable("message.clockworkdiver.precision_screw"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.clockworkdiver.screw_slip"), true);
            }
            Vec3 away = player.position().subtract(Vec3.atCenterOf(s.origin)).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 0.01) away = new Vec3(1.0, 0.0, 0.0);
            away = away.normalize().scale(1.3);
            player.setDeltaMovement(away.x, 0.6, away.z);
            player.hurtMarked = true;
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
        int waves = s.has(DiveMods.SWARM) ? 4 : 3;
        if (s.waveIndex < waves && s.progress >= (s.waveIndex + 1) * (100.0f / (waves + 1))) {
            spawnWave(player, s, level, s.waveIndex);
            s.waveIndex++;
        }
        if (--s.spawnTimer <= 0) {
            s.spawnTimer = timer(s, 220);
            if (count(level, s, Monster.class) < cap(s, 2)) spawnNear(level, s, ModEntities.RUST_MITE.get(), player);
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
        if (s == null || s.type != DefectType.SPRING || s.phase != DiveSession.ACTIVE || s.pendingComplete) return;
        if (s.actionCooldown > 0) return;
        s.actionCooldown = 5;
        ServerLevel level = player.serverLevel();
        Quality q = TickStrike.consumeQuality(player, s);
        if (q == Quality.MISS && s.has(DiveMods.PRECISION)) {
            s.progress = Math.max(0.0f, s.progress - 6.0f);
            syncSpring(level, s);
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6f, 1.6f);
            player.displayClientMessage(Component.translatable("message.clockworkdiver.precision_spring"), true);
            return;
        }
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

    // ------------------------------------------------------------------ dust

    private static void tickDust(ServerPlayer player, DiveSession s, ServerLevel level) {
        BlockState pile = ModBlocks.DUST_PILE.get().defaultBlockState();
        if (s.ticks % 5 == 0) {
            int remaining = remaining(s, level, pile).size();
            s.done = s.total - remaining;
            if (remaining == 0) {
                complete(player, s, level);
                return;
            }
        }
        if (--s.spawnTimer <= 0) {
            s.spawnTimer = timer(s, s.tutorial ? 180 : Math.max(80, 150 - s.difficulty * 12));
            int moths = count(level, s, DustMoth.class);
            if (moths < cap(s, s.tutorial ? 2 : 3 + s.difficulty / 2)) spawnFlying(level, s, player);
            if (moths >= 2 && s.done > 0 && s.dustRegrown < 3 && level.random.nextInt(3) == 0) {
                // The moths carry dust back into the gears.
                List<BlockPos> cleared = new ArrayList<>();
                for (BlockPos t : s.layout.targets) if (level.getBlockState(t).isAir()) cleared.add(t);
                if (!cleared.isEmpty()) {
                    BlockPos t = cleared.get(level.random.nextInt(cleared.size()));
                    level.setBlock(t, pile, 3);
                    level.sendParticles(ParticleTypes.WHITE_ASH, t.getX() + 0.5, t.getY() + 0.8, t.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.02);
                    s.dustRegrown++;
                    player.displayClientMessage(Component.translatable("message.clockworkdiver.dust_back"), true);
                }
            }
        }
    }

    /** A Steam Burst blows away the dust heaps around the player. */
    public static void onSteamBurst(ServerPlayer player, double radius, Quality quality) {
        DiveSession s = session(player);
        if (s == null || s.type != DefectType.DUST || s.phase != DiveSession.ACTIVE || s.pendingComplete) return;
        ServerLevel level = player.serverLevel();
        int cleared = 0;
        Vec3 center = player.position().add(0.0, 0.5, 0.0);
        for (BlockPos t : s.layout.targets) {
            if (!level.getBlockState(t).is(ModBlocks.DUST_PILE.get())) continue;
            if (Vec3.atCenterOf(t).distanceTo(center) > radius + 1.0) continue;
            clearDust(player, s, level, t);
            cleared++;
        }
        if (cleared > 0) player.displayClientMessage(Component.translatable("message.clockworkdiver.dust_cleared", cleared), true);
    }

    /** A Just Tick blow scatters a single dust heap; anything less only stirs it. */
    public static boolean onDustPoked(ServerPlayer player, BlockPos pos, Quality quality) {
        DiveSession s = session(player);
        if (s == null || s.type != DefectType.DUST || s.phase != DiveSession.ACTIVE) return false;
        ServerLevel level = player.serverLevel();
        if (quality == Quality.PERFECT) {
            clearDust(player, s, level, pos);
            TickStrike.onPerfectAction(player);
        } else {
            level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
            player.displayClientMessage(Component.translatable("message.clockworkdiver.dust_soft"), true);
        }
        return true;
    }

    private static void clearDust(ServerPlayer player, DiveSession s, ServerLevel level, BlockPos t) {
        level.setBlock(t, Blocks.AIR.defaultBlockState(), 3);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ModBlocks.DUST_PILE.get().defaultBlockState()),
                t.getX() + 0.5, t.getY() + 0.4, t.getZ() + 0.5, 24, 0.4, 0.3, 0.4, 0.1);
        level.sendParticles(ParticleTypes.CLOUD, t.getX() + 0.5, t.getY() + 0.6, t.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.05);
        level.playSound(null, t, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        if (level.random.nextFloat() < 0.25f && count(level, s, DustMoth.class) < cap(s, 5)) {
            spawn(level, s, ModEntities.DUST_MOTH.get(), Vec3.atBottomCenterOf(t).add(0.0, 1.2, 0.0), player);
        }
    }

    // ------------------------------------------------------------------ tuning

    private static long judgedBeat(DiveSession s, long now) {
        return Math.floorDiv(now - s.anchor - s.offset, (long) Math.max(1, s.period));
    }

    private static void newMelody(DiveSession s, RandomSource random) {
        s.melody.clear();
        int length = Math.min(7, 3 + s.done + (s.difficulty >= 3 ? 1 : 0));
        int prev = -1;
        for (int i = 0; i < length; i++) {
            int note = random.nextInt(4);
            if (note == prev && random.nextInt(3) != 0) note = (note + 1 + random.nextInt(3)) % 4;
            s.melody.add(note);
            prev = note;
        }
    }

    private static void tickTuning(ServerPlayer player, DiveSession s, ServerLevel level) {
        if (s.litBell != null && --s.litTicks <= 0) {
            setLit(level, s.litBell, false);
            s.litBell = null;
        }
        long beat = judgedBeat(s, level.getGameTime());
        boolean newBeat = beat != s.tuneBeat;
        s.tuneBeat = beat;
        switch (s.tuneState) {
            case DiveSession.TUNE_PAUSE -> {
                if (newBeat && --s.tuneWait <= 0) {
                    if (s.melody.isEmpty()) newMelody(s, level.random);
                    s.tuneState = DiveSession.TUNE_PLAY;
                    s.tuneIndex = 0;
                    player.displayClientMessage(Component.translatable("message.clockworkdiver.tune_listen"), true);
                }
            }
            case DiveSession.TUNE_PLAY -> {
                if (newBeat) {
                    if (s.tuneIndex < s.melody.size()) {
                        ring(level, s, s.melody.get(s.tuneIndex));
                        s.tuneIndex++;
                    } else {
                        s.tuneState = DiveSession.TUNE_INPUT;
                        s.tuneIndex = 0;
                        s.tuneTimeout = s.melody.size() * s.period * 3 + 80;
                        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 2.0f);
                        player.displayClientMessage(Component.translatable("message.clockworkdiver.tune_play", s.melody.size()), true);
                    }
                }
            }
            case DiveSession.TUNE_INPUT -> {
                if (--s.tuneTimeout <= 0) tuneFail(player, s, level, "message.clockworkdiver.tune_slow");
            }
            default -> {
            }
        }
        if (--s.spawnTimer <= 0) {
            s.spawnTimer = timer(s, s.tutorial ? 260 : 200);
            if (count(level, s, Monster.class) < cap(s, s.tutorial ? 1 : 2)) {
                if (level.random.nextBoolean()) spawnFlying(level, s, player);
                else spawnNear(level, s, ModEntities.RUST_MITE.get(), player);
            }
        }
    }

    private static void ring(ServerLevel level, DiveSession s, int note) {
        if (note < 0 || note >= s.layout.bells.size()) return;
        BlockPos pos = s.layout.bells.get(note);
        if (s.litBell != null && !s.litBell.equals(pos)) setLit(level, s.litBell, false);
        setLit(level, pos, true);
        s.litBell = pos;
        s.litTicks = Math.max(6, (int) (s.period * 0.6f));
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 2.0f, BELL_PITCH[note]);
        level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0, note / 4.0 + 0.1, 0.0, 0.0, 1.0);
    }

    private static void setLit(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState st = level.getBlockState(pos);
        if (st.is(ModBlocks.TUNING_BELL.get()) && st.getValue(TuningBellBlock.LIT) != lit) {
            level.setBlock(pos, st.setValue(TuningBellBlock.LIT, lit), 3);
        }
    }

    private static void tuneFail(ServerPlayer player, DiveSession s, ServerLevel level, String messageKey) {
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
        if (s.has(DiveMods.PRECISION) && s.done > 0) {
            s.done--;
            player.displayClientMessage(Component.translatable("message.clockworkdiver.precision_tune"), true);
        } else {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
        s.tuneState = DiveSession.TUNE_PAUSE;
        s.tuneWait = 3;
    }

    /** The player struck a tuning bell. */
    public static void onBellStruck(ServerPlayer player, BlockPos pos, int note) {
        DiveSession s = session(player);
        ServerLevel level = player.serverLevel();
        if (s == null || s.type != DefectType.TUNING || s.phase != DiveSession.ACTIVE || s.pendingComplete) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, BELL_PITCH[Math.floorMod(note, 4)]);
            return;
        }
        if (s.actionCooldown > 0) return;
        s.actionCooldown = 3;
        if (s.tuneState != DiveSession.TUNE_INPUT) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.5f, BELL_PITCH[Math.floorMod(note, 4)]);
            player.displayClientMessage(Component.translatable("message.clockworkdiver.tune_wait"), true);
            return;
        }
        Quality q = TickStrike.consumeQuality(player, s);
        int expected = s.melody.get(Math.min(s.tuneIndex, s.melody.size() - 1));
        ring(level, s, note);
        if (note != expected || q == Quality.MISS) {
            tuneFail(player, s, level, note != expected ? "message.clockworkdiver.tune_wrong" : "message.clockworkdiver.tune_offbeat");
            return;
        }
        if (q == Quality.PERFECT) TickStrike.onPerfectAction(player);
        else TickStrike.addSteam(player, 4.0f);
        s.tuneIndex++;
        s.tuneTimeout = Math.max(s.tuneTimeout, s.period * 4);
        if (s.tuneIndex >= s.melody.size()) {
            s.done++;
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
            for (BlockPos b : s.layout.bells) {
                level.sendParticles(ParticleTypes.NOTE, b.getX() + 0.5, b.getY() + 1.3, b.getZ() + 0.5, 0, level.random.nextDouble(), 0.0, 0.0, 1.0);
            }
            player.displayClientMessage(Component.translatable("message.clockworkdiver.tune_done", s.done, s.total), true);
            if (s.done >= s.total) {
                complete(player, s, level);
                return;
            }
            s.melody.clear();
            s.tuneState = DiveSession.TUNE_PAUSE;
            s.tuneWait = 3;
        }
    }

    // ------------------------------------------------------------------ memory fragments

    /** The player touched a memory fragment. */
    public static void onFragment(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.getBlockState(pos).is(ModBlocks.MEMORY_COG.get())) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.05);
        level.playSound(null, pos, ModSounds.MEMORY.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        DiveSession s = session(player);
        if (s == null) return;
        s.fragCollected = Math.min(Math.max(1, s.fragCount), s.fragCollected + 1);
        ModNetwork.send(player, new S2C.Memory(s.fragmentKey(s.fragCollected), s.fragCollected, s.fragCount));
        Objectives.sync(player);
        if (s.pendingComplete && s.fragCollected >= s.fragCount) complete(player, s, level);
    }

    /** Debug and CI: picks up the next memory fragment of the current dive. */
    public static boolean debugFragment(ServerPlayer player) {
        DiveSession s = session(player);
        if (s == null) return false;
        for (BlockPos p : s.layout.fragments) {
            if (player.serverLevel().getBlockState(p).is(ModBlocks.MEMORY_COG.get())) {
                onFragment(player, p);
                return true;
            }
        }
        return false;
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
        if ((s.difficulty >= 3 || s.has(DiveMods.SWARM)) && --s.spawnTimer <= 0) {
            s.spawnTimer = timer(s, 300);
            if (count(level, s, DustMoth.class) < cap(s, 2)) spawnFlying(level, s, player);
        }
    }

    private static void spawnBoss(ServerPlayer player, DiveSession s, ServerLevel level) {
        TimeEater boss = ModEntities.TIME_EATER.get().create(level);
        if (boss == null) return;
        boss.setVariant(s.boss);
        boss.moveTo(s.origin.getX() + 0.5, s.origin.getY() + 0.1, s.origin.getZ() + 0.5, 0.0f, 0.0f);
        double hp = switch (s.boss) {
            case Defect.BOSS_SHADOW -> 230.0 + 25.0 * s.difficulty;
            case Defect.BOSS_SHADE -> 270.0 + 25.0 * s.difficulty;
            case Defect.BOSS_QUEEN -> 560.0;
            default -> 150.0 + 25.0 * s.difficulty;
        };
        AttributeInstance maxHp = boss.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(hp);
        boss.setHealth(boss.getMaxHealth());
        boss.setTarget(player);
        if (s.has(DiveMods.DARK)) boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 20, 0, false, false));
        level.addFreshEntity(boss);
        s.bossId = boss.getUUID();
        s.bossSpawned = true;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, boss.getX(), boss.getY() + 1.0, boss.getZ(), 60, 1.2, 1.0, 1.2, 0.05);
        level.playSound(null, boss.blockPosition(), ModSounds.BOSS_ROAR.get(), SoundSource.HOSTILE, 2.0f, s.boss == Defect.BOSS_QUEEN ? 0.6f : 0.8f);
        String suffix = switch (s.boss) {
            case Defect.BOSS_SHADOW -> ".shadow";
            case Defect.BOSS_SHADE -> ".shade";
            case Defect.BOSS_QUEEN -> ".queen";
            default -> "";
        };
        title(player, "title.clockworkdiver.boss" + suffix, "title.clockworkdiver.boss" + suffix + ".sub");
    }

    /** A Time Eater dropped below half health: the clock's beat speeds up. */
    public static void onBossEnraged(TimeEater boss) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        for (DiveSession s : SESSIONS.values()) {
            if (!s.layout.bounds.contains(boss.position()) || s.enraged) continue;
            s.enraged = true;
            s.period = Math.max(8, Math.round(s.period * 0.65f));
            s.anchor = level.getGameTime();
            if (s.offset != 0) s.offset = s.period / 2;
            retime(level, s);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(s.playerId);
            if (player != null) {
                ModNetwork.send(player, new S2C.TempoSync(s.period, s.anchor, s.offset));
                title(player, "title.clockworkdiver.enraged", "title.clockworkdiver.enraged.sub");
            }
        }
    }

    /** The Queen of Time Eaters changes phase: 2 = the beat races, 3 = the beat turns inside out (off-beat). */
    public static void onBossPhase(TimeEater boss, int phase) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        for (DiveSession s : SESSIONS.values()) {
            if (!s.layout.bounds.contains(boss.position()) || s.bossPhase >= phase) continue;
            s.bossPhase = phase;
            if (phase == 2) {
                s.period = Math.max(8, Math.round(s.period * 0.72f));
                s.offset = s.offset != 0 ? s.period / 2 : 0;
            } else {
                s.offset = s.period / 2;
            }
            s.anchor = level.getGameTime();
            retime(level, s);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(s.playerId);
            if (player != null) {
                ModNetwork.send(player, new S2C.TempoSync(s.period, s.anchor, s.offset));
                ModNetwork.send(player, new S2C.Transition(S2C.TRANSITION_ALERT, "title.clockworkdiver.queen." + phase,
                        "title.clockworkdiver.queen." + phase + ".sub"));
            }
        }
    }

    private static void retime(ServerLevel level, DiveSession s) {
        for (BlockPos mp : s.layout.mechanisms) {
            if (level.getBlockEntity(mp) instanceof MechanismBlockEntity m) {
                m.period = s.period;
                m.running = true;
                m.sync();
            }
        }
    }

    // ------------------------------------------------------------------ completion

    private static void complete(ServerPlayer player, DiveSession s, ServerLevel level) {
        if (s.phase == DiveSession.COMPLETE) return;
        if (s.fragCollected < s.fragCount) {
            // Repaired, but memory fragments are still lying around: let the player gather them in peace.
            if (s.pendingComplete) return;
            s.pendingComplete = true;
            s.deadline = 0;
            s.done = Math.max(s.done, s.total);
            clearVermin(level, s);
            runMechanisms(level, s);
            level.playSound(null, player.blockPosition(), ModSounds.REPAIRED.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
            player.displayClientMessage(Component.translatable("message.clockworkdiver.fragments_left", s.fragCount - s.fragCollected), false);
            sendState(player, s, true);
            Objectives.sync(player);
            return;
        }
        s.phase = DiveSession.COMPLETE;
        s.phaseTicks = 0;
        s.done = Math.max(s.done, s.total);
        clearVermin(level, s);
        runMechanisms(level, s);
        level.playSound(null, player.blockPosition(), ModSounds.REPAIRED.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 60, 1.5, 1.0, 1.5, 0.3);
        ModNetwork.send(player, new S2C.Transition(S2C.TRANSITION_REPAIRED, "title.clockworkdiver.repaired", DefectType.partKey(s.part)));
        Objectives.sync(player);
    }

    private static void clearVermin(ServerLevel level, DiveSession s) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, s.layout.bounds, Entity::isAlive)) {
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            mob.discard();
        }
    }

    private static void runMechanisms(ServerLevel level, DiveSession s) {
        for (BlockPos mp : s.layout.mechanisms) {
            if (level.getBlockEntity(mp) instanceof MechanismBlockEntity m) {
                m.running = true;
                m.missing = 0;
                if (m.kind == MechanismBlockEntity.SPRING) m.progress = 1.0f;
                m.sync();
            }
        }
        if (s.litBell != null) setLit(level, s.litBell, false);
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
        if (s.pendingComplete) {
            // The part is already repaired; leaving now just leaves the fragments behind.
            exit(player, s, true);
            return;
        }
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
        exit(player, s, s.pendingComplete);
        return true;
    }

    public static void onLogout(ServerPlayer player) {
        DiveSession s = SESSIONS.remove(player.getUUID());
        if (s != null) cleanupZone(player.server, s);
    }

    /** Debug command: finish the current repair instantly (picking up any memory fragments silently). */
    public static boolean forceComplete(ServerPlayer player) {
        DiveSession s = session(player);
        if (s == null || s.phase == DiveSession.COMPLETE) return false;
        ServerLevel level = player.serverLevel();
        if (s.bossId != null) {
            Entity boss = level.getEntity(s.bossId);
            if (boss != null) boss.discard();
        }
        for (BlockPos p : s.layout.fragments) {
            if (level.getBlockState(p).is(ModBlocks.MEMORY_COG.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        s.fragCollected = s.fragCount;
        s.pendingComplete = false;
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
        if (s.has(DiveMods.ELITE)) {
            if (hp != null) hp.setBaseValue(hp.getBaseValue() * 1.7);
            AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * 1.3);
            AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.1);
            mob.addTag(ELITE_TAG);
        }
        if (s.has(DiveMods.DARK)) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 10, 0, false, false));
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
