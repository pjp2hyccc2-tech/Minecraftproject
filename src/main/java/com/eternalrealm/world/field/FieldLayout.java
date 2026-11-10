package com.eternalrealm.world.field;

import com.eternalrealm.world.gen.GenNoise;

import java.util.List;

/**
 * The map of アストリア as ten fields around the royal capital (the "Arcelia Kingdom Field Map"): the capital in the
 * north-centre, the soaring peaks behind it, the end forest to the north-west, the moonshadow forest to the west, the
 * iron mine beyond it, the beginners' meadow to the south, the lake and the windy harbour to the south-east, the farms
 * to the east, the ancient ruins on their hill and the sacred falls to the north-east.
 *
 * <p>Fields are regions of a weighted Voronoi diagram (several seeds per field) computed on noise-warped coordinates, so
 * their borders wander naturally; inside the capital's footprint the field is the meadow (area 1, the starting area).
 * The same scores blend the field terrains in {@link FieldTerrain}. Rivers, the lake, the coast, roads, waystones and
 * the playable bounds are fixed geometry here. Pure, deterministic and thread-safe (world generation calls it from
 * worker threads). Coordinates are world blocks with the capital centre at (0, 0); north is -z.</p>
 */
public final class FieldLayout {
    public static final long SEED = 0x4649_454C_4453L;
    /** Layout generation: 1 was the seven concentric rings, 2 is this field map. */
    public static final int VERSION = 2;

    // ---- fields (area ids of world.Areas)
    public static final int MEADOW = 1;
    public static final int MOON_FOREST = 2;
    public static final int IRON_MINE = 3;
    public static final int SACRED_FALLS = 4;
    public static final int ANCIENT_RUINS = 5;
    public static final int SOARING_PEAKS = 6;
    public static final int END_FOREST = 7;
    public static final int FARM = 8;
    public static final int LAKESIDE = 9;
    public static final int HARBOUR = 10;
    public static final int FIELD_COUNT = 10;

    // ---- the world
    /** Playable bounds: beyond them the sea (south, east) or the mountain wall (north, west). */
    public static final int MIN_X = -1700;
    public static final int MAX_X = 1700;
    public static final int MIN_Z = -1500;
    public static final int MAX_Z = 1300;
    public static final int SEA_LEVEL = 63;

    // ---- the capital (its blueprint is built over this footprint; tools/capital/world.py)
    public static final int CAPITAL_MIN_X = -176;
    public static final int CAPITAL_MAX_X = 175;
    public static final int CAPITAL_MIN_Z = -208;
    public static final int CAPITAL_MAX_Z = 175;
    public static final int CAPITAL_CORNER = 40;
    /** World y of the capital's ground (blueprint y = 0). */
    public static final int CAPITAL_GROUND_Y = 70;

    /** A Voronoi seed: points nearer (after dividing by the weight) belong to {@code field}. */
    public record Seed(int field, double x, double z, double weight) {
    }

    private static final Seed[] SEEDS = {
            new Seed(MEADOW, -380, 560, 1.0), new Seed(MEADOW, -60, 380, 0.9), new Seed(MEADOW, -560, 880, 1.0),
            new Seed(MOON_FOREST, -760, -40, 1.0), new Seed(MOON_FOREST, -620, 260, 0.9),
            new Seed(MOON_FOREST, -430, -60, 0.8), new Seed(MOON_FOREST, -950, 250, 1.0),
            new Seed(IRON_MINE, -1320, 520, 1.0), new Seed(IRON_MINE, -1250, 900, 1.0),
            new Seed(IRON_MINE, -1450, 200, 0.9),
            new Seed(SACRED_FALLS, 560, -420, 1.0), new Seed(SACRED_FALLS, 380, -300, 0.85),
            new Seed(SACRED_FALLS, 760, -580, 0.95),
            new Seed(ANCIENT_RUINS, 1300, -200, 1.0), new Seed(ANCIENT_RUINS, 1450, 120, 0.9),
            new Seed(ANCIENT_RUINS, 1100, -460, 0.9),
            new Seed(SOARING_PEAKS, -300, -1050, 1.15), new Seed(SOARING_PEAKS, 250, -1080, 1.2),
            new Seed(SOARING_PEAKS, 800, -1050, 1.1), new Seed(SOARING_PEAKS, 0, -700, 0.9),
            new Seed(END_FOREST, -1150, -700, 1.05), new Seed(END_FOREST, -850, -950, 1.0),
            new Seed(END_FOREST, -1400, -1100, 1.0),
            new Seed(FARM, 720, 200, 1.0), new Seed(FARM, 1000, 420, 1.0), new Seed(FARM, 450, 120, 0.8),
            new Seed(LAKESIDE, 350, 860, 1.0), new Seed(LAKESIDE, 180, 620, 0.85), new Seed(LAKESIDE, 600, 1000, 0.9),
            new Seed(HARBOUR, 1200, 820, 1.0), new Seed(HARBOUR, 1450, 1050, 1.0), new Seed(HARBOUR, 950, 1150, 0.9),
    };

    // ---- water
    /** The lake of 湖畔のほとり: an ellipse with a wooded island. */
    public static final double LAKE_X = 360;
    public static final double LAKE_Z = 880;
    public static final double LAKE_RX = 300;
    public static final double LAKE_RZ = 170;
    public static final double ISLAND_X = 320;
    public static final double ISLAND_Z = 905;
    public static final double ISLAND_R = 58;

    /**
     * The falls of 聖なる滝: a stream on the plateau pours over the lip of a cliff into the plunge pool. The cliff runs
     * through the lip at right angles to {@code (CLIFF_NX, CLIFF_NZ)}, the unit normal pointing onto the plateau.
     */
    public static final double LIP_X = 612;
    public static final double LIP_Z = -410;
    public static final double CLIFF_NX = 0.6;
    public static final double CLIFF_NZ = -0.8;
    /** Height of the plateau above the falls (world y of its ground). */
    public static final int PLATEAU_Y = 132;
    /** Half width of the curtain of water. */
    public static final double FALLS_HALF_WIDTH = 8;
    /** The plunge pool in front of the falls. */
    public static final double POOL_X = LIP_X - CLIFF_NX * 14.0;
    public static final double POOL_Z = LIP_Z - CLIFF_NZ * 14.0;
    public static final double POOL_R = 14;

    /** A river: a polyline with a water level and a half width that grows downstream. */
    public record River(String id, double[][] points, int waterLevel, double sourceHalfWidth, double mouthHalfWidth) {
    }

    private static final List<River> RIVERS = List.of(
            // from the plunge pool round the east of the capital, through the farms to the lake
            new River("falls_river", new double[][]{{603, -398}, {560, -330}, {520, -250}, {455, -120}, {380, 40},
                    {330, 220},
                    {320, 420}, {360, 610}, {360, 720}}, SEA_LEVEL, 5.0, 8.0),
            // the lake's outflow to the harbour bay
            new River("harbour_river", new double[][]{{640, 900}, {800, 960}, {980, 950}, {1120, 940}},
                    SEA_LEVEL, 7.0, 10.0),
            // the brook from the moonshadow pond across the meadow into the lake
            new River("meadow_brook", new double[][]{{-745, 228}, {-600, 330}, {-460, 420}, {-330, 520}, {-150, 690},
                    {60, 800}, {120, 850}}, SEA_LEVEL, 3.0, 5.0),
            // the stream on the falls plateau, feeding the falls
            new River("plateau_stream", new double[][]{{860, -700}, {760, -560}, {660, -460}, {624, -426},
                    {LIP_X, LIP_Z}}, PLATEAU_Y - 1, 4.0, FALLS_HALF_WIDTH));

    /** The moonshadow pond. */
    public static final double POND_X = -780;
    public static final double POND_Z = 200;
    public static final double POND_R = 46;

    // ---- the coast of 風の港 (sea to the east and south-east)
    /** Shore line: the sea lies where {@link #coastDistance} is negative. */
    private static final double[][] COAST = {{900, 1300}, {1000, 1140}, {1140, 1020}, {1180, 960}, {1260, 900},
            {1340, 760}, {1460, 640}, {1560, 520}, {1700, 470}};

    // ---- roads (from the capital gates to every field)
    /** A road: a polyline of a few blocks' half width. */
    public record Road(String id, double[][] points, double halfWidth) {
    }

    private static final List<Road> ROADS = List.of(
            new Road("south", new double[][]{{0, 176}, {-30, 300}, {-160, 450}, {-360, 560}, {-520, 720},
                    {-640, 900}}, 2.5),
            new Road("lakeside", new double[][]{{0, 176}, {110, 330}, {220, 520}, {280, 660}, {520, 720}, {800, 800},
                    {1100, 860}}, 2.5),
            new Road("west", new double[][]{{-176, 0}, {-330, 20}, {-520, 40}, {-700, 70}, {-900, 260},
                    {-1080, 450}, {-1250, 600}, {-1340, 740}}, 2.5),
            new Road("end", new double[][]{{-700, 70}, {-780, -180}, {-900, -420}, {-1000, -620}, {-1100, -820},
                    {-1140, -940}}, 2.0),
            new Road("east", new double[][]{{176, 0}, {300, 15}, {420, 60}, {600, 160}, {740, 230}, {900, 120},
                    {1080, -20}, {1250, -150}}, 2.5),
            new Road("farm_harbour", new double[][]{{740, 230}, {860, 420}, {980, 600}, {1100, 760}, {1180, 860}},
                    2.0),
            // up the east bank of the falls river, then across it (the shrine's bridge) to the waystone's bank
            new Road("falls", new double[][]{{420, 60}, {470, -60}, {478, -120}, {530, -222}, {552, -276},
                    {524, -293}}, 2.0),
            new Road("peaks", new double[][]{{524, -293}, {470, -470}, {380, -620}, {300, -800}, {230, -980},
                    {200, -1200}}, 1.5));

    // ---- waystones (転移碑): id, field, position
    /** A teleport stone: players unlock it by touching it and can then travel between unlocked stones. */
    public record Waystone(String id, int field, int x, int z) {
    }

    private static final List<Waystone> WAYSTONES = List.of(
            new Waystone("capital", MEADOW, 0, 196),
            new Waystone("meadow", MEADOW, -360, 572),
            new Waystone("moon_forest", MOON_FOREST, -700, 86),
            new Waystone("iron_mine", IRON_MINE, -1250, 618),
            new Waystone("sacred_falls", SACRED_FALLS, 530, -290),
            new Waystone("ancient_ruins", ANCIENT_RUINS, 1230, -132),
            new Waystone("soaring_peaks", SOARING_PEAKS, 300, -812),
            new Waystone("end_forest", END_FOREST, -1000, -604),
            new Waystone("farm", FARM, 752, 248),
            new Waystone("lakeside", LAKESIDE, 282, 676),
            new Waystone("harbour", HARBOUR, 1100, 876));

    /**
     * Four or five more waystones per field, placed by {@code FieldSitesGenerator} (tests) between the shrines, the
     * habitats and the lords' territories; fixed here so they never move.
     */
    // GENERATED MINOR WAYSTONES BEGIN (FieldSitesGenerator)
    private static final List<Waystone> MINOR_WAYSTONES = List.of(
            new Waystone("meadow_2", 1, -632, 876),
            new Waystone("meadow_3", 1, -272, 924),
            new Waystone("meadow_4", 1, -356, 372),
            new Waystone("meadow_5", 1, -704, 732),
            new Waystone("meadow_6", 1, -668, 1080),
            new Waystone("moon_forest_2", 2, -824, -248),
            new Waystone("moon_forest_3", 2, -404, -92),
            new Waystone("moon_forest_4", 2, -968, 76),
            new Waystone("moon_forest_5", 2, -884, 412),
            new Waystone("moon_forest_6", 2, -548, 232),
            new Waystone("iron_mine_2", 3, -1472, 972),
            new Waystone("iron_mine_3", 3, -1376, 60),
            new Waystone("iron_mine_4", 3, -1040, 1140),
            new Waystone("iron_mine_5", 3, -1484, 468),
            new Waystone("sacred_falls_2", 4, 708, -616),
            new Waystone("sacred_falls_3", 4, 252, -388),
            new Waystone("sacred_falls_4", 4, 672, -184),
            new Waystone("sacred_falls_5", 4, 420, -580),
            new Waystone("ancient_ruins_2", 5, 1396, -560),
            new Waystone("ancient_ruins_3", 5, 1504, 256),
            new Waystone("ancient_ruins_4", 5, 1000, -176),
            new Waystone("ancient_ruins_5", 5, 1036, -536),
            new Waystone("soaring_peaks_2", 6, -172, -756),
            new Waystone("soaring_peaks_3", 6, 1112, -1032),
            new Waystone("soaring_peaks_4", 6, 476, -1056),
            new Waystone("soaring_peaks_5", 6, 68, -780),
            new Waystone("soaring_peaks_6", 6, 56, -1092),
            new Waystone("end_forest_2", 7, -1196, -1164),
            new Waystone("end_forest_3", 7, -1460, -612),
            new Waystone("end_forest_4", 7, -1592, -1056),
            new Waystone("end_forest_5", 7, -968, -1188),
            new Waystone("farm_2", 8, 1012, 544),
            new Waystone("farm_3", 8, 484, 124),
            new Waystone("farm_4", 8, 1132, 256),
            new Waystone("farm_5", 8, 772, 484),
            new Waystone("lakeside_2", 9, 712, 912),
            new Waystone("lakeside_3", 9, 28, 1020),
            new Waystone("lakeside_4", 9, 520, 624),
            new Waystone("lakeside_5", 9, 40, 684),
            new Waystone("lakeside_6", 9, 460, 1140),
            new Waystone("harbour_2", 10, 1308, 672),
            new Waystone("harbour_3", 10, 912, 1104),
            new Waystone("harbour_4", 10, 948, 768),
            new Waystone("harbour_5", 10, 1116, 672));
    // GENERATED MINOR WAYSTONES END

    private static final List<Waystone> ALL_WAYSTONES = concat(WAYSTONES, MINOR_WAYSTONES);

    private static List<Waystone> concat(List<Waystone> a, List<Waystone> b) {
        List<Waystone> all = new java.util.ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }

    /** Where each field's name is written on the map (index = field id). */
    private static final double[][] LABELS = {{0, 0}, {-330, 610}, {-800, -10}, {-1300, 560}, {610, -300},
            {1300, -40}, {150, -950}, {-1110, -700}, {820, 330}, {360, 770}, {1250, 760}};

    private static final GenNoise WARP_X = new GenNoise(SEED);
    private static final GenNoise WARP_Z = new GenNoise(SEED + 1);
    private static final GenNoise WARP_FINE_X = new GenNoise(SEED + 2);
    private static final GenNoise WARP_FINE_Z = new GenNoise(SEED + 3);
    private static final GenNoise SHORE = new GenNoise(SEED + 4);

    private FieldLayout() {
    }

    public static List<Seed> seeds() {
        return List.of(SEEDS);
    }

    public static List<River> rivers() {
        return RIVERS;
    }

    public static List<Road> roads() {
        return ROADS;
    }

    /** Every waystone: the eleven shrines of the field map, then the minor waystones. */
    public static List<Waystone> waystones() {
        return ALL_WAYSTONES;
    }

    /** The eleven shrines (hand-made set pieces, generated with the terrain). */
    public static List<Waystone> shrines() {
        return WAYSTONES;
    }

    /** The minor waystones (a stone on a small platform, built by the server when a player first comes near). */
    public static List<Waystone> minorWaystones() {
        return MINOR_WAYSTONES;
    }

    public static boolean isMinor(String waystoneId) {
        for (Waystone waystone : MINOR_WAYSTONES) {
            if (waystone.id().equals(waystoneId)) {
                return true;
            }
        }
        return false;
    }

    /** Map position of a field's name: {@code {x, z}}. */
    public static double[] labelPoint(int field) {
        if (field < 1 || field > FIELD_COUNT) {
            throw new IllegalArgumentException("field " + field);
        }
        return LABELS[field].clone();
    }

    /** The field (area id 1..{@value #FIELD_COUNT}) at (x, z); the capital counts as the meadow. */
    public static int areaAt(double x, double z) {
        if (capitalDistance(x, z) <= 0.0) {
            return MEADOW;
        }
        double[] scores = new double[FIELD_COUNT + 1];
        scores(x, z, scores);
        int best = MEADOW;
        for (int field = 1; field <= FIELD_COUNT; field++) {
            if (scores[field] < scores[best]) {
                best = field;
            }
        }
        return best;
    }

    /**
     * Voronoi scores (warped distance divided by the seed weight, smaller = closer) of every field at (x, z), written to
     * {@code out[1..FIELD_COUNT]}.
     */
    public static void scores(double x, double z, double[] out) {
        double wx = x + 70.0 * WARP_X.fractal(x / 430.0, z / 430.0, 2) + 14.0 * WARP_FINE_X.sample(x / 95.0, z / 95.0);
        double wz = z + 70.0 * WARP_Z.fractal(x / 430.0, z / 430.0, 2) + 14.0 * WARP_FINE_Z.sample(x / 95.0, z / 95.0);
        for (int field = 1; field <= FIELD_COUNT; field++) {
            out[field] = Double.MAX_VALUE;
        }
        for (Seed seed : SEEDS) {
            double dx = wx - seed.x();
            double dz = wz - seed.z();
            double score = Math.sqrt(dx * dx + dz * dz) / seed.weight();
            if (score < out[seed.field()]) {
                out[seed.field()] = score;
            }
        }
    }

    /** How far (in score units, roughly blocks) (x, z) is from the nearest border between two fields. */
    public static double borderDistance(double x, double z) {
        double[] scores = new double[FIELD_COUNT + 1];
        scores(x, z, scores);
        double first = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;
        for (int field = 1; field <= FIELD_COUNT; field++) {
            double s = scores[field];
            if (s < first) {
                second = first;
                first = s;
            } else if (s < second) {
                second = s;
            }
        }
        return (second - first) / 2.0;
    }

    /**
     * Distance outside the capital's footprint (a rectangle with rounded corners); 0 or negative inside it.
     */
    public static double capitalDistance(double x, double z) {
        double r = CAPITAL_CORNER;
        double cx = (CAPITAL_MIN_X + CAPITAL_MAX_X) / 2.0;
        double cz = (CAPITAL_MIN_Z + CAPITAL_MAX_Z) / 2.0;
        double hx = (CAPITAL_MAX_X - CAPITAL_MIN_X) / 2.0 - r;
        double hz = (CAPITAL_MAX_Z - CAPITAL_MIN_Z) / 2.0 - r;
        double qx = Math.abs(x - cx) - hx;
        double qz = Math.abs(z - cz) - hz;
        double outside = Math.sqrt(Math.max(qx, 0.0) * Math.max(qx, 0.0) + Math.max(qz, 0.0) * Math.max(qz, 0.0));
        return outside + Math.min(Math.max(qx, qz), 0.0) - r;
    }

    public static boolean inCapital(double x, double z) {
        return capitalDistance(x, z) <= 0.0;
    }

    private static volatile int[][] regionBounds;

    /**
     * Bounding box {@code {minX, minZ, maxX, maxZ}} of a field's region inside the world bounds (sampled every 16
     * blocks, so accurate to that; the capital counts as part of the meadow). Computed once.
     */
    public static int[] regionBounds(int field) {
        if (field < 1 || field > FIELD_COUNT) {
            throw new IllegalArgumentException("field " + field);
        }
        int[][] bounds = regionBounds;
        if (bounds == null) {
            bounds = new int[FIELD_COUNT + 1][];
            for (int f = 1; f <= FIELD_COUNT; f++) {
                bounds[f] = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            for (int x = MIN_X; x <= MAX_X; x += 16) {
                for (int z = MIN_Z; z <= MAX_Z; z += 16) {
                    int[] b = bounds[areaAt(x, z)];
                    b[0] = Math.min(b[0], x);
                    b[1] = Math.min(b[1], z);
                    b[2] = Math.max(b[2], x);
                    b[3] = Math.max(b[3], z);
                }
            }
            regionBounds = bounds;
        }
        return bounds[field].clone();
    }

    public static boolean inBounds(double x, double z) {
        return x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z;
    }

    /** How far outside the playable bounds (x, z) is; 0 inside. */
    public static double outOfBounds(double x, double z) {
        double dx = Math.max(MIN_X - x, Math.max(0.0, x - MAX_X));
        double dz = Math.max(MIN_Z - z, Math.max(0.0, z - MAX_Z));
        return Math.max(dx, dz);
    }

    /** Signed distance to the shore of the harbour sea: negative at sea, positive inland. */
    public static double coastDistance(double x, double z) {
        double d = distanceToPolyline(x, z, COAST);
        // the sea is on the south-east side of the shore line
        double[] nearest = nearestOnPolyline(x, z, COAST);
        double side = (x - nearest[0]) * nearest[3] - (z - nearest[1]) * nearest[2];
        double wobble = 9.0 * SHORE.fractal(x / 70.0, z / 70.0, 2);
        return (side < 0.0 ? -d : d) + wobble;
    }

    /** Lake membership: < 1 inside the water, 1 at the shore. */
    public static double lakeRadius(double x, double z) {
        double dx = (x - LAKE_X) / LAKE_RX;
        double dz = (z - LAKE_Z) / LAKE_RZ;
        double angle = Math.atan2(dz, dx);
        double wobble = 0.06 * SHORE.sample(Math.cos(angle) * 3.0 + 7.0, Math.sin(angle) * 3.0 - 5.0);
        return Math.sqrt(dx * dx + dz * dz) * (1.0 + wobble);
    }

    /** Distance to the river polylines, and which river is nearest: {@code {distance, index, fraction along}}. */
    public static double[] nearestRiver(double x, double z) {
        double best = Double.MAX_VALUE;
        double index = -1;
        double along = 0.0;
        for (int i = 0; i < RIVERS.size(); i++) {
            double[] hit = nearestOnPolyline(x, z, RIVERS.get(i).points());
            double d = Math.hypot(x - hit[0], z - hit[1]);
            if (d < best) {
                best = d;
                index = i;
                along = hit[4];
            }
        }
        return new double[]{best, index, along};
    }

    /** Distance to the nearest road and its half width: {@code {distance, halfWidth}}. */
    public static double[] nearestRoad(double x, double z) {
        double best = Double.MAX_VALUE;
        double half = 0.0;
        for (Road road : ROADS) {
            double d = distanceToPolyline(x, z, road.points());
            if (d < best) {
                best = d;
                half = road.halfWidth();
            }
        }
        return new double[]{best, half};
    }

    public static double distanceToPolyline(double x, double z, double[][] points) {
        double[] hit = nearestOnPolyline(x, z, points);
        return Math.hypot(x - hit[0], z - hit[1]);
    }

    /**
     * The nearest point of a polyline: {@code {px, pz, dirX, dirZ, fraction}} where (dirX, dirZ) is the unit direction of
     * the segment and fraction runs 0..1 along the whole line.
     */
    public static double[] nearestOnPolyline(double x, double z, double[][] points) {
        double best = Double.MAX_VALUE;
        double[] result = {points[0][0], points[0][1], 1.0, 0.0, 0.0};
        double total = 0.0;
        double[] lengths = new double[points.length - 1];
        for (int i = 0; i + 1 < points.length; i++) {
            lengths[i] = Math.hypot(points[i + 1][0] - points[i][0], points[i + 1][1] - points[i][1]);
            total += lengths[i];
        }
        double walked = 0.0;
        for (int i = 0; i + 1 < points.length; i++) {
            double ax = points[i][0];
            double az = points[i][1];
            double bx = points[i + 1][0];
            double bz = points[i + 1][1];
            double len = lengths[i];
            double ux = len > 0.0 ? (bx - ax) / len : 1.0;
            double uz = len > 0.0 ? (bz - az) / len : 0.0;
            double t = len > 0.0 ? Math.max(0.0, Math.min(len, (x - ax) * ux + (z - az) * uz)) : 0.0;
            double px = ax + ux * t;
            double pz = az + uz * t;
            double d = (x - px) * (x - px) + (z - pz) * (z - pz);
            if (d < best) {
                best = d;
                result = new double[]{px, pz, ux, uz, total > 0.0 ? (walked + t) / total : 0.0};
            }
            walked += len;
        }
        return result;
    }
}
