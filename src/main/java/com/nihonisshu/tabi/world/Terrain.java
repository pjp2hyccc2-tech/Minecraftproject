package com.nihonisshu.tabi.world;

/**
 * Deterministic macro terrain shared with tools/terrain.py (same integer hash and float math),
 * so hand-built areas from the Python builder blend seamlessly into procedural surroundings.
 * Coordinates: +X east, +Z south. The Seto Inland Sea lies to the south of the Hyogo coast.
 */
public final class Terrain {
    private Terrain() {}

    public static final int WATER_TOP = 62;

    public static double hash(int ix, int iz, int s) {
        int h = ix * 374761393 + iz * 668265263 + s * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return (h & 0xFFFFFF) / 16777216.0;
    }

    public static double noise(double x, double z, double scale, int s) {
        double fx = x / scale, fz = z / scale;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        double ux = tx * tx * (3.0 - 2.0 * tx);
        double uz = tz * tz * (3.0 - 2.0 * tz);
        double a = hash(ix, iz, s), b = hash(ix + 1, iz, s);
        double c = hash(ix, iz + 1, s), d = hash(ix + 1, iz + 1, s);
        double ab = a + (b - a) * ux;
        double cd = c + (d - c) * ux;
        return ab + (cd - ab) * uz;
    }

    public static double fbm(double x, double z) {
        return 0.55 * noise(x, z, 220.0, 11) + 0.30 * noise(x, z, 80.0, 23) + 0.15 * noise(x, z, 28.0, 37);
    }

    /** z of the mainland shoreline (sea is south of it). Inland (no sea) west of x=1500. */
    public static double coastZ(int x) {
        if (x < 1500) return 1.0e9;
        double ramp = Math.min(1.0, (x - 1500) / 300.0);
        double base = 700.0 + 50.0 * (noise(x, 0.0, 300.0, 51) - 0.5) * 2.0;
        return base + (1.0 - ramp) * 2000.0;
    }

    /** northern shore of Awaji island (land again south of it) for 1500 <= x <= 2700. */
    public static double awajiZ(int x) {
        if (x < 1500 || x > 2700) return 1.0e9;
        double edge = Math.min(x - 1500, 2700 - x);
        double bulge = Math.min(1.0, edge / 250.0);
        return 1350.0 + 40.0 * (noise(x, 5.0, 200.0, 61) - 0.5) * 2.0 + (1.0 - bulge) * 600.0;
    }

    /** signed distance inland from the nearest shore (negative = at sea). */
    public static double inland(int x, int z) {
        double c = coastZ(x);
        double a = awajiZ(x);
        if (z <= c) return c - z;
        if (z >= a) return z - a;
        return -Math.min(z - c, a - z);
    }

    /** terrain surface height (y of the top solid block). */
    public static int height(int x, int z) {
        double d = inland(x, z);
        if (d < 0) {
            double depth = Math.min(18.0, -d * 0.12);
            return (int) Math.floor(58.0 - depth);
        }
        double n = fbm(x, z);
        double h = 68.0 + n * 44.0;
        // the Rokko mountains rise behind Kobe
        if (x > 3000) {
            double ridge = Math.max(0.0, Math.min(1.0, (420.0 - z) / 260.0));
            h += ridge * (40.0 + 50.0 * noise(x, z, 160.0, 71));
        }
        if (d < 60.0) {
            h = 63.0 + (h - 63.0) * (d / 60.0);
        }
        return (int) Math.floor(h);
    }

    /** 0 = grass, 1 = sand beach, 2 = sea floor sand, 3 = gravel sea floor */
    public static int surface(int x, int z, int h) {
        double d = inland(x, z);
        if (d < 0) return (Terrain.hash(x, z, 91) < 0.3) ? 3 : 2;
        if (h <= 64 && d < 14) return 1;
        return 0;
    }

    /** forest density 0..1 */
    public static double forest(int x, int z) {
        return noise(x, z, 140.0, 83);
    }
}
