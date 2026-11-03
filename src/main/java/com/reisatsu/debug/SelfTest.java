package com.reisatsu.debug;

import com.mojang.authlib.GameProfile;
import com.reisatsu.Reisatsu;
import com.reisatsu.entity.Manager;
import com.reisatsu.entity.Medium;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.Camera;
import com.reisatsu.game.Director;
import com.reisatsu.game.GameData;
import com.reisatsu.game.GameManager;
import com.reisatsu.game.Nights;
import com.reisatsu.game.NoiseSystem;
import com.reisatsu.game.PlayerState;
import com.reisatsu.game.Sight;
import com.reisatsu.game.Spirits;
import com.reisatsu.game.Story;
import com.reisatsu.game.Week;
import com.reisatsu.item.GameItem;
import com.reisatsu.registry.ModItems;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
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
import java.util.function.BooleanSupplier;

/**
 * CI only ({@code -Dreisatsu.selftest=true} on a dedicated server): the world's checks, then a fake-player playthrough —
 * start, items, a valid / aware / back-turned / through-glass shot of each spirit, awareness from sight and noise,
 * caught → wake → お札; then the week (docs §1 Flow): night 1 (nothing shows; the witness rule; the director's pace until
 * five kinds are witnessed; a photo of nothing; the medium's explanation), and each night after it — the title card,
 * the film topped up, its signs, the spirit showing itself, a valid photo, handing it in, goodnight — with a save and
 * quit (back to the start of the night) and being caught mid-night on night 3, closing time on night 7, the exorcism,
 * the good ending, a reset, and the 相席 ending. Writes {@code reisatsu-selftest.txt} ("SELFTEST RESULT: PASS/FAIL")
 * and stops the server.
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
    private static int reishouBefore;
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
        if (ticks > 20 * 60 * 21) {
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
                case 35 -> breathWatch(level, data);
                case 36 -> heldResult(level, data);
                case 4 -> noiseStart(level, data);
                case 5 -> noiseResult(level, data);
                case 6 -> caughtStart(level, data);
                case 7 -> caughtResult(level, data);
                case 8 -> week(server, level, data);
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
        // the mechanics first, undisturbed; the week's director runs from stage 8
        Director.paused = true;
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
        next(35, 0);
    }

    /** Watched tick by tick: once it has heard you it turns, sees you and would take you (which would end the checks). */
    private static void breathWatch(ServerLevel level, GameData data) {
        float a = probe.awarenessOf(fake);
        if (a <= 0.05f && stageTicks <= 90) return;
        check(a > 0.05f, String.format(Locale.ROOT, "...but it hears your panicked breathing 3 blocks behind it (%.2f after %d ticks)", a, stageTicks));
        // ...unless you hold it: a fresh pair of ears, back turned
        probe.discard();
        probe = Spirits.spawnAt(level, SpiritKind.WAITRESS, along(3.0), laneYaw);
        if (probe != null) {
            probe.debugStill = true;
            probe.setAnimPose(com.reisatsu.entity.Waitress.STAND);
            probe.faceYaw(laneYaw);
        }
        PlayerState ps = GameManager.state(fake);
        ps.breathHolding = true;
        ps.breathTicks = 0;
        ps.breathGauge = 1.0f;
        next(36, 40);
    }

    private static void heldResult(ServerLevel level, GameData data) {
        float a = probe == null ? -1 : probe.awarenessOf(fake);
        check(a == 0.0f, String.format(Locale.ROOT, "holding your breath, it hears nothing (%.2f)", a));
        PlayerState ps = GameManager.state(fake);
        ps.breathHolding = false;
        ps.breathTicks = 0;
        ps.forceFear = 0.3f;
        ps.forceTicks = 40;
        if (probe != null) probe.discard();
        check(GameManager.huntable(fake), "still huntable (nothing took you)");
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
        reishouBefore = data.reishou;
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
        check(data.reishou == Math.min(3, reishouBefore + 1), "霊障 +1 (" + reishouBefore + " → " + data.reishou + ")");
        Spirits.removeAll(level);
        next(8, 5);
    }

    // ------------------------------------------------------------------------------------------------ 8: the week
    /** One step of the week's playthrough: run every tick until it returns true (or it times out). */
    private record Step(String name, int timeout, BooleanSupplier run) {}

    private static final List<Step> WEEK = new ArrayList<>();
    private static int weekIdx;
    private static int stepTicks;
    private static ServerLevel lvl;
    private static GameData dat;
    private static MinecraftServer srv;
    private static int keptPhotos, keptOfuda, keptFilm;
    private static Spirit tonight;
    private static boolean placed;

    private static void week(MinecraftServer server, ServerLevel level, GameData data) {
        lvl = level;
        dat = data;
        srv = server;
        if (WEEK.isEmpty()) buildWeek();
        if (weekIdx >= WEEK.size()) {
            finish(server);
            return;
        }
        Step st = WEEK.get(weekIdx);
        stepTicks++;
        if (stepTicks == 1) log("-- " + st.name());
        if (st.run().getAsBoolean()) {
            weekIdx++;
            stepTicks = 0;
            placed = false;
        } else if (stepTicks > st.timeout()) {
            check(false, "timed out: " + st.name() + " (night " + data.night + ", stage " + data.nightStage + ", phase " + data.phase
                    + ", signs " + data.signs + ")");
            weekIdx++;
            stepTicks = 0;
            placed = false;
        }
    }

    private static void step(String name, int timeout, BooleanSupplier run) {
        WEEK.add(new Step(name, timeout, run));
    }

    private static PlayerState ps() {
        return GameManager.state(fake);
    }

    /** Puts the fake player somewhere once per step; true from the tick after (its room has been updated). */
    private static boolean at(Vec3 feet, float yaw) {
        if (!placed) {
            place(feet, yaw);
            placed = true;
            return false;
        }
        return true;
    }

    private static Vec3 v(double x, double z) {
        return new Vec3(x, Layout.FEET_Y, z);
    }

    /** Runs the director alone for up to maxTicks (fast-forward) until the condition holds; returns the ticks used. */
    private static int fastForward(int maxTicks, BooleanSupplier until) {
        int t = 0;
        while (t < maxTicks && !until.getAsBoolean()) {
            Director.tick(lvl, dat);
            t++;
        }
        return t;
    }

    private static void skipOpening() {
        Nights.skip(fake, true);
    }

    private static int photoItems() {
        int n = 0;
        for (var st : fake.getInventory().items) if (GameItem.is(st, GameItem.Kind.PHOTO)) n++;
        return n;
    }

    /** A valid photo of s from the first of these spots (feet) that gives one; the test lane as a last resort. */
    private static boolean shoot(Spirit s, int lanePose, Vec3... spots) {
        String last = "no spot tried";
        for (Vec3 feet : spots) {
            place(feet, 0.0f);
            Camera.Shot shot = aim(lvl, s, fake.getEyePosition());
            if (shot.result() == GameData.RESULT_CAPTURED && shot.spirit() == s) {
                Camera.take(lvl, fake, "selftest_" + s.kind().id, shot);
                log(String.format(Locale.ROOT, "%s photographed from %.1f, %.1f (★%d)", s.kind().id, feet.x, feet.z, shot.stars()));
                return true;
            }
            last = shot.reason();
        }
        log(s.kind().id + ": no photo from its place (" + last + "): the test lane");
        s.moveTo(laneSpirit.x, laneSpirit.y, laneSpirit.z, laneYaw + 180.0f, 0.0f);
        s.freeze(lanePose);
        s.faceYaw(laneYaw + 180.0f);
        place(laneEye, laneYaw);
        Camera.Shot shot = aim(lvl, s, fake.getEyePosition());
        if (shot.result() == GameData.RESULT_CAPTURED) Camera.take(lvl, fake, "selftest_" + s.kind().id, shot);
        else log("lane: " + shot.reason());
        return shot.result() == GameData.RESULT_CAPTURED;
    }

    /** Camera spots in front of a spirit's face, at these distances and angle offsets. */
    private static Vec3[] inFront(Spirit s, double... dists) {
        List<Vec3> out = new ArrayList<>();
        for (double d : dists) {
            for (float off : new float[]{0, 30, -30, 55, -55}) {
                Vec3 dir = Vec3.directionFromRotation(0.0f, s.getYHeadRot() + off);
                out.add(new Vec3(s.getX() + dir.x * d, Layout.FEET_Y, s.getZ() + dir.z * d));
            }
        }
        return out.toArray(new Vec3[0]);
    }

    /** By the medium, the photo handed over (or her lines heard); true once the night's end has begun. */
    private static boolean talkToMedium() {
        Medium m = Spirits.medium(lvl);
        if (m == null) {
            Spirits.ensure(lvl, dat);
            return false;
        }
        place(m.position().add(1.5, 0, 0), 90.0f);
        Story.skipDialogue(fake);
        Story.talk(fake, m);
        return true;
    }

    /** The night's end (her lines skipped), black, then the next night's card. */
    private static boolean nextNight(int n) {
        if (stepTicks == 1) Nights.skip(fake, true);
        if (dat.night != n || dat.nightStage != GameData.NS_OPENING) return false;
        PlayerState ps = ps();
        check(ps.locked && fake.position().distanceTo(Layout.marker("spawn").center()) < 1.5, "night " + n + " begins by the car (held for the card)");
        check(GameItem.film(GameManager.camera(fake)) >= GameItem.START_FILM, "the film is topped up (" + GameItem.film(GameManager.camera(fake)) + ")");
        check(photosKept(), "the photos and the お札 are kept (" + dat.photos.size() + " photos, " + GameManager.count(fake, ModItems.OFUDA.get()) + " お札)");
        int spirits = 0;
        for (Spirit sp : Spirits.all(lvl)) if (!sp.extra) spirits++;
        check(spirits == 0, "nothing is in the restaurant yet (" + spirits + ")");
        return true;
    }

    private static void remember() {
        keptPhotos = dat.photos.size();
        keptOfuda = GameManager.count(fake, ModItems.OFUDA.get());
    }

    private static boolean photosKept() {
        return dat.photos.size() == keptPhotos && GameManager.count(fake, ModItems.OFUDA.get()) == keptOfuda;
    }

    /** Inside at this spot and the night's clock started (its first step inside), then set back by `back` seconds. */
    private static boolean enter(Vec3 feet, float yaw, int back) {
        if (!at(feet, yaw)) return false;
        if (dat.enteredAt < 0) return false;
        if (back > 0) dat.enteredAt = dat.playTicks - 20L * back;
        return true;
    }

    /** Fast-forwards the night's signs until two (and the extra condition) were witnessed. */
    private static boolean signs(SpiritKind k, BooleanSupplier extra) {
        int t = fastForward(20 * 60 * 5, () -> Nights.signsOf(dat, k) >= Week.MANIFEST_SIGNS && extra.getAsBoolean());
        boolean ok = Nights.signsOf(dat, k) >= Week.MANIFEST_SIGNS && extra.getAsBoolean();
        check(ok, String.format(Locale.ROOT, "%s: two of its signs witnessed (%s after %d s of signs; staged %s)", k.id, dat.signs, t / 20, Director.LOG));
        return true;
    }

    /** Waits for the night's spirit to show itself. */
    private static boolean shows(SpiritKind k) {
        Spirit s = Spirits.get(lvl, k);
        if (dat.nightStage != GameData.NS_HUNT || s == null) return false;
        tonight = s;
        check(true, k.id + " shows itself (after " + (dat.playTicks - dat.enteredAt) / 20 + " s inside, signs " + dat.signs + ")");
        return true;
    }

    private static void buildWeek() {
        // ---------------------------------------------------------------- night 1: watching
        step("night 1 begins", 5, () -> {
            Director.paused = false;
            Spirits.paused = false;
            GameManager.debugPeaceful = false;
            GameItem.setFilm(GameManager.camera(fake), 3);
            remember();
            Nights.start(lvl, dat, 1, false);
            PlayerState ps = ps();
            check(dat.night == 1 && dat.nightStage == GameData.NS_OPENING, "night 1: the title card, by the car (stage " + dat.nightStage + ")");
            check(ps.locked && fake.position().distanceTo(Layout.marker("spawn").center()) < 1.5, "held still at the spawn while the medium speaks");
            check(GameItem.film(GameManager.camera(fake)) == GameItem.START_FILM, "she tops the film up to " + GameItem.START_FILM);
            return true;
        });
        step("night 1: her opening lines", 20 * 40, () -> {
            if (dat.nightStage != GameData.NS_WORK) return false;
            check(!ps().locked, "after her lines (" + stepTicks / 20 + " s) the night is yours");
            return true;
        });
        step("night 1: nothing shows itself", 30, () -> {
            if (stepTicks < 25) return false;
            Spirits.ensure(lvl, dat);
            int n = lvl.getEntitiesOfClass(Spirit.class, Spirits.AREA, net.minecraft.world.entity.Entity::isAlive).size();
            check(n == 0, "night 1: no spirit in the restaurant (" + n + ")");
            return true;
        });
        step("the witness rule", 5, () -> {
            // the same room counts without a line of sight; another room needs one; 18 blocks at most
            place(v(10.5, 19.5), 0.0f);
            check(Director.canWitness(fake, new Vec3(8.5, Layout.FEET_Y + 1.0, 21.5)), "witness rule: the same room (the occupied stall from the sinks)");
            check(!Director.canWitness(fake, new Vec3(3.5, Layout.FEET_Y + 1.0, 19.5)), "witness rule: not through a wall from another room (the men's room)");
            place(v(15.5, 13.5), 0.0f);
            check(!Director.canWitness(fake, new Vec3(34.5, Layout.FEET_Y + 1.6, 13.5)), "witness rule: not beyond 18 blocks, even in the same room");
            place(v(12.5, 24.5), 0.0f);
            Vec3 eye = fake.getEyePosition();
            Vec3 seen = null;
            for (double[] c : new double[][]{{20.5, 24.5}, {18.5, 25.5}, {20.5, 26.5}, {17.5, 23.5}, {22.5, 22.5}}) {
                Vec3 pt = new Vec3(c[0], Layout.FEET_Y + 1.6, c[1]);
                if (!Director.roomOf(pt).equals(Director.roomOf(fake.position())) && Sight.clear(lvl, eye, pt)) {
                    seen = pt;
                    break;
                }
            }
            check(seen != null && Director.canWitness(fake, seen), "witness rule: another room in plain sight counts (" + seen + ")");
            return true;
        });
        step("night 1: near and far", 40, () -> {
            if (!at(v(10.5, 19.5), 0.0f)) return false;
            if (stepTicks < 4) return false;
            Director.run(lvl, fake, "plate");
            check(!dat.signs.contains("plate") && !dat.witnessed.contains("plate"), "a plate smashing in the far kitchen is not witnessed from the women's restroom");
            Director.run(lvl, fake, "dryer");
            check(dat.signs.contains("dryer") && dat.witnessed.contains("dryer"), "the hand dryer roaring in the same restroom is witnessed (report ticked)");
            return true;
        });
        step("night 1: the director's pace", 40, () -> {
            if (!at(v(24.5, 20.5), 0.0f)) return false;
            if (stepTicks < 4) return false;
            Director.LOG.clear();
            long from = Director.clock();
            int t = fastForward(20 * 60 * 6, () -> dat.nightStage == GameData.NS_RETURN);
            long prev = -1, minGap = Long.MAX_VALUE, maxGap = 0;
            for (String e : Director.LOG) {
                long c = Long.parseLong(e.substring(0, e.indexOf(' ')));
                if (c < from) continue;
                if (prev >= 0) {
                    minGap = Math.min(minGap, c - prev);
                    maxGap = Math.max(maxGap, c - prev);
                }
                prev = c;
            }
            log("night 1 staged: " + Director.LOG);
            check(dat.nightStage == GameData.NS_RETURN && dat.signs.size() >= Week.OBSERVE_GOAL,
                    String.format(Locale.ROOT, "five kinds witnessed in the dining room after %d s (%s)", t / 20, dat.signs));
            check(minGap >= 20 * 25 && maxGap <= 20 * 46, String.format(Locale.ROOT, "one phenomenon every 25-45 s (gaps %.0f..%.0f s)", minGap / 20.0, maxGap / 20.0));
            check(dat.witnessed.containsAll(dat.signs), "the report's list holds what was witnessed (" + dat.witnessed + ")");
            return true;
        });
        step("night 1: a photo of nothing", 5, () -> {
            for (int i = 0; i < 9; i++) {
                GameItem.Kind k = GameItem.kindOf(fake.getInventory().getItem(i));
                if (k != null && k.camera()) fake.getInventory().selected = i;
            }
            GameManager.swap(fake, net.minecraft.world.InteractionHand.MAIN_HAND, ModItems.REISHA_CAMERA_FINDER.get());
            int before = GameItem.film(fake.getMainHandItem());
            int shots = dat.photos.size();
            GameManager.state(fake).shutterCooldown = 0;
            Camera.shutter(fake, new com.reisatsu.network.C2S.Shutter("selftest_nothing", 70.0f, 0.0f, fake.getYRot(), fake.getXRot()));
            int after = GameItem.film(fake.getMainHandItem());
            boolean empty = dat.photos.size() == shots + 1 && dat.photos.get(dat.photos.size() - 1).result() == GameData.RESULT_EMPTY;
            check(after == before - 1 && empty, "a photo of the 霊障 uses film and captures nothing (" + before + " → " + after + ")");
            GameManager.lowerCamera(fake);
            return true;
        });
        step("night 1: back to the medium", 40, () -> {
            remember();
            if (!talkToMedium()) return false;
            check(dat.nightStage == GameData.NS_ENDING && ps().locked, "she explains: six of them, one a night (the night ends)");
            return true;
        });
        // ---------------------------------------------------------------- night 2: the visitor
        step("night 2 begins", 20 * 30, () -> nextNight(2));
        step("night 2: before two o'clock", 20 * 10, () -> {
            if (stepTicks == 1) skipOpening();
            GameManager.debugPeaceful = true;
            if (!enter(v(1.5, 28.5), 180.0f, 0)) return false;
            Spirits.ensure(lvl, dat);
            check(Spirits.get(lvl, SpiritKind.VISITOR) == null, "night 2: nobody comes before two o'clock");
            // two o'clock now (the cue, then him 13 s later)
            Nights.manifest(lvl, dat);
            return true;
        });
        step("night 2: the crash, then the door", 20 * 25, () -> {
            if (!(Spirits.get(lvl, SpiritKind.VISITOR) instanceof com.reisatsu.entity.Visitor v) || dat.nightStage != GameData.NS_HUNT) return false;
            check(dat.signs.contains("crash"), "the 2:00 cue is heard (" + dat.signs + ")");
            v.walkFrom(1);
            tonight = v;
            return true;
        });
        step("night 2: he comes in", 20 * 20, () -> {
            if (!(dat.signs.contains("door") && dat.signs.contains("prints"))) return false;
            check(true, "the doors open for nobody, wet footprints come in (" + dat.signs + ")");
            if (tonight instanceof com.reisatsu.entity.Visitor v) v.seatNow();
            return true;
        });
        step("night 2: table 7", 20 * 12, () -> {
            if (!at(v(31.5, 26.5), 90.0f)) return false;
            if (!dat.signs.contains("call")) return false;
            check(true, "he presses table 7's call button");
            return true;
        });
        step("night 2: his photo", 5, () -> {
            boolean ok = shoot(tonight, 1, v(36.5, 27.5), v(36.5, 26.5), v(35.5, 26.5), v(34.5, 26.5), v(37.5, 28.5), v(36.5, 28.5));
            check(ok && dat.isCaptured(SpiritKind.VISITOR) && dat.nightStage == GameData.NS_RETURN && photoItems() == 1,
                    "night 2: the visitor's photo (back to the medium, stage " + dat.nightStage + ")");
            return true;
        });
        step("night 2: to the medium", 40, () -> {
            remember();
            if (!talkToMedium()) return false;
            check(dat.isSubmitted(SpiritKind.VISITOR) && dat.nightStage == GameData.NS_ENDING && photoItems() == 0, "she reads 三浦 健司 and says goodnight");
            return true;
        });
        // ---------------------------------------------------------------- night 3: the waitress (+ save and quit, being caught)
        step("night 3 begins", 20 * 30, () -> nextNight(3));
        step("night 3: inside", 20 * 10, () -> {
            if (stepTicks == 1) skipOpening();
            GameManager.debugPeaceful = false;
            return enter(v(20.5, 20.5), 0.0f, 100);
        });
        step("night 3: her signs", 5, () -> signs(SpiritKind.WAITRESS, () -> true));
        step("night 3: she shows herself", 40, () -> shows(SpiritKind.WAITRESS));
        step("night 3: save and quit", 20 * 5, () -> {
            remember();
            Nights.forgetSession();
            check(Nights.needsResume(dat), "a world loaded mid-night resumes it");
            Nights.start(lvl, dat, dat.night, true);
            check(dat.night == 3 && dat.nightStage == GameData.NS_OPENING && dat.signs.isEmpty() && dat.enteredAt < 0,
                    "...at the start of night 3 (stage " + dat.nightStage + ", signs " + dat.signs + ")");
            check(Spirits.get(lvl, SpiritKind.WAITRESS) == null && photosKept(), "the waitress is dormant again, photos and お札 kept");
            return true;
        });
        step("night 3 again: inside", 20 * 10, () -> {
            if (stepTicks == 1) skipOpening();
            return enter(v(20.5, 20.5), 0.0f, 100);
        });
        step("night 3 again: her signs", 5, () -> signs(SpiritKind.WAITRESS, () -> true));
        step("night 3 again: she shows herself", 40, () -> shows(SpiritKind.WAITRESS));
        step("night 3: caught", 20 * 8, () -> {
            if (stepTicks == 1) {
                remember();
                place(tonight.position().add(0.6, 0, 0), 90.0f);
                GameManager.caught(fake, tonight);
                check(ps().caught(), "night 3: the waitress takes you");
                return false;
            }
            if (ps().caught()) return false;
            int left = GameManager.count(fake, ModItems.OFUDA.get());
            check(left == keptOfuda - 1 && fake.position().distanceTo(Layout.marker("wake").center()) < 2.0,
                    "you wake by the car, an お札 burnt (" + left + " left)");
            check(dat.night == 3 && dat.nightStage == GameData.NS_HUNT && Spirits.get(lvl, SpiritKind.WAITRESS) != null
                    && !Spirits.get(lvl, SpiritKind.WAITRESS).hunting(), "the night goes on, the waitress back at her work");
            remember();
            return true;
        });
        step("night 3: her photo (the bow)", 20 * 15, () -> {
            GameManager.debugPeaceful = true;
            Spirit w = Spirits.get(lvl, SpiritKind.WAITRESS);
            // after taking you she stays wary a while (photos of her fail meanwhile, §3.3)
            if (w == null || w.waryTicks > 0) return false;
            Layout.Marker b = Layout.marker("waitress_bow");
            w.moveTo(b.center().x, b.center().y, b.center().z, b.yaw(), 0.0f);
            w.freeze(com.reisatsu.entity.Waitress.BOW);
            w.faceYaw(b.yaw());
            boolean ok = shoot(w, 2, inFront(w, 3.0, 4.0));
            check(ok && dat.isCaptured(SpiritKind.WAITRESS) && dat.nightStage == GameData.NS_RETURN, "night 3: the waitress's photo, bowing");
            return true;
        });
        step("night 3: to the medium", 40, () -> {
            if (!talkToMedium()) return false;
            check(dat.isSubmitted(SpiritKind.WAITRESS) && dat.nightStage == GameData.NS_ENDING, "she reads 小野寺 千夏 and says goodnight");
            return true;
        });
        // ---------------------------------------------------------------- night 4: the child
        step("night 4 begins", 20 * 30, () -> nextNight(4));
        step("night 4: inside", 20 * 10, () -> {
            if (stepTicks == 1) skipOpening();
            GameManager.debugPeaceful = false;
            return enter(v(11.5, 24.5), 0.0f, 100);
        });
        step("night 4: her signs", 5, () -> signs(SpiritKind.CHILD, () -> true));
        step("night 4: もういいかい", 40, () -> {
            if (!shows(SpiritKind.CHILD)) return false;
            GameManager.debugPeaceful = true;
            return true;
        });
        step("night 4: she hides", 20 * 40, () -> {
            Spirit c = Spirits.get(lvl, SpiritKind.CHILD);
            if (c == null || c.pose() != com.reisatsu.entity.Child.HIDE) return false;
            tonight = c;
            check(true, "she runs off and hides (" + c.blockPosition().toShortString() + ")");
            return true;
        });
        step("night 4: her photo (hiding)", 5, () -> {
            tonight.freeze(com.reisatsu.entity.Child.HIDE);
            boolean ok = shoot(tonight, 2, inFront(tonight, 2.5, 3.5, 1.8));
            check(ok && dat.isCaptured(SpiritKind.CHILD) && dat.nightStage == GameData.NS_RETURN, "night 4: the child's photo, hands over her eyes");
            return true;
        });
        step("night 4: to the medium", 40, () -> {
            if (!talkToMedium()) return false;
            check(dat.isSubmitted(SpiritKind.CHILD) && dat.nightStage == GameData.NS_ENDING, "she reads 春日 ゆうか and says goodnight");
            return true;
        });
        // ---------------------------------------------------------------- night 5: the woman in the mirror
        step("night 5 begins", 20 * 30, () -> nextNight(5));
        step("night 5: the women's restroom", 20 * 10, () -> {
            if (stepTicks == 1) skipOpening();
            GameManager.debugPeaceful = false;
            return enter(v(10.5, 19.5), 0.0f, 100);
        });
        step("night 5: her signs", 5, () -> signs(SpiritKind.MIRROR_WOMAN, () -> dat.signs.contains("taps")));
        step("night 5: behind the mirror", 40, () -> {
            if (!shows(SpiritKind.MIRROR_WOMAN)) return false;
            GameManager.debugPeaceful = true;
            return true;
        });
        step("night 5: her photo (through the mirror)", 20 * 5, () -> {
            Spirit m = Spirits.get(lvl, SpiritKind.MIRROR_WOMAN);
            if (m == null || m.pose() != com.reisatsu.entity.MirrorWoman.BOWED) return false;
            Vec3 standAt = Layout.marker("sink_w").center().add(0, 0, 1.5);
            boolean ok = shoot(m, 0, standAt, standAt.add(1, 0, 0), standAt.add(-1, 0, 0));
            check(ok && dat.isCaptured(SpiritKind.MIRROR_WOMAN) && dat.nightStage == GameData.NS_RETURN, "night 5: her photo through the mirror, bowed");
            return true;
        });
        step("night 5: to the medium", 40, () -> {
            if (!talkToMedium()) return false;
            check(dat.isSubmitted(SpiritKind.MIRROR_WOMAN) && dat.nightStage == GameData.NS_ENDING, "she reads 白石 玲子 and says goodnight");
            return true;
        });
        // ---------------------------------------------------------------- night 6: the chef
        step("night 6 begins", 20 * 30, () -> nextNight(6));
        step("night 6: the kitchen", 20 * 10, () -> {
            if (stepTicks == 1) skipOpening();
            GameManager.debugPeaceful = false;
            return enter(v(36.5, 6.5), 90.0f, 100);
        });
        step("night 6: chopping that stops", 20 * 3, () -> {
            if (stepTicks == 1) {
                Director.run(lvl, fake, "chop");
                check(Director.chopping(), "chopping at the board, far off");
                return false;
            }
            if (stepTicks == 30) place(v(27.5, 7.5), 90.0f);
            if (stepTicks < 34) return false;
            check(!Director.chopping() && dat.signs.contains("chop"), "...it stops as you come near (" + dat.signs + ")");
            place(v(36.5, 6.5), 90.0f);
            return true;
        });
        step("night 6: his signs", 5, () -> signs(SpiritKind.CHEF, () -> true));
        step("night 6: at his board", 40, () -> {
            if (!shows(SpiritKind.CHEF)) return false;
            GameManager.debugPeaceful = true;
            return true;
        });
        step("night 6: his photo (through the pass window)", 5, () -> {
            Layout.Marker pm = Layout.marker("chef_pass");
            tonight.moveTo(pm.center().x, pm.center().y, pm.center().z, pm.yaw(), 0.0f);
            tonight.freeze(com.reisatsu.entity.Chef.PLATE);
            tonight.faceYaw(pm.yaw());
            boolean ok = shoot(tonight, 2, v(24.5, 12.5), v(24.5, 11.5), v(23.5, 12.5), v(25.5, 12.5), v(24.5, 13.5));
            check(ok && dat.isCaptured(SpiritKind.CHEF) && dat.nightStage == GameData.NS_RETURN, "night 6: the chef's photo, plating");
            return true;
        });
        step("night 6: to the medium", 40, () -> {
            if (!talkToMedium()) return false;
            check(dat.isSubmitted(SpiritKind.CHEF) && dat.nightStage == GameData.NS_ENDING, "she reads 権藤 剛 and says goodnight");
            return true;
        });
        // ---------------------------------------------------------------- night 7: closing time, the manager, the exorcism
        step("night 7 begins", 20 * 30, () -> nextNight(7));
        step("night 7: closing time", 20 * 20, () -> {
            if (stepTicks == 1) skipOpening();
            GameManager.debugPeaceful = true;
            if (!at(v(20.5, 20.5), 0.0f)) return false;
            if (dat.phase != GameData.PHASE_CLOSING) return false;
            check(true, "night 7 begins as closing time (" + (dat.playTicks - dat.enteredAt) / 20 + " s after coming in)");
            return true;
        });
        step("night 7: the store closes", 20 * 12, () -> {
            if (Story.closingRunning()) return false;
            check(Fixtures.circuitOn(lvl, com.reisatsu.world.Circuit.DINING) && Fixtures.deviceOn(lvl, "store_music"),
                    "every light on, the store music playing");
            BlockState disp = lvl.getBlockState(Layout.marker("call_display").pos());
            check(disp.hasProperty(com.reisatsu.block.Props.CLOSED) && disp.getValue(com.reisatsu.block.Props.CLOSED), "the call display reads 「閉店」");
            check(dat.signs.contains("pa") && dat.signs.contains("lights") && dat.signs.contains("music"), "its signs witnessed (" + dat.signs + ")");
            dat.enteredAt = dat.playTicks - 20L * 100;
            return true;
        });
        step("night 7: the manager wakes", 40, () -> {
            if (!shows(SpiritKind.MANAGER)) return false;
            check(tonight instanceof Manager m && m.phase2(), "he closes the store (patrolling)");
            return true;
        });
        step("night 7: his photo", 5, () -> {
            boolean ok = shoot(tonight, 1, inFront(tonight, 3.0, 4.0));
            check(ok && dat.isCaptured(SpiritKind.MANAGER) && dat.allCaptured(), "night 7: the manager's photo — all six");
            return true;
        });
        step("night 7: it goes quiet", 20 * 10, () -> {
            if (dat.phase != GameData.PHASE_RETURN) return false;
            check(!Fixtures.deviceOn(lvl, "store_music"), "...the music stops: back to the medium");
            return true;
        });
        step("night 7: to the medium", 40, () -> {
            if (!talkToMedium()) return false;
            check(dat.allSubmitted(), "she takes the last photo: all six names");
            return true;
        });
        step("the exorcism", 20 * 300, () -> {
            if (dat.phase == GameData.PHASE_EXORCISM && stepTicks % 400 == 1) {
                Medium m = Spirits.medium(lvl);
                log("exorcism running (" + stepTicks + " ticks), the medium at " + (m == null ? "-" : m.blockPosition().toShortString()));
            }
            if (dat.phase != GameData.PHASE_ENDING) return false;
            check(dat.ending == GameData.ENDING_GOOD, "the exorcism ends at dawn: 送り (お札 used: " + dat.ofudaUsed + ")");
            return true;
        });
        step("the week is over", 5, () -> {
            Story.endingDone(fake);
            check(!dat.started, "the week is over");
            dat.resetProgress();
            Fixtures.resetAll(lvl);
            Spirits.removeAll(lvl);
            Nights.forgetSession();
            check(dat.captured == 0 && dat.photos.isEmpty() && dat.phase == GameData.PHASE_IDLE && dat.night == 1, "a reset clears the week");
            return true;
        });
        // ---------------------------------------------------------------- the other ending
        step("相席: caught with no お札", 5, () -> {
            dat.started = true;
            dat.phase = GameData.PHASE_INVESTIGATE;
            dat.night = 3;
            dat.nightStage = GameData.NS_HUNT;
            dat.introSeen = true;
            dat.setDirty();
            GameManager.debugPeaceful = false;
            Spirits.paused = true;
            for (int i = 0; i < fake.getInventory().getContainerSize(); i++) {
                if (fake.getInventory().getItem(i).is(ModItems.OFUDA.get())) fake.getInventory().setItem(i, ItemStack.EMPTY);
            }
            place(laneEye, laneYaw);
            probe = Spirits.spawnAt(lvl, SpiritKind.WAITRESS, fake.position().add(0, 0, 0.5), 180.0f);
            if (probe != null) GameManager.caught(fake, probe);
            check(GameManager.state(fake).caught(), "another week: taken, with no お札 left");
            return true;
        });
        step("相席", GameManager.CAUGHT_TICKS + 400, () -> {
            if (stepTicks < GameManager.CAUGHT_TICKS + 330) return false;
            check(dat.phase == GameData.PHASE_ENDING && dat.ending == GameData.ENDING_BAD, "no お札: the 相席 ending (phase " + dat.phase + ", ending " + dat.ending + ")");
            check(fake.isPassenger() && fake.position().distanceTo(Layout.marker("seat_7").center()) < 1.0, "seated at table 7 (" + fake.blockPosition().toShortString() + ")");
            boolean serving = false;
            for (Spirit sp : Spirits.all(lvl)) if (sp.kind() == SpiritKind.WAITRESS && sp.pose() == com.reisatsu.entity.Waitress.SERVE) serving = true;
            check(serving, "the waitress serves the table");
            Story.endingDone(fake);
            check(!dat.started && !fake.isPassenger(), "the night is over, you are back");
            dat.resetProgress();
            Fixtures.resetAll(lvl);
            Spirits.removeAll(lvl);
            return true;
        });
    }

    private static void finish(MinecraftServer server) {
        stage = 99;
        if (fake != null) GameManager.removeTestPlayer(fake);
        Spirits.paused = false;
        Director.paused = false;
        GameManager.debugPeaceful = false;
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
