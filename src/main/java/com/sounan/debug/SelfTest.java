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
                    // keep the test areas loaded (nothing else holds a chunk on an empty dedicated server)
                    for (BlockPos c : new BlockPos[]{L.start, L.hutSleeper}) {
                        for (int dx = -1; dx <= 1; dx++)
                            for (int dz = -1; dz <= 1; dz++) level.setChunkForced((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz, true);
                    }
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
                    stage = 4;
                }
                case 4 -> {
                    playthrough(server, level);
                    stage = 5;
                    wait = 60;
                }
                case 5 -> {
                    monsterChecks(level);
                    wait = 80;
                    stage = 6;
                }
                case 6 -> {
                    monsterResults(level);
                    wait = 40;
                    stage = 7;
                }
                case 7 -> {
                    check(sleeper != null && !sleeper.asleep(), "the sleeper wakes when a door bangs next to it");
                    if (sleeper != null) sleeper.discard();
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

    private static net.minecraftforge.common.util.FakePlayer fake;
    private static com.sounan.entity.Nozoki nozoki;
    private static com.sounan.entity.Koyanushi sleeper;

    /** The whole chain of gates with a fake player: bag, rope, sake, shrine, key, gate, car. */
    private static void playthrough(MinecraftServer server, ServerLevel level) {
        PoiLayout L = PoiLayout.get();
        GameData data = GameData.get(server);
        fake = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("5c0a0a00-0000-4000-8000-00000000c0de"), "selftest"));
        fake.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        fake.moveTo(L.bag.getX() + 1.5, L.bag.getY(), L.bag.getZ() + 0.5, 0, 0);
        GameManager.interact(fake, L.bag, "bag");
        check(data.bagOpened, "the bag opens");
        check(GameManager.has(fake, com.sounan.registry.ModItems.ROPE.get()) && GameManager.has(fake, com.sounan.registry.ModItems.FLARE.get())
                && GameManager.has(fake, com.sounan.registry.ModItems.BATTERY.get()), "the bag holds the rope, the flare and batteries");
        GameManager.interact(fake, L.ropeAnchor, "rope_anchor");
        check(data.roped, "the rope is tied to the stake");
        int rope = 0;
        for (int y = L.ropeTopY; y >= L.ropeBottomY; y--) {
            if (level.getBlockState(new BlockPos(L.ropeX, y, L.ropeZ)).is(ModBlocks.CLIMBING_ROPE.get())) rope++;
        }
        check(rope >= L.ropeTopY - L.ropeBottomY - 1, "the rope hangs down the cliff (" + rope + " blocks)");
        {
            // you can walk to the edge and step onto the rope: its top is level with the edge, the edge is free to stand
            // on, the rope lies against the edge's face, and nothing stands on the edge above it
            BlockPos top = new BlockPos(L.ropeX, L.ropeTopY, L.ropeZ);
            BlockPos edgeGround = new BlockPos(L.ropeEdgeX, L.ropeEdgeH, L.ropeEdgeZ);
            boolean walkable = level.getBlockState(edgeGround).isSolid()
                    && !level.getBlockState(edgeGround.above()).blocksMotion()
                    && !level.getBlockState(edgeGround.above(2)).blocksMotion();
            boolean free = !level.getBlockState(top.above()).blocksMotion() && !level.getBlockState(top.above(2)).blocksMotion();
            log("rope top " + top + " edge " + edgeGround + " edge above: " + level.getBlockState(edgeGround.above()) + " / "
                    + level.getBlockState(edgeGround.above(2)) + ", over the rope: " + level.getBlockState(top.above()));
            check(level.getBlockState(top).is(ModBlocks.CLIMBING_ROPE.get()), "the rope starts level with the cliff edge");
            check(walkable, "the cliff edge next to the rope is free to stand on (no rock on it)");
            check(free, "the air over the rope's top is free, so you can step onto it from the edge");
            check(level.getBlockState(top.relative(L.ropeFacing.getOpposite())).isSolid(), "the rope's top lies against the cliff face");
        }
        GameManager.interact(fake, L.hokora, "hokora");
        check(!data.offered, "the shrine wants an offering first");
        GameManager.interact(fake, L.omiki, "omiki");
        check(data.omikiTaken && level.getBlockState(L.omiki).isAir(), "the sake is taken from the village house");
        GameManager.interact(fake, L.hokora, "hokora");
        check(data.offered, "the sake is offered at the shrine");
        check(level.getBlockState(L.hokora).getValue(com.sounan.block.HokoraBlock.OFFERED), "the shrine shows the offering");
        GameManager.interact(fake, L.gate, "gate");
        check(!data.gateUnlocked, "the gate stays locked without the key");
        GameManager.interact(fake, L.hutKeyHook, "key_hook");
        check(data.keyTaken, "the key is taken from the hut");
        GameManager.interact(fake, L.gate, "gate");
        check(data.gateUnlocked && !level.getBlockState(L.gate).getValue(LockedGateBlock.LOCKED), "the key opens the tunnel gate");
        log("checkpoint after the key: " + data.checkpoint);
        // a fall from the cliff must not be a shortcut: the night loops you back
        check(data.ending == 0, "no ending yet");
        GameManager.interact(fake, L.carDoor, "car");
        check(data.ending == 1, "the car ends the night");
        data.reset();
        check(!data.started && data.ending == 0, "progress resets");
        GameManager.resetWorldState(level);
        check(!level.getBlockState(L.bag).getValue(com.sounan.block.LostBagBlock.OPEN)
                && level.getBlockState(L.omiki).is(ModBlocks.OMIKI_BOTTLE.get())
                && !level.getBlockState(L.hokora).getValue(com.sounan.block.HokoraBlock.OFFERED)
                && level.getBlockState(L.hutKeyHook).getValue(com.sounan.block.KeyHookBlock.HAS_KEY)
                && level.getBlockState(L.gate).getValue(LockedGateBlock.LOCKED)
                && !level.getBlockState(new BlockPos(L.ropeX, L.ropeTopY - 3, L.ropeZ)).is(ModBlocks.CLIMBING_ROPE.get()),
                "a replay puts the mountain back (bag, rope, sake, shrine, key, gate)");
    }

    private static void monsterChecks(ServerLevel level) {
        PoiLayout L = PoiLayout.get();
        // the fake player stands still near the start; a nozoki 5 blocks away must be provoked
        BlockPos at = L.start.offset(2, 0, 2);
        fake.moveTo(at.getX() + 0.5, TerrainData.get().height(at.getX(), at.getZ()) + 1, at.getZ() + 0.5, 0, 0);
        nozoki = ModEntities.NOZOKI.get().create(level);
        if (nozoki != null) {
            nozoki.moveTo(at.getX() + 5.5, TerrainData.get().height(at.getX() + 5, at.getZ()) + 1, at.getZ() + 0.5, 90, 0);
            nozoki.setTarget(fake);
            nozoki.peek(true);
            level.addFreshEntity(nozoki);
        }
        // a sleeper wakes when noise is made next to it
        sleeper = ModEntities.KOYANUSHI.get().create(level);
        if (sleeper != null) {
            sleeper.moveTo(L.hutSleeper.getX() + 0.5, L.hutSleeper.getY(), L.hutSleeper.getZ() + 0.5, 0, 0);
            sleeper.setHome(L.hutSleeper, L.hutSleeperFacing);
            level.addFreshEntity(sleeper);
        }
    }

    private static void monsterResults(ServerLevel level) {
        check(nozoki != null && (nozoki.hostileNow() || !nozoki.isAlive() || GameManager.state(fake).captured),
                "a nozoki provoked at 5 blocks attacks (hostile=" + (nozoki != null && nozoki.hostileNow()) + ", captured="
                        + GameManager.state(fake).captured + ")");
        if (nozoki != null) nozoki.discard();
        check(sleeper != null && sleeper.asleep(), "the hut sleeper sleeps while it is quiet");
        if (sleeper != null) {
            com.sounan.game.NoiseSystem.emit(level, sleeper.position().add(1, 0, 0), 8.0f, com.sounan.game.NoiseSystem.Source.DOOR);
            sleeper.life = 100;
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
        if (m instanceof com.sounan.entity.SounanMonster sm) sm.temporary = false;
        level.addFreshEntity(m);
        MOBS.add(m);
    }

    private static void finish(MinecraftServer server) {
        stage = 99;
        ServerLevel level = GameManager.mountain(server);
        if (level != null) {
            PoiLayout L = PoiLayout.get();
            for (BlockPos c : new BlockPos[]{L.start, L.hutSleeper}) {
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) level.setChunkForced((c.getX() >> 4) + dx, (c.getZ() >> 4) + dz, false);
            }
        }
        log("SELFTEST RESULT: " + (failures == 0 ? "PASS" : "FAIL (" + failures + ")"));
        server.halt(false);
    }
}
