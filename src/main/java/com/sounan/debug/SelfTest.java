package com.sounan.debug;

import com.sounan.Sounan;
import com.sounan.block.LockedGateBlock;
import com.sounan.game.GameData;
import com.sounan.game.GameManager;
import com.sounan.registry.ModBlocks;
import com.sounan.registry.ModEntities;
import com.sounan.world.Pois;
import com.sounan.world.PoiLayout;
import com.sounan.world.TerrainData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * CI only ({@code -Dsounan.selftest=true} on a dedicated server): generates the mountain around every POI, checks
 * that every scripted block is where the game logic expects it, measures generation time, ticks every monster, and
 * writes {@code sounan-selftest.txt} ("SELFTEST RESULT: PASS/FAIL"), then stops the server.
 */
public final class SelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("sounan.selftest");
    private static final List<String> LINES = new ArrayList<>();
    private static final List<Mob> MOBS = new ArrayList<>();
    private static int failures;
    private static int stage = -1;
    private static int wait;
    private static long ticks;

    private SelfTest() {}

    public static void onStarted(MinecraftServer server) {
        if (!ENABLED) return;
        stage = 0;
        wait = 40;
        log("selftest starting");
    }

    public static void check(boolean ok, String what) {
        if (!ok) failures++;
        log((ok ? "PASS " : "FAIL ") + what);
    }

    public static void log(String s) {
        LINES.add(s);
        Sounan.LOGGER.info("[sounan-selftest] {}", s);
        try {
            Files.writeString(java.nio.file.Path.of("sounan-selftest.txt"), String.join("\n", LINES) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static void tick(MinecraftServer server) {
        if (!ENABLED || stage < 0 || stage == 99) return;
        ticks++;
        if (ticks > 20 * 60 * 10) {
            check(false, "global timeout at stage " + stage);
            finish(server);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        ServerLevel level = GameManager.mountain(server);
        try {
            switch (stage) {
                case 0 -> {
                    check(level != null, "mountain dimension exists");
                    if (level == null) {
                        finish(server);
                        return;
                    }
                    TerrainData t = TerrainData.get();
                    check(t.size == 512, "terrain loaded (" + t.size + ")");
                    Pois p = Pois.get();
                    check(p.points.size() >= 12, "pois loaded (" + p.points.size() + " points, " + p.ribbons.size() + " ribbons)");
                    PoiLayout L = PoiLayout.get();
                    log("start " + L.start + " bag " + L.bag + " rope " + L.ropeAnchor + " rope column " + L.ropeX + "," + L.ropeZ
                            + " y " + L.ropeTopY + ".." + L.ropeBottomY);
                    check(L.ropeTopY - L.ropeBottomY >= 6, "the cliff below the stake is high enough for a rope");
                    stage = 1;
                }
                case 1 -> {
                    PoiLayout L = PoiLayout.get();
                    long t0 = System.currentTimeMillis();
                    BlockPos[] spots = {L.start, L.bag, L.ropeAnchor, L.omiki, L.hokora, L.hutKeyHook, L.gate, L.carDoor, L.pylon,
                            L.publicPhone, L.noticeBoard, L.memorial};
                    for (BlockPos s : spots) level.getChunk(s.getX() >> 4, s.getZ() >> 4);
                    log("generated " + spots.length + " POI chunks in " + (System.currentTimeMillis() - t0) + " ms");
                    expect(level, L.bag, ModBlocks.LOST_BAG.get(), "lost bag");
                    expect(level, L.ropeAnchor, ModBlocks.ROPE_ANCHOR.get(), "rope anchor");
                    expect(level, L.omiki, ModBlocks.OMIKI_BOTTLE.get(), "omiki bottle");
                    expect(level, L.hokora, ModBlocks.HOKORA.get(), "hokora");
                    expect(level, L.hutKeyHook, ModBlocks.KEY_HOOK.get(), "hut key hook");
                    expect(level, L.gate, ModBlocks.IRON_GATE.get(), "tunnel gate");
                    expect(level, L.carDoor, ModBlocks.CAR_DOOR.get(), "car door");
                    expect(level, L.publicPhone, ModBlocks.PUBLIC_PHONE.get(), "public phone");
                    expect(level, L.noticeBoard, ModBlocks.NOTICE_BOARD.get(), "notice board");
                    expect(level, L.memorial, ModBlocks.NOTICE_BOARD.get(), "memorial board");
                    BlockState gate = level.getBlockState(L.gate);
                    check(gate.hasProperty(LockedGateBlock.LOCKED) && gate.getValue(LockedGateBlock.LOCKED), "the gate starts locked");
                    check(level.getBlockState(L.start).isAir(), "the start position is free");
                    // the tunnel is a hollow tube
                    int z = (L.tunnelZ0 + L.tunnelZ1) / 2;
                    level.getChunk(L.tunnelX >> 4, z >> 4);
                    check(level.getBlockState(new BlockPos(L.tunnelX, L.roadY(z) + 2, z)).isAir(), "tunnel interior is air");
                    check(level.getBlockState(new BlockPos(L.tunnelX, L.roadY(z) + 7, z)).is(ModBlocks.CONCRETE_OLD.get()),
                            "tunnel ceiling is concrete");
                    stage = 2;
                }
                case 2 -> {
                    PoiLayout L = PoiLayout.get();
                    // a sweep along the route: chunk generation must be fast enough to walk through
                    long t0 = System.currentTimeMillis();
                    int n = 0;
                    for (int[] r : Pois.get().route) {
                        level.getChunk(r[0] >> 4, r[1] >> 4);
                        n++;
                    }
                    long ms = System.currentTimeMillis() - t0;
                    log("route sweep: " + n + " chunks in " + ms + " ms");
                    check(ms < 60000, "route chunks generate in reasonable time");
                    BlockPos at = L.start.offset(3, 0, 3);
                    spawn(level, ModEntities.NOZOKI.get(), at);
                    spawn(level, ModEntities.YOBIGOE.get(), at.offset(2, 0, 0));
                    spawn(level, ModEntities.USHIRO.get(), at.offset(-2, 0, 0));
                    spawn(level, ModEntities.KOYANUSHI.get(), at.offset(0, 0, 2));
                    spawn(level, ModEntities.OYAMASAMA.get(), at.offset(0, 0, -3));
                    spawn(level, ModEntities.UTSURUMONO.get(), at.offset(3, 0, -2));
                    check(MOBS.size() == 6, "all six monsters spawn");
                    wait = 100;
                    stage = 3;
                }
                case 3 -> {
                    int alive = 0;
                    for (Mob m : MOBS) if (m.isAlive()) alive++;
                    check(alive == MOBS.size(), "monsters survive 100 ticks (" + alive + ")");
                    for (Mob m : MOBS) m.discard();
                    GameData data = GameData.get(server);
                    check(!data.started, "nobody started the game on the dedicated server");
                    finish(server);
                }
                default -> finish(server);
            }
        } catch (Throwable e) {
            check(false, "exception at stage " + stage + ": " + e);
            Sounan.LOGGER.error("[sounan-selftest] exception", e);
            finish(server);
        }
    }

    private static void expect(ServerLevel level, BlockPos pos, Block block, String what) {
        BlockState s = level.getBlockState(pos);
        check(s.is(block), what + " at " + pos.toShortString() + " (found " + s.getBlock().getDescriptionId() + ")");
    }

    private static void spawn(ServerLevel level, EntityType<? extends Mob> type, BlockPos near) {
        int y = TerrainData.get().height(near.getX(), near.getZ()) + 1;
        Mob m = type.create(level);
        if (m == null) return;
        m.moveTo(near.getX() + 0.5, y, near.getZ() + 0.5, 0.0f, 0.0f);
        level.addFreshEntity(m);
        MOBS.add(m);
    }

    private static void finish(MinecraftServer server) {
        stage = 99;
        log("SELFTEST RESULT: " + (failures == 0 ? "PASS" : "FAIL (" + failures + ")"));
        server.halt(false);
    }
}
