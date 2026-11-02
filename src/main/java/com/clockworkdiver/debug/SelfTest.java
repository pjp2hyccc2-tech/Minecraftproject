package com.clockworkdiver.debug;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.block.MechanismBlockEntity;
import com.clockworkdiver.block.ToothSocketBlock;
import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.CommissionGenerator;
import com.clockworkdiver.clock.DefectType;
import com.clockworkdiver.customer.CustomerEntity;
import com.clockworkdiver.customer.CustomerProfile;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.entity.BoltProjectile;
import com.clockworkdiver.entity.CogGremlin;
import com.clockworkdiver.entity.TimeEater;
import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.interior.ZoneBuilder;
import com.clockworkdiver.interior.ZoneLayout;
import com.clockworkdiver.registry.ModBlocks;
import com.clockworkdiver.registry.ModDimensions;
import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.workshop.WorkshopBuilder;
import com.clockworkdiver.workshop.WorkshopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CI only (-Dclockworkdiver.selftest=true): on a dedicated server, builds a workshop and every repair
 * chamber, spawns every entity and lets them tick for ten seconds, checks the commission data, then stops
 * the server and writes {@code run/selftest-report.txt}.
 */
@Mod.EventBusSubscriber(modid = ClockworkDiver.MODID)
public final class SelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("clockworkdiver.selftest");
    private static final List<String> LINES = new ArrayList<>();
    private static boolean failed;
    private static int ticks = -1;
    private static CustomerEntity customer;
    private static double customerStartDist;
    private static final List<Entity> SPAWNED = new ArrayList<>();

    private SelfTest() {}

    private static void check(String name, boolean ok) {
        LINES.add((ok ? "ok   " : "FAIL ") + name);
        if (!ok) failed = true;
    }

    private interface Check {
        void run() throws Exception;
    }

    private static void section(String name, Check body) {
        long t0 = System.nanoTime();
        try {
            body.run();
            LINES.add("     (" + name + " took " + (System.nanoTime() - t0) / 1_000_000 + " ms)");
        } catch (Throwable e) {
            failed = true;
            LINES.add("FAIL " + name + " threw " + e);
            ClockworkDiver.LOGGER.error("Self test section {} failed", name, e);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ENABLED) return;
        MinecraftServer server = event.getServer();
        LINES.add("Clockwork Diver self test");
        ServerLevel ws = server.getLevel(ModDimensions.WORKSHOP);
        ServerLevel interior = server.getLevel(ModDimensions.INTERIOR);
        check("workshop dimension loaded", ws != null);
        check("clock interior dimension loaded", interior != null);
        check("command /clockwork registered", server.getCommands().getDispatcher().getRoot().getChild("clockwork") != null);

        section("commission data", () -> {
            RandomSource rand = RandomSource.create(42);
            int n = 0;
            boolean roundTrip = true;
            for (int rep = 0; rep <= 14; rep++) {
                for (int i = 0; i < 25; i++) {
                    ClockData c = CommissionGenerator.random(rand, rep, n++);
                    ClockData back = ClockData.load(c.save());
                    roundTrip &= back != null && back.save().equals(c.save()) && !c.defects.isEmpty();
                }
            }
            check("commission save/load round trip (" + n + " clocks)", roundTrip);
            ClockData tut = CommissionGenerator.tutorial(1);
            check("tutorial clock has 2 defects", tut.defects.size() == 2 && tut.tutorial);
        });

        if (ws == null || interior == null) {
            ticks = 0;
            return;
        }
        int slot = WorldProgress.get(server).reserveSlot();
        BlockPos wo = WorkshopManager.origin(slot);
        section("workshop build", () -> {
            forceChunks(ws, wo, 2);
            WorkshopBuilder.build(ws, wo);
            check("repair bench placed", ws.getBlockState(wo.offset(WorkshopManager.BENCH)).is(ModBlocks.REPAIR_BENCH.get()));
            check("gear train block entity", ws.getBlockEntity(wo.offset(0, 3, -7)) instanceof MechanismBlockEntity);
            check("tower clock block entity", ws.getBlockEntity(wo.offset(0, 26, 33)) instanceof MechanismBlockEntity);
            check("counter spot is free", ws.getBlockState(wo.offset(WorkshopManager.COUNTER_SPOT)).isAir());
            check("street spawn is free", ws.getBlockState(wo.offset(WorkshopManager.STREET_SPAWN)).isAir()
                    && !ws.getBlockState(wo.offset(WorkshopManager.STREET_SPAWN).below()).isAir());
        });
        section("customer spawn", () -> {
            customer = ModEntities.CUSTOMER.get().create(ws);
            check("customer created", customer != null);
            if (customer == null) return;
            BlockPos counter = wo.offset(WorkshopManager.COUNTER_SPOT);
            BlockPos street = wo.offset(WorkshopManager.STREET_SPAWN);
            customer.setup(UUID.randomUUID(), CustomerProfile.ADA, counter, street);
            customer.moveTo(street.getX() + 0.5, street.getY(), street.getZ() + 0.5, 180.0f, 0.0f);
            customer.setState(CustomerEntity.ARRIVING);
            check("customer added", ws.addFreshEntity(customer));
            customerStartDist = Math.sqrt(customer.distanceToSqr(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5));
        });

        BlockPos base = DiveManager.zoneOrigin(slot);
        for (DefectType type : DefectType.values()) {
            BlockPos zo = base.offset(0, 0, type.ordinal() * 72);
            section("zone " + type.id, () -> {
                forceChunks(interior, zo, 2);
                ZoneLayout layout = ZoneBuilder.build(interior, zo, type, 2, 16, false, RandomSource.create(type.ordinal()));
                BlockPos feet = BlockPos.containing(layout.spawn);
                check(type.id + ": spawn has floor and headroom", !interior.getBlockState(feet.below()).isAir()
                        && interior.getBlockState(feet).isAir() && interior.getBlockState(feet.above()).isAir());
                check(type.id + ": mechanisms placed (" + layout.mechanisms.size() + ")", !layout.mechanisms.isEmpty()
                        && layout.mechanisms.stream().allMatch(p -> interior.getBlockEntity(p) instanceof MechanismBlockEntity));
                switch (type) {
                    case RUST -> check("rust: clusters placed (" + layout.targets.size() + ")", layout.targets.size() >= 8
                            && layout.targets.stream().allMatch(p -> interior.getBlockState(p).is(ModBlocks.RUST_CLUSTER.get())));
                    case TEETH -> {
                        check("teeth: sockets placed (" + layout.targets.size() + ")", layout.targets.size() >= 3
                                && layout.targets.stream().allMatch(p -> {
                                    BlockState s = interior.getBlockState(p);
                                    return s.is(ModBlocks.TOOTH_SOCKET.get()) && !s.getValue(ToothSocketBlock.FILLED);
                                }));
                        check("teeth: escape wheel missing teeth", interior.getBlockEntity(layout.mainMechanism) instanceof MechanismBlockEntity m
                                && m.missing == layout.targets.size());
                    }
                    case SCREW -> {
                        int h = layout.screwHeight;
                        check("screw: head at height " + h, interior.getBlockState(zo.offset(0, h, 0)).is(ModBlocks.SCREW_HEAD.get()));
                        ZoneBuilder.rebuildScrew(interior, zo, h - 1, 1);
                        check("screw: rebuilt one block lower", interior.getBlockState(zo.offset(0, h - 1, 0)).is(ModBlocks.SCREW_HEAD.get())
                                && interior.getBlockState(zo.offset(0, h + 1, 0)).isAir());
                    }
                    case SPRING -> check("spring: crank placed", layout.crank != null
                            && interior.getBlockState(layout.crank).is(ModBlocks.WINDING_CRANK.get()));
                    case NEST -> check("nest: balance wheel", interior.getBlockEntity(layout.mainMechanism) instanceof MechanismBlockEntity m
                            && m.kind == MechanismBlockEntity.BALANCE);
                }
                check(type.id + ": steam vents (" + layout.vents.size() + ")", !layout.vents.isEmpty()
                        && layout.vents.stream().allMatch(p -> interior.getBlockState(p).is(ModBlocks.STEAM_VENT.get())));
                check(type.id + ": enemy spawn points (" + layout.enemySpawns.size() + ")", layout.enemySpawns.size() >= 6);
            });
        }

        section("enemies", () -> {
            BlockPos zo = base;
            List<EntityType<? extends Mob>> types = List.of(ModEntities.RUST_MITE.get(), ModEntities.COG_GREMLIN.get(),
                    ModEntities.DUST_MOTH.get(), ModEntities.TIME_EATER.get());
            int i = 0;
            for (EntityType<? extends Mob> type : types) {
                Mob mob = type.create(interior);
                check("create " + EntityType.getKey(type), mob != null);
                if (mob == null) continue;
                mob.moveTo(zo.getX() + 4.5 + i * 3, zo.getY() + (type == ModEntities.DUST_MOTH.get() ? 4 : 0.2), zo.getZ() + 8.5, 0.0f, 0.0f);
                if (mob instanceof CogGremlin g) g.setHasTooth(true);
                check("spawn " + EntityType.getKey(type), interior.addFreshEntity(mob));
                SPAWNED.add(mob);
                i++;
            }
            BoltProjectile bolt = new BoltProjectile(ModEntities.BOLT.get(), interior);
            bolt.moveTo(zo.getX() + 0.5, zo.getY() + 3, zo.getZ() + 6.5);
            bolt.shoot(0.0, -0.2, -1.0, 1.0f, 0.0f);
            check("spawn bolt", interior.addFreshEntity(bolt));
        });
        ticks = 0;
    }

    private static void forceChunks(ServerLevel level, BlockPos center, int radiusChunks) {
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        for (int x = cx - radiusChunks; x <= cx + radiusChunks; x++) {
            for (int z = cz - radiusChunks; z <= cz + radiusChunks + 1; z++) level.setChunkForced(x, z, true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED || ticks < 0 || event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ticks == 220) {
            section("after 11 seconds of ticking", () -> {
                if (customer != null) {
                    BlockPos counter = customer.counterPos();
                    double d = counter == null ? 99 : Math.sqrt(customer.distanceToSqr(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5));
                    check(String.format("customer walked to the counter (%.1f -> %.1f blocks, state %d)", customerStartDist, d, customer.state()),
                            customer.state() == CustomerEntity.WAITING || d < customerStartDist - 4.0);
                }
                int alive = 0;
                for (Entity e : SPAWNED) if (e.isAlive()) alive++;
                check("enemies still alive and ticking (" + alive + "/" + SPAWNED.size() + ")", alive == SPAWNED.size());
                boolean bossOk = SPAWNED.stream().anyMatch(e -> e instanceof TimeEater t && t.isAlive() && !t.isEnraged());
                check("time eater ticking", bossOk);
            });
            finish(event.getServer());
        }
    }

    private static void finish(MinecraftServer server) {
        LINES.add("SELFTEST RESULT: " + (failed ? "FAIL" : "PASS"));
        try {
            Files.write(Path.of("selftest-report.txt"), LINES, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ClockworkDiver.LOGGER.error("Could not write self test report", e);
        }
        ClockworkDiver.LOGGER.info("Clockwork Diver self test: {}", failed ? "FAIL" : "PASS");
        server.halt(false);
    }
}
