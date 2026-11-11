package com.eternalrealm.world.field;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.random.RandomGenerator;

/**
 * The 放浪する大物: fifteen wandering bosses (boss.FieldBosses) that turn up now and then in their field while players
 * are there (docs/modules/field_threats.md). Pure rules: where each may appear, and the schedule of a field.
 *
 * <p>Places ({@link #findSpots}): flat, dry ground near the heart of the field, away from roads, waystones, dungeon
 * entrances, set pieces, the lords' territories and the calamity's lair. Schedule ({@link Schedule}): a field's next
 * wanderer comes {@value #MIN_GAP_MINUTES}..{@value #MAX_GAP_MINUTES} minutes after the last one went, counted only
 * while players are in the field; at most one per field at a time and never while the field's calamity is coming or
 * out; one that nobody fights leaves after {@value #STAY_MINUTES} minutes (or when the calamity rises); the same
 * wanderer does not come back for {@value #COOLDOWN_MINUTES} minutes.</p>
 */
public final class Wanderers {
    public static final int MIN_GAP_MINUTES = 12;
    public static final int MAX_GAP_MINUTES = 25;
    public static final int STAY_MINUTES = 8;
    public static final int COOLDOWN_MINUTES = 30;
    /** A player this close to a wanderer that is out wakes it: the fight starts, and everyone this close joins. */
    public static final double WAKE_RADIUS = 40.0;
    /** A fought wanderer walks off when no fighter has been within {@link FieldThreats#JOIN_RADIUS} for this long. */
    public static final int ABANDON_TICKS = 90 * 20;
    private static final int MINUTE = 20 * 60;
    /** Spot rules: clearances in blocks from the arena centre (the arena itself is {@link FieldThreats#LAIR_RADIUS}). */
    static final int ROAD_CLEARANCE = 30;
    static final int WAYSTONE_CLEARANCE = 64;
    static final int LANDMARK_CLEARANCE = 48;
    static final int PIECE_CLEARANCE = 32;
    static final int LORD_CLEARANCE = 40;
    /**
     * Arena rim to habitat rim. Where a field has too few spots with it (the habitats grew to twice their area on
     * 2026-11-11 and the harbour is mostly sea), the arena may come as close as {@link #HABITAT_CLEARANCE_TIGHT}:
     * habitat monsters stay calm unless attacked or lingered near, so a fight next door does not pull them in.
     */
    static final int HABITAT_CLEARANCE = 12;
    static final int HABITAT_CLEARANCE_TIGHT = 0;
    /** Fewest spots a field should offer (the wanderer turns up in different places). */
    static final int MIN_SPOTS = 3;
    static final int LAIR_CLEARANCE = 80;
    static final int CAPITAL_CLEARANCE = 120;
    static final int BORDER_CLEARANCE = 36;
    static final int EDGE_CLEARANCE = 96;
    /** Spots lie this close to the heart of their field (its name on the map), where players hunt. */
    static final int HEART_RADIUS = 520;
    static final int SHORE_CLEARANCE = 28;
    static final int RIVER_CLEARANCE = 28;
    /** Most the ground may rise or fall inside the arena (blocks). */
    static final int MAX_STEP = 2;
    static final int SPOT_SPACING = 140;
    static final int SPOTS = 4;
    private static final int GRID = 16;

    /** One wanderer: its boss (boss.FieldBosses id) and field. */
    public record Wanderer(String id, int field, String boss) {
        /** Its name: the name of its boss. */
        public String translationKey() {
            return "boss." + ModInfo.MOD_ID + "." + boss;
        }
    }

    // the texts of the wanderers (docs/i18n/field_wanderers.json)
    private static final String TEXT = "threat." + ModInfo.MOD_ID + ".";
    /** Broadcast: {@code field, name, level, waystone, direction, distance, x, z}. */
    public static final String APPEARS = TEXT + "wanderer.appears";
    /** Broadcast: {@code name, field} (its stay ran out). */
    public static final String LEAVES = TEXT + "wanderer.leaves";
    /** Broadcast: {@code name, field} (the field's calamity rose). */
    public static final String FLEES = TEXT + "wanderer.flees";
    /** Broadcast: {@code name, field} (its fighters left it). */
    public static final String WALKS_OFF = TEXT + "wanderer.walks_off";
    /** Broadcast: {@code field, name, hero}. */
    public static final String DOWN = TEXT + "wanderer.down";
    /** Name over the body: {@code name}. */
    public static final String NAMEPLATE = TEXT + "wanderer.nameplate";
    /** Board line: {@code name, level}; status texts after it. */
    public static final String BOARD = TEXT + "board.wanderer";
    public static final String BOARD_OUT = TEXT + "board.wanderer_out";
    public static final String BOARD_FIGHTING = TEXT + "board.wanderer_fighting";
    public static final String BOARD_AWAY = TEXT + "board.wanderer_away";
    /** Map label of a wanderer that is out. */
    public static final String MAP = TEXT + "map.wanderer";
    /** Header of the result screen. */
    public static final String RESULT = "result." + ModInfo.MOD_ID + ".wanderer_clear";
    private static final List<String> DIRECTIONS = List.of("n", "ne", "e", "se", "s", "sw", "w", "nw");

    /** Every text key the wanderers use (names are their bosses' names). */
    public static List<String> textKeys() {
        List<String> keys = new ArrayList<>(List.of(APPEARS, LEAVES, FLEES, WALKS_OFF, DOWN, NAMEPLATE, BOARD, BOARD_OUT,
                BOARD_FIGHTING, BOARD_AWAY, MAP, RESULT));
        for (String direction : DIRECTIONS) {
            keys.add(directionKey(direction));
        }
        return keys;
    }

    /** Text key of a compass direction ({@link #direction}). */
    public static String directionKey(String direction) {
        return TEXT + "dir." + direction;
    }

    /**
     * The compass direction (n, ne, e, ... nw; north is -z) from (fromX, fromZ) to (toX, toZ), for "about 200 blocks
     * north-east of the waystone".
     */
    public static String direction(double fromX, double fromZ, double toX, double toZ) {
        double angle = Math.toDegrees(Math.atan2(toX - fromX, -(toZ - fromZ)));
        int index = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return DIRECTIONS.get(index);
    }

    /** The waystone of {@code field} nearest (x, z) (to tell players where a wanderer is). */
    public static Optional<FieldLayout.Waystone> landmark(int field, int x, int z) {
        FieldLayout.Waystone best = null;
        double bestDistance = Double.MAX_VALUE;
        for (FieldLayout.Waystone waystone : FieldLayout.waystones()) {
            double distance = Math.hypot(waystone.x() - x, waystone.z() - z);
            if (waystone.field() == field && distance < bestDistance) {
                best = waystone;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** A place a wanderer may appear (ground column). */
    public record Spot(int x, int z) {
    }

    /** A rectangle (inclusive) a wanderer's arena must keep clear of: a set piece of the field map. */
    public record Box(int minX, int minZ, int maxX, int maxZ) {
        /** Distance from (x, z) to the rectangle (0 inside). */
        public double distance(double x, double z) {
            double dx = Math.max(minX - x, Math.max(0.0, x - maxX));
            double dz = Math.max(minZ - z, Math.max(0.0, z - maxZ));
            return Math.hypot(dx, dz);
        }
    }

    private static final List<Wanderer> WANDERERS = List.of(
            new Wanderer("bramble_knight", 1, "w_bramble_knight"),
            new Wanderer("moonmoth_witch", 2, "w_moonmoth_witch"),
            new Wanderer("slag_titan", 3, "w_slag_titan"),
            new Wanderer("cascade_wyvern", 4, "w_cascade_wyvern"),
            new Wanderer("mist_hermit", 4, "w_mist_hermit"),
            new Wanderer("walking_glyph", 5, "w_walking_glyph"),
            new Wanderer("ash_herald", 5, "w_ash_herald"),
            new Wanderer("thunder_roc", 6, "w_thunder_roc"),
            new Wanderer("glacier_bearer", 6, "w_glacier_bearer"),
            new Wanderer("hollow_walker", 7, "w_hollow_walker"),
            new Wanderer("gravebloom", 7, "w_gravebloom"),
            new Wanderer("scarecrow_king", 8, "w_scarecrow_king"),
            new Wanderer("reed_serpent", 9, "w_reed_serpent"),
            new Wanderer("sunken_captain", 10, "w_sunken_captain"),
            new Wanderer("tide_singer", 10, "w_tide_singer"));

    private Wanderers() {
    }

    public static List<Wanderer> all() {
        return WANDERERS;
    }

    public static Optional<Wanderer> byId(String id) {
        return WANDERERS.stream().filter(wanderer -> wanderer.id().equals(id)).findFirst();
    }

    public static Optional<Wanderer> byBoss(String bossId) {
        return WANDERERS.stream().filter(wanderer -> wanderer.boss().equals(bossId)).findFirst();
    }

    public static List<Wanderer> of(int field) {
        return WANDERERS.stream().filter(wanderer -> wanderer.field() == field).toList();
    }

    /** The set pieces of {@code data} as rectangles to keep clear of. */
    public static List<Box> boxes(FieldPieceData data) {
        List<Box> boxes = new ArrayList<>();
        for (FieldPieceData.Piece piece : data.pieces()) {
            int[] box = piece.box();
            boxes.add(new Box(box[0], box[2], box[3], box[5]));
        }
        return boxes;
    }

    /**
     * Where the wanderers of {@code field} may appear: up to {@value #SPOTS} spots of flat, dry ground of the field,
     * at least {@value #SPOT_SPACING} blocks apart, clear of roads, waystones, dungeon entrances, the set pieces
     * {@code keepOut}, the lords' territories, the calamity's lair and the monsters' habitats. Deterministic; a few hundred milliseconds of
     * terrain sampling, so the server finds them off the main thread.
     */
    public static List<Spot> findSpots(int field, List<Box> keepOut) {
        List<Spot> spots = findSpots(field, keepOut, HABITAT_CLEARANCE);
        return spots.size() >= MIN_SPOTS ? spots : findSpots(field, keepOut, HABITAT_CLEARANCE_TIGHT);
    }

    private static List<Spot> findSpots(int field, List<Box> keepOut, int habitatClearance) {
        int[] bounds = FieldLayout.regionBounds(field);
        double[] label = FieldLayout.labelPoint(field);
        List<Spot> candidates = new ArrayList<>();
        for (int x = bounds[0] + GRID / 2; x <= bounds[2]; x += GRID) {
            for (int z = bounds[1] + GRID / 2; z <= bounds[3]; z += GRID) {
                if (Math.hypot(x - label[0], z - label[1]) <= HEART_RADIUS
                        && suitable(field, x, z, keepOut, habitatClearance)) {
                    candidates.add(new Spot(x, z));
                }
            }
        }
        // spread out: the most central candidate first (nearest the field's name on the map), then each next one as
        // far as possible from those already chosen
        List<Spot> chosen = new ArrayList<>();
        while (chosen.size() < SPOTS && !candidates.isEmpty()) {
            Spot best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Spot spot : candidates) {
                double score;
                if (chosen.isEmpty()) {
                    score = -Math.hypot(spot.x() - label[0], spot.z() - label[1]);
                } else {
                    score = Double.MAX_VALUE;
                    for (Spot other : chosen) {
                        score = Math.min(score, Math.hypot(other.x() - spot.x(), other.z() - spot.z()));
                    }
                    if (score < SPOT_SPACING) {
                        continue;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = spot;
                }
            }
            if (best == null) {
                break;
            }
            chosen.add(best);
        }
        return List.copyOf(chosen);
    }

    /**
     * Whether a wanderer's arena (radius {@link FieldThreats#LAIR_RADIUS}) centred on (x, z) is flat dry ground of
     * {@code field}, clear of what a fight must not trample.
     */
    public static boolean suitable(int field, int x, int z, List<Box> keepOut) {
        return suitable(field, x, z, keepOut, HABITAT_CLEARANCE);
    }

    private static boolean suitable(int field, int x, int z, List<Box> keepOut, int habitatClearance) {
        int edge = Math.min(Math.min(x - FieldLayout.MIN_X, FieldLayout.MAX_X - x),
                Math.min(z - FieldLayout.MIN_Z, FieldLayout.MAX_Z - z));
        if (edge < EDGE_CLEARANCE || FieldLayout.areaAt(x, z) != field
                || FieldLayout.capitalDistance(x, z) < CAPITAL_CLEARANCE
                || FieldLayout.borderDistance(x, z) < BORDER_CLEARANCE) {
            return false;
        }
        double[] road = FieldLayout.nearestRoad(x, z);
        if (road[0] - road[1] < ROAD_CLEARANCE) {
            return false;
        }
        for (FieldLayout.Waystone waystone : FieldLayout.waystones()) {
            if (Math.hypot(waystone.x() - x, waystone.z() - z) < WAYSTONE_CLEARANCE) {
                return false;
            }
        }
        for (com.eternalrealm.world.gen.LandmarkSite site : com.eternalrealm.world.gen.LandmarkSites.all()) {
            if (Math.hypot(site.x() - x, site.z() - z) < site.padRadius() + LANDMARK_CLEARANCE) {
                return false;
            }
        }
        for (Box box : keepOut) {
            if (box.distance(x, z) < PIECE_CLEARANCE) {
                return false;
            }
        }
        for (FieldThreats.Lord lord : FieldThreats.lords()) {
            if (Math.hypot(lord.x() - x, lord.z() - z) < lord.territory() + LORD_CLEARANCE) {
                return false;
            }
        }
        for (FieldThreats.Calamity calamity : FieldThreats.calamities()) {
            if (Math.hypot(calamity.x() - x, calamity.z() - z) < FieldThreats.LAIR_RADIUS + LAIR_CLEARANCE) {
                return false;
            }
        }
        for (Habitats.Habitat habitat : Habitats.all()) {
            // the fight would pull the habitat's monsters in
            if (habitat.distanceTo(x, z) < habitat.radius() + FieldThreats.LAIR_RADIUS + habitatClearance) {
                return false;
            }
        }
        if (FieldLayout.coastDistance(x, z) < SHORE_CLEARANCE || FieldLayout.lakeRadius(x, z) < 1.25
                || FieldLayout.nearestRiver(x, z)[0] < RIVER_CLEARANCE) {
            return false;
        }
        int top = (int) Math.floor(FieldTerrain.heightAt(x, z));
        int reach = FieldThreats.LAIR_RADIUS - 4;
        for (int dx = -reach; dx <= reach; dx += 4) {
            for (int dz = -reach; dz <= reach; dz += 4) {
                if (dx * dx + dz * dz > reach * reach) {
                    continue;
                }
                double sx = x + dx;
                double sz = z + dz;
                int h = (int) Math.floor(FieldTerrain.heightAt(sx, sz));
                if (Math.abs(h - top) > MAX_STEP || FieldTerrain.isFalls(sx, sz)
                        || FieldTerrain.waterTop(sx, sz, h) != FieldTerrain.NO_WATER) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ schedule

    /** What the schedule tells the platform. */
    public sealed interface Event permits Event.Appeared, Event.Left {
        /** {@code wanderer} appears at {@code spot} of its field. */
        record Appeared(Wanderer wanderer, Spot spot) implements Event {
        }

        /** The wanderer left without a fight: its stay ran out, or its field's calamity rose. */
        record Left(Wanderer wanderer, boolean calamity) implements Event {
        }
    }

    /**
     * The wanderers' clock, one per server. Server thread only; {@code now} is the server tick. A field's clock only
     * runs while players are in it ({@link #tick}); the platform reports the fight ({@link #engaged}) and its end
     * ({@link #ended}).
     */
    public static final class Schedule {
        private final RandomGenerator random;
        private final IntFunction<List<Spot>> spots;
        /** Per field: player time (ticks) since the last wanderer went, and how much of it the next one waits for. */
        private final Map<Integer, Long> clock = new HashMap<>();
        private final Map<Integer, Long> due = new HashMap<>();
        private final Map<Integer, Long> lastTick = new HashMap<>();
        private final Map<Integer, Up> up = new HashMap<>();
        private final Map<String, Long> cooldownUntil = new HashMap<>();

        /** The wanderer out in a field. */
        private static final class Up {
            final Wanderer wanderer;
            final Spot spot;
            final long since;
            boolean fighting;

            Up(Wanderer wanderer, Spot spot, long since) {
                this.wanderer = wanderer;
                this.spot = spot;
                this.since = since;
            }
        }

        /**
         * @param spots where each field's wanderers may appear ({@link #findSpots}); empty while not known yet (no
         *              wanderer appears then)
         */
        public Schedule(RandomGenerator random, IntFunction<List<Spot>> spots) {
            this.random = random;
            this.spots = spots;
        }

        /** Player time between two wanderers of a field: {@value #MIN_GAP_MINUTES}..{@value #MAX_GAP_MINUTES} min. */
        private long gap() {
            return (long) (MIN_GAP_MINUTES + random.nextInt(MAX_GAP_MINUTES - MIN_GAP_MINUTES + 1)) * MINUTE;
        }

        /** The wanderer out in {@code field}, if any. */
        public Optional<Wanderer> current(int field) {
            Up current = up.get(field);
            return current == null ? Optional.empty() : Optional.of(current.wanderer);
        }

        /** Where the wanderer of {@code field} appeared. */
        public Optional<Spot> spot(int field) {
            Up current = up.get(field);
            return current == null ? Optional.empty() : Optional.of(current.spot);
        }

        /** Whether the wanderer of {@code field} is being fought. */
        public boolean fighting(int field) {
            Up current = up.get(field);
            return current != null && current.fighting;
        }

        /** Ticks until the unengaged wanderer of {@code field} leaves (0 when none, or while it is fought). */
        public long leavesIn(int field, long now) {
            Up current = up.get(field);
            if (current == null || current.fighting) {
                return 0L;
            }
            return Math.max(0L, current.since + (long) STAY_MINUTES * MINUTE - now);
        }

        /** Ticks until {@code wanderer} may appear again (0: it may). */
        public long cooldown(Wanderer wanderer, long now) {
            return Math.max(0L, cooldownUntil.getOrDefault(wanderer.id(), now) - now);
        }

        /**
         * Advances {@code field} to {@code now} (call it every second or so for every field). {@code playersInField}:
         * someone is in the field (its clock only runs then); {@code calamityUp}: the field's calamity is coming or out
         * (no wanderer appears then, and one that nobody fights leaves).
         */
        public List<Event> tick(int field, long now, boolean playersInField, boolean calamityUp) {
            long last = lastTick.getOrDefault(field, now);
            lastTick.put(field, now);
            Up current = up.get(field);
            if (current != null) {
                if (!current.fighting && (calamityUp || now - current.since >= (long) STAY_MINUTES * MINUTE)) {
                    end(field, now);
                    return List.of(new Event.Left(current.wanderer, calamityUp));
                }
                return List.of();
            }
            if (!playersInField) {
                return List.of();
            }
            long run = clock.merge(field, Math.max(0L, now - last), Long::sum);
            // the first wanderer of a field after a restart comes after half a gap
            long wait = due.computeIfAbsent(field, f -> gap() / 2);
            if (run < wait || calamityUp) {
                return List.of();
            }
            List<Wanderer> ready = new ArrayList<>();
            for (Wanderer wanderer : of(field)) {
                if (cooldown(wanderer, now) == 0L) {
                    ready.add(wanderer);
                }
            }
            List<Spot> places = spots.apply(field);
            if (ready.isEmpty() || places == null || places.isEmpty()) {
                return List.of();
            }
            return List.of(appear(field, ready.get(random.nextInt(ready.size())),
                    places.get(random.nextInt(places.size())), now));
        }

        /**
         * Operators and tests: {@code wanderer} appears now at {@code spot} (empty if its field already has one out).
         */
        public Optional<Event.Appeared> force(Wanderer wanderer, Spot spot, long now) {
            if (up.containsKey(wanderer.field())) {
                return Optional.empty();
            }
            lastTick.put(wanderer.field(), now);
            return Optional.of(appear(wanderer.field(), wanderer, spot, now));
        }

        private Event.Appeared appear(int field, Wanderer wanderer, Spot spot, long now) {
            up.put(field, new Up(wanderer, spot, now));
            return new Event.Appeared(wanderer, spot);
        }

        /** Players engaged the wanderer of {@code field}: it no longer leaves on its own. */
        public void engaged(int field) {
            Up current = up.get(field);
            if (current != null) {
                current.fighting = true;
            }
        }

        /** The wanderer of {@code field} is gone (defeated, or the players left it and it walked off). */
        public void ended(int field, long now) {
            if (up.containsKey(field)) {
                end(field, now);
            }
        }

        private void end(int field, long now) {
            Up gone = up.remove(field);
            clock.put(field, 0L);
            due.put(field, gap());
            cooldownUntil.put(gone.wanderer.id(), now + (long) COOLDOWN_MINUTES * MINUTE);
        }
    }
}
