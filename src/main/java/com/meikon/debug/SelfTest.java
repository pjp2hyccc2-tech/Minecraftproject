package com.meikon.debug;

import com.meikon.Meikon;
import com.meikon.entity.Hanayome;
import com.meikon.entity.Ichimatsu;
import com.meikon.entity.Kageboshi;
import com.meikon.entity.Maboroshi;
import com.meikon.game.Closet;
import com.meikon.game.GameData;
import com.meikon.game.GameManager;
import com.meikon.game.Mansion;
import com.meikon.game.MansionBuilder;
import com.meikon.game.NoiseSystem;
import com.meikon.game.Room;
import com.meikon.registry.ModBlocks;
import com.meikon.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * CI only ({@code -Dmeikon.selftest=true} on a dedicated server): builds a mansion, checks its structure and
 * determinism, path-finds to every room, measures the bride's speeds and hearing, ticks every creature, and
 * writes {@code meikon-selftest.txt} ("SELFTEST RESULT: PASS/FAIL"), then stops the server.
 */
public final class SelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("meikon.selftest");
    private static final List<String> LINES = new ArrayList<>();
    private static int failures;
    private static int stage = -1;
    private static int wait;
    private static long ticks;
    private static Hanayome bride;
    private static Vec3 measureFrom;
    private static double maxMoved;
    private static int speedIndex;
    private static final double[] SPEEDS = {0.52, 0.85, 1.13};

    private SelfTest() {}

    public static void onStarted(MinecraftServer server) {
        if (!ENABLED) return;
        stage = 0;
        wait = 40;
        log("selftest starting");
    }

    private static void check(boolean ok, String what) {
        if (!ok) failures++;
        log((ok ? "PASS " : "FAIL ") + what);
    }

    private static void log(String s) {
        LINES.add(s);
        Meikon.LOGGER.info("[meikon-selftest] {}", s);
        try {
            Files.writeString(java.nio.file.Path.of("meikon-selftest.txt"), String.join("\n", LINES) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static void tick(MinecraftServer server) {
        if (!ENABLED || stage < 0 || stage == 99) return;
        ticks++;
        if (ticks > 20 * 60 * 8) {
            check(false, "global timeout at stage " + stage);
            finish(server);
            return;
        }
        if (stage == 6 && bride != null && measureFrom != null) {
            maxMoved = Math.max(maxMoved, bride.position().distanceTo(measureFrom));
            if (wait % 20 == 0) {
                net.minecraft.world.level.pathfinder.Path pth = bride.getNavigation().getPath();
                log(String.format("  wander trace: %s at %s, path %s", bride.state(), bride.blockPosition(),
                        pth == null ? "none" : (pth.isDone() ? "done" : "to " + pth.getTarget() + " node " + pth.getNextNodeIndex() + "/" + pth.getNodeCount())));
            }
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            run(server);
        } catch (Exception e) {
            check(false, "exception at stage " + stage + ": " + e);
            Meikon.LOGGER.error("[meikon-selftest] failure", e);
            finish(server);
        }
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = GameManager.kakuriyo(server);
        switch (stage) {
            case 0 -> {
                check(level != null, "dimension meikon:kakuriyo exists");
                if (level == null) {
                    finish(server);
                    return;
                }
                GameData data = GameData.get(server);
                long t = System.currentTimeMillis();
                GameManager.newMansion(level, data, 20261102L);
                log("mansion built in " + (System.currentTimeMillis() - t) + " ms");
                Mansion m = GameManager.mansion();
                check(m != null, "mansion recorded");
                if (m == null) {
                    finish(server);
                    return;
                }
                check(m.rooms.size() == 20, "20 rooms (" + m.rooms.size() + ")");
                check(m.closets.size() >= 6, "closets >= 6 (" + m.closets.size() + ")");
                check(m.bellSpots.size() == 3, "3 bells on pedestals (" + m.bellSpots.size() + ")");
                check(m.kuraBell != null && m.butsumaBell != null && m.dollBell != null && m.well != null, "puzzle bells (kura, butsuma, dolls, well)");
                check(m.gateBlocks.size() == 6, "torii veil blocks (" + m.gateBlocks.size() + ")");
                check(m.letters.size() >= 10, "notes placed (" + m.letters.size() + ")");
                check(m.tansuLoot.size() >= 8, "drawers (" + m.tansuLoot.size() + ")");
                check(m.tansuLoot.containsValue("kura_key"), "the storehouse key is somewhere");
                check(m.tansuLoot.values().stream().anyMatch(s -> s.contains("doll_head")), "the doll's head is somewhere");
                check(m.tansuLoot.values().stream().anyMatch(s -> s.contains("matches")), "matches are somewhere");
                check(m.candles[0] != null && m.candles[1] != null && m.candles[2] != null, "three butsuma candles");
                check(level.getBlockState(m.spawn).isAir() && level.getBlockState(m.spawn.above()).isAir(), "spawn is free");
                check(level.getBlockState(m.spawn.below()).isSolidRender(level, m.spawn.below()), "spawn has a floor");
                check(level.getBlockState(m.safeDoor).is(ModBlocks.SEALED_DOOR.get()), "lamplight room has the sealed door");
                for (Closet c : m.closets) {
                    boolean ok = level.getBlockState(c.front()).getBlock() instanceof DoorBlock
                            && level.getBlockState(c.inside()).isAir() && level.getBlockState(c.inside().above()).isAir();
                    if (!ok) check(false, "closet " + c.index() + " at " + c.inside().toShortString());
                }
                log("closets checked");
                LayoutDump.write(level, m);
                int blocked = 0;
                for (BlockPos w : m.waypoints) {
                    if (!level.getBlockState(w).isAir() && !level.getBlockState(w).getCollisionShape(level, w).isEmpty()) blocked++;
                }
                check(blocked <= 2, "waypoints standable (" + blocked + " blocked)");
                // determinism: recording the same seed without building must give the same mansion
                Mansion r = MansionBuilder.record(20261102L);
                boolean same = r.closets.size() == m.closets.size() && r.bellSpots.equals(m.bellSpots) && r.letters.equals(m.letters)
                        && r.tansuLoot.equals(m.tansuLoot) && r.spawn.equals(m.spawn) && r.waypoints.equals(m.waypoints);
                check(same, "mansion record is deterministic");
                // save data round trip
                CompoundTag tag = data.save(new CompoundTag());
                GameData back = GameData.load(tag);
                check(back.seed == data.seed && back.active == data.active, "game data save/load");
                stage = 1;
                wait = 60;
            }
            case 1 -> {
                List<Hanayome> brides = level.getEntitiesOfClass(Hanayome.class, new AABB(-60, 40, -60, 60, 120, 60));
                List<Ichimatsu> dolls = level.getEntitiesOfClass(Ichimatsu.class, new AABB(-60, 40, -60, 60, 120, 60));
                LayoutDump.entities(level);
                check(brides.size() == 1, "one bride (" + brides.size() + ")");
                check(dolls.size() >= 6, "dolls (" + dolls.size() + ")");
                if (brides.isEmpty()) {
                    finish(server);
                    return;
                }
                bride = brides.get(0);
                // path to every room
                Mansion m = GameManager.mansion();
                bride.teleportTo(m.safeOutside.getX() + 0.5, m.safeOutside.getY(), m.safeOutside.getZ() + 0.5);
                int unreachable = 0;
                for (Room room : m.rooms) {
                    if (room.type == Room.Type.SAFE) continue;
                    BlockPos c = room.center();
                    BlockPos best = c;
                    for (BlockPos w : m.waypoints) {
                        if (room.contains(w.getX() + 0.5, w.getZ() + 0.5)) best = w;
                    }
                    c = best;
                    Path path = bride.getNavigation().createPath(c, 1);
                    boolean ok = path != null && path.canReach();
                    if (!ok) {
                        unreachable++;
                        log("  unreachable: " + room.type + " at " + c.toShortString());
                    }
                }
                check(unreachable == 0, "bride can reach every room (" + unreachable + " unreachable)");
                Path toSafe = bride.getNavigation().createPath(m.spawn, 0);
                check(toSafe == null || !toSafe.canReach(), "bride cannot path into the lamplight room");
                stage = 2;
                speedIndex = 0;
                wait = 5;
            }
            case 2 -> {
                // speed measurement along the north engawa
                if (speedIndex >= SPEEDS.length) {
                    stage = 4;
                    wait = 5;
                    return;
                }
                bride.force(Hanayome.State.IDLE, null);
                bride.teleportTo(-12.5, 65, -15.5);
                bride.getNavigation().stop();
                stage = 3;
                wait = 10;
            }
            case 3 -> {
                if (measureFrom == null) {
                    measureFrom = bride.position();
                    bride.getNavigation().moveTo(12.5, 65, -15.5, SPEEDS[speedIndex] * GameManager.brideSpeedFactor());
                    wait = 40;
                    return;
                }
                double dist = bride.position().distanceTo(measureFrom);
                log(String.format("  bride speed x%.2f: %.2f blocks/s (player walks 4.32, sprints 5.61)", SPEEDS[speedIndex], dist / 2.0));
                measureFrom = null;
                speedIndex++;
                stage = 2;
                wait = 2;
            }
            case 4 -> {
                bride.force(Hanayome.State.WANDER, null);
                bride.teleportTo(0.5, 65, -16.5);
                NoiseSystem.emit(level, new Vec3(8.5, 65, -16.5), 20.0f, NoiseSystem.Source.MARBLE);
                stage = 5;
                wait = 12;
            }
            case 5 -> {
                check(bride.state() == Hanayome.State.INVESTIGATE, "bride hears a marble and investigates (" + bride.state() + ")");
                // creatures tick without errors
                Kageboshi k = ModEntities.KAGEBOSHI.get().create(level);
                if (k != null) {
                    k.moveTo(-16.5, 65, 0.5, 0, 0);
                    level.addFreshEntity(k);
                }
                Maboroshi mb = ModEntities.MABOROSHI.get().create(level);
                if (mb != null) {
                    mb.moveTo(16.5, 65, 0.5, 0, 0);
                    level.addFreshEntity(mb);
                }
                // forget the marble first, or she walks back to it and searches the spot
                NoiseSystem.clear();
                bride.force(Hanayome.State.WANDER, null);
                stage = 6;
                wait = 200;
                measureFrom = bride.position();
                maxMoved = 0.0;
            }
            case 6 -> {
                double moved = Math.max(maxMoved, bride.position().distanceTo(measureFrom));
                check(moved > 3.0, String.format("bride wanders (up to %.1f blocks away in 10 s, state %s)", moved, bride.state()));
                check(bride.isAlive(), "bride survived 10 s of ticking");
                log("mansion seed 20261102: " + GameManager.mansion().rooms.stream().map(r -> r.type.name()).toList());
                finish(server);
            }
            default -> {
            }
        }
    }

    private static void finish(MinecraftServer server) {
        stage = 99;
        log("SELFTEST RESULT: " + (failures == 0 ? "PASS" : "FAIL (" + failures + ")"));
        server.halt(false);
    }
}
