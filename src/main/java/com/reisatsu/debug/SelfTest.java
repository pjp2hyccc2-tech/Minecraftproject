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
import java.lang.reflect.Method;
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
                case 0 -> setup(server, level, data);
                case 1 -> shots(level, data);
                case 2 -> awarenessStart(level, data);
                case 3 -> awarenessResult(level, data);
                case 4 -> noiseStart(level, data);
                case 5 -> noiseResult(level, data);
                case 6 -> caughtStart(level, data);
                case 7 -> caughtResult(level, data);
                case 8 -> captureFive(level, data);
                case 9 -> closingResult(level, data);
                case 10 -> submit(level, data);
                case 11 -> exorcismWait(level, data);
                case 12 -> endingAndReset(server, level, data);
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

    // ------------------------------------------------------------------------------------------------ 0: start
    private static void setup(MinecraftServer server, ServerLevel level, GameData data) {
        log("level " + level.dimension().location() + (Greybox.fallback(server) ? " (greybox stand-in: the world's dimension is not merged yet)" : ""));
        worldCheck(server);
        Greybox.ensure(level);
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
        GameManager.debugPeaceful = false;
        kindIdx = 0;
        next(1, 10);
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
            Layout.Marker t = Layout.marker("table_12");
            Vec3 base = new Vec3(t.pos().getX() + 0.5, Layout.FEET_Y, t.pos().getZ() + 3.5);
            place(base, 0.0f); // facing south (+z)
            at = base.add(0, 0, 6);
            spiritYaw = 180.0f; // facing the camera (north)
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
    private static void awarenessStart(ServerLevel level, GameData data) {
        Layout.Marker t = Layout.marker("table_12");
        Vec3 base = new Vec3(t.pos().getX() + 0.5, Layout.FEET_Y, t.pos().getZ() + 3.5);
        place(base, 0.0f);
        probe = Spirits.spawnAt(level, SpiritKind.MANAGER, base.add(0, 0, 3.0), 180.0f);
        if (probe != null) {
            probe.debugStill = true;
            probe.setAnimPose(Manager.STAND);
            probe.faceYaw(180.0f);
        }
        next(3, 60);
    }

    private static void awarenessResult(ServerLevel level, GameData data) {
        float a = probe == null ? 0 : probe.awarenessOf(fake);
        boolean hunting = probe != null && probe.hunting();
        check(a > 0.25f || hunting, String.format(Locale.ROOT, "a spirit that sees you grows aware (%.2f, hunting=%s)", a, hunting));
        if (probe != null) probe.discard();
        next(4, 5);
    }

    private static void noiseStart(ServerLevel level, GameData data) {
        Layout.Marker t = Layout.marker("table_12");
        Vec3 base = new Vec3(t.pos().getX() + 0.5, Layout.FEET_Y, t.pos().getZ() + 3.5);
        place(base, 0.0f);
        probe = Spirits.spawnAt(level, SpiritKind.WAITRESS, base.add(0, 0, 4.0), 0.0f); // facing away
        if (probe != null) {
            probe.debugStill = true;
            probe.faceYaw(0.0f);
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
        Layout.Marker t = Layout.marker("table_12");
        place(new Vec3(t.pos().getX() + 0.5, Layout.FEET_Y, t.pos().getZ() + 3.5), 0.0f);
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
        if (data.phase == GameData.PHASE_EXORCISM && stageTicks % 200 == 1) log("exorcism running (" + stageTicks + " ticks)");
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
        finish(server);
    }

    // ------------------------------------------------------------------------------------------------ the world's own check
    private static void worldCheck(MinecraftServer server) {
        try {
            Class<?> c = Class.forName("com.reisatsu.debug.WorldCheck");
            for (Method m : c.getMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) continue;
                Class<?> pt = m.getParameterTypes()[0];
                Object arg = pt.isInstance(server) ? server : pt.isInstance(GameManager.level(server)) ? GameManager.level(server) : null;
                if (arg == null) continue;
                Object r = m.invoke(null, arg);
                log("WorldCheck." + m.getName() + " → " + r);
                if (r instanceof Boolean b) check(b, "the world's own check");
                return;
            }
            log("WorldCheck has no static (MinecraftServer|ServerLevel) method");
        } catch (ClassNotFoundException e) {
            log("no WorldCheck (the world is not merged)");
        } catch (Throwable t) {
            check(false, "WorldCheck threw " + t);
        }
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
