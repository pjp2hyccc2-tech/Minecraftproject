package com.reisatsu.game;

import com.reisatsu.block.Props;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.registry.ModBlocks;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The 霊障 (docs/DESIGN.md §1 Flow, §3.12): what the restaurant does by itself, and what the player witnesses of it.
 * <p>
 * <b>Night 1</b> stages the poltergeist around the player at a steady, readable pace — one every 25–45 s, never two
 * big ones within 15 s, chosen near the player's area and sometimes behind them, preferring kinds not seen yet —
 * until five kinds are witnessed (then slower). <b>Nights 2–7</b> play mostly the signs of the night's spirit (it
 * shows once two of them were witnessed, {@link Nights}) and now and then a quiet general one; nothing on top of a
 * chase, nothing during the medium's scenes.
 * <p>
 * <b>Witnessing</b>: a phenomenon counts when it happens within 18 blocks of a player who is in the same room or has a
 * line of sight to it (sound alone in the same room counts). Lasting ones (taps running, a glass on a table, the turned
 * bear, footprints, the scattered menus...) count while they last. Each kind counts once; the report note's list ticks
 * every kind it names.
 */
public final class Director {
    private static final RandomSource RAND = RandomSource.create();
    public static final double WITNESS_RANGE = 18.0;
    /** Ids of every event, for /reisatsu debug event. */
    public static final String[] EVENTS = {"door", "taps", "menus", "call", "chair", "flicker", "burst", "trip", "music", "pa", "phone",
            "tv", "dryer", "drink", "plate", "knock_fridge", "knock_stall", "bear", "glass", "prints", "board", "car", "crash",
            "greet", "tray", "giggle", "ball", "sob", "taps_w", "gas", "chop", "keys", "camera"};
    private static final Set<String> BIG = Set.of("door", "menus", "music", "phone", "burst", "plate", "prints", "trip", "crash", "chop");
    /** The events of the first night (each makes one of {@link Week#OBSERVE}). */
    private static final String[] OBSERVE_EVENTS = {"door", "taps", "menus", "call", "chair", "flicker", "burst", "music", "pa", "phone",
            "tv", "dryer", "drink", "plate", "knock", "bear", "glass", "prints"};
    private static final Vec3 DOOR_IN = new Vec3(4.0, Layout.FEET_Y + 1.0, 29.4), DOOR_OUT = new Vec3(4.0, Layout.FEET_Y + 1.0, 31.6);
    private static final int[][] KITCHEN_FLOOR = {{21, 3}, {25, 4}, {29, 3}, {33, 5}, {27, 7}, {22, 7}, {31, 8}, {36, 6}};

    private static long clock;
    /** The night's next staged event and the next quiet general one (-1: not scheduled yet). */
    private static long next = -1, nextQuiet = -1;
    private static long lastBig = -20 * 60;
    private static String lastKind = "";
    private static final List<long[]> PENDING = new ArrayList<>();
    private static final List<Runnable> PENDING_RUN = new ArrayList<>();
    private static final List<Phenomenon> ACTIVE = new ArrayList<>();
    /** Until when the phone rings (the world clears its RINGING state before telling us it was picked up). */
    private static long phoneUntil = -1;
    /** The chopping at the chef's board (night 6): until when, and the next stroke loop. */
    private static long chopUntil = -1, chopNext;
    /** Self test: events staged so far tonight (time, id). */
    public static final List<String> LOG = new ArrayList<>();
    /** Self test: nothing is staged (witnessing and delayed parts still run). */
    public static boolean paused;

    /** Something happening (or going on until {@code until}) at one of these points; inside = heard anywhere inside. */
    private record Phenomenon(String kind, List<Vec3> at, long until, boolean inside) {}

    /** A place an event can happen: the points the witness rule looks at, and what the event needs there. */
    private record Spot(List<Vec3> points, int table, @Nullable BlockPos pos, String key) {
        static Spot of(Vec3 p) {
            return new Spot(List.of(p), 0, null, "");
        }

        Vec3 main() {
            return points.get(0);
        }
    }

    private Director() {}

    public static void reset() {
        clock = 0;
        next = -1;
        nextQuiet = -1;
        lastBig = -20 * 60;
        lastKind = "";
        phoneUntil = -1;
        chopUntil = -1;
        PENDING.clear();
        PENDING_RUN.clear();
        ACTIVE.clear();
        LOG.clear();
    }

    public static void clearFor(ServerPlayer p) {
    }

    public static long clock() {
        return clock;
    }

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

    // ================================================================================================ witnessing
    /** The room a position is in, at feet level ("outside" for none). */
    public static String roomOf(Vec3 at) {
        Layout.Room r = Layout.roomAt(BlockPos.containing(at.x, Layout.FEET_Y, at.z));
        return r == null ? "outside" : r.key();
    }

    /** §1: within 18 blocks, and in the same room or in sight (sound alone counts in the same room). */
    public static boolean canWitness(ServerPlayer p, Vec3 at) {
        Vec3 eye = p.getEyePosition();
        if (eye.distanceToSqr(at) > WITNESS_RANGE * WITNESS_RANGE) return false;
        if (roomOf(p.position()).equals(roomOf(at))) return true;
        return Sight.clear(p.level(), eye, at);
    }

    private static boolean witnessable(ServerPlayer p, Spot s) {
        for (Vec3 v : s.points()) if (canWitness(p, v)) return true;
        return false;
    }

    private static boolean witnesses(ServerPlayer p, Phenomenon ph) {
        if (ph.inside()) return !roomOf(p.position()).equals("outside");
        for (Vec3 v : ph.at()) if (canWitness(p, v)) return true;
        return false;
    }

    /** A phenomenon at these points, lasting {@code ticks}: whoever is in range now (or comes in range meanwhile) sees it. */
    public static void happen(ServerLevel level, String kind, int ticks, Vec3... at) {
        happen(level, kind, ticks, List.of(at), false);
    }

    public static void happen(ServerLevel level, String kind, int ticks, List<Vec3> at) {
        happen(level, kind, ticks, at, false);
    }

    /** A phenomenon heard anywhere inside the building (the PA, the store speakers). */
    public static void happenInside(ServerLevel level, String kind, int ticks) {
        happen(level, kind, ticks, List.of(Vec3.ZERO), true);
    }

    private static void happen(ServerLevel level, String kind, int ticks, List<Vec3> at, boolean inside) {
        if (at.isEmpty()) return;
        long until = clock + Math.max(1, ticks);
        Phenomenon ph = new Phenomenon(kind, List.copyOf(at), until, inside);
        boolean merged = false;
        for (int i = 0; i < ACTIVE.size(); i++) {
            Phenomenon o = ACTIVE.get(i);
            if (o.kind().equals(kind) && o.inside() == inside && o.at().get(0).distanceToSqr(at.get(0)) < 4.0) {
                ACTIVE.set(i, new Phenomenon(kind, o.at(), Math.max(o.until(), until), inside));
                merged = true;
                break;
            }
        }
        if (!merged) ACTIVE.add(ph);
        for (ServerPlayer p : GameManager.players(level)) {
            if (watching(p) && witnesses(p, ph)) record(p, kind);
        }
    }

    /** Can this player witness anything now (in the night, not taken, not in a scene)? */
    private static boolean watching(ServerPlayer p) {
        if (!GameManager.inGame(p)) return false;
        PlayerState ps = GameManager.state(p);
        return !ps.caught() && !ps.locked && ps.introTicks < 0 && ps.endingTicks < 0;
    }

    private static void checkWitnesses(ServerLevel level) {
        ACTIVE.removeIf(ph -> ph.until() < clock);
        if (ACTIVE.isEmpty()) return;
        for (ServerPlayer p : GameManager.players(level)) {
            if (!watching(p)) continue;
            for (Phenomenon ph : ACTIVE) if (witnesses(p, ph)) record(p, ph.kind());
        }
    }

    /** The player saw (or heard) this: the report's list, and tonight's count (night 1) or signs (nights 2–7). */
    public static void record(ServerPlayer p, String kind) {
        if (!GameManager.inGame(p)) return;
        GameData data = GameData.get(p.server);
        if (isPhenomenon(kind) && data.witnessed.add(kind)) data.setDirty();
        if (!counts(data, kind) || !data.signs.add(kind)) return;
        data.setDirty();
        GameManager.event(p, GameManager.EV_WITNESS, data.signs.size(), kind);
        Nights.onSign(p.serverLevel(), p, data, kind);
    }

    /** Old name of {@link #record}. */
    public static void witness(ServerPlayer p, String what) {
        record(p, what);
    }

    private static boolean isPhenomenon(String kind) {
        for (String k : Notes.PHENOMENA) if (k.equals(kind)) return true;
        return false;
    }

    /** Does this kind count tonight? */
    public static boolean counts(GameData data, String kind) {
        if (!Nights.nightPhase(data)) return false;
        if (data.night == 1) return (data.nightStage == GameData.NS_WORK || data.nightStage == GameData.NS_RETURN) && Week.observes(kind);
        return (data.nightStage == GameData.NS_WORK || data.nightStage == GameData.NS_HUNT) && Week.isSign(Week.spirit(data.night), kind);
    }

    // ================================================================================================ pacing
    public static void tick(ServerLevel level, GameData data) {
        clock++;
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
        if (clock % 5 == 0) checkWitnesses(level);
        tickChop(level);
        if (paused || data.nightStage == GameData.NS_OPENING || data.nightStage == GameData.NS_ENDING) return;
        ServerPlayer p = focus(level);
        if (p == null) return;
        PlayerState ps = GameManager.state(p);
        // nothing on top of a chase
        if (ps.danger > 0.35f) {
            if (next >= 0) next = Math.max(next, clock + 20 * 8);
            if (nextQuiet >= 0) nextQuiet = Math.max(nextQuiet, clock + 20 * 8);
            return;
        }
        if (data.night == 1) observe(level, p, ps, data);
        else signs(level, p, ps, data);
    }

    @Nullable
    private static ServerPlayer focus(ServerLevel level) {
        for (ServerPlayer p : GameManager.players(level)) if (GameManager.huntable(p)) return p;
        return null;
    }

    private static boolean inside(PlayerState ps) {
        return !ps.room.isEmpty() && !ps.room.equals("outside");
    }

    /** The first night: one phenomenon every 25–45 s around the player (40–70 s once five were seen). */
    private static void observe(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        if (next < 0) {
            next = clock + 20 * (12 + RAND.nextInt(7));
            return;
        }
        if (clock < next) return;
        boolean done = data.signs.size() >= Week.OBSERVE_GOAL;
        if (!inside(ps)) {
            // still in the lot: the door may open for nobody as you come near it
            Spot door = pick(p, spots(level, p, "door"), true);
            if (door != null && !done && RAND.nextInt(2) == 0) {
                stage(level, p, "door", door);
                next = clock + 20 * (25 + RAND.nextInt(21));
            } else {
                next = clock + 20 * 3;
            }
            return;
        }
        boolean bigOk = clock - lastBig >= 20 * 15;
        List<String> ids = new ArrayList<>();
        List<Spot> where = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (String id : OBSERVE_EVENTS) {
            String kind = kindOf(id);
            if (kind.equals(lastKind) || (!bigOk && BIG.contains(id))) continue;
            if (id.equals("burst") && RAND.nextInt(3) != 0) continue; // a tube bursting is rarer than a flicker
            Spot s = pick(p, spots(level, p, id), true);
            if (s == null) continue;
            ids.add(id);
            where.add(s);
            weights.add(data.signs.contains(kind) ? 1 : 5);
        }
        if (!ids.isEmpty()) {
            int total = 0;
            for (int w : weights) total += w;
            int r = RAND.nextInt(total);
            int i = 0;
            while (r >= weights.get(i)) r -= weights.get(i++);
            stage(level, p, ids.get(i), where.get(i));
        }
        next = clock + 20L * (done ? 40 + RAND.nextInt(31) : 25 + RAND.nextInt(21));
    }

    /** Nights 2–7: the signs of the night's spirit, a few quiet general ones. */
    private static void signs(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data) {
        SpiritKind k = Week.spirit(data.night);
        if (k == null || !data.entered()) return;
        long since = data.insideTicks();
        if (next < 0) next = clock + 20 * (14 + RAND.nextInt(10));
        if (nextQuiet < 0) nextQuiet = clock + 20 * (45 + RAND.nextInt(30));
        if (k == SpiritKind.VISITOR) {
            // before two o'clock: the phone, or table 7's call button, once (the cue and his arrival are his own)
            if (data.nightStage == GameData.NS_WORK && since > 20 * 45 && data.tonight("foreshadow")) {
                if (RAND.nextBoolean()) run(level, p, "phone");
                else stage(level, p, "call", new Spot(List.of(button(7), callDisplay()), 7, null, ""));
            }
        } else if (data.nightStage == GameData.NS_WORK) {
            // before it shows: a sign every 20–32 s (a few seconds later if none could happen)
            if (clock >= next) next = clock + 20L * (sign(level, p, data, k, false) ? 20 + RAND.nextInt(13) : 4);
        } else if (data.nightStage == GameData.NS_HUNT) {
            // once it is there its own routine speaks; the director only now and then
            if (clock >= next) {
                if (RAND.nextInt(2) == 0) sign(level, p, data, k, true);
                next = clock + 20L * (45 + RAND.nextInt(40));
            }
        }
        if (clock >= nextQuiet && data.nightStage != GameData.NS_RETURN) {
            quiet(level, p, ps, k);
            nextQuiet = clock + 20 * (60 + RAND.nextInt(60));
        }
    }

    /**
     * The events that announce a spirit (the director's part; the spirit's own routine makes more). Once it is there,
     * only those that do not cross its routine (its taps, its stove, its board, its cameras are its own).
     */
    private static String[] signEvents(SpiritKind k, boolean shown) {
        return switch (k) {
            case WAITRESS -> new String[]{"glass", "greet", "tray"};
            case CHILD -> new String[]{"menus", "giggle", "ball", "bear"};
            case MIRROR_WOMAN -> shown ? new String[]{"knock_stall"} : new String[]{"taps_w", "sob", "knock_stall"};
            case CHEF -> shown ? new String[]{"plate"} : new String[]{"plate", "gas", "chop"};
            case MANAGER -> shown ? new String[]{"keys"} : new String[]{"camera", "keys"};
            default -> new String[0];
        };
    }

    /** One sign of the night's spirit: where the player can witness it, else (a lead) the nearest place it can happen. */
    private static boolean sign(ServerLevel level, ServerPlayer p, GameData data, SpiritKind k, boolean shown) {
        if (k == SpiritKind.MANAGER && !data.happenedTonight("closing")) return false;
        // not seen yet first, the same kind twice in a row last
        List<String> fresh = new ArrayList<>(), seen = new ArrayList<>(), again = new ArrayList<>();
        for (String id : signEvents(k, shown)) {
            String kind = kindOf(id);
            (kind.equals(lastKind) ? again : data.signs.contains(kind) ? seen : fresh).add(id);
        }
        java.util.Collections.shuffle(fresh, new java.util.Random(RAND.nextLong()));
        java.util.Collections.shuffle(seen, new java.util.Random(RAND.nextLong()));
        List<String> order = new ArrayList<>(fresh);
        order.addAll(seen);
        order.addAll(again);
        for (String id : order) {
            Spot s = pick(p, spots(level, p, id), true);
            if (s != null) return stage(level, p, id, s);
        }
        // nothing in reach: something the player can hear from where they are, in the spirit's place
        for (String id : order) {
            Spot s = nearest(p, spots(level, p, id));
            if (s != null && !id.equals("giggle")) return stage(level, p, id, s);
        }
        return false;
    }

    /** A quiet general one (not one of tonight's signs). */
    private static void quiet(ServerLevel level, ServerPlayer p, PlayerState ps, SpiritKind k) {
        int r = RAND.nextInt(4);
        if (r == 0 && inside(ps) && !Week.isSign(k, "lights")) {
            Circuit c = Circuit.ofRoom(ps.room);
            if (c != null && Fixtures.circuitOn(level, c)) {
                Spot s = pick(p, spots(level, p, "flicker"), true);
                if (s != null) {
                    stage(level, p, "flicker", s);
                    return;
                }
            }
        }
        if (r == 1 && inside(ps)) {
            Spot s = pick(p, spots(level, p, "chair"), true);
            if (s != null) {
                stage(level, p, "chair", s);
                return;
            }
        }
        run(level, p, "car");
    }

    // ================================================================================================ where
    /** Every place an event could happen for this player now (not filtered by the witness rule). */
    private static List<Spot> spots(ServerLevel level, ServerPlayer p, String id) {
        List<Spot> out = new ArrayList<>();
        PlayerState ps = GameManager.state(p);
        Vec3 eye = p.getEyePosition();
        switch (id) {
            case "door" -> {
                if (!Fixtures.isAutoDoorOpen(level, "outer")) out.add(new Spot(List.of(DOOR_IN, DOOR_OUT), 0, null, "outer"));
            }
            case "taps", "taps_w" -> {
                for (String m : id.equals("taps_w") ? new String[]{"sink_w"} : new String[]{"sink_kitchen", "sink_m", "sink_w"}) {
                    if (!Layout.has(m)) continue;
                    List<Vec3> pts = new ArrayList<>();
                    boolean running = false;
                    for (Layout.Marker mk : Layout.markers(m)) {
                        pts.add(Vec3.atCenterOf(mk.pos()).add(0, 0.6, 0));
                        running |= Fixtures.isTapRunning(level, mk.pos());
                    }
                    if (!running && !pts.isEmpty()) out.add(new Spot(pts, 0, Layout.marker(m).pos(), m));
                }
            }
            case "menus" -> {
                for (int n = 1; n <= 17; n++) {
                    if (level.getBlockState(Fixtures.menuSlot(n)).is(ModBlocks.MENU_STAND.get())) out.add(new Spot(List.of(table(n)), n, null, ""));
                }
            }
            case "glass" -> {
                for (int n = 1; n <= 17; n++) {
                    BlockState s = level.getBlockState(Fixtures.menuSlot(n));
                    if (s.is(ModBlocks.MENU_STAND.get()) || (s.is(ModBlocks.WATER_GLASS.get()) && s.getValue(Props.GLASSES) < 4)) {
                        out.add(new Spot(List.of(Vec3.atCenterOf(Fixtures.menuSlot(n))), n, null, ""));
                    }
                }
            }
            case "call" -> {
                for (int n = 1; n <= 17; n++) out.add(new Spot(List.of(button(n), callDisplay()), n, null, ""));
            }
            case "chair" -> {
                for (BlockPos c : Fixtures.chairsNear(level, p.blockPosition(), 12)) {
                    if (c.distSqr(p.blockPosition()) > 9) out.add(new Spot(List.of(Vec3.atCenterOf(c)), 0, c, ""));
                }
            }
            case "flicker", "burst", "trip" -> {
                if (inside(ps) && !Fixtures.lightsIn(level, ps.room).isEmpty()) {
                    Circuit c = Circuit.ofRoom(ps.room);
                    boolean on = c != null && Fixtures.circuitOn(level, c);
                    if ((!id.equals("trip") || on) && (!id.equals("burst") || on)) out.add(new Spot(List.of(eye), 0, null, ps.room));
                }
            }
            case "music", "pa", "crash" -> {
                if (inside(ps) || id.equals("crash")) out.add(new Spot(List.of(eye), 0, null, "inside"));
            }
            case "phone" -> {
                if (clock >= phoneUntil) out.add(Spot.of(Vec3.atCenterOf(Layout.marker("phone").pos())));
            }
            case "tv" -> out.add(new Spot(List.of(Vec3.atCenterOf(Layout.marker("tv").pos()).add(0, 0.5, 0)), 0, null, "tv"));
            case "dryer" -> {
                out.add(new Spot(List.of(Vec3.atCenterOf(Layout.marker("hand_dryer_w").pos())), 0, null, "hand_dryer_w"));
                out.add(new Spot(List.of(Vec3.atCenterOf(Layout.marker("hand_dryer_m").pos())), 0, null, "hand_dryer_m"));
            }
            case "drink" -> {
                List<Vec3> pts = new ArrayList<>();
                for (Layout.Marker m : Layout.markers("drink_bar")) pts.add(Vec3.atCenterOf(m.pos()));
                out.add(new Spot(pts, 0, null, "drink_bar"));
            }
            case "plate" -> {
                for (int[] f : KITCHEN_FLOOR) out.add(new Spot(List.of(new Vec3(f[0] + 0.5, Layout.FEET_Y + 0.3, f[1] + 0.5)), 0,
                        new BlockPos(f[0], Layout.FEET_Y, f[1]), ""));
            }
            case "knock", "knock_fridge", "knock_stall" -> {
                if (!id.equals("knock_stall")) {
                    Vec3 d = Layout.marker("fridge_door").center();
                    out.add(new Spot(List.of(new Vec3(d.x, d.y + 1.2, d.z - 0.6), new Vec3(d.x, d.y + 1.2, d.z + 0.7)), 0, null, "fridge"));
                }
                if (!id.equals("knock_fridge")) out.add(new Spot(List.of(Layout.marker("stall_occupied").center().add(0, 1.0, 0)), 0, null, "stall"));
            }
            case "bear" -> out.add(Spot.of(Layout.marker("stuffed_bear").center().add(0, 0.4, 0)));
            case "prints" -> {
                List<Layout.Marker> path = Layout.markers("visitor_path");
                List<Vec3> pts = new ArrayList<>();
                for (int i = Math.min(2, path.size() - 1); i < path.size(); i++) pts.add(path.get(i).center().add(0, 0.2, 0));
                out.add(new Spot(pts, 0, null, ""));
            }
            case "board" -> out.add(Spot.of(Layout.marker("menu_board").center().add(0, 0.8, 0)));
            case "car" -> out.add(Spot.of(new Vec3(p.getX() + (RAND.nextBoolean() ? -40 : 40), Layout.FEET_Y + 1, 54)));
            // ---- the signs of the spirits
            case "greet", "tray" -> {
                List<Vec3> cands = new ArrayList<>();
                for (Layout.Marker m : Layout.markers("waitress_route")) cands.add(m.center().add(0, 1.4, 0));
                if (id.equals("tray")) cands.add(Layout.marker("waitress_station").center().add(0, 1.1, 0));
                for (Vec3 c : cands) {
                    double d = c.distanceTo(eye);
                    if (d >= 4.0) out.add(Spot.of(c));
                }
            }
            case "giggle" -> {
                if (inside(ps)) {
                    Vec3 back = p.getViewVector(1.0f).multiply(1, 0, 1).normalize().scale(-1.8);
                    out.add(new Spot(List.of(eye.add(back).add(0, -0.5, 0)), 0, null, "behind"));
                }
            }
            case "ball" -> out.add(Spot.of(new Vec3(11.5, Layout.FEET_Y + 0.3, 28.2)));
            case "sob" -> out.add(Spot.of(new Vec3(10.5, Layout.FEET_Y + 1.5, 17.3)));
            case "gas" -> {
                if (!Fixtures.deviceOn(level, "gas_range")) {
                    List<Vec3> pts = new ArrayList<>();
                    for (Layout.Marker m : Layout.markers("gas_range")) pts.add(Vec3.atCenterOf(m.pos()).add(0, 0.7, 0));
                    out.add(new Spot(pts, 0, null, ""));
                }
            }
            case "chop" -> {
                Vec3 board = Layout.marker("chef_board").center();
                // it stops when you come near: it does not start with you already there
                if (chopUntil < clock && horizontal(p.position(), board) > 9.0) out.add(Spot.of(board.add(0, 1.2, 0)));
            }
            case "keys" -> {
                out.add(Spot.of(new Vec3(6.5, Layout.FEET_Y + 1.0, 8.5)));
                out.add(Spot.of(new Vec3(11.5, Layout.FEET_Y + 1.0, 8.5)));
            }
            case "camera" -> {
                if (Fixtures.circuitOn(level, Circuit.CAMERAS)) {
                    for (Layout.Marker m : Layout.markers("cameras")) {
                        Vec3 c = new Vec3(m.pos().getX() + 0.5, m.pos().getY() - 0.4, m.pos().getZ() + 0.5);
                        if (c.distanceTo(eye) < 14.0) out.add(new Spot(List.of(c), 0, m.pos(), ""));
                    }
                }
            }
            default -> {
            }
        }
        return out;
    }

    /**
     * A spot the player can witness (or any, when {@code witness} is false): near the player's area, and about one
     * time in three behind them when there is one.
     */
    @Nullable
    private static Spot pick(ServerPlayer p, List<Spot> spots, boolean witness) {
        List<Spot> ok = new ArrayList<>();
        for (Spot s : spots) if (!witness || witnessable(p, s)) ok.add(s);
        if (ok.isEmpty()) return null;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);
        List<Spot> behind = new ArrayList<>(), near = new ArrayList<>();
        for (Spot s : ok) {
            Vec3 to = s.main().subtract(eye);
            double d = to.length();
            if (d > 1.0 && to.scale(1.0 / d).dot(look) < -0.2) behind.add(s);
            if (d <= 12.0) near.add(s);
        }
        if (!behind.isEmpty() && RAND.nextInt(3) == 0) return behind.get(RAND.nextInt(behind.size()));
        List<Spot> from = near.isEmpty() ? ok : near;
        return from.get(RAND.nextInt(from.size()));
    }

    @Nullable
    private static Spot nearest(ServerPlayer p, List<Spot> spots) {
        Spot best = null;
        double bd = Double.MAX_VALUE;
        for (Spot s : spots) {
            double d = s.main().distanceToSqr(p.getEyePosition());
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    private static Vec3 table(int n) {
        return Layout.marker("table_" + n).center().add(0, 1.0, 0);
    }

    private static Vec3 button(int n) {
        return Vec3.atCenterOf(Layout.marker("button_" + n).pos());
    }

    private static Vec3 callDisplay() {
        return Vec3.atCenterOf(Layout.marker("call_display").pos()).add(0, -0.6, 0);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The witness key an event makes. */
    public static String kindOf(String id) {
        return switch (id) {
            case "flicker", "burst", "trip" -> "lights";
            case "knock_fridge", "knock_stall" -> "knock";
            case "taps_w" -> "taps";
            default -> id;
        };
    }

    // ================================================================================================ the events
    /** Runs one event near the player (debug, the scripts); false if it cannot happen now. */
    public static boolean run(ServerLevel level, ServerPlayer p, String id) {
        List<Spot> all = spots(level, p, id);
        Spot s = pick(p, all, true);
        if (s == null) s = nearest(p, all);
        if (s == null) return false;
        return stage(level, p, id, s);
    }

    private static boolean stage(ServerLevel level, ServerPlayer p, String id, Spot s) {
        boolean ok = play(level, p, id, s);
        if (!ok) return false;
        lastKind = kindOf(id);
        if (BIG.contains(id)) lastBig = clock;
        LOG.add(clock + " " + id);
        if (LOG.size() > 64) LOG.remove(0);
        return true;
    }

    private static boolean play(ServerLevel level, ServerPlayer p, String id, Spot s) {
        Vec3 pp = p.position();
        switch (id) {
            case "door" -> {
                Fixtures.openAutoDoor(level, "outer", 60);
                later(40, () -> {
                    if (level.random.nextBoolean()) Fixtures.openAutoDoor(level, "inner", 50);
                });
                fear(p, 0.12f);
                happen(level, "door", 60, s.points());
            }
            case "taps", "taps_w" -> {
                List<BlockPos> taps = new ArrayList<>();
                for (Layout.Marker m : Layout.markers(s.key())) taps.add(m.pos());
                for (BlockPos t : taps) Fixtures.setTap(level, t, true);
                int ticks = id.equals("taps_w") ? 20 * 45 : 20 * 14;
                later(ticks, () -> {
                    // the woman in the mirror keeps her own taps running once she is there
                    if (s.key().equals("sink_w") && Spirits.get(level, SpiritKind.MIRROR_WOMAN) != null) return;
                    for (BlockPos t : taps) Fixtures.setTap(level, t, false);
                });
                fear(p, 0.06f);
                happen(level, "taps", ticks, s.points());
            }
            case "menus" -> {
                Fixtures.scatterMenus(level, s.table());
                fear(p, 0.16f);
                happen(level, "menus", 20 * 30, s.points());
            }
            case "call" -> {
                Fixtures.ringCallButton(level, s.table());
                fear(p, 0.06f);
                happen(level, "call", 40, s.points());
            }
            case "chair" -> {
                if (s.pos() == null) return false;
                Fixtures.knockChair(level, s.pos(), RAND.nextInt(3) == 0);
                NoiseSystem.emit(level, Vec3.atCenterOf(s.pos()), 10.0f, "chair", null);
                fear(p, 0.07f);
                happen(level, "chair", 40, s.points());
            }
            case "flicker" -> {
                String room = s.key().isEmpty() ? GameManager.state(p).room : s.key();
                Circuit c = Circuit.ofRoom(room);
                if (c != null && Fixtures.circuitOn(level, c)) Fixtures.flickerRoom(level, room, 30 + RAND.nextInt(40));
                else stutterLights(level, room);
                fear(p, 0.06f);
                happen(level, "lights", 50, lightPoints(level, room, s));
            }
            case "burst" -> {
                List<BlockPos> lights = Fixtures.lightsIn(level, s.key().isEmpty() ? GameManager.state(p).room : s.key());
                if (lights.isEmpty()) return false;
                BlockPos l = lights.get(RAND.nextInt(lights.size()));
                Fixtures.burstLight(level, l);
                fear(p, 0.14f);
                happen(level, "lights", 40, Vec3.atCenterOf(l).add(0, -0.8, 0));
            }
            case "trip" -> {
                Circuit[] cs = {Circuit.DINING, Circuit.KITCHEN, Circuit.RESTROOM, Circuit.BACK};
                Circuit c = Circuit.ofRoom(GameManager.state(p).room);
                if (c == null || !Fixtures.circuitOn(level, c)) c = cs[RAND.nextInt(cs.length)];
                if (!Fixtures.circuitOn(level, c)) return false;
                Breaker.switchCircuit(level, c, false, null);
                fear(p, 0.14f);
                happenInside(level, "lights", 20);
            }
            case "music" -> {
                Fixtures.setDevice(level, "store_music", true);
                later(20 * 28, () -> {
                    if (GameData.get(p.server).phase != GameData.PHASE_CLOSING) Fixtures.setDevice(level, "store_music", false);
                });
                fear(p, 0.12f);
                happenInside(level, "music", 20 * 28);
            }
            case "pa" -> {
                GameManager.privateSound(p, ModSounds.FIXTURE_PA_CHIME.get(), pp.add(0, 3, 0), 0.8f, 0.94f);
                later(40, () -> {
                    GameManager.privateSound(p, ModSounds.FIXTURE_PA_VOICE.get(), p.position().add(0, 3, 0), 0.8f, 0.9f);
                    Story.say(p, "pa", 70, "dialogue.reisatsu.pa.mumble");
                });
                fear(p, 0.08f);
                happenInside(level, "pa", 60);
            }
            case "phone" -> {
                Fixtures.setDevice(level, "phone", true);
                phoneUntil = clock + 20 * 14;
                NoiseSystem.emit(level, Layout.marker("phone").center(), 12.0f, "phone", null);
                later(20 * 14, () -> Fixtures.setDevice(level, "phone", false));
                fear(p, 0.1f);
                happen(level, "phone", 20 * 14, s.points());
            }
            case "tv" -> device(level, p, "tv", 20 * 16, "tv", 0.07f, s);
            case "dryer" -> device(level, p, s.key().isEmpty() ? "hand_dryer_w" : s.key(), 20 * 5, "dryer", 0.1f, s);
            case "drink" -> device(level, p, "drink_bar", 20 * 6, "drink", 0.06f, s);
            case "plate" -> {
                BlockPos at = s.pos() != null ? s.pos() : BlockPos.containing(s.main());
                Fixtures.smashPlate(level, at);
                NoiseSystem.emit(level, Vec3.atCenterOf(at), 16.0f, "plate", null);
                fear(p, 0.14f);
                happen(level, "plate", 20 * 20, s.points());
            }
            case "knock", "knock_fridge", "knock_stall" -> {
                boolean fridge = s.key().equals("fridge");
                Vec3 at = fridge ? Layout.marker("fridge_door").center().add(0, 1.2, 0) : s.main();
                SoundEvent e = fridge ? ModSounds.FIXTURE_FRIDGE_KNOCK.get() : ModSounds.FIXTURE_STALL_KNOCK.get();
                for (int i = 0; i < 3; i++) later(i * (fridge ? 14 : 12), () -> sound(level, at, e, 1.1f));
                fear(p, 0.08f);
                happen(level, "knock", 50, s.points());
            }
            case "bear" -> {
                Vec3 bear = Layout.marker("stuffed_bear").center();
                double dx = pp.x - bear.x, dz = pp.z - bear.z;
                Direction d = Direction.fromYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f);
                Fixtures.turnBear(level, d);
                sound(level, bear, ModSounds.FIXTURE_TOY_SQUEAK.get(), 0.25f);
                fear(p, 0.05f);
                happen(level, "bear", 20 * 40, s.points());
            }
            case "glass" -> {
                Fixtures.placeWaterGlass(level, s.table());
                fear(p, 0.05f);
                happen(level, "glass", 20 * 40, s.points());
            }
            case "prints" -> {
                List<Layout.Marker> path = Layout.markers("visitor_path");
                int start = Math.max(0, path.size() / 3);
                int n = 0;
                for (int i = start; i < path.size() - 1; i++) {
                    Vec3 a = path.get(i).center(), b = path.get(i + 1).center();
                    double len = a.distanceTo(b);
                    float yaw = (float) (Mth.atan2(b.z - a.z, b.x - a.x) * Mth.RAD_TO_DEG) - 90.0f;
                    for (double st = 0; st < len; st += 0.9) {
                        Vec3 at = a.add(b.subtract(a).scale(st / Math.max(0.01, len)));
                        final int k = n++;
                        later(k * 3, () -> Fixtures.wetFootprint(level, at.add((k % 2 == 0 ? 0.15 : -0.15), 0.01, 0), yaw));
                    }
                }
                fear(p, 0.1f);
                happen(level, "prints", 20 * 60, s.points());
            }
            case "board" -> {
                if (!GameData.get(p.server).once("menu_board")) return false;
                Fixtures.setMenuBoard(level, 1);
                happen(level, "board", 20 * 120, s.points());
            }
            case "car" -> sound(level, s.main(), ModSounds.FIXTURE_CAR_PASS.get(), 1.0f);
            case "crash" -> crashCue(level);
            // ---- the signs of the spirits
            case "greet" -> {
                Vec3 at = s.main();
                level.playSound(null, at.x, at.y, at.z, ModSounds.WAITRESS_GREET.get(), SoundSource.HOSTILE, 1.0f, 0.94f + RAND.nextFloat() * 0.08f);
                if (at.distanceTo(p.getEyePosition()) < 20.0) Story.say(p, "waitress", 50, "dialogue.reisatsu.waitress.greet");
                fear(p, 0.1f);
                happen(level, "greet", 40, at);
            }
            case "tray" -> {
                sound(level, s.main(), ModSounds.WAITRESS_TRAY.get(), 1.0f);
                fear(p, 0.08f);
                happen(level, "tray", 30, s.main());
            }
            case "giggle" -> {
                GameManager.privateSound(p, ModSounds.CHILD_GIGGLE.get(), s.main(), 0.8f, 1.0f + RAND.nextFloat() * 0.1f);
                fear(p, 0.1f);
                if (inside(GameManager.state(p))) record(p, "giggle");
            }
            case "ball" -> {
                sound(level, s.main(), RAND.nextInt(3) == 0 ? ModSounds.FIXTURE_TOY_SQUEAK.get() : ModSounds.FIXTURE_BALL_BOUNCE.get(), 1.0f);
                fear(p, 0.07f);
                happen(level, "ball", 40, s.main());
            }
            case "sob" -> {
                Vec3 glass = new Vec3(10.5, Layout.FEET_Y + 1.5, 16.4);
                sound(level, glass, ModSounds.MIRROR_SOB.get(), 1.4f);
                later(70, () -> sound(level, glass, ModSounds.MIRROR_SOB.get(), 1.4f));
                fear(p, 0.08f);
                happen(level, "sob", 110, s.main());
            }
            case "gas" -> {
                Fixtures.setDevice(level, "gas_range", true);
                later(20 * 8, () -> {
                    if (Spirits.get(level, SpiritKind.CHEF) == null) Fixtures.setDevice(level, "gas_range", false);
                });
                fear(p, 0.1f);
                happen(level, "gas", 20 * 8, s.points());
            }
            case "chop" -> {
                chopUntil = clock + 20 * 18;
                chopNext = clock;
                happen(level, "chop", 20 * 18, s.points());
            }
            case "keys" -> {
                sound(level, s.main(), ModSounds.MANAGER_KEYS.get(), 1.0f);
                later(14, () -> sound(level, s.main(), ModSounds.MANAGER_STEP.get(), 0.7f));
                later(30, () -> sound(level, s.main(), ModSounds.MANAGER_STEP.get(), 0.6f));
                fear(p, 0.07f);
                happen(level, "keys", 50, s.main());
            }
            case "camera" -> {
                if (s.pos() == null) return false;
                BlockPos cam = s.pos();
                Fixtures.setCameraSeen(level, cam, true);
                later(50, () -> {
                    if (Spirits.get(level, SpiritKind.MANAGER) == null) Fixtures.setCameraSeen(level, cam, false);
                });
                fear(p, 0.08f);
                happen(level, "camera", 50, s.points());
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void device(ServerLevel level, ServerPlayer p, String key, int ticks, String witness, float fear, Spot s) {
        Fixtures.setDevice(level, key, true);
        later(ticks, () -> Fixtures.setDevice(level, key, false));
        fear(p, fear);
        happen(level, witness, ticks, s.points());
    }

    /** The points of a room's lights, a little below the ceiling (so they are in the room for the witness rule). */
    private static List<Vec3> lightPoints(ServerLevel level, String room, Spot s) {
        List<Vec3> pts = new ArrayList<>();
        for (BlockPos l : Fixtures.lightsIn(level, room)) pts.add(new Vec3(l.getX() + 0.5, Layout.FEET_Y + 2.5, l.getZ() + 0.5));
        if (pts.isEmpty()) pts.add(s.main());
        return pts;
    }

    /** The dead tubes of a dark room stutter on by themselves for a moment, then go dark again. */
    private static void stutterLights(ServerLevel level, String room) {
        List<BlockPos> lights = Fixtures.lightsIn(level, room);
        if (lights.isEmpty()) return;
        int[] at = {0, 3, 5, 6, 11, 14, 21, 23};
        for (int i = 0; i < at.length; i++) {
            boolean on = i % 2 == 0;
            later(at[i], () -> setLit(level, lights, on));
        }
        later(32, () -> {
            for (BlockPos q : lights) setLit(level, List.of(q), Fixtures.shouldBeLit(level, q));
        });
        sound(level, Vec3.atCenterOf(lights.get(0)), ModSounds.FIXTURE_LIGHT_FLICKER.get(), 1.0f);
    }

    private static void setLit(ServerLevel level, List<BlockPos> lights, boolean on) {
        for (BlockPos q : lights) {
            if (!level.isLoaded(q)) continue;
            BlockState s = level.getBlockState(q);
            if (s.hasProperty(Props.LIT) && s.getValue(Props.LIT) != on) level.setBlock(q, s.setValue(Props.LIT, on), 3);
        }
    }

    /** The chopping at the board (night 6): 4 s strokes until someone comes near — then the last heavy chop, and nothing. */
    private static void tickChop(ServerLevel level) {
        if (chopUntil < 0) return;
        Vec3 board = Layout.marker("chef_board").center();
        boolean near = false;
        for (ServerPlayer p : GameManager.players(level)) {
            if (GameManager.inGame(p) && horizontal(p.position(), board) < 7.5) near = true;
        }
        if (near || clock >= chopUntil || Spirits.get(level, SpiritKind.CHEF) != null) {
            if (near || clock < chopUntil + 2) sound(level, board.add(0, 1.0, 0), ModSounds.CHEF_CHOP_STOP.get(), 1.0f);
            chopUntil = -1;
            return;
        }
        if (clock >= chopNext) {
            sound(level, board.add(0, 1.0, 0), ModSounds.CHEF_CHOP.get(), 1.0f);
            chopNext = clock + 80;
        }
    }

    /** Is the chopping going on (self test)? */
    public static boolean chopping() {
        return chopUntil >= 0;
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
                // heard from everywhere
                record(p, "crash");
            });
        }
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent e, float vol) {
        level.playSound(null, at.x, at.y, at.z, e, SoundSource.AMBIENT, vol, 0.95f + level.random.nextFloat() * 0.1f);
    }

    private static void fear(ServerPlayer p, float f) {
        GameManager.addFear(p, f);
    }

    // ================================================================================================ hooks
    public static void onEnterRoom(ServerLevel level, ServerPlayer p, String room) {
        GameData data = GameData.get(p.server);
        // the first night: the staff room TV comes on to static as you walk in
        if (room.equals("staff") && data.night == 1 && data.nightStage == GameData.NS_WORK && data.tonight("tv_first")) {
            later(20 * 6, () -> run(level, p, "tv"));
        }
    }

    public static void onNoticed(ServerLevel level, ServerPlayer p, Spirit s) {
        if (next >= 0) next = Math.max(next, clock + 20 * 20);
        if (nextQuiet >= 0) nextQuiet = Math.max(nextQuiet, clock + 20 * 30);
    }
}
