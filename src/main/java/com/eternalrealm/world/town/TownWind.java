package com.eternalrealm.world.town;

/**
 * The wind over the capital that drives flags, windmill sails and chimney smoke: a weather-dependent base with slow
 * gusts. Each object gets its own gust phase from its position seed, so a row of flags never moves in lockstep.
 * Strength is roughly 0.2 (calm) .. 1.4 (storm); 0.6 is a fair breeze.
 */
public final class TownWind {
    public static final double MIN = 0.2;
    public static final double MAX = 1.4;
    /**
     * The wind flags, banners and bunting hang in when their waving is switched off (client setting
     * {@code scenery.flagAnimation}): a light breeze, steady.
     */
    public static final double STILL = 0.35;

    private TownWind() {
    }

    /**
     * @param seconds game time in seconds
     * @param seed    per-object seed (e.g. the block position as a long)
     * @param rain    rain level 0..1
     * @param thunder thunder level 0..1
     */
    public static double strength(double seconds, long seed, double rain, double thunder) {
        double s = phase(seed);
        double base = 0.55 + 0.25 * clamp01(rain) + 0.35 * clamp01(thunder);
        double gust = 0.22 * Math.sin(seconds * 0.31 + s) * Math.sin(seconds * 0.071 + s * 0.37)
                + 0.08 * Math.sin(seconds * 1.3 + s * 2.1);
        return Math.max(MIN, Math.min(MAX, base + gust));
    }

    /** A stable pseudo-random phase in [0, 2pi) for a seed. */
    public static double phase(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 11) * 0x1.0p-53) * Math.PI * 2.0;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
