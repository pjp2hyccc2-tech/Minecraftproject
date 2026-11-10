package com.eternalrealm.world.field;

import com.eternalrealm.world.gen.GenNoise;

/**
 * The land of the field map: a height for every column, the water standing on it and what its ground is made of.
 * Every field has its own terrain (rolling meadow, forest hills, rugged mine ridges with a terraced quarry, the falls'
 * cliff and plateau, the ruins' hill, the soaring peaks, flat farmland, shores) and neighbouring fields blend over
 * about a hundred blocks using the Voronoi scores of {@link FieldLayout}. Rivers carve valleys, the lake and the sea
 * have shelving shores, the capital's footprint is flat at {@link FieldLayout#CAPITAL_GROUND_Y}, and the land rises
 * into an impassable wall beyond the northern and western bounds and drops into the sea beyond the southern and
 * eastern ones. Pure, deterministic, thread-safe.
 */
public final class FieldTerrain {
    /** No water in a column. */
    public static final int NO_WATER = Integer.MIN_VALUE;
    /** Highest ground the terrain may reach. */
    public static final int MAX_HEIGHT = 300;
    /** Blend width of neighbouring fields (score units, about blocks). */
    private static final double TAU = 30.0;
    private static final int POND_LEVEL = 69;

    /** What the top of a column is made of (the generator turns this into blocks; biomes may repaint). */
    public enum Surface {
        GRASS, FOREST_FLOOR, DIRT_PATH, SAND, GRAVEL, STONE, SNOW, MUD, COARSE_DIRT, RIVERBED, LAKEBED, SEABED,
        CLIFF, QUARRY, ASH
    }

    private static final GenNoise ROLL = new GenNoise(FieldLayout.SEED + 10);
    private static final GenNoise DETAIL = new GenNoise(FieldLayout.SEED + 11);
    private static final GenNoise RIDGE = new GenNoise(FieldLayout.SEED + 12);
    private static final GenNoise MOUNTAIN = new GenNoise(FieldLayout.SEED + 13);
    private static final GenNoise CRAG = new GenNoise(FieldLayout.SEED + 14);
    private static final GenNoise PATCH = new GenNoise(FieldLayout.SEED + 15);
    private static final GenNoise SNOWLINE = new GenNoise(FieldLayout.SEED + 16);

    private FieldTerrain() {
    }

    // ================================================================================================ heights
    /** Ground height (top solid block, before rounding) at (x, z). */
    public static double heightAt(double x, double z) {
        double[] scores = new double[FieldLayout.FIELD_COUNT + 1];
        FieldLayout.scores(x, z, scores);
        double min = Double.MAX_VALUE;
        for (int f = 1; f <= FieldLayout.FIELD_COUNT; f++) {
            min = Math.min(min, scores[f]);
        }
        double sum = 0.0;
        double weights = 0.0;
        for (int f = 1; f <= FieldLayout.FIELD_COUNT; f++) {
            double d = scores[f] - min;
            if (d > TAU * 6.0) {
                continue;
            }
            double w = Math.exp(-d / TAU);
            sum += w * fieldHeight(f, x, z);
            weights += w;
        }
        double h = sum / weights;
        h = bounds(x, z, h);
        h = capital(x, z, h);
        h = waters(x, z, h);
        return Math.max(-50.0, Math.min(MAX_HEIGHT, h));
    }

    /** The terrain of one field alone (before rivers, lakes, the coast and the capital). */
    public static double fieldHeight(int field, double x, double z) {
        double roll = ROLL.fractal(x / 240.0, z / 240.0, 3);
        double detail = DETAIL.fractal(x / 55.0, z / 55.0, 2);
        return switch (field) {
            case FieldLayout.MEADOW -> 70.0 + 6.5 * roll + 2.2 * detail
                    + 5.0 * Math.max(0.0, PATCH.fractal(x / 150.0, z / 150.0, 2));
            case FieldLayout.MOON_FOREST -> 72.5 + 9.0 * roll + 3.5 * detail;
            case FieldLayout.IRON_MINE -> mine(x, z, roll, detail);
            case FieldLayout.SACRED_FALLS -> falls(x, z, roll, detail);
            case FieldLayout.ANCIENT_RUINS -> ruins(x, z, roll, detail);
            case FieldLayout.SOARING_PEAKS -> peaks(x, z, roll, detail);
            case FieldLayout.END_FOREST -> endForest(x, z, roll, detail);
            case FieldLayout.FARM -> 69.0 + 2.5 * roll + 0.8 * detail;
            case FieldLayout.LAKESIDE -> 68.0 + 4.0 * roll + 1.5 * detail;
            case FieldLayout.HARBOUR -> harbour(x, z, roll, detail);
            default -> 70.0;
        };
    }

    private static double ridged(GenNoise noise, double x, double z, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double total = 0.0;
        for (int i = 0; i < octaves; i++) {
            double r = 1.0 - Math.abs(noise.sample(x * frequency + i * 31.7, z * frequency - i * 19.3));
            sum += amplitude * r * r;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / total;
    }

    private static double mine(double x, double z, double roll, double detail) {
        double ridges = ridged(RIDGE, x / 230.0, z / 230.0, 4);
        double h = 80.0 + 6.0 * roll + 38.0 * Math.pow(ridges, 1.6) + 7.0 * detail
                + 6.0 * Math.max(0.0, CRAG.fractal(x / 40.0, z / 40.0, 2));
        // the terraced quarry: stepped walls down to its floor
        double d = Math.hypot(x - QUARRY_X, z - QUARRY_Z);
        if (d < QUARRY_R + 30.0) {
            double steps = QUARRY_FLOOR + 7.0 * Math.floor(Math.max(0.0, d - 18.0) / 11.0);
            h = d < QUARRY_R ? Math.min(h, steps) : Math.min(h, lerp(steps, h, smoothstep(QUARRY_R, QUARRY_R + 30.0, d)));
        }
        // the miners' camp: a levelled terrace around the waystone
        double camp = Math.hypot(x - CAMP_X, z - CAMP_Z);
        if (camp < 60.0) {
            h = lerp(CAMP_Y, h, smoothstep(34.0, 60.0, camp));
        }
        return h;
    }

    /** The iron mine's quarry and camp. */
    public static final double QUARRY_X = -1330;
    public static final double QUARRY_Z = 760;
    public static final double QUARRY_R = 72;
    public static final double QUARRY_FLOOR = 66;
    public static final double CAMP_X = -1250;
    public static final double CAMP_Z = 618;
    public static final double CAMP_Y = 86;

    private static double falls(double x, double z, double roll, double detail) {
        double lower = 80.0 + 8.0 * roll + 2.0 * detail;
        double plateau = FieldLayout.PLATEAU_Y + 2.5 * detail + 3.0 * Math.max(0.0, roll);
        double dx = x - FieldLayout.LIP_X;
        double dz = z - FieldLayout.LIP_Z;
        double s = dx * FieldLayout.CLIFF_NX + dz * FieldLayout.CLIFF_NZ;          // + onto the plateau
        double t = -dx * FieldLayout.CLIFF_NZ + dz * FieldLayout.CLIFF_NX;         // along the cliff
        double near = Math.abs(t) - FieldLayout.FALLS_HALF_WIDTH - 10.0;
        double width = near < 0.0 ? 1.2 : Math.min(18.0, 1.2 + near * 0.12);       // vertical at the falls
        double wiggle = near < 0.0 ? 0.0 : 10.0 * CRAG.fractal(t / 80.0, 3.7, 2);
        double cliff = smoothstep(-width, width, s + wiggle);
        // the cliff only exists near the falls: far along it the plateau sinks back into the highland
        double extent = 1.0 - smoothstep(380.0, 560.0, Math.abs(t));
        return lerp(lower, lerp(lower, plateau, extent), cliff);
    }

    private static double ruins(double x, double z, double roll, double detail) {
        double h = 76.0 + 7.0 * roll + 2.0 * detail;
        double d = Math.hypot(x - RUINS_X, z - RUINS_Z);
        double hill = 46.0 * Math.exp(-(d / 240.0) * (d / 240.0));
        h += hill;
        if (h > RUINS_TOP) {
            h = RUINS_TOP + (h - RUINS_TOP) * 0.2;                                 // the levelled hilltop
        }
        return h;
    }

    /** The ancient ruins' hill (its top is levelled for the temple ruins). */
    public static final double RUINS_X = 1300;
    public static final double RUINS_Z = -200;
    public static final double RUINS_TOP = 116;

    private static double peaks(double x, double z, double roll, double detail) {
        double north = smoothstep(-650.0, -1350.0, z);
        double m = ridged(MOUNTAIN, x / 300.0, z / 300.0, 4);
        double h = 92.0 + 12.0 * roll + 3.0 * detail + (40.0 + 170.0 * north) * Math.pow(m, 1.4) + 25.0 * north;
        return Math.min(MAX_HEIGHT - 4.0, h);
    }

    private static double endForest(double x, double z, double roll, double detail) {
        double h = 73.0 + 8.0 * roll + 3.0 * detail;
        double d = Math.hypot(x - TOWER_X, z - TOWER_Z);
        if (d < 90.0) {
            h = lerp(TOWER_GROUND, h, smoothstep(40.0, 90.0, d));                // the tower's plinth
        }
        return h;
    }

    /** The dark tower (終焉の塔) in the end forest. */
    public static final int TOWER_X = -1150;
    public static final int TOWER_Z = -980;
    public static final double TOWER_GROUND = 78;

    private static double harbour(double x, double z, double roll, double detail) {
        double h = 67.0 + 4.0 * roll + 1.2 * detail;
        double town = Math.hypot(x - HARBOUR_TOWN_X, z - HARBOUR_TOWN_Z);
        if (town < 110.0) {
            h = lerp(67.0, h, smoothstep(60.0, 110.0, town));                         // the harbour town's terrace
        }
        return h;
    }

    /** The harbour town (風の港) on its bay. */
    public static final double HARBOUR_TOWN_X = 1150;
    public static final double HARBOUR_TOWN_Z = 860;

    // ================================================================================================ modifiers
    /** Beyond the bounds: a mountain wall to the north and west, the open sea to the south and east. */
    private static double bounds(double x, double z, double h) {
        double wall = Math.max(Math.max(FieldLayout.MIN_Z - z, FieldLayout.MIN_X - x), 0.0);
        double sea = Math.max(Math.max(z - FieldLayout.MAX_Z, x - FieldLayout.MAX_X), 0.0);
        if (wall > 0.0) {
            double ridges = ridged(MOUNTAIN, x / 140.0, z / 140.0, 3);
            return h + Math.min(170.0, wall * 1.4) * (0.6 + 0.6 * ridges);
        }
        if (sea > 0.0) {
            double bed = FieldLayout.SEA_LEVEL - 6.0 - Math.min(26.0, sea * 0.3);
            return lerp(h, bed, smoothstep(0.0, 60.0, sea));
        }
        return h;
    }

    /** The capital's footprint is flat; the land ramps back to the fields over 100 blocks. */
    private static double capital(double x, double z, double h) {
        double d = FieldLayout.capitalDistance(x, z);
        if (d <= 0.0) {
            return FieldLayout.CAPITAL_GROUND_Y;
        }
        if (d < 100.0) {
            return lerp(FieldLayout.CAPITAL_GROUND_Y, h, smoothstep(0.0, 100.0, d));
        }
        return h;
    }

    /** Rivers, the lake and its island, the pond, the plunge pool and the sea shore. */
    private static double waters(double x, double z, double h) {
        // sea shore (harbour)
        double coast = FieldLayout.coastDistance(x, z);
        if (coast < 60.0) {
            if (coast < 0.0) {
                h = Math.min(h, FieldLayout.SEA_LEVEL - 3.0 - Math.min(24.0, -coast * 0.22));
            } else {
                h = lerp(Math.min(h, FieldLayout.SEA_LEVEL + 1.0), h, smoothstep(0.0, 60.0, coast));
            }
        }
        // lake
        double r = FieldLayout.lakeRadius(x, z);
        if (r < 1.3) {
            if (r < 1.0) {
                double bed = FieldLayout.SEA_LEVEL - 2.0 - 11.0 * (1.0 - r * r);
                h = Math.min(h, bed);
            } else {
                h = lerp(Math.min(h, FieldLayout.SEA_LEVEL + 1.0), h, smoothstep(1.0, 1.3, r));
            }
            double di = Math.hypot(x - FieldLayout.ISLAND_X, z - FieldLayout.ISLAND_Z) / FieldLayout.ISLAND_R;
            if (di < 1.25) {
                double island = di < 1.0 ? FieldLayout.SEA_LEVEL + 1.0 + 8.0 * (1.0 - di * di)
                        : FieldLayout.SEA_LEVEL - 1.0 - 4.0 * (di - 1.0) / 0.25;
                h = Math.max(h, island);
            }
        }
        // the moonshadow pond, perched in the forest
        double pond = Math.hypot(x - FieldLayout.POND_X, z - FieldLayout.POND_Z);
        if (pond < FieldLayout.POND_R + 14.0) {
            if (pond < FieldLayout.POND_R) {
                h = Math.min(h, POND_LEVEL - 1.0 - 5.0 * (1.0 - pond / FieldLayout.POND_R));
            } else {
                h = lerp(POND_LEVEL + 1.0, h, smoothstep(FieldLayout.POND_R, FieldLayout.POND_R + 14.0, pond));
            }
        }
        // the plunge pool below the falls (never cutting into the cliff behind the curtain of water)
        double pool = Math.hypot(x - FieldLayout.POOL_X, z - FieldLayout.POOL_Z);
        if (pool < FieldLayout.POOL_R + 10.0 && cliffSide(x, z) < -0.6) {
            if (pool < FieldLayout.POOL_R) {
                h = Math.min(h, FieldLayout.SEA_LEVEL - 3.0 - 5.0 * (1.0 - pool / FieldLayout.POOL_R));
            } else {
                h = Math.min(h, lerp(FieldLayout.SEA_LEVEL + 1.0, h,
                        smoothstep(FieldLayout.POOL_R, FieldLayout.POOL_R + 10.0, pool)));
            }
        }
        if (isFalls(x, z)) {
            h = Math.min(h, FieldLayout.SEA_LEVEL - 4.0);
        }
        // rivers (each carves a valley down to its water level)
        for (FieldLayout.River river : FieldLayout.rivers()) {
            double[] hit = FieldLayout.nearestOnPolyline(x, z, river.points());
            double d = Math.hypot(x - hit[0], z - hit[1]);
            double half = lerp(river.sourceHalfWidth(), river.mouthHalfWidth(), hit[4]);
            if (d > half + 40.0) {
                continue;
            }
            int level = river.waterLevel();
            if (level < FieldLayout.PLATEAU_Y - 10 && onPlateau(x, z)) {
                continue;                       // a low river's valley never cuts into the plateau above the falls
            }
            if (d < half) {
                h = Math.min(h, level - 2.0 - 2.5 * (1.0 - d / half));
            } else {
                h = Math.min(h, level + 1.0 + (d - half) * 0.45);
            }
        }
        return h;
    }

    // ================================================================================================ water
    /**
     * The top water block of a column with ground at {@code height}, or {@link #NO_WATER}. Rivers, lake and sea stand
     * at sea level, the pond and the plateau stream higher; the curtain of the falls is reported by {@link #isFalls}.
     */
    public static int waterTop(double x, double z, int height) {
        if (isFalls(x, z)) {
            return FieldLayout.PLATEAU_Y - 1;
        }
        double pond = Math.hypot(x - FieldLayout.POND_X, z - FieldLayout.POND_Z);
        if (pond < FieldLayout.POND_R + 2.0 && height < POND_LEVEL) {
            return POND_LEVEL;
        }
        for (FieldLayout.River river : FieldLayout.rivers()) {
            if (river.waterLevel() == FieldLayout.SEA_LEVEL) {
                continue;
            }
            double[] hit = FieldLayout.nearestOnPolyline(x, z, river.points());
            double d = Math.hypot(x - hit[0], z - hit[1]);
            double half = lerp(river.sourceHalfWidth(), river.mouthHalfWidth(), hit[4]);
            if (d < half + 1.0 && height < river.waterLevel() && height >= river.waterLevel() - 7) {
                return river.waterLevel();
            }
        }
        return height < FieldLayout.SEA_LEVEL ? FieldLayout.SEA_LEVEL : NO_WATER;
    }

    /** Columns of the falling curtain of water just in front of the cliff at the lip. */
    public static boolean isFalls(double x, double z) {
        double dx = x - FieldLayout.LIP_X;
        double dz = z - FieldLayout.LIP_Z;
        double t = -dx * FieldLayout.CLIFF_NZ + dz * FieldLayout.CLIFF_NX;
        double s = cliffSide(x, z);
        return Math.abs(t) <= FieldLayout.FALLS_HALF_WIDTH && s >= -2.6 && s < -0.6;
    }

    /** On the plateau side of the falls cliff, where the cliff stands (not where it has sunk back). */
    static boolean onPlateau(double x, double z) {
        double t = -(x - FieldLayout.LIP_X) * FieldLayout.CLIFF_NZ + (z - FieldLayout.LIP_Z) * FieldLayout.CLIFF_NX;
        return cliffSide(x, z) > -0.6 && Math.abs(t) < 560.0;
    }

    /** Signed distance from the falls cliff line: positive on the plateau. */
    public static double cliffSide(double x, double z) {
        return (x - FieldLayout.LIP_X) * FieldLayout.CLIFF_NX + (z - FieldLayout.LIP_Z) * FieldLayout.CLIFF_NZ;
    }

    // ================================================================================================ surface
    /**
     * The ground cover of a column.
     *
     * @param slope steepest height difference to the neighbouring columns (blocks per block)
     */
    public static Surface surfaceAt(double x, double z, int height, int waterTop, double slope) {
        int field = FieldLayout.areaAt(x, z);
        if (waterTop != NO_WATER && waterTop > height) {
            if (FieldLayout.coastDistance(x, z) < 0.0 || FieldLayout.outOfBounds(x, z) > 0.0) {
                return Surface.SEABED;
            }
            if (FieldLayout.lakeRadius(x, z) < 1.05) {
                return Surface.LAKEBED;
            }
            return Surface.RIVERBED;
        }
        double[] road = FieldLayout.nearestRoad(x, z);
        if (road[0] <= road[1] && !FieldLayout.inCapital(x, z)) {
            return Surface.DIRT_PATH;
        }
        if (road[0] <= road[1] + 0.9 && !FieldLayout.inCapital(x, z)) {
            return PATCH.sample(x / 3.0, z / 3.0) > 0.0 ? Surface.GRAVEL : Surface.COARSE_DIRT;
        }
        double snowLine = 158.0 + 10.0 * SNOWLINE.fractal(x / 90.0, z / 90.0, 2);
        if (height >= snowLine && slope < 1.6) {
            return Surface.SNOW;
        }
        if (slope >= 1.4) {
            return Surface.CLIFF;
        }
        if (height <= FieldLayout.SEA_LEVEL + 2 && nearWater(x, z)) {
            return field == FieldLayout.HARBOUR || FieldLayout.lakeRadius(x, z) < 1.25 ? Surface.SAND : Surface.MUD;
        }
        double patch = PATCH.fractal(x / 45.0, z / 45.0, 2);
        return switch (field) {
            case FieldLayout.MOON_FOREST -> patch > 0.25 ? Surface.COARSE_DIRT : Surface.FOREST_FLOOR;
            case FieldLayout.IRON_MINE -> {
                if (Math.hypot(x - QUARRY_X, z - QUARRY_Z) < QUARRY_R + 6.0) {
                    yield Surface.QUARRY;
                }
                yield patch > 0.2 ? Surface.STONE : patch < -0.25 ? Surface.GRAVEL : Surface.COARSE_DIRT;
            }
            case FieldLayout.SOARING_PEAKS -> height > 130 ? (patch > 0.0 ? Surface.STONE : Surface.GRAVEL)
                    : Surface.GRASS;
            case FieldLayout.END_FOREST -> patch > 0.3 ? Surface.ASH : Surface.FOREST_FLOOR;
            case FieldLayout.ANCIENT_RUINS -> patch > 0.35 ? Surface.COARSE_DIRT : Surface.GRASS;
            default -> Surface.GRASS;
        };
    }

    private static boolean nearWater(double x, double z) {
        if (FieldLayout.coastDistance(x, z) < 8.0 || FieldLayout.lakeRadius(x, z) < 1.12) {
            return true;
        }
        double[] river = FieldLayout.nearestRiver(x, z);
        return river[0] < 9.0;
    }

    // ================================================================================================ helpers
    static double smoothstep(double a, double b, double x) {
        double t = (x - a) / (b - a);
        t = t < 0.0 ? 0.0 : Math.min(t, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
