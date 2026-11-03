package com.reisatsu.debug;

import com.mojang.authlib.GameProfile;
import com.reisatsu.Reisatsu;
import com.reisatsu.entity.Manager;
import com.reisatsu.entity.Medium;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.Camera;
import com.reisatsu.game.GameData;
import com.reisatsu.game.GameManager;
import com.reisatsu.game.NoiseSystem;
import com.reisatsu.game.PlayerState;
import com.reisatsu.game.Sight;
import com.reisatsu.game.Spirits;
import com.reisatsu.game.Story;
import com.reisatsu.item.GameItem;
import com.reisatsu.registry.ModItems;
import com.reisatsu.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * CI only ({@code -Dreisatsu.selftest=true} on a dedicated server): a fake-player playthrough of the night — start,
 * items, a valid / aware / back-turned / through-glass shot of each spirit, awareness from sight and noise, caught →
 * wake → お札, closing time, the photos handed to the medium, the exorcism, the ending, a reset. Writes
 * {@code reisatsu-selftest.txt} ("SELFTEST RESULT: PASS/FAIL") and stops the server.
 */
public final class SelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("reisatsu.selftest");
    private static final List<String> LINES = new ArrayList<>();
    private static int failures;
    private static int stage = -1;
    private static int wait;
    private static long ticks;
    private static FakePlayer fake;
    private static int kindIdx;
    private static Spirit probe;
    private static int ofudaBefore;
    private static int stageTicks;
    private static final int STAGE_WORLD = 50;
    private static int worldStage;
    /** The test lane: where the camera stands and where the spirit stands (clear line of sight, chosen at runtime). */
    private static Vec3 laneEye, laneSpirit;
    private static float maxAware;
    private static float laneYaw;

    private SelfTest() {}

    public static void onStarted(MinecraftServer server) {
        if (!ENABLED) return;
        stage = STAGE_WORLD;
        worldStage = 0;
        wait = 40;
        log("selftest starting");
        WorldCheck.dumpLayout(Path.of("reisatsu-layout.txt"));
    }

    public static void check(boolean ok, String what) {
        if (!ok) failures++;
        log((ok ? "PASS " : "FAIL ") + what);
    }

    public static void log(String s) {
        LINES.add(s);
        Reisatsu.LOGGER.info("[reisatsu-selftest] {}", s);
        try {
            Files.writeString(Path.of("reisatsu-selftest.txt"), String.join("\n", LINES) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static void tick(MinecraftServer server) {
        if (!ENABLED || stage < 0 || stage == 99) return;
        ticks++;
        if (ticks > 20 * 60 * 14) {
            check(false, "global timeout at stage " + stage);
            finish(server);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        ServerLevel level = GameManager.level(server);
        try {
            if (level == null) {
                check(false, "the restaurant level exists");
                finish(server);
                return;
            }
            GameData data = GameData.get(server);
            stageTicks++;
            switch (stage) {
                case STAGE_WORLD -> world(level);
                case 0 -> setup(server, level, data);
                case 1 -> shots(level, data);
                case 2 -> awarenessStart(level, data);
                case 3 -> awarenessWatch(level, data);
                case 30 -> awayStart(level, data);
                case 31 -> awayResult(level, data);
                case 32 -> camStart(level, data);
                case 33 -> camWatch(level, data);
                case 34 -> camOffResult(level, data);
                case 35 -> breathResult(level, data);
                case 36 -> heldResult(level, data);
                case 4 -> noiseStart(level, data);
                case 5 -> noiseResult(level, data);
                case 6 -> caughtStart(level, data);
                case 7 -> caughtResult(level, data);
                case 8 -> captureFive(level, data);
                case 9 -> closingResult(level, data);
                case 10 -> submit(level, data);
                case 11 -> exorcismWait(level, data);
                case 12 -> endingAndReset(server, level, data);
                case 13 -> badEnding(server, level, data);
                case 20 -> fixtures(level, data);
                case 21 -> lockerResult(level, data);
                default -> finish(server);
            }
        } catch (Throwable e) {
            check(false, "exception at stage " + stage + ": " + e);
            Reisatsu.LOGGER.error("[reisatsu-selftest] exception", e);
            finish(server);
        }
    }

    private static void next(int s, int w) {
        stage = s;
        wait = w;
        stageTicks = 0;
    }

    private static void place(Vec3 at, float yaw) {
        fake.moveTo(at.x, at.y, at.z, yaw, 0.0f);
        fake.setYHeadRot(yaw);
        fake.setXRot(0.0f);
    }

    // ------------------------------------------------------------------------------------------------ the world's own checks
    private static void world(ServerLevel level) {
        if (worldStage == 0) log("---- the world's checks (WorldCheck)");
        int w = WorldCheck.run(level, worldStage++, SelfTest::log, SelfTest::check);
        if (w < 0) {
            log("---- the game");
            next(0, 10);
        } else {
            wait = w;
        }
    }

    // ------------------------------------------------------------------------------------------------ 0: start
    private static void setup(MinecraftServer server, ServerLevel level, GameData data) {
        log("level " + level.dimension().location() + (Greybox.fallback(server) ? " (greybox stand-in: the world's dimension is not merged yet)" : ""));
        Greybox.ensure(level);
        com.reisatsu.world.Fixtures.resetAll(level);
        for (Layout.Marker m : List.of(Layout.marker("spawn"), Layout.marker("table_7"), Layout.marker("manager_desk"))) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                level.setChunkForced((m.pos().getX() >> 4) + dx, (m.pos().getZ() >> 4) + dz, true);
        }
        fake = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c0a0a00-0000-4000-8000-00000000fa11"), "selftest"));
        fake.setGameMode(GameType.ADVENTURE);
        GameManager.addTestPlayer(fake);
        Spirits.paused = true;
        data.resetProgress();
        data.started = true;
        data.phase = GameData.PHASE_INVESTIGATE;
        data.introSeen = true;
        Story.resetWorld(level);
        Story.finishIntro(fake, data);
        Layout.Marker spawn = Layout.marker("spawn");
        place(spawn.center(), spawn.yaw());
        check(GameItem.film(GameManager.camera(fake)) == GameItem.START_FILM, "the medium's camera holds " + GameItem.START_FILM + " film");
        check(GameManager.has(fake, ModItems.FLASHLIGHT.get()), "a flashlight");
        check(GameManager.count(fake, ModItems.OFUDA.get()) == 3, "three お札");
        check(GameManager.has(fake, ModItems.SHOP_KEY.get()), "the shop key");
        check(data.notes.contains("report") && data.notes.contains("medium"), "the report and the medium's note are in the album");
        Spirits.ensure(level, data);
        check(Spirits.medium(level) != null, "the medium waits in the lot");
        Spirits.removeAll(level);
        int film3 = level.getEntities((net.minecraft.world.entity.Entity) null, Spirits.AREA, e -> e.getTags().contains("reisatsu_film_3")).size();
        int key = level.getEntities((net.minecraft.world.entity.Entity) null, Spirits.AREA, e -> e.getTags().contains("reisatsu_office_key")).size();
        check(film3 == 1, "film pack 3 lies in the walk-in fridge (" + film3 + ")");
        check(key == 1, "the office key lies in the staff room (" + key + ")");
        GameManager.debugPeaceful = false;
        lane(level);
        kindIdx = 0;
        next(20, 10);
    }

    /** A straight, clear lane in the dining room: the camera at one end, the spirit 7 blocks away facing it. */
    private static void lane(ServerLevel level) {
        double[][] candidates = {
                {16.5, 13.5, 23.5, 13.5}, {24.5, 13.5, 31.5, 13.5}, {36.5, 14.5, 36.5, 21.5}, {17.5, 26.5, 24.5, 26.5},
                {16.5, 16.5, 16.5, 23.5}};
        for (double[] c : candidates) {
            Vec3 eye = new Vec3(c[0], Layout.FEET_Y, c[1]);
            Vec3 at = new Vec3(c[2], Layout.FEET_Y, c[3]);
            if (Sight.clear(level, eye.add(0, 1.62, 0), at.add(0, 1.55, 0)) && Sight.clear(level, eye.add(0, 1.62, 0), at.add(0, 0.9, 0))) {
                laneEye = eye;
                laneSpirit = at;
                laneYaw = (float) (Mth.atan2(at.z - eye.z, at.x - eye.x) * Mth.RAD_TO_DEG) - 90.0f;
                log(String.format(Locale.ROOT, "test lane %.1f,%.1f -> %.1f,%.1f", c[0], c[1], c[2], c[3]));
                return;
            }
            log(String.format(Locale.ROOT, "lane %.1f,%.1f -> %.1f,%.1f is blocked", c[0], c[1], c[2], c[3]));
        }
        Layout.Marker t = Layout.marker("table_12");
        laneEye = new Vec3(t.pos().getX() + 0.5, Layout.FEET_Y, t.pos().getZ() + 3.5);
        laneSpirit = laneEye.add(0, 0, 6);
        laneYaw = 0.0f;
        check(false, "a clear test lane in the dining room");
    }

    // ------------------------------------------------------------------------------------------------ 20-21: the restaurant's things
    /** The register drawer gives film pack 1, the locker at film_2 hides you and gives film pack 2, through the world's blocks. */
    private static void fixtures(ServerLevel level, GameData data) {
        int before = GameManager.count(fake, ModItems.FILM_PACK.get());
        BlockPos reg = Layout.marker("register").pos();
        use(level, reg);
        check(GameManager.count(fake, ModItems.FILM_PACK.get()) == before + 1, "opening the register drawer finds film pack 1");
        use(level, reg); // shut it again
        use(level, reg);
        check(GameManager.count(fake, ModItems.FILM_PACK.get()) == before + 1, "the drawer gives it only once");
        BlockPos locker = Layout.marker("film_2").pos();
        Vec3 front = Vec3.atBottomCenterOf(locker.relative(Layout.marker("film_2").facing()));
        place(front, Layout.marker("film_2").facing().getOpposite().toYRot());
        use(level, locker);
        PlayerState ps = GameManager.state(fake);
        check(ps.hiding(), "right-clicking a locker hides you in it");
        check(fake.blockPosition().equals(locker), "inside the locker's block (the world's own test for stepping out)");
        check(GameManager.count(fake, ModItems.FILM_PACK.get()) == before + 2, "film pack 2 is in that locker");
        next(21, 10);
    }

    private static void lockerResult(ServerLevel level, GameData data) {
        PlayerState ps = GameManager.state(fake);
        BlockPos locker = Layout.marker("film_2").pos();
        check(ps.hiding() && fake.isPassenger(), "still hidden a moment later");
        use(level, locker);
        check(!ps.hiding() && !fake.isPassenger(), "right-clicking from inside steps out");
        check(fake.blockPosition().equals(locker.relative(Layout.marker("film_2").facing())), "standing in front of the locker");
        Layout.Marker spawn = Layout.marker("spawn");
        place(spawn.center(), spawn.yaw());
        next(1, 5);
    }

    /** Right-clicks a block as the fake player (the world's block behaviours run). */
    private static void use(ServerLevel level, BlockPos pos) {
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        level.getBlockState(pos).use(level, fake, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    }

    // ------------------------------------------------------------------------------------------------ 1: shots
    /** For each spirit: a valid shot, an aware one, one with its back turned, one through glass (or a wall). */
    private static void shots(ServerLevel level, GameData data) {
        if (kindIdx >= SpiritKind.ALL.length) {
            Spirits.removeAll(level);
            next(2, 10);
            return;
        }
        SpiritKind kind = SpiritKind.ALL[kindIdx++];
        Spirits.removeAll(level);
        Vec3 at;
        Vec3 eye;
        float spiritYaw;
        if (kind == SpiritKind.MIRROR_WOMAN) {
            // in the cavity, photographed from the women's restroom through the mirror
            Layout.Marker mw = Layout.marker("mirror_woman");
            at = mw.center();
            Vec3 standAt = Layout.marker("sink_w").center().add(0, 0, 1.5);
            place(standAt, 180.0f);
            spiritYaw = mw.yaw();
        } else {
            place(laneEye, laneYaw);
            at = laneSpirit;
            spiritYaw = laneYaw + 180.0f; // facing the camera
        }
        Spirit s = Spirits.spawnAt(level, kind, at, spiritYaw);
        check(s != null, kind.id + " spawns");
        if (s == null) return;
        int pose = switch (kind) {
            case VISITOR -> 0;
            case WAITRESS -> 2;
            case CHEF -> 2;
            case CHILD -> 2;
            case MIRROR_WOMAN -> 0;
            case MANAGER -> 0;
        };
        s.freeze(pose);
        s.faceYaw(spiritYaw);
        eye = fake.getEyePosition();
        Camera.Shot ok = aim(level, s, eye);
        check(ok.result() == GameData.RESULT_CAPTURED, kind.id + ": unaware, facing the camera → captured (" + ok.reason() + ", ★" + ok.stars() + ")");
        s.setAwareness(fake, 1.0f);
        Camera.Shot aware = aim(level, s, eye);
        check(aware.result() == GameData.RESULT_FAILED, kind.id + ": it has noticed you → blurred (" + aware.reason() + ")");
        s.setAwareness(fake, 0.0f);
        s.faceYaw(spiritYaw + 180.0f);
        Camera.Shot back = aim(level, s, eye);
        check(back.result() != GameData.RESULT_CAPTURED, kind.id + ": back turned → no name (" + back.reason() + ")");
        s.faceYaw(spiritYaw);
        if (kind == SpiritKind.MIRROR_WOMAN) {
            check(ok.result() == GameData.RESULT_CAPTURED, kind.id + ": through the mirror glass ✓");
        } else {
            BlockPos mid = BlockPos.containing(eye.add(Camera.head(s).subtract(eye).scale(0.5)));
            BlockState was = level.getBlockState(mid);
            level.setBlock(mid, Blocks.GLASS.defaultBlockState(), 2);
            Camera.Shot glass = aim(level, s, eye);
            check(glass.result() == GameData.RESULT_CAPTURED, kind.id + ": through glass → captured (" + glass.reason() + ")");
            level.setBlock(mid, Blocks.STONE.defaultBlockState(), 2);
            Camera.Shot wall = aim(level, s, eye);
            check(wall.result() != GameData.RESULT_CAPTURED, kind.id + ": behind a wall → nothing (" + wall.reason() + ")");
            level.setBlock(mid, was, 2);
        }
        s.discard();
        wait = 2;
    }

    private static Camera.Shot aim(ServerLevel level, Spirit s, Vec3 eye) {
        Vec3 h = Camera.head(s);
        float yaw = (float) (Mth.atan2(h.z - eye.z, h.x - eye.x) * Mth.RAD_TO_DEG) - 90.0f;
        float pitch = (float) -(Mth.atan2(h.y - eye.y, Math.sqrt((h.x - eye.x) * (h.x - eye.x) + (h.z - eye.z) * (h.z - eye.z))) * Mth.RAD_TO_DEG);
        com.reisatsu.game.Spirits.invalidate();
        return Camera.validate(level, fake, eye, yaw, pitch, 70.0f, 0.0f);
    }

    // ------------------------------------------------------------------------------------------------ 2-5: awareness
    private static Vec3 along(double blocks) {
        return laneEye.add(laneSpirit.subtract(laneEye).normalize().scale(blocks));
    }

    /** Sight alone: the waitress (no security cameras, unlike the manager) 3 blocks away, facing you, in the dark. */
    private static void awarenessStart(ServerLevel level, GameData data) {
        place(laneEye, laneYaw);
        probe = Spirits.spawnAt(level, SpiritKind.WAITRESS, along(3.0), laneYaw + 180.0f);
        if (probe != null) {
            probe.debugStill = true;
            probe.setAnimPose(com.reisatsu.entity.Waitress.STAND);
            probe.faceYaw(laneYaw + 180.0f);
        }
        maxAware = 0.0f;
        next(3, 0);
    }

    /**
     * Watches the awareness grow tick by tick until the spirit notices (then it is removed before it can hunt the test
     * player down: being caught clears its awareness and would leave the player un-huntable for the next checks).
     */
    private static void awarenessWatch(ServerLevel level, GameData data) {
        if (probe == null) {
            check(false, "the probe spirit spawns");
            next(30, 5);
            return;
        }
        float a = probe.awarenessOf(fake);
        maxAware = Math.max(maxAware, a);
        boolean noticed = a >= 1.0f || probe.mode() == Spirit.Mode.NOTICE || probe.hunting();
        if (noticed || stageTicks > 120) {
            check(noticed, String.format(Locale.ROOT, "a spirit that sees you (3 blocks, in the dark) notices you (after %d ticks, awareness %.2f, %s)",
                    stageTicks, maxAware, probe.mode()));
            check(GameManager.huntable(fake), "you are still huntable");
            probe.discard();
            next(30, 5);
        }
    }

    private static void awayStart(ServerLevel level, GameData data) {
        // calm (a panicked player's breathing carries: tested next)
        PlayerState ps = GameManager.state(fake);
        ps.forceFear = 0.3f;
        ps.forceDanger = 0.0f;
        ps.forceTicks = 600;
        place(laneEye, laneYaw);
        probe = Spirits.spawnAt(level, SpiritKind.WAITRESS, along(3.0), laneYaw); // her back to you
        if (probe != null) {
            probe.debugStill = true;
            probe.setAnimPose(com.reisatsu.entity.Waitress.STAND);
            probe.faceYaw(laneYaw);
        }
        next(31, 40);
    }

    private static void awayResult(ServerLevel level, GameData data) {
        float a = probe == null ? -1 : probe.awarenessOf(fake);
        check(a == 0.0f && probe != null && !probe.hunting(), String.format(Locale.ROOT, "a spirit with its back to you does not see you (%.2f)", a));
        if (probe == null) {
            next(32, 5);
            return;
        }
        // §3.5: at high fear your breathing is heard close by...
        GameManager.state(fake).forceFear = 0.95f;
        probe.setAwareness(fake, 0.0f);
        next(35, 70);
    }

    private static void breathResult(ServerLevel level, GameData data) {
        float a = probe.awarenessOf(fake);
        check(a > 0.05f, String.format(Locale.ROOT, "...but it hears your panicked breathing 3 blocks behind it (%.2f)", a));
        // ...unless you hold it
        probe.setAwareness(fake, 0.0f);
        PlayerState ps = GameManager.state(fake);
        ps.breathHolding = true;
        ps.breathTicks = 0;
        ps.breathGauge = 1.0f;
        next(36, 40);
    }

    private static void heldResult(ServerLevel level, GameData data) {
        float a = probe.awarenessOf(fake);
        check(a == 0.0f, String.format(Locale.ROOT, "holding your breath, it hears nothing (%.2f)", a));
        PlayerState ps = GameManager.state(fake);
        ps.breathHolding = false;
        ps.breathTicks = 0;
        ps.forceFear = 0.3f;
        ps.forceTicks = 40;
        probe.discard();
        next(32, 5);
    }

    // ------------------------------------------------------------------------------------------------ 32-34: the security cameras
    /** The manager at his desk, far away in the office, watches the dining room through its cameras (circuit on). */
    private static void camStart(ServerLevel level, GameData data) {
        place(laneEye, laneYaw);
        Layout.Marker d = Layout.marker("manager_desk");
        probe = Spirits.spawnAt(level, SpiritKind.MANAGER, Manager.deskSpot(), d.yaw());
        if (probe != null) {
            probe.debugStill = true;
            probe.setAnimPose(Manager.SIT);
        }
        maxAware = 0.0f;
        next(33, 0);
    }

    private static void camWatch(ServerLevel level, GameData data) {
        if (probe == null) {
            check(false, "the manager spawns at his desk");
            next(4, 5);
            return;
        }
        float a = probe.awarenessOf(fake);
        maxAware = Math.max(maxAware, a);
        if (a >= 0.5f || stageTicks > 100) {
            check(a >= 0.5f, String.format(Locale.ROOT, "the manager at his desk sees you through a security camera (%.2f after %d ticks)", maxAware, stageTicks));
            probe.setAwareness(fake, 0.0f);
            com.reisatsu.world.Fixtures.setCircuit(level, com.reisatsu.world.Circuit.CAMERAS, false);
            next(34, 40);
        }
    }

    private static void camOffResult(ServerLevel level, GameData data) {
        float a = probe == null ? -1 : probe.awarenessOf(fake);
        check(a == 0.0f, String.format(Locale.ROOT, "with the cameras' circuit off he sees nothing from there (%.2f)", a));
        if (probe != null) probe.discard();
        com.reisatsu.world.Fixtures.setCircuit(level, com.reisatsu.world.Circuit.CAMERAS, true);
        next(4, 5);
    }

    private static void noiseStart(ServerLevel level, GameData data) {
        place(laneEye, laneYaw);
        probe = Spirits.spawnAt(level, SpiritKind.WAITRESS, along(4.0), laneYaw); // facing away
        if (probe != null) {
            probe.debugStill = true;
            probe.faceYaw(laneYaw);
        }
        next(5, 3);
    }

    private static void noiseResult(ServerLevel level, GameData data) {
        if (stageTicks == 1) {
            NoiseSystem.emit(level, fake.position(), 14.0f, "sprint", fake);
            return;
        }
        if (stageTicks < 4) return;
        float a = probe == null ? 0 : probe.awarenessOf(fake);
        check(a > 0.1f, String.format(Locale.ROOT, "a sprinting step behind a spirit raises its awareness (%.2f)", a));
        check(probe != null && (probe.mode() == Spirit.Mode.INVESTIGATE || probe.hunting()), "it turns to investigate the noise ("
                + (probe == null ? "-" : probe.mode()) + ")");
        if (probe != null) probe.discard();
        next(6, 5);
    }

    // ------------------------------------------------------------------------------------------------ 6-7: caught
    private static void caughtStart(ServerLevel level, GameData data) {
        ofudaBefore = GameManager.count(fake, ModItems.OFUDA.get());
        place(laneEye, laneYaw);
        probe = Spirits.spawnAt(level, SpiritKind.WAITRESS, fake.position().add(0, 0, 0.5), 180.0f);
        if (probe != null) GameManager.caught(fake, probe);
        check(GameManager.state(fake).caught(), "touched by a hunting spirit = caught");
        next(7, GameManager.CAUGHT_TICKS + 10);
    }

    private static void caughtResult(ServerLevel level, GameData data) {
        PlayerState ps = GameManager.state(fake);
        check(!ps.caught(), "you wake up");
        check(GameManager.count(fake, ModItems.OFUDA.get()) == ofudaBefore - 1, "the medium burnt one お札 (" + GameManager.count(fake, ModItems.OFUDA.get()) + " left)");
        check(fake.position().distanceTo(Layout.marker("wake").center()) < 2.0, "you wake by the car");
        check(data.reishou == 1, "霊障 +1 (" + data.reishou + ")");
        Spirits.removeAll(level);
        next(8, 5);
    }

    // ------------------------------------------------------------------------------------------------ 8-9: closing time
    private static void captureFive(ServerLevel level, GameData data) {
        for (SpiritKind k : SpiritKind.FIVE) {
            Spirit s = Spirits.spawnAt(level, k, Layout.marker("table_" + (12 + k.ordinal())).center(), 0.0f);
            if (s != null) Camera.take(level, fake, "selftest_" + k.id, new Camera.Shot(GameData.RESULT_CAPTURED, s, 2, "selftest"));
        }
        check(data.capturedCount(SpiritKind.FIVE) == 5, "five spirits bound into photos");
        int photos = 0;
        for (var st : fake.getInventory().items) if (GameItem.is(st, GameItem.Kind.PHOTO)) photos++;
        check(photos == 5, "five evidence photos in the inventory (" + photos + ")");
        Spirits.paused = false;
        Spirits.ensure(level, data);
        next(9, 20 * 5 + 160);
    }

    private static void closingResult(ServerLevel level, GameData data) {
        check(data.phase == GameData.PHASE_CLOSING, "closing time after the fifth photo (phase " + data.phase + ")");
        Spirit m = Spirits.get(level, SpiritKind.MANAGER);
        check(m instanceof Manager mg && mg.phase2(), "the manager closes the store");
        if (m != null) Camera.take(level, fake, "selftest_manager", new Camera.Shot(GameData.RESULT_CAPTURED, m, 3, "selftest"));
        check(data.allCaptured(), "all six in photos");
        next(10, 20 * 6 + 10);
    }

    // ------------------------------------------------------------------------------------------------ 10-12: the medium, the exorcism, the end
    private static void submit(ServerLevel level, GameData data) {
        check(data.phase == GameData.PHASE_RETURN, "back to the medium (phase " + data.phase + ")");
        Spirits.ensure(level, data);
        Medium m = Spirits.medium(level);
        check(m != null, "the medium is there");
        if (m != null) {
            place(m.position().add(1.5, 0, 0), 90.0f);
            Story.talk(fake, m);
        }
        check(data.allSubmitted(), "she takes the six photos and reads their names");
        next(11, 20);
    }

    private static void exorcismWait(ServerLevel level, GameData data) {
        if (data.phase == GameData.PHASE_EXORCISM && stageTicks % 200 == 1) {
            Medium m = Spirits.medium(level);
            log("exorcism running (" + stageTicks + " ticks), the medium at " + (m == null ? "-" : m.blockPosition().toShortString()));
        }
        if (data.phase == GameData.PHASE_ENDING || stageTicks > 20 * 400) {
            check(data.phase == GameData.PHASE_ENDING && data.ending == GameData.ENDING_GOOD, "the exorcism ends at dawn: 送り");
            next(12, 10);
        }
    }

    private static void endingAndReset(MinecraftServer server, ServerLevel level, GameData data) {
        Story.endingDone(fake);
        check(!data.started, "the night is over");
        data.resetProgress();
        com.reisatsu.world.Fixtures.resetAll(level);
        Spirits.removeAll(level);
        check(data.captured == 0 && data.photos.isEmpty() && data.phase == GameData.PHASE_IDLE, "a reset clears the progress");
        // ---- a second night for the other ending: no お札 left, and the waitress takes you
        data.started = true;
        data.phase = GameData.PHASE_INVESTIGATE;
        data.introSeen = true;
        data.setDirty();
        GameManager.debugPeaceful = false;
        Spirits.paused = true;
        for (int i = 0; i < fake.getInventory().getContainerSize(); i++) {
            if (fake.getInventory().getItem(i).is(ModItems.OFUDA.get())) fake.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
        place(laneEye, laneYaw);
        probe = Spirits.spawnAt(level, SpiritKind.WAITRESS, fake.position().add(0, 0, 0.5), 180.0f);
        if (probe != null) GameManager.caught(fake, probe);
        check(GameManager.state(fake).caught(), "the second night: taken again, with no お札 left");
        next(13, GameManager.CAUGHT_TICKS + 330);
    }

    /** 相席: you stay at table 7; the waitress sets down a glass of water for one. */
    private static void badEnding(MinecraftServer server, ServerLevel level, GameData data) {
        check(data.phase == GameData.PHASE_ENDING && data.ending == GameData.ENDING_BAD, "no お札: the 相席 ending (phase " + data.phase + ", ending " + data.ending + ")");
        check(fake.isPassenger() && fake.position().distanceTo(Layout.marker("seat_7").center()) < 1.0, "seated at table 7 (" + fake.blockPosition().toShortString() + ")");
        boolean serving = false;
        for (Spirit sp : Spirits.all(level)) if (sp.kind() == SpiritKind.WAITRESS && sp.pose() == com.reisatsu.entity.Waitress.SERVE) serving = true;
        check(serving, "the waitress serves the table");
        Story.endingDone(fake);
        check(!data.started && !fake.isPassenger(), "the night is over, you are back");
        data.resetProgress();
        com.reisatsu.world.Fixtures.resetAll(level);
        Spirits.removeAll(level);
        finish(server);
    }

    private static void finish(MinecraftServer server) {
        stage = 99;
        if (fake != null) GameManager.removeTestPlayer(fake);
        Spirits.paused = false;
        ServerLevel level = GameManager.level(server);
        if (level != null) {
            for (Layout.Marker m : List.of(Layout.marker("spawn"), Layout.marker("table_7"), Layout.marker("manager_desk"))) {
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                    level.setChunkForced((m.pos().getX() >> 4) + dx, (m.pos().getZ() >> 4) + dz, false);
            }
        }
        log("SELFTEST RESULT: " + (failures == 0 ? "PASS" : "FAIL (" + failures + ")"));
        server.halt(false);
    }
}
