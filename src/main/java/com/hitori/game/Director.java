package com.hitori.game;

import com.hitori.Hitori;
import com.hitori.entity.FixedCamera;
import com.hitori.entity.OniDoll;
import com.hitori.entity.Woman;
import com.hitori.registry.ModEntities;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The 霊障 (docs/DESIGN.md §3.7), paced by phase, fear and time; the quiet stretches matter. Before 3:00 almost
 * nothing (a creak, the fridge, the rain). Phase 1: the count's sloshing ({@link Ritual}). Phase 2, once the tub is
 * empty: the TV changing channel, the intercom, knocking on the closet from outside, wet small footsteps, the bath tap,
 * the swinging lamp, the pull-cord, the washing machine, things falling in the kitchen, the doll's giggle from the
 * wrong room, your name whispered at the fusuma — and the woman in a corner of one camera's feed, closer every time
 * you come back to it (in the closet's feed, if you put a camera in there: behind you). Never two big ones close
 * together, nothing while the doll stands at the fusuma. Phase 3: silence, the TV and the rain.
 */
public final class Director {
    private static final RandomSource RAND = RandomSource.create();
    public static final String[] EVENTS = {"creak", "ceiling", "tv_channel", "tv_voice", "intercom", "knock_closet", "knock_door", "wet_steps",
            "bath_tap", "lamp_swing", "pull_cord", "washing", "kitchen_fall", "dish", "giggle", "whisper_name", "woman", "camera_fall", "flicker"};
    private static final java.util.Set<String> BIG = java.util.Set.of("dish", "intercom", "knock_door", "tv_channel", "washing", "woman", "camera_fall");

    private static long clock;
    private static long next = -1;
    private static long lastBig = -20 * 60;
    private static String lastKind = "";
    private static final List<long[]> PENDING = new ArrayList<>();
    private static final List<Runnable> PENDING_RUN = new ArrayList<>();
    /** Self test / debug: events so far (id @ clock). */
    public static final List<String> LOG = new ArrayList<>();
    public static boolean paused;
    private static int women;

    private Director() {}

    public static void reset() {
        clock = 0;
        next = -1;
        lastBig = -20 * 60;
        lastKind = "";
        PENDING.clear();
        PENDING_RUN.clear();
        LOG.clear();
        women = 0;
    }

    public static void clearFor(ServerPlayer p) {
    }

    /** Runs something a few ticks later (the phone call, the endings' staging). */
    public static void laterPublic(int ticks, Runnable r) {
        later(ticks, r);
    }

    private static void later(int ticks, Runnable r) {
        PENDING.add(new long[]{clock + ticks, PENDING_RUN.size()});
        PENDING_RUN.add(r);
    }

    // ================================================================================================ tick
    public static void tick(ServerLevel level, GameData data) {
        clock++;
        if (!PENDING.isEmpty()) {
            List<long[]> due = new ArrayList<>();
            for (long[] e : PENDING) if (e[0] <= clock) due.add(e);
            for (long[] e : due) {
                PENDING.remove(e);
                try {
                    PENDING_RUN.get((int) e[1]).run();
                } catch (Exception ex) {
                    Hitori.LOGGER.warn("[hitori] director: a delayed part failed", ex);
                }
            }
            if (PENDING.isEmpty()) PENDING_RUN.clear();
        }
        if (paused || data.phase == GameData.PHASE_ENDING) return;
        ServerPlayer p = Ritual.firstPlayer(level);
        if (p == null) return;
        PlayerState ps = GameManager.state(p);
        if (next < 0) next = clock + gap(data, ps);
        if (clock < next) return;
        String kind = choose(level, data, ps);
        next = clock + gap(data, ps);
        if (kind == null) return;
        if (BIG.contains(kind) && clock - lastBig < 20 * 15) return;
        run(level, p, kind);
    }

    /** Ticks until the next event: long before 3:00, shorter as the hiding goes on and fear rises. */
    private static long gap(GameData data, PlayerState ps) {
        return switch (data.phase) {
            case GameData.PHASE_PREP -> 20L * (55 + RAND.nextInt(50));
            case GameData.PHASE_RITUAL -> 20L * (45 + RAND.nextInt(40));
            case GameData.PHASE_HIDING -> {
                float t = OniDoll.tension(data);
                yield (long) (20 * (40 + RAND.nextInt(45)) * (1.0f - 0.4f * t) * (1.0f - 0.2f * ps.fear));
            }
            default -> 20L * (90 + RAND.nextInt(60));
        };
    }

    @Nullable
    private static String choose(ServerLevel level, GameData data, PlayerState ps) {
        OniDoll doll = Doll.get(level);
        switch (data.phase) {
            case GameData.PHASE_PREP -> {
                return RAND.nextInt(3) == 0 ? "ceiling" : "creak";
            }
            case GameData.PHASE_RITUAL -> {
                return RAND.nextInt(2) == 0 ? "creak" : null;
            }
            case GameData.PHASE_HIDING -> {
                if (doll == null || !data.has("doll_left_tub")) return RAND.nextInt(3) == 0 ? "creak" : null;
                if (doll.state() == OniDoll.State.FUSUMA || doll.state() == OniDoll.State.STANDOFF) return null;
                List<String> pool = new ArrayList<>(List.of("tv_channel", "tv_voice", "intercom", "wet_steps", "bath_tap", "lamp_swing", "pull_cord",
                        "washing", "kitchen_fall", "dish", "giggle", "creak", "ceiling", "flicker", "knock_door"));
                if (ps.inCloset()) {
                    pool.add("knock_closet");
                    pool.add("whisper_name");
                }
                if (!data.cameras.isEmpty() && women < 3 && womanEntity(level) == null) {
                    pool.add("woman");
                    pool.add("woman");
                }
                if (!data.cameras.isEmpty() && RAND.nextInt(4) == 0) pool.add("camera_fall");
                pool.remove(lastKind);
                return pool.get(RAND.nextInt(pool.size()));
            }
            case GameData.PHASE_FINISH -> {
                return RAND.nextInt(4) == 0 ? "creak" : null;
            }
            default -> {
                return null;
            }
        }
    }

    // ================================================================================================ the events
    private static Vec3 m(String marker) {
        return Layout.has(marker) ? Layout.marker(marker).center().add(0.0, 1.0, 0.0) : Vec3.ZERO;
    }

    private static Vec3 roomCentre(String room) {
        for (Layout.Room r : Layout.rooms()) if (r.key().equals(room)) return r.box().getCenter();
        return m("table");
    }

    private static void world(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.AMBIENT, vol, pitch);
    }

    /** Runs one event now (also /hitori debug event). */
    public static boolean run(ServerLevel level, @Nullable ServerPlayer p, String kind) {
        GameData data = GameData.get(level.getServer());
        lastKind = kind;
        if (BIG.contains(kind)) lastBig = clock;
        LOG.add(kind + "@" + Clock.hhmm(data.clock));
        if (LOG.size() > 300) LOG.remove(0);
        float pitch = 0.92f + RAND.nextFloat() * 0.16f;
        switch (kind) {
            case "creak" -> {
                String[] rooms = {"hall", "dk", "tatami", "western", "washroom", "genkan"};
                world(level, roomCentre(rooms[RAND.nextInt(rooms.length)]), ModSounds.FIXTURE_CREAK.get(), 0.7f, pitch);
            }
            case "ceiling" -> {
                Vec3 at = roomCentre(RAND.nextBoolean() ? "tatami" : "western").add(0.0, 3.0, 0.0);
                world(level, at, ModSounds.FIXTURE_FOOTSTEPS_CEILING.get(), 0.6f, 1.0f);
            }
            case "tv_channel" -> {
                int ch = RAND.nextInt(3) == 0 ? 2 : 1;
                Apt.setTvChannel(level, ch);
                world(level, m("tv"), ModSounds.FIXTURE_TV_CHANNEL.get(), 1.0f, 1.0f);
                later(20 * (7 + RAND.nextInt(9)), () -> {
                    Apt.setTvChannel(level, 0);
                    world(level, m("tv"), ModSounds.FIXTURE_TV_CHANNEL.get(), 0.8f, 0.9f);
                });
                Recording.event(level, "tv_channel", m("tv"));
                fear(level, 0.1f);
            }
            case "tv_voice" -> {
                world(level, m("tv"), ModSounds.FIXTURE_TV_VOICE.get(), 0.9f, pitch);
                if (p != null && GameManager.state(p).room.equals("tatami") || p != null && GameManager.state(p).inCloset())
                    GameManager.say(p, "tv", "say.hitori.tv_voice." + (1 + RAND.nextInt(3)), 60);
            }
            case "intercom" -> {
                Apt.setDevice(level, "intercom", true);
                world(level, m("intercom"), ModSounds.FIXTURE_INTERCOM.get(), 1.0f, 1.0f);
                later(60, () -> world(level, m("intercom"), ModSounds.FIXTURE_INTERCOM.get(), 1.0f, 1.0f));
                later(140, () -> Apt.setDevice(level, "intercom", false));
                fear(level, 0.12f);
                // the camera that sees the genkan: someone is standing there now
                FixedCamera cam = cameraSeeing(level, data, m("door_front").add(0, -0.5, 0));
                if (cam != null && womanEntity(level) == null) {
                    later(80, () -> placeWoman(level, data, cam, Layout.marker("door_front").center().add(0.0, 0.0, 0.0), 1));
                }
                Recording.event(level, "intercom", m("intercom"));
            }
            case "knock_closet" -> {
                if (p == null) return false;
                Vec3 at = OniDoll.fusumaCentre();
                for (int i = 0; i < 3; i++) {
                    int k = i;
                    later(k * 9, () -> GameManager.privateSound(p, ModSounds.FIXTURE_KNOCK_CLOSET.get(), at, 0.9f, 1.0f - k * 0.03f));
                }
                fear(level, 0.18f);
            }
            case "knock_door" -> {
                Vec3 at = m("door_front");
                for (int i = 0; i < 3; i++) {
                    int k = i;
                    later(k * 11, () -> world(level, at, ModSounds.FIXTURE_KNOCK_DOOR.get(), 1.0f, 1.0f));
                }
                fear(level, 0.12f);
            }
            case "wet_steps" -> {
                if (p == null) return false;
                Vec3 to = p.position();
                Vec3 from = roomCentre(RAND.nextBoolean() ? "hall" : "dk");
                Vec3 end = to.add(to.subtract(from).normalize().scale(-1.6));
                for (int i = 0; i < 7; i++) {
                    double f = i / 6.0;
                    Vec3 at = from.lerp(end, f);
                    later(i * 12, () -> GameManager.privateSound(p, ModSounds.DOLL_STEP.get(), at, 0.7f, 1.05f));
                }
                fear(level, 0.1f);
            }
            case "bath_tap" -> {
                Apt.setBathTap(level, true);
                world(level, m("bath_tap"), ModSounds.FIXTURE_TAP_BATH.get(), 0.8f, 1.0f);
                later(20 * 8, () -> Apt.setBathTap(level, false));
                Recording.event(level, "bath_tap", m("bath_tap"));
            }
            case "lamp_swing" -> {
                Apt.swingLamp(level, 20 * 12);
                world(level, roomCentre("tatami").add(0.0, 1.0, 0.0), ModSounds.FIXTURE_LAMP_SWING.get(), 0.7f, 1.0f);
                Recording.event(level, "lamp_swing", roomCentre("tatami"));
            }
            case "pull_cord" -> {
                world(level, roomCentre("tatami").add(0.0, 1.0, 0.0), ModSounds.FIXTURE_PULL_CORD.get(), 0.8f, 1.0f);
                Apt.flicker(level, "tatami", 10);
            }
            case "washing" -> {
                Apt.setDevice(level, "washing_machine", true);
                world(level, roomCentre("washroom"), ModSounds.FIXTURE_WASHING_START.get(), 1.0f, 1.0f);
                later(20 * 30, () -> Apt.setDevice(level, "washing_machine", false));
                Recording.event(level, "washing", roomCentre("washroom"));
                fear(level, 0.08f);
            }
            case "kitchen_fall" -> {
                Vec3 at = roomCentre("dk");
                Apt.dropSomething(level, BlockPos.containing(at));
                world(level, at, ModSounds.FIXTURE_THING_FALL.get(), 1.0f, pitch);
                Recording.event(level, "kitchen_fall", at);
            }
            case "dish" -> {
                Vec3 at = roomCentre("dk");
                Apt.dropSomething(level, BlockPos.containing(at));
                world(level, at, ModSounds.FIXTURE_DISH_BREAK.get(), 1.0f, 1.0f);
                fear(level, 0.15f);
                if (p != null) GameManager.event(p, GameManager.EV_STINGER, 0, "");
            }
            case "giggle" -> {
                OniDoll doll = Doll.get(level);
                String[] rooms = {"bath", "western", "dk", "genkan", "toilet", "washroom"};
                String room = rooms[RAND.nextInt(rooms.length)];
                for (int i = 0; i < 4 && doll != null && Cameras.room(doll.position()).equals(room); i++) room = rooms[RAND.nextInt(rooms.length)];
                world(level, roomCentre(room), ModSounds.DOLL_GIGGLE.get(), 0.8f, pitch);
                fear(level, 0.1f);
            }
            case "whisper_name" -> {
                if (p == null) return false;
                Vec3 at = OniDoll.fusumaCentre();
                GameManager.privateSound(p, ModSounds.WHISPER_NAME.get(), at, 0.9f, 1.0f);
                GameManager.say(p, "whisper", "say.hitori.whisper_name", 50, p.getGameProfile().getName());
                fear(level, 0.2f);
            }
            case "woman" -> {
                FixedCamera cam = pickCameraForWoman(level, data, p);
                if (cam == null) return false;
                Vec3 spot = womanSpot(level, cam, p);
                if (spot == null) return false;
                placeWoman(level, data, cam, spot, 0);
                women++;
            }
            case "camera_fall" -> {
                if (data.cameras.isEmpty()) return false;
                GameData.CamSpot c = data.cameras.get(RAND.nextInt(data.cameras.size()));
                Cameras.knockOver(level, data, c.number());
                Recording.event(level, "camera_fall", new Vec3(c.x(), c.y(), c.z()));
            }
            case "flicker" -> {
                String[] rooms = {"hall", "dk", "tatami", "western", "washroom", "bath"};
                Apt.flicker(level, rooms[RAND.nextInt(rooms.length)], 30);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void fear(ServerLevel level, float f) {
        for (ServerPlayer p : GameManager.players(level)) if (GameManager.inGame(p)) GameManager.addFear(p, f);
    }

    @Nullable
    private static FixedCamera cameraSeeing(ServerLevel level, GameData data, Vec3 at) {
        for (FixedCamera c : Cameras.all(level)) if (Watch.cameraSees(c, at)) return c;
        return null;
    }

    // ================================================================================================ the woman
    @Nullable
    public static Woman womanEntity(ServerLevel level) {
        List<Woman> l = level.getEntitiesOfClass(Woman.class, Doll.AREA, Entity::isAlive);
        return l.isEmpty() ? null : l.get(0);
    }

    /** A camera for her: the closet's own if there is one and you hide there, else one you are not watching right now. */
    @Nullable
    private static FixedCamera pickCameraForWoman(ServerLevel level, GameData data, @Nullable ServerPlayer p) {
        List<FixedCamera> cams = Cameras.all(level);
        if (cams.isEmpty()) return null;
        PlayerState ps = p == null ? null : GameManager.state(p);
        if (ps != null && ps.inCloset()) {
            for (FixedCamera c : cams) if (Cameras.room(c.position()).equals("closet")) return c;
        }
        List<FixedCamera> pool = new ArrayList<>(cams);
        if (ps != null && pool.size() > 1) pool.removeIf(c -> c.number() == ps.appCamera);
        return pool.get(RAND.nextInt(pool.size()));
    }

    /** Where she stands for this camera: a woman_spot it sees, behind you in the closet, or a corner of its frame. */
    @Nullable
    private static Vec3 womanSpot(ServerLevel level, FixedCamera cam, @Nullable ServerPlayer p) {
        if (p != null && GameManager.state(p).inCloset() && Cameras.room(cam.position()).equals("closet")) {
            // in the closet's feed: behind you
            Vec3 me = p.position();
            for (Layout.Marker m : Layout.markers("closet_hide")) {
                Vec3 c = m.center();
                if (c.distanceTo(me) > 0.6) return c;
            }
        }
        List<Layout.Marker> spots = new ArrayList<>(Layout.markers("woman_spots"));
        java.util.Collections.shuffle(spots, new java.util.Random(RAND.nextLong()));
        for (Layout.Marker m : spots) {
            Vec3 at = m.center();
            if (Watch.cameraSees(cam, at.add(0.0, 1.5, 0.0)) && Watch.cameraSees(cam, at.add(0.0, 0.4, 0.0))) return at;
        }
        // a corner of the frame, 3–4 blocks out
        Vec3 eye = cam.getEyePosition();
        Vec3 f = Vec3.directionFromRotation(0.0f, cam.getYRot());
        Vec3 r = new Vec3(-f.z, 0.0, f.x);
        for (double dist = 4.0; dist >= 2.2; dist -= 0.6) {
            for (double side : new double[]{1.1, -1.1, 0.5, -0.5}) {
                Vec3 at = new Vec3(eye.x + f.x * dist + r.x * side, Layout.FEET_Y, eye.z + f.z * dist + r.z * side);
                if (Watch.cameraSees(cam, at.add(0.0, 1.5, 0.0)) && Watch.cameraSees(cam, at.add(0.0, 0.4, 0.0))) return at;
            }
        }
        return null;
    }

    private static void placeWoman(ServerLevel level, GameData data, FixedCamera cam, Vec3 at, int stage) {
        Woman w = womanEntity(level);
        if (w == null) {
            w = ModEntities.WOMAN.get().create(level);
            if (w == null) return;
            level.addFreshEntity(w);
        }
        w.setHaunts(cam.number());
        w.stage = stage;
        w.seenTicks = 0;
        w.lifeTicks = 20 * 200;
        w.getPersistentData().putDouble("hx", at.x);
        w.getPersistentData().putDouble("hy", at.y);
        w.getPersistentData().putDouble("hz", at.z);
        Vec3 lens = cam.getEyePosition();
        if (stage == 0) {
            // facing the wall: away from the lens
            Vec3 away = at.add(at.subtract(lens).normalize());
            w.stand(at, away, Woman.STAND);
        } else {
            w.stand(at, lens, Woman.FACE);
        }
        Recording.event(level, "woman", at.add(0.0, 1.0, 0.0));
    }

    /** Watching her feed this long counts as having seen her (half a second). */
    private static final int SEEN_TICKS = 10;

    /** Per player tick while the app is open: the woman of the watched feed is being seen. */
    static void watching(ServerLevel level, PlayerState ps) {
        Woman w = womanEntity(level);
        if (w != null && ps.appCamera > 0 && w.haunts() == ps.appCamera) w.seenTicks++;
    }

    /** Debug: the woman in this camera's feed now. */
    public static boolean debugWoman(ServerLevel level, @Nullable ServerPlayer p, int number) {
        FixedCamera cam = Cameras.entity(level, number);
        if (cam == null) return false;
        Vec3 spot = womanSpot(level, cam, p);
        if (spot == null) return false;
        placeWoman(level, GameData.get(level.getServer()), cam, spot, 0);
        return true;
    }

    /** The player switched to camera {@code now} (from {@code before}): the woman of that feed has come closer. */
    public static void onCameraShown(ServerLevel level, ServerPlayer p, PlayerState ps, int before, int now) {
        Woman w = womanEntity(level);
        // she comes closer only once you have seen her where she stood: the first look finds her as she was placed
        if (w == null || w.haunts() != now || before == now || w.seenTicks < SEEN_TICKS) return;
        FixedCamera cam = Cameras.entity(level, now);
        if (cam == null) return;
        w.stage++;
        w.seenTicks = 0;
        Vec3 home = new Vec3(w.getPersistentData().getDouble("hx"), w.getPersistentData().getDouble("hy"), w.getPersistentData().getDouble("hz"));
        Vec3 lens = cam.getEyePosition();
        Vec3 floorLens = new Vec3(lens.x, home.y, lens.z);
        switch (w.stage) {
            case 1 -> w.stand(home, lens, Woman.FACE);
            case 2 -> w.stand(home.lerp(floorLens, 0.45), lens, Woman.FACE);
            case 3 -> w.stand(home.lerp(floorLens, 0.72), lens, Woman.CLOSE);
            default -> {
                // right in front of the lens: her face fills the feed, a whisper, and she is gone
                Vec3 close = floorLens.add(Vec3.directionFromRotation(0.0f, cam.getYRot()).scale(0.75));
                w.stand(close, lens, Woman.CLOSE);
                GameManager.privateSound(p, ModSounds.WOMAN_WHISPER.get(), p.getEyePosition(), 1.0f, 1.0f);
                GameManager.say(p, "woman", "say.hitori.woman", 60);
                boolean closet = Cameras.room(cam.position()).equals("closet") || nearestToCloset(level) == cam;
                GameManager.event(p, GameManager.EV_SCARE, closet ? 2 : 3, "");
                GameManager.addFear(p, closet ? 0.4f : 0.25f);
                w.lifeTicks = 12;
                Woman ww = w;
                later(10, () -> ww.setAnimPose(Woman.GONE));
            }
        }
        if (w.stage < 4) {
            GameManager.event(p, GameManager.EV_FEED, 0, "woman");
            if (w.stage >= 2) GameManager.privateSound(p, ModSounds.WOMAN_BREATH.get(), p.getEyePosition(), 0.6f, 1.0f);
        }
    }

    @Nullable
    private static FixedCamera nearestToCloset(ServerLevel level) {
        Vec3 c = OniDoll.fusumaCentre();
        FixedCamera best = null;
        double bd = Double.MAX_VALUE;
        for (FixedCamera f : Cameras.all(level)) {
            double d = f.position().distanceToSqr(c);
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        return best;
    }

    public static void onEnterRoom(ServerLevel level, ServerPlayer p, String from, String to) {
    }

    static float clamp(float v) {
        return Mth.clamp(v, 0.0f, 1.0f);
    }
}
