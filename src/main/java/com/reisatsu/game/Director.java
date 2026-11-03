package com.reisatsu.game;

import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Circuit;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Paces the poltergeist (docs/DESIGN.md §3.12): small things every half minute or so, a big one now and then — never
 * two big events within 20 s, never while something is hunting you, and real quiet stretches. Busier with each 霊障
 * level. Every event the player witnesses goes into the report's list.
 */
public final class Director {
    private static final RandomSource RAND = RandomSource.create();
    private static long nextMinor = 20 * 40;
    private static long nextBig = 20 * 75;
    private static long lastBig = -20 * 60;
    private static long clock;
    private static final List<long[]> PENDING = new ArrayList<>();
    private static final List<Runnable> PENDING_RUN = new ArrayList<>();
    /** Ids of every event, for /reisatsu debug event. */
    public static final String[] EVENTS = {"door", "taps", "menus", "call", "chair", "flicker", "burst", "trip", "music", "pa", "phone",
            "tv", "dryer", "drink", "plate", "knock_fridge", "knock_stall", "bear", "glass", "prints", "board", "car", "crash"};

    private Director() {}

    public static void reset() {
        nextMinor = 20 * 40;
        nextBig = 20 * 75;
        lastBig = -20 * 60;
        clock = 0;
        phoneUntil = -1;
        PENDING.clear();
        PENDING_RUN.clear();
    }

    public static void clearFor(ServerPlayer p) {
    }

    /** Remembers that the player saw (or heard) this phenomenon. */
    public static void witness(ServerPlayer p, String what) {
        GameData data = GameData.get(p.server);
        if (data.witnessed.add(what)) data.setDirty();
    }

    /** Until when the phone rings (the world clears its RINGING state before telling us it was picked up). */
    private static long phoneUntil = -1;

    /** Was the phone ringing (and is now answered)? Clears the ringing. */
    public static boolean answerPhone() {
        boolean ringing = clock < phoneUntil;
        phoneUntil = -1;
        return ringing;
    }

    private static void later(int ticks, Runnable r) {
        PENDING.add(new long[]{clock + ticks, PENDING_RUN.size()});
        PENDING_RUN.add(r);
    }

    public static void tick(ServerLevel level, GameData data) {
        clock++;
        // delayed parts of events
        for (int i = 0; i < PENDING.size(); i++) {
            long[] e = PENDING.get(i);
            if (e[0] <= clock && e[1] >= 0) {
                Runnable r = PENDING_RUN.get((int) e[1]);
                e[1] = -1;
                try {
                    r.run();
                } catch (Exception ignored) {
                }
            }
        }
        PENDING.removeIf(e -> e[1] < 0);
        if (PENDING.isEmpty()) PENDING_RUN.clear();
        ServerPlayer p = focus(level);
        if (p == null) return;
        PlayerState ps = GameManager.state(p);
        float busy = 1.0f - 0.16f * data.reishou;
        // nothing on top of a chase
        if (ps.danger > 0.35f) {
            nextMinor = Math.max(nextMinor, clock + 20 * 8);
            return;
        }
        if (clock >= nextBig && clock - lastBig > 20 * 20) {
            if (big(level, p, ps, data)) {
                lastBig = clock;
                nextBig = clock + (long) ((20 * 70 + RAND.nextInt(20 * 60)) * busy);
                nextMinor = Math.max(nextMinor, clock + 20 * 15);
            } else {
                nextBig = clock + 20 * 10;
            }
        }
        if (clock >= nextMinor) {
            minor(level, p, ps, data);
            nextMinor = clock + (long) ((20 * 25 + RAND.nextInt(20 * 30)) * busy);
        }
    }

    @Nullable
    private static ServerPlayer focus(ServerLevel level) {
        for (ServerPlayer p : GameManager.players(level)) if (GameManager.huntable(p)) return p;
        return null;
    }

    private static boolean inside(PlayerState ps) {
        return !ps.room.isEmpty() && !ps.room.equals("outside");
    }

    // ================================================================================================ choosing
    private static void minor(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        String room = ps.room;
        List<String> pool = new ArrayList<>();
        if (!inside(ps)) {
            pool.add("car");
            pool.add("car");
            if (data.capturedCount(SpiritKind.ALL) >= 2 && !data.happened("menu_board")) pool.add("board");
        } else {
            pool.add("call");
            pool.add("chair");
            pool.add("flicker");
            pool.add("pa");
            pool.add("glass");
            pool.add("car");
            switch (room) {
                case "kitchen", "fridge" -> {
                    pool.add("taps");
                    pool.add("knock_fridge");
                    pool.add("knock_fridge");
                }
                case "womens", "mens" -> {
                    pool.add("dryer");
                    pool.add("knock_stall");
                    pool.add("taps");
                }
                case "staff", "office", "corridor", "storage" -> {
                    pool.add("tv");
                    pool.add("tv");
                }
                case "waiting", "vestibule" -> {
                    pool.add("bear");
                    pool.add("drink");
                }
                default -> {
                    pool.add("drink");
                    pool.add("bear");
                }
            }
        }
        run(level, p, pool.get(RAND.nextInt(pool.size())));
    }

    private static boolean big(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        if (!inside(ps)) {
            if (RAND.nextInt(3) == 0) {
                run(level, p, "door");
                return true;
            }
            return false;
        }
        List<String> pool = new ArrayList<>();
        pool.add("door");
        pool.add("menus");
        pool.add("menus");
        pool.add("music");
        pool.add("phone");
        pool.add("burst");
        pool.add("prints");
        if (ps.room.equals("kitchen")) {
            pool.add("plate");
            pool.add("plate");
        }
        if (data.reishou > 0) pool.add("trip");
        run(level, p, pool.get(RAND.nextInt(pool.size())));
        return true;
    }

    // ================================================================================================ the events
    /** Runs one event near the player; returns false if it could not happen here. */
    public static boolean run(ServerLevel level, ServerPlayer p, String id) {
        Vec3 pp = p.position();
        switch (id) {
            case "door" -> {
                Fixtures.openAutoDoor(level, "outer", 60);
                later(40, () -> {
                    if (level.random.nextBoolean()) Fixtures.openAutoDoor(level, "inner", 50);
                });
                fear(p, 0.12f);
                witnessNear(p, "door", Layout.marker("door_outer").center(), 30);
            }
            case "taps" -> {
                BlockPos tap = pick(level, p, "sink_kitchen", "sink_m");
                if (tap == null) return false;
                Fixtures.setTap(level, tap, true);
                later(20 * 12, () -> Fixtures.setTap(level, tap, false));
                fear(p, 0.06f);
                witnessNear(p, "taps", Vec3.atCenterOf(tap), 16);
            }
            case "menus" -> {
                int t = nearestTable(pp, 4.0, 18.0);
                if (t < 0) return false;
                Fixtures.scatterMenus(level, t);
                fear(p, 0.16f);
                witnessNear(p, "menus", Layout.marker("table_" + t).center(), 20);
            }
            case "call" -> {
                int t = nearestTable(pp, 5.0, 24.0);
                if (t < 0) return false;
                Fixtures.ringCallButton(level, t);
                fear(p, 0.06f);
                witnessNear(p, "call", Layout.marker("table_" + t).center(), 30);
            }
            case "chair" -> {
                List<BlockPos> chairs = Fixtures.chairsNear(level, p.blockPosition(), 12);
                List<BlockPos> ok = new ArrayList<>();
                for (BlockPos c : chairs) if (c.distSqr(p.blockPosition()) > 16) ok.add(c);
                if (ok.isEmpty()) return false;
                BlockPos c = ok.get(RAND.nextInt(ok.size()));
                Fixtures.knockChair(level, c, RAND.nextInt(3) == 0);
                NoiseSystem.emit(level, Vec3.atCenterOf(c), 10.0f, "chair", null);
                fear(p, 0.07f);
                witnessNear(p, "chair", Vec3.atCenterOf(c), 16);
            }
            case "flicker" -> {
                if (!inside(GameManager.state(p))) return false;
                Fixtures.flickerRoom(level, GameManager.state(p).room, 30 + RAND.nextInt(40));
                fear(p, 0.05f);
                witness(p, "lights");
            }
            case "burst" -> {
                List<BlockPos> lights = Fixtures.lightsIn(level, GameManager.state(p).room);
                if (lights.isEmpty()) return false;
                Fixtures.burstLight(level, lights.get(RAND.nextInt(lights.size())));
                fear(p, 0.14f);
                witness(p, "lights");
            }
            case "trip" -> {
                Circuit[] cs = {Circuit.DINING, Circuit.KITCHEN, Circuit.RESTROOM, Circuit.BACK};
                Circuit c = cs[RAND.nextInt(cs.length)];
                if (!Fixtures.circuitOn(level, c)) return false;
                Breaker.switchCircuit(level, c, false, null);
                fear(p, 0.14f);
                witness(p, "lights");
            }
            case "music" -> {
                Fixtures.setDevice(level, "store_music", true);
                GameData data = GameData.get(p.server);
                data.once("music_event");
                later(20 * 28, () -> {
                    if (GameData.get(p.server).phase == GameData.PHASE_INVESTIGATE) Fixtures.setDevice(level, "store_music", false);
                });
                fear(p, 0.12f);
                witness(p, "music");
            }
            case "pa" -> {
                GameManager.privateSound(p, ModSounds.FIXTURE_PA_CHIME.get(), pp.add(0, 3, 0), 0.8f, 0.94f);
                later(40, () -> {
                    GameManager.privateSound(p, ModSounds.FIXTURE_PA_VOICE.get(), pp.add(0, 3, 0), 0.8f, 0.9f);
                    Story.say(p, "pa", 70, "dialogue.reisatsu.pa.mumble");
                });
                fear(p, 0.08f);
                witness(p, "pa");
            }
            case "phone" -> {
                Fixtures.setDevice(level, "phone", true);
                phoneUntil = clock + 20 * 14;
                NoiseSystem.emit(level, Layout.marker("phone").center(), 12.0f, "phone", null);
                later(20 * 14, () -> Fixtures.setDevice(level, "phone", false));
                fear(p, 0.1f);
                witnessNear(p, "phone", Layout.marker("phone").center(), 40);
            }
            case "tv" -> device(level, p, "tv", 20 * 16, "tv", 0.07f);
            case "dryer" -> device(level, p, GameManager.state(p).room.equals("mens") ? "hand_dryer_m" : "hand_dryer_w", 20 * 5, "dryer", 0.1f);
            case "drink" -> device(level, p, "drink_bar", 20 * 6, "drink", 0.06f);
            case "plate" -> {
                Vec3 at = Layout.marker("chef_stove").center().add(RAND.nextInt(9) - 4, 0, RAND.nextInt(5) - 2);
                Fixtures.smashPlate(level, BlockPos.containing(at));
                NoiseSystem.emit(level, at, 16.0f, "plate", null);
                fear(p, 0.14f);
                witnessNear(p, "plate", at, 30);
            }
            case "knock_fridge" -> {
                Vec3 at = Layout.marker("fridge_door").center().add(0, 1.2, 0);
                for (int i = 0; i < 3; i++) later(i * 14, () -> sound(level, at, ModSounds.FIXTURE_FRIDGE_KNOCK.get(), 0.9f));
                fear(p, 0.08f);
                witnessNear(p, "knock", at, 16);
            }
            case "knock_stall" -> {
                Vec3 at = Layout.marker("stall_occupied").center().add(0, 1.0, 0);
                for (int i = 0; i < 3; i++) later(i * 12, () -> sound(level, at, ModSounds.FIXTURE_STALL_KNOCK.get(), 0.8f));
                fear(p, 0.08f);
                witnessNear(p, "knock", at, 16);
            }
            case "bear" -> {
                Vec3 bear = Layout.marker("stuffed_bear").center();
                double dx = pp.x - bear.x, dz = pp.z - bear.z;
                Direction d = Direction.fromYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f);
                Fixtures.turnBear(level, d);
                fear(p, 0.05f);
                witnessNear(p, "bear", bear, 20);
            }
            case "glass" -> {
                int t = nearestTable(pp, 3.0, 16.0);
                if (t < 0) return false;
                Fixtures.placeWaterGlass(level, t);
                fear(p, 0.05f);
                witnessNear(p, "glass", Layout.marker("table_" + t).center(), 20);
            }
            case "prints" -> {
                List<Layout.Marker> path = Layout.markers("visitor_path");
                int start = Math.max(0, path.size() / 3);
                int n = 0;
                for (int i = start; i < path.size() - 1; i++) {
                    Vec3 a = path.get(i).center(), b = path.get(i + 1).center();
                    double len = a.distanceTo(b);
                    float yaw = (float) (Mth.atan2(b.z - a.z, b.x - a.x) * Mth.RAD_TO_DEG) - 90.0f;
                    for (double s = 0; s < len; s += 0.9) {
                        Vec3 at = a.add(b.subtract(a).scale(s / Math.max(0.01, len)));
                        final int k = n++;
                        later(k * 3, () -> Fixtures.wetFootprint(level, at.add((k % 2 == 0 ? 0.15 : -0.15), 0.01, 0), yaw));
                    }
                }
                fear(p, 0.1f);
                witness(p, "prints");
            }
            case "board" -> {
                if (!GameData.get(p.server).once("menu_board")) return false;
                Fixtures.setMenuBoard(level, 1);
                witness(p, "board");
            }
            case "car" -> {
                Vec3 road = new Vec3(pp.x + (RAND.nextBoolean() ? -40 : 40), Layout.FEET_Y + 1, 54);
                sound(level, road, ModSounds.FIXTURE_CAR_PASS.get(), 1.0f);
            }
            case "crash" -> crashCue(level);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void device(ServerLevel level, ServerPlayer p, String key, int ticks, String witness, float fear) {
        Fixtures.setDevice(level, key, true);
        later(ticks, () -> Fixtures.setDevice(level, key, false));
        fear(p, fear);
        if (Layout.has(key)) witnessNear(p, witness, Layout.marker(key).center(), 18);
        else witness(p, witness);
    }

    /** 2:00 — far away a motorbike, tyres screaming, an impact, silence. Then the door opens. */
    public static void crashCue(ServerLevel level) {
        Vec3 far = new Vec3(110, Layout.FEET_Y + 2, 54);
        sound(level, far, ModSounds.FIXTURE_MOTORBIKE.get(), 4.0f);
        later(20 * 8, () -> sound(level, new Vec3(76, Layout.FEET_Y + 2, 52), ModSounds.FIXTURE_CRASH.get(), 5.0f));
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            later(20 * 9, () -> {
                GameManager.addFear(p, 0.22f);
                witness(p, "crash");
            });
        }
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent e, float vol) {
        level.playSound(null, at.x, at.y, at.z, e, SoundSource.AMBIENT, vol, 0.95f + level.random.nextFloat() * 0.1f);
    }

    private static void fear(ServerPlayer p, float f) {
        GameManager.addFear(p, f);
    }

    private static void witnessNear(ServerPlayer p, String what, Vec3 at, double range) {
        if (p.position().distanceToSqr(at) < range * range) witness(p, what);
    }

    @Nullable
    private static BlockPos pick(ServerLevel level, ServerPlayer p, String... markers) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (String m : markers) {
            if (!Layout.has(m)) continue;
            for (Layout.Marker mk : Layout.markers(m)) {
                double d = mk.pos().distSqr(p.blockPosition());
                if (d < bd) {
                    bd = d;
                    best = mk.pos();
                }
            }
        }
        return best;
    }

    /** A table between min and max blocks away, preferring ones the player is not looking at. */
    private static int nearestTable(Vec3 pp, double min, double max) {
        List<Integer> ok = new ArrayList<>();
        for (int n = 1; n <= 17; n++) {
            if (!Layout.has("table_" + n)) continue;
            double d = Layout.marker("table_" + n).center().distanceTo(pp);
            if (d >= min && d <= max) ok.add(n);
        }
        return ok.isEmpty() ? -1 : ok.get(RAND.nextInt(ok.size()));
    }

    // ================================================================================================ hooks
    public static void onEnterRoom(ServerLevel level, ServerPlayer p, String room) {
        if (room.equals("staff") && GameData.get(p.server).once("tv_first")) later(20 * 6, () -> run(level, p, "tv"));
    }

    public static void onNoticed(ServerLevel level, ServerPlayer p, Spirit s) {
        nextMinor = Math.max(nextMinor, clock + 20 * 20);
        nextBig = Math.max(nextBig, clock + 20 * 30);
    }
}
