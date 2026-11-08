package com.eternalrealm.world.gen;

import java.util.SplittableRandom;

/**
 * Deterministic 2D gradient (Perlin) noise used for biome borders, sub-biome patches and surface details.
 * Immutable and thread-safe: world generation calls it from several worker threads at once.
 */
public final class GenNoise {
    private final int[] permutation = new int[512];

    public GenNoise(long seed) {
        int[] table = new int[256];
        for (int i = 0; i < table.length; i++) {
            table[i] = i;
        }
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = table.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = table[i];
            table[i] = table[j];
            table[j] = swap;
        }
        for (int i = 0; i < permutation.length; i++) {
            permutation[i] = table[i & 255];
        }
    }

    /** Smooth noise in [-1, 1]; one feature per unit of input. */
    public double sample(double x, double z) {
        double floorX = Math.floor(x);
        double floorZ = Math.floor(z);
        int cellX = ((int) floorX) & 255;
        int cellZ = ((int) floorZ) & 255;
        double fx = x - floorX;
        double fz = z - floorZ;
        double u = fade(fx);
        double v = fade(fz);
        int a = permutation[cellX] + cellZ;
        int b = permutation[cellX + 1] + cellZ;
        double bottom = lerp(u, gradient(permutation[a], fx, fz), gradient(permutation[b], fx - 1.0, fz));
        double top = lerp(u, gradient(permutation[a + 1], fx, fz - 1.0), gradient(permutation[b + 1], fx - 1.0, fz - 1.0));
        double value = lerp(v, bottom, top);
        return value > 1.0 ? 1.0 : Math.max(value, -1.0);
    }

    /** Fractal sum of {@code octaves} octaves (each half the size and amplitude), normalised to [-1, 1]. */
    public double fractal(double x, double z, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double total = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * sample(x * frequency + i * 17.31, z * frequency - i * 11.57);
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / total;
    }

    /** Ridged noise in [0, 1]: 1 on the ridge lines (where the noise crosses zero), falling off around them. */
    public double ridge(double x, double z) {
        return 1.0 - Math.abs(sample(x, z));
    }

    /** Cheap per-position hash in [0, 1) for scattered details (same seed and position: same value). */
    public static double hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double gradient(int hash, double x, double z) {
        return switch (hash & 7) {
            case 0 -> x + z;
            case 1 -> -x + z;
            case 2 -> x - z;
            case 3 -> -x - z;
            case 4 -> x;
            case 5 -> -x;
            case 6 -> z;
            default -> -z;
        };
    }
}
