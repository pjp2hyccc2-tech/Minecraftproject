package com.frostbound.world;

/**
 * Height and zone functions of the frozen wastes. Pure and deterministic (one fixed map for every
 * FROSTBOUND world): a flat basin of radius 96 around the generator at (0, 0), a transition to 160, a ring
 * of wind-breaking mountains around radius 215 and rugged wilds with frozen lakes beyond.
 */
public final class Terrain {
    /** Top solid block of the basin; building floors are one above. */
    public static final int BASIN_Y = 64;
    public static final int SEA = 58;
    private static final int SEED = 0x46524F53;

    public enum Zone {
        BASIN,
        ICE_FIELD,
        FROST_PEAKS,
        GLACIER,
        FROZEN_LAKE,
        BURIED_RUINS,
        INDUSTRIAL
    }

    private Terrain() {
    }

    private static int hash(int x, int z, int seed) {
        int h = x * 0x27D4EB2D ^ z * 0x165667B1 ^ seed * 0x5BD1E995;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h;
    }

    /** Uniform value in [0, 1) for a lattice point. */
    public static double rand(int x, int z, int seed) {
        return (hash(x, z, seed ^ SEED) >>> 8) / (double) (1 << 24);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Smooth value noise in [-1, 1]. */
    public static double noise(double x, double z, int seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double a = rand(x0, z0, seed);
        double b = rand(x0 + 1, z0, seed);
        double c = rand(x0, z0 + 1, seed);
        double d = rand(x0 + 1, z0 + 1, seed);
        double v = a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
        return v * 2 - 1;
    }

    public static double fbm(double x, double z, int seed, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x * f, z * f, seed + i * 31) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.0;
        }
        return sum / norm;
    }

    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static double lakeValue(int x, int z) {
        return fbm(x / 140.0 + 100, z / 140.0 - 50, 4, 3);
    }

    public static boolean isLake(int x, int z) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        return r > 240 && lakeValue(x, z) > 0.32;
    }

    /** Height of the top solid block at (x, z). */
    public static int height(int x, int z) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (r < 96) {
            return BASIN_Y;
        }
        double wild = 68 + 14 * fbm(x / 170.0, z / 170.0, 1, 4) + 9 * fbm(x / 55.0, z / 55.0, 2, 3);
        double ridge = 1 - Math.abs(fbm(x / 260.0, z / 260.0, 3, 3));
        double peak = Math.max(0, ridge - 0.45) / 0.55;
        wild += 46 * peak * peak;
        double ring = 34 * Math.exp(-Math.pow((r - 215) / 48.0, 2));
        double outer = wild + ring;
        if (r > 240) {
            double lake = lakeValue(x, z);
            if (lake > 0.32) {
                outer = Math.min(outer, SEA - 2 - (lake - 0.32) * 20);
            } else if (lake > 0.24) {
                double shore = (lake - 0.24) / 0.08;
                outer = outer + (SEA + 1 - outer) * shore;
            }
        }
        double t = smoothstep(96, 160, r);
        return (int) Math.round(BASIN_Y + (outer - BASIN_Y) * t);
    }

    public static Zone zone(int x, int z) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (r < 150) {
            return Zone.BASIN;
        }
        if (isLake(x, z)) {
            return Zone.FROZEN_LAKE;
        }
        int h = height(x, z);
        if (h > 108) {
            return Zone.FROST_PEAKS;
        }
        double a = Math.atan2(z, x) + Math.PI + 0.35 * noise(x / 300.0, z / 300.0, 9);
        int sector = Math.floorMod((int) Math.floor(a / (Math.PI / 3)), 6);
        return switch (sector) {
            case 0 -> Zone.GLACIER;
            case 1, 5 -> Zone.BURIED_RUINS;
            case 3 -> Zone.INDUSTRIAL;
            default -> Zone.ICE_FIELD;
        };
    }
}
