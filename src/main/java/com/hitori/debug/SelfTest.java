package com.hitori.debug;

import com.mojang.authlib.GameProfile;
import com.hitori.Hitori;
import com.hitori.entity.FixedCamera;
import com.hitori.entity.OniDoll;
import com.hitori.entity.Woman;
import com.hitori.game.Apt;
import com.hitori.game.Cameras;
import com.hitori.game.Clock;
import com.hitori.game.Director;
import com.hitori.game.Doll;
import com.hitori.game.Endings;
import com.hitori.game.GameData;
import com.hitori.game.GameFixtureListener;
import com.hitori.game.GameManager;
import com.hitori.game.Hiding;
import com.hitori.game.NoiseSystem;
import com.hitori.game.PlayerState;
import com.hitori.game.Recording;
import com.hitori.game.Ritual;
import com.hitori.game.Step;
import com.hitori.game.Watch;
import com.hitori.item.GameItem;
import com.hitori.registry.ModItems;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
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
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * CI only ({@code -Dhitori.selftest=true} on a dedicated server): the world's own checks ({@code debug/WorldCheck}, by
 * reflection, when it exists), then a fake-player playthrough of the night — every preparation step through the
 * apartment's interaction keys and the items, three o'clock, every step of the ritual, the closet, the doll waking and
 * leaving the tub, its suspicion and the salt water's protection, phase 3 (finding it, spitting, 「私の勝ち」, the
 * fire) and the good ending; then the 鬼, 外へ and 未完 endings, the woman coming closer feed by feed, every director
 * event, save-data round trip and a reset. Writes {@code hitori-selftest.txt} ("SELFTEST RESULT: PASS/FAIL").
 */
public final class SelfTest {
    private static final boolean ENABLED = Boolean.getBoolean("hitori.selftest");
    private static final List<String> LINES = new ArrayList<>();
    private static int failures;
    private static int stage = -1;
    private static int wait;
    private static long ticks;
    private static FakePlayer fake;
    private static ServerLevel level;
    private static final List<T> STEPS = new ArrayList<>();
    private static int index;
    private static int untilTicks;
    private static int worldStage;
    private static boolean worldDone;

    /** A test step: run once, then wait; or wait until a condition holds (then check it). */
    private record T(String name, Runnable run, int pause, BooleanSupplier until, int timeout, String what) {}

    private SelfTest() {}

    public static void onStarted(MinecraftServer server) {
        if (!ENABLED) return;
        stage = 0;
        wait = 60;
        log("selftest starting");
        dumpLayout(Path.of("hitori-layout.txt"));
    }

    public static void check(boolean ok, String what) {
        if (!ok) failures++;
        log((ok ? "PASS " : "FAIL ") + what);
    }

    public static void log(String s) {
        LINES.add(s);
        Hitori.LOGGER.info("[hitori-selftest] {}", s);
        try {
            Files.writeString(Path.of("hitori-selftest.txt"), String.join("\n", LINES) + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static void tick(MinecraftServer server) {
        if (!ENABLED || stage < 0 || stage == 99) return;
        ticks++;
        if (ticks > 20 * 60 * 22) {
            check(false, "global timeout at " + (index < STEPS.size() ? STEPS.get(index).name() : "?"));
            finish(server);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            if (stage == 0) {
                level = GameManager.level(server);
                if (level == null) {
                    check(false, "the apartment level exists");
                    finish(server);
                    return;
                }
                log("level " + level.dimension().location() + (Greybox.fallback(server) ? " (greybox stand-in: the world's dimension is not merged yet)" : ""));
                stage = 1;
                worldStage = 0;
                return;
            }
            if (stage == 1) {
                if (!worldDone && worldCheck(level)) return;
                stage = 2;
                setup(server);
                build();
                index = 0;
                return;
            }
            if (index >= STEPS.size()) {
                finish(server);
                return;
            }
            T t = STEPS.get(index);
            if (t.until() != null) {
                if (t.until().getAsBoolean()) {
                    check(true, t.what() + " (" + untilTicks + " ticks)");
                    untilTicks = 0;
                    index++;
                    wait = t.pause();
                } else if (++untilTicks > t.timeout()) {
                    check(false, t.what() + " (timed out after " + t.timeout() + " ticks)");
                    untilTicks = 0;
                    index++;
                }
                return;
            }
            index++;
            t.run().run();
            wait = t.pause();
        } catch (Throwable e) {
            check(false, "exception at " + (index > 0 && index <= STEPS.size() ? STEPS.get(index - 1).name() : "stage " + stage) + ": " + e);
            Hitori.LOGGER.error("[hitori-selftest] exception", e);
            index++;
        }
    }

    // ------------------------------------------------------------------------------------------------ the world's checks
    /** com.hitori.debug.WorldCheck.run(level, stage, log, check) when the world has written it; true while it runs. */
    private static boolean worldCheck(ServerLevel level) {
        try {
            Class<?> c = Class.forName("com.hitori.debug.WorldCheck");
            Method run = c.getMethod("run", ServerLevel.class, int.class, Consumer.class, BiConsumer.class);
            if (worldStage == 0) log("---- the world's checks (WorldCheck)");
            Consumer<String> lg = SelfTest::log;
            BiConsumer<Boolean, String> ck = SelfTest::check;
            int w = (Integer) run.invoke(null, level, worldStage++, lg, ck);
            if (w < 0) {
                worldDone = true;
                log("---- the game");
                return false;
            }
            wait = w;
            return true;
        } catch (ClassNotFoundException e) {
            worldDone = true;
            log("(no WorldCheck yet: the world's checks are skipped)");
            return false;
        } catch (Throwable e) {
            worldDone = true;
            check(false, "WorldCheck ran: " + e);
            return false;
        }
    }

    private static void dumpLayout(Path file) {
        List<String> lines = new ArrayList<>();
        for (Layout.Room r : Layout.rooms()) lines.add("room " + r.key() + " " + r.nameJa() + " " + r.box() + " " + r.acoustics());
        for (var e : Layout.all().entrySet()) {
            StringBuilder sb = new StringBuilder(e.getKey()).append(":");
            for (Layout.Marker m : e.getValue()) sb.append(" ").append(m.pos().toShortString()).append("/").append(m.facing().getName());
            lines.add(sb.toString());
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers
    private static void setup(MinecraftServer server) {
        Apt.ensureBuilt(level);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) level.setChunkForced(1 + dx, dz, true);
        fake = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c0a0a00-0000-4000-8000-0000000000a1"), "selftest"));
        fake.setGameMode(GameType.ADVENTURE);
        GameManager.addTestPlayer(fake);
        Director.paused = true;
        GameManager.debugPeaceful = false;
    }

    private static void add(String name, Runnable run, int waitAfter) {
        STEPS.add(new T(name, run, waitAfter, null, 0, ""));
    }

    private static void until(String what, BooleanSupplier cond, int timeout) {
        STEPS.add(new T("until " + what, null, 2, cond, timeout, what));
    }

    private static GameData data() {
        return GameData.get(level.getServer());
    }

    private static PlayerState ps() {
        return GameManager.state(fake);
    }

    private static BlockPos m(String name) {
        return Layout.marker(name).pos();
    }

    private static void place(Vec3 at, float yaw, float pitch) {
        fake.moveTo(at.x, at.y, at.z, yaw, pitch);
        fake.setYHeadRot(yaw);
        PlayerState ps = ps();
        ps.lastX = at.x;
        ps.lastZ = at.z;
        ps.lockPos = null;
    }

    private static void hold(ItemStack s) {
        fake.setItemInHand(InteractionHand.MAIN_HAND, s);
    }

    private static void holdFirst(net.minecraft.world.item.Item item) {
        ItemStack s = GameManager.find(fake, item);
        if (s.isEmpty()) return;
        ItemStack copy = s.copy();
        s.setCount(0);
        ItemStack old = fake.getMainHandItem().copy();
        fake.setItemInHand(InteractionHand.MAIN_HAND, copy);
        if (!old.isEmpty()) fake.getInventory().add(old);
    }

    private static void interact(String key, String marker) {
        GameFixtureListener.INSTANCE.interact(fake, key, Layout.has(marker) ? m(marker) : fake.blockPosition());
    }

    private static boolean has(net.minecraft.world.item.Item item) {
        return GameManager.has(fake, item);
    }

    private static void faceDoll() {
        OniDoll d = Doll.get(level);
        if (d == null) return;
        Vec3 to = d.position().add(0, 0.3, 0).subtract(fake.getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        fake.setYRot(yaw);
        fake.setYHeadRot(yaw);
        fake.setXRot(pitch);
    }

    /** A free spot next to the doll with a clear view of it (avoiding walls). */
    private static Vec3 besideDoll(double dist) {
        OniDoll d = Doll.get(level);
        if (d == null) return fake.position();
        double[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0.7, 0.7}, {-0.7, 0.7}, {0.7, -0.7}, {-0.7, -0.7}};
        for (double k : new double[]{dist, dist * 0.7, dist * 1.3}) {
            for (double[] dv : dirs) {
                Vec3 at = new Vec3(d.getX() + dv[0] * k, d.getY(), d.getZ() + dv[1] * k);
                BlockPos feet = BlockPos.containing(at);
                if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
                if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
                if (com.hitori.game.Sight.clear(level, at.add(0, 1.62, 0), d.position().add(0, 0.3, 0))) return at;
            }
        }
        return new Vec3(d.getX() + dist, d.getY(), d.getZ());
    }

    private static void restart() {
        GameData d = data();
        d.resetProgress();
        Endings.reset();
        Director.reset();
        Director.paused = true;
        Doll.removeAll(level);
        Cameras.removeAll(level);
        PlayerState ps = ps();
        ps.caughtTicks = -1;
        ps.endingTicks = -1;
        ps.sotoTicks = -1;
        ps.locked = false;
        ps.lockPos = null;
        ps.mouth = 0;
        Hiding.exitQuietly(fake, ps);
        GameManager.startGame(fake);
        Layout.Marker s = Layout.marker("spawn");
        place(s.center(), s.yaw(), 0.0f);
    }

    /** Everything up to (not including) a step done; the night set up as it would be. */
    private static void jumpTo(Step target) {
        GameData d = data();
        for (Step s : Step.ALL) {
            if (s.ordinal() >= target.ordinal()) break;
            d.setDone(s, true);
        }
        if (target.ordinal() >= Step.NAME.ordinal()) {
            d.phase = target.ordinal() > Step.NEXT_ONI.ordinal() ? (target.phase == GameData.PHASE_FINISH ? GameData.PHASE_FINISH : GameData.PHASE_HIDING)
                    : GameData.PHASE_RITUAL;
            d.clock = Math.max(d.clock, Clock.THREE + 120);
            Apt.setBathWater(level, 2);
            Apt.setTv(level, true);
        }
        if (target.ordinal() > Step.TUB.ordinal() && target.phase != GameData.PHASE_PREP) {
            OniDoll doll = Doll.spawnInTub(level, d);
            if (doll != null && target.ordinal() > Step.NEXT_ONI.ordinal()) {
                doll.wake();
                d.hideStartTick = d.playTicks;
            }
        }
        d.setDirty();
    }

    private static void placeCameras() {
        GameData d = data();
        int n = 0;
        for (Layout.Marker mk : Layout.markers("cam_suggest")) {
            if (n >= Cameras.MAX) break;
            BlockPos below = mk.pos().below();
            Vec3 hit = new Vec3(mk.pos().getX() + 0.5, mk.pos().getY(), mk.pos().getZ() + 0.5);
            fake.setYRot(mk.yaw());
            fake.setXRot(15.0f);
            ItemStack cam = new ItemStack(ModItems.FIXED_CAMERA.get());
            hold(cam);
            UseOnContext ctx = new UseOnContext(fake, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, below, false));
            if (Cameras.place(fake, ctx)) n++;
        }
        log("cameras placed: " + n + " (data " + d.cameras.size() + ")");
    }

    // ================================================================================================ the playthrough
    private static void build() {
        STEPS.clear();
        // ---- start
        add("start", () -> {
            restart();
            GameData d = data();
            check(d.started && d.phase == GameData.PHASE_PREP, "the night starts in phase 0 (preparation)");
            check(Math.abs(d.clock - Clock.START) < 5, "the clock starts at 02:20 (" + Clock.hhmm(d.clock) + ")");
            check(has(ModItems.PHONE.get()), "your phone is in your pocket");
            check(Step.hudLine(d) == Step.PLUSH.ordinal(), "the HUD's first step: take the plush");
            check(fake.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "adventure mode");
        }, 10);
        // ---- phase 0
        add("plush", () -> {
            interact("plush_shelf", "plush_shelf");
            check(has(ModItems.PLUSH.get()), "the plush from the western room's shelf");
            check(data().done(Step.PLUSH), "step 1 (plush) done");
            interact("plush_shelf", "plush_shelf");
            check(GameManager.count(fake, ModItems.PLUSH.get()) == 1, "the shelf gives it only once");
            interact("sewing_box", "scissors");
            check(has(ModItems.SCISSORS.get()) && has(ModItems.RED_THREAD.get()), "scissors and red thread from the sewing box");
            interact("printout", "printout");
            check(has(ModItems.PRINTOUT.get()), "the printout from the desk");
        }, 5);
        add("cut", () -> {
            Layout.Marker t = Layout.marker("table");
            place(Vec3.atBottomCenterOf(t.pos().relative(Direction.SOUTH)), 180.0f, 30.0f);
            holdFirst(ModItems.SCISSORS.get());
            interact("table", "table");
            check(ps().busy() && "cut".equals(ps().action), "the scissors at the low table: cutting it open (a timed action)");
        }, 0);
        until("cut: the plush is open, step 2 done", () -> data().done(Step.CUT) && has(ModItems.PLUSH_OPEN.get()) && !has(ModItems.PLUSH.get()), 100);
        add("rice", () -> {
            interact("rice", "rice");
            check(ps().busy() && "rice".equals(ps().action), "the rice bag with the open plush: filling it");
        }, 0);
        until("rice: filled, step 3 done", () -> data().done(Step.RICE) && has(ModItems.PLUSH_RICE.get()), 100);
        add("nails", () -> {
            interact("nail_clipper", "nail_clipper");
            check(has(ModItems.NAIL_CLIPPER.get()), "the nail clipper from the washroom");
            holdFirst(ModItems.NAIL_CLIPPER.get());
            check(Ritual.useItem(fake, InteractionHand.MAIN_HAND, GameItem.Kind.NAIL_CLIPPER), "using the clipper with the plush: clipping");
        }, 0);
        until("nails: clippings in it, step 4 done", () -> data().done(Step.NAILS) && GameItem.hasNails(GameManager.find(fake, ModItems.PLUSH_RICE.get())), 100);
        add("sew", () -> {
            holdFirst(ModItems.RED_THREAD.get());
            check(Ritual.useItem(fake, InteractionHand.MAIN_HAND, GameItem.Kind.RED_THREAD), "sewing it shut with the red thread");
        }, 0);
        until("sew: the ritual doll, step 5 done", () -> data().done(Step.SEW) && has(ModItems.RITUAL_DOLL.get()) && !has(ModItems.RED_THREAD.get()), 160);
        add("bath", () -> Apt.setBathTap(level, true), 0);
        until("bath: the tub fills, step 6 done", () -> data().done(Step.BATH), 20 * 45);
        add("salt", () -> {
            interact("cup", "cup");
            check(has(ModItems.CUP_WATER.get()), "a cup of water at the sink");
            interact("salt", "salt");
        }, 0);
        until("salt: salt water, step 7 done", () -> data().done(Step.SALT) && has(ModItems.SALT_WATER.get()), 80);
        add("cameras", () -> {
            interact("camera_bag", "camera_bag");
            check(GameManager.count(fake, ModItems.FIXED_CAMERA.get()) == 4, "four cameras from the bag");
            interact("laptop", "laptop");
            check(has(ModItems.LAPTOP.get()), "the laptop from the desk");
            placeCameras();
            check(data().cameras.size() >= 2, "cameras placed at the suggested spots (" + data().cameras.size() + ")");
            check(Cameras.all(level).size() == data().cameras.size(), "a camera entity for each");
            FixedCamera c1 = Cameras.entity(level, 1);
            check(c1 != null && c1.number() == 1, "numbered in placement order (CAM1)");
            Cameras.app(fake, ps(), 1, 0);
            check(ps().appCamera == 1, "the camera app on the laptop shows CAM1");
            Cameras.app(fake, ps(), 0, 0);
        }, 5);
        until("cameras: step 8 done", () -> data().done(Step.CAMERAS), 40);
        add("tv", () -> {
            Apt.setTv(level, true);
            GameFixtureListener.INSTANCE.switched(level, "tv", true);
        }, 0);
        until("tv: step 9 done", () -> data().done(Step.TV), 40);
        add("clock", () -> {
            check(Step.allDone(data(), Step.PREP), "the whole preparation is done");
            check(Step.hudLine(data()) == Step.HUD_WAIT, "the HUD waits for 3:00");
            data().clock = Clock.HOLD - 30;
        }, 0);
        until("three o'clock: phase 1", () -> data().phase == GameData.PHASE_RITUAL, 20 * 20);
        // ---- phase 1
        add("name", () -> {
            check(data().clock >= Clock.THREE, "it is 3:00 (" + Clock.hhmm(data().clock) + ")");
            Ritual.named(fake, "テスト");
            check(data().done(Step.NAME) && data().dollName.equals("テスト"), "step 10: the doll is named");
            check(GameItem.dollName(GameManager.find(fake, ModItems.RITUAL_DOLL.get())).equals("テスト"), "the name is on the doll");
            holdFirst(ModItems.RITUAL_DOLL.get());
            Ritual.actionKey(fake);
        }, 50);
        add("first_oni_2", () -> Ritual.actionKey(fake), 50);
        add("first_oni_3", () -> Ritual.actionKey(fake), 5);
        until("step 11: 「最初の鬼は…」 three times", () -> data().done(Step.FIRST_ONI), 60);
        add("tub", () -> {
            holdFirst(ModItems.RITUAL_DOLL.get());
            BlockPos tub = m("bathtub");
            place(Vec3.atBottomCenterOf(tub.relative(Layout.marker("bathtub").facing())), 0.0f, 30.0f);
            check(Ritual.rightClickBlock(fake, InteractionHand.MAIN_HAND, tub), "the doll into the full tub");
            check(data().done(Step.TUB), "step 12 done");
            OniDoll d = Doll.get(level);
            check(d != null && d.state() == OniDoll.State.DORMANT && d.pose() == OniDoll.FLOAT, "it floats in the tub (the oni_doll entity)");
        }, 5);
        add("lights", () -> Apt.allLights(level, false), 0);
        until("step 13: every light off", () -> data().done(Step.LIGHTS), 40);
        add("count", () -> {
            Layout.Marker tv = Layout.marker("tv");
            place(Vec3.atBottomCenterOf(tv.pos().relative(tv.facing(), 2)), tv.facing().getOpposite().toYRot(), 0.0f);
            Ritual.actionKey(fake);
            check(ps().eyesClosed, "at the TV: eyes closed, counting");
        }, 0);
        until("step 14: counted to ten", () -> data().done(Step.COUNT) && !ps().eyesClosed, 260);
        add("knife", () -> {
            interact("knife_rack", "knife");
            check(has(ModItems.KNIFE.get()), "the kitchen knife from the rack");
            holdFirst(ModItems.KNIFE.get());
            BlockPos tub = m("bathtub");
            place(Vec3.atBottomCenterOf(tub.relative(Layout.marker("bathtub").facing())), 0.0f, 30.0f);
        }, 0);
        until("step 15: to the bathroom with the knife", () -> data().done(Step.KNIFE), 60);
        add("stab", () -> {
            OniDoll d = Doll.get(level);
            check(d != null && Ritual.interactDoll(fake, InteractionHand.MAIN_HAND, d), "「みつけた」: the stab");
            check(data().done(Step.STAB) && d != null && d.hasKnife() && !has(ModItems.KNIFE.get()), "step 16: the knife is in the doll now");
        }, 50);
        add("next_oni", () -> {
            Ritual.actionKey(fake);
            check(data().done(Step.NEXT_ONI), "step 17: 「次は〇〇が鬼だから」");
            check(data().phase == GameData.PHASE_HIDING, "phase 2: it is the oni now");
            OniDoll d = Doll.get(level);
            check(d != null && d.state() == OniDoll.State.TUB, "it wakes, still floating in the tub");
        }, 5);
        add("hide", () -> {
            holdFirst(ModItems.SALT_WATER.get());
            check(Ritual.useItem(fake, InteractionHand.MAIN_HAND, GameItem.Kind.SALT_WATER) && ps().mouth > 0.9f, "a mouthful of salt water");
            check(GameItem.sips(fake.getMainHandItem()) == GameItem.CUP_SIPS - 1, "one sip less in the cup");
            check(GameFixtureListener.INSTANCE.hide(fake, m("closet_hide")), "into the 押し入れ through the world's hide()");
            check(ps().inCloset() && Apt.closetFusuma(level) == 0, "hidden, the fusuma shut");
        }, 0);
        until("step 18: hidden with salt water in the mouth", () -> data().done(Step.HIDE), 40);
        // ---- phase 2
        add("peek", () -> {
            Hiding.peek(fake, true);
            check(ps().peeking && Apt.closetFusuma(level) == 1, "peeking: the fusuma opens a crack");
            NoiseSystem.Noise last = NoiseSystem.recent().isEmpty() ? null : NoiseSystem.recent().get(NoiseSystem.recent().size() - 1);
            check(last != null && last.cause().equals("peek") && last.radius() == 2.0f, "peeking makes a soft noise (2)");
            Hiding.peek(fake, false);
            check(!ps().peeking && Apt.closetFusuma(level) == 0, "and closes again");
        }, 5);
        add("wake", () -> {
            OniDoll d = Doll.get(level);
            if (d != null) d.tubTicks = 0;
        }, 0);
        until("it leaves the tub when nobody watches", () -> {
            OniDoll d = Doll.get(level);
            return d != null && d.state() != OniDoll.State.TUB && data().has("doll_left_tub");
        }, 80);
        until("it searches (moving along its route)", () -> {
            OniDoll d = Doll.get(level);
            return d != null && (d.state() == OniDoll.State.SEARCH || d.state() == OniDoll.State.CHECK || d.state() == OniDoll.State.INVESTIGATE)
                    && d.position().distanceTo(OniDoll.tubSpot(0)) > 1.5;
        }, 20 * 30);
        add("freeze", () -> {
            OniDoll d = Doll.get(level);
            GameManager.debugPeaceful = true;
            Hiding.exitQuietly(fake, ps());
            place(besideDoll(2.5), 90.0f, 10.0f);
            faceDoll();
            log("doll " + d.describe());
        }, 3);
        add("freeze_check", () -> {
            OniDoll d = Doll.get(level);
            Vec3 before = d.position();
            check(d.seenByEyes || Watch.eyesOn(fake, d.position().add(0, 0.3, 0)), "seen directly: your eyes are on it");
            d.getPersistentData().putDouble("fx", before.x);
            d.getPersistentData().putDouble("fz", before.z);
        }, 20);
        add("freeze_result", () -> {
            OniDoll d = Doll.get(level);
            double moved = Math.hypot(d.getX() - d.getPersistentData().getDouble("fx"), d.getZ() - d.getPersistentData().getDouble("fz"));
            check(moved < 0.05, String.format(Locale.ROOT, "frozen while you look at it (moved %.3f)", moved));
            fake.setYRot(fake.getYRot() + 180.0f);
            fake.setYHeadRot(fake.getYRot());
        }, 30);
        add("suspicion", () -> {
            OniDoll d = Doll.get(level);
            GameManager.debugPeaceful = false;
            check(GameFixtureListener.INSTANCE.hide(fake, m("closet_hide")), "back into the closet");
            Vec3 f = OniDoll.fusumaFront();
            d.moveTo(f.x, f.y, f.z, OniDoll.fusumaYaw(), 0.0f);
            d.suspicion = 0.3f;
            d.setState(OniDoll.State.SEARCH);
            NoiseSystem.emit(level, fake.getEyePosition(), 8.0f, "gasp", fake);
        }, 4);
        add("suspicion_check", () -> {
            OniDoll d = Doll.get(level);
            check(d.suspicion > 0.5f, String.format(Locale.ROOT, "a noise from the closet raises its suspicion (%.2f)", d.suspicion));
            check(d.state() == OniDoll.State.FUSUMA || d.state() == OniDoll.State.STANDOFF, "and it comes to the fusuma (" + d.state() + ")");
            ps().mouth = 1.0f;
            d.suspicion = 1.0f;
        }, 6);
        add("standoff", () -> {
            OniDoll d = Doll.get(level);
            check(d.state() == OniDoll.State.STANDOFF, "at 1.0 with salt water in your mouth it stops at the fusuma (" + d.state() + ")");
            check(!ps().caught(), "and does not open it");
            d.setState(OniDoll.State.SEARCH);
            d.suspicion = 0.0f;
        }, 5);
        add("light_leak", () -> {
            OniDoll d = Doll.get(level);
            Vec3 f = OniDoll.fusumaFront();
            d.moveTo(f.x, f.y, f.z, OniDoll.fusumaYaw(), 0.0f);
            d.setYHeadRot(OniDoll.fusumaYaw());
            d.debugStill = true;
            d.suspicion = 0.0f;
            Hiding.peek(fake, true);
        }, 40);
        add("light_leak_check", () -> {
            OniDoll d = Doll.get(level);
            check(d.suspicion > 0.05f, String.format(Locale.ROOT, "light from the gap while it faces it raises its suspicion (%.2f)", d.suspicion));
            Hiding.peek(fake, false);
            d.debugStill = false;
        }, 5);
        // ---- phase 3
        add("out", () -> {
            Hiding.exit(fake, true);
            check(data().phase == GameData.PHASE_FINISH, "out of the closet: phase 3");
            OniDoll d = Doll.get(level);
            check(d != null && d.state() == OniDoll.State.WAIT, "it stops searching and waits");
            check(ps().mouth > 0.5f, "salt water still in the mouth");
        }, 10);
        add("find", () -> {
            place(besideDoll(1.6), 90.0f, 20.0f);
            faceDoll();
        }, 5);
        until("step: found it", () -> data().done(Step.FIND), 40);
        add("spit", () -> {
            faceDoll();
            Ritual.actionKey(fake);
            OniDoll d = Doll.get(level);
            check(data().done(Step.SPIT) && d != null && d.state() == OniDoll.State.LIMP, "spat on it: it shrieks and goes limp");
            check(ps().mouth <= 0.0f, "the mouth is empty");
            check(data().broken.isEmpty(), "spitting it out at the end breaks no rule (" + data().broken + ")");
        }, 50);
        add("win_1", () -> Ritual.actionKey(fake), 50);
        add("win_2", () -> Ritual.actionKey(fake), 50);
        add("win_3", () -> Ritual.actionKey(fake), 5);
        until("「私の勝ち」 three times", () -> data().done(Step.WIN), 60);
        add("carry", () -> {
            OniDoll d = Doll.get(level);
            check(d != null && Ritual.interactDoll(fake, InteractionHand.MAIN_HAND, d), "picking it up");
            ItemStack doll = GameManager.find(fake, ModItems.RITUAL_DOLL.get());
            check(!doll.isEmpty() && GameItem.spat(doll), "the doll in your hands (spat on)");
            check(Doll.get(level) == null, "no doll entity any more");
            interact("lighter_drawer", "lighter");
            check(has(ModItems.LIGHTER.get()), "the lighter from the drawer");
            holdFirst(ModItems.LIGHTER.get());
            BlockPos sink = m("kitchen_sink");
            place(Vec3.atBottomCenterOf(sink.relative(Layout.marker("kitchen_sink").facing())), Layout.marker("kitchen_sink").facing().getOpposite().toYRot(), 40.0f);
            check(Ritual.rightClickBlock(fake, InteractionHand.MAIN_HAND, sink), "the lighter at the kitchen sink");
            OniDoll b = Doll.get(level);
            check(b != null && b.burning(), "it burns in the sink");
        }, 0);
        until("the fire has done its work: step done, the good ending", () -> data().done(Step.BURN) && data().ending == GameData.ENDING_GOOD, 20 * 15);
        until("the good ending reaches its screen", () -> Endings.playing() == false || data().playTicks - data().endingTick > 130, 20 * 10);
        add("good_done", () -> {
            check(Apt.lightsOn(level, "western") || !Apt.grey(level), "the lights came on");
            check(Recording.LOG.stream().anyMatch(s -> s.startsWith("stab")), "the night's recording log has the stab");
            Endings.done(fake);
            check(!data().started && data().ending == GameData.ENDING_GOOD, "after the ending screen: the night is over (good)");
        }, 10);
        // ---- 鬼
        add("oni", () -> {
            restart();
            jumpTo(Step.FIND);
            data().phase = GameData.PHASE_HIDING;
            data().setDone(Step.FIND, false);
            OniDoll d = Doll.get(level);
            d.setState(OniDoll.State.SEARCH);
            check(GameFixtureListener.INSTANCE.hide(fake, m("closet_hide")), "oni: hidden");
            ps().mouth = 0.0f;
            Vec3 f = OniDoll.fusumaFront();
            d.moveTo(f.x, f.y, f.z, OniDoll.fusumaYaw(), 0.0f);
            d.suspicion = 1.0f;
            d.setState(OniDoll.State.FUSUMA);
        }, 4);
        add("oni_check", () -> {
            check(ps().caught(), "at 1.0 without salt water it opens the fusuma: found");
            check(data().ending == GameData.ENDING_ONI, "the 鬼 ending");
            check(Apt.closetFusuma(level) == 2, "the fusuma is open");
        }, 200);
        add("oni_done", () -> {
            check(data().playTicks - data().endingTick > 150, "the 鬼 ending ran its staging");
            Endings.done(fake);
        }, 10);
        // ---- 外へ
        add("soto", () -> {
            restart();
            jumpTo(Step.KNIFE);
            GameFixtureListener.INSTANCE.frontDoorOpened(fake);
            check(data().ending == GameData.ENDING_SOTO && data().broken.contains("door"), "the front door opened during the ritual: 外へ (a rule broken)");
        }, 30);
        add("soto_loop", () -> {
            Vec3 p = fake.position();
            check(p.z < Layout.marker("door_front").pos().getZ(), String.format(Locale.ROOT, "out in the corridor (%.1f %.1f)", p.x, p.z));
            place(new Vec3(8.0, p.y, p.z), 90.0f, 0.0f);
        }, 3);
        add("soto_loop_check", () -> check(fake.getX() > 20.0, String.format(Locale.ROOT, "the corridor goes on forever (x %.1f)", fake.getX())), 20 * 36);
        add("soto_done", () -> {
            check(ps().endingTicks >= 0, "外へ: the ending screen");
            Endings.done(fake);
        }, 10);
        // ---- 未完
        add("mikan", () -> {
            restart();
            jumpTo(Step.HIDE);
            data().clock = Clock.DAWN - 1;
        }, 30);
        add("mikan_check", () -> {
            check(data().ending == GameData.ENDING_MIKAN, "dawn without an end: 未完");
        }, 60);
        add("mikan_balcony", () -> {
            OniDoll d = Doll.get(level);
            check(d != null && d.position().distanceTo(Layout.marker("balcony").center()) < 1.0, "the doll on the balcony, looking in");
            Endings.done(fake);
        }, 10);
        // ---- the woman, the director, the phone
        add("woman", () -> {
            restart();
            placeCameras();
            jumpTo(Step.HIDE);
            int cam = 0;
            for (int n = 1; n <= Cameras.MAX && cam == 0; n++) if (Director.debugWoman(level, fake, n)) cam = n;
            Woman w = Director.womanEntity(level);
            check(cam > 0 && w != null && w.haunts() == cam, "the woman in a camera's feed (CAM" + cam + ")");
            int other = cam == 1 ? 2 : 1;
            if (w != null) {
                Director.onCameraShown(level, fake, ps(), other, cam);
                check(w.stage == 0, "the first look finds her as she was placed, facing the wall (stage " + w.stage + ")");
            }
            for (int i = 0; i < 4 && w != null; i++) {
                w.seenTicks = 20;  // watched her feed
                Director.onCameraShown(level, fake, ps(), other, cam);
            }
            check(w != null && w.stage >= 4, "closer every time you come back to her feed (stage " + (w == null ? -1 : w.stage) + ")");
        }, 30);
        add("woman_gone", () -> check(Director.womanEntity(level) == null, "and then she is gone"), 5);
        add("director", () -> {
            int ok = 0;
            for (String e : Director.EVENTS) {
                try {
                    if (Director.run(level, fake, e)) ok++;
                    else log("director event " + e + " did not run here");
                } catch (Throwable t) {
                    check(false, "director event " + e + ": " + t);
                }
            }
            check(ok >= Director.EVENTS.length - 3, "the director's events run (" + ok + "/" + Director.EVENTS.length + ")");
        }, 60);
        add("phone", () -> {
            GameData d = data();
            d.clock = Clock.CALL + 1;
            d.unflag("call_333");
        }, 5);
        add("phone_check", () -> {
            check(ps().ringTicks >= 0, "3:33: a call from your own number");
            NoiseSystem.Noise last = null;
            for (NoiseSystem.Noise n : NoiseSystem.recent()) if (n.cause().equals("phone")) last = n;
            check(last != null && last.radius() == 7.0f, "the phone vibrates audibly (7)");
            com.hitori.game.Phone.action(fake, 3);
            com.hitori.game.Phone.action(fake, 0);
            check(data().phoneSilent, "the silent switch");
        }, 5);
        add("light", () -> {
            holdFirst(ModItems.PHONE.get());
            com.hitori.game.Phone.toggleLight(fake, ps());
            check(fake.getMainHandItem().is(ModItems.PHONE_LIGHT.get()), "the phone's light: the held item becomes phone_light");
            com.hitori.game.Phone.toggleLight(fake, ps());
            check(fake.getMainHandItem().is(ModItems.PHONE.get()), "and back");
        }, 5);
        add("save", () -> {
            GameData d = data();
            var tag = d.save(new net.minecraft.nbt.CompoundTag());
            GameData back = GameData.load(tag);
            check(back.phase == d.phase && back.steps == d.steps && back.dollName.equals(d.dollName) && back.cameras.size() == d.cameras.size(),
                    "the night's data saves and loads (phase, steps, name, cameras)");
            Doll.save(level, d);
            Doll.removeAll(level);
            Doll.ensure(level, d);
            check(Doll.get(level) != null, "save and quit: the doll comes back where it was");
        }, 5);
        add("reset", () -> {
            data().resetProgress();
            Doll.removeAll(level);
            Cameras.removeAll(level);
            Apt.resetAll(level);
            check(!data().started && data().steps == 0 && Doll.get(level) == null && Cameras.all(level).isEmpty(), "reset: a fresh night");
        }, 5);
    }

    private static void finish(MinecraftServer server) {
        stage = 99;
        if (fake != null) GameManager.removeTestPlayer(fake);
        log("SELFTEST RESULT: " + (failures == 0 ? "PASS" : "FAIL (" + failures + ")"));
        server.halt(false);
    }
}
