package com.eternalrealm.world.town;

/**
 * How bunting moves in the town's wind ({@link TownWind}, which blows from the west towards the east). The cord swings
 * about its chord like a pendulum whose length is set by its sag, pushed downwind by a steady part of the wind; the
 * cloths on it flutter about the cord with waves that run along the string, so a gust travels down the line instead of
 * moving every pennant in lockstep. Angles are radians.
 */
public final class BuntingSway {
    /** Direction the town's wind blows towards (east). */
    public static final double WIND_X = 1.0;
    public static final double WIND_Z = 0.0;
    private static final double GRAVITY = 9.81;
    /** Wave number of the flutter along the cord (radians per block). */
    private static final double FLUTTER_K = 1.9;

    private BuntingSway() {
    }

    /** Natural swing frequency (rad/s) of a cord sagging {@code sag} blocks: a pendulum about 2/3 of the sag long. */
    public static double pendulumOmega(double sag) {
        return Math.sqrt(GRAVITY / (0.3 + 0.67 * Math.max(0.0, sag)));
    }

    /** Steady deflection of the cord's plane in a wind of the given strength; {@code cross} from -1 to 1. */
    public static double meanSwing(double wind, double cross) {
        return 0.42 * clampWind(wind) * cross;
    }

    /**
     * Swing of the cord about its chord at a time.
     *
     * @param seconds game time in seconds
     * @param seed    per-string seed (the anchor position)
     * @param wind    wind strength (see {@link TownWind}) times the string's own factor
     * @param cross   {@link BuntingLine#crossWind} for the town wind
     * @param sag     the cord's sag in blocks
     */
    public static double swingAngle(double seconds, long seed, double wind, double cross, double sag) {
        double w = clampWind(wind);
        double phase = TownWind.phase(seed);
        double omega = pendulumOmega(sag);
        double amp = (0.04 + 0.09 * w) * (0.35 + 0.65 * Math.abs(cross));
        return meanSwing(w, cross) + amp * (Math.sin(omega * seconds + phase)
                + 0.25 * Math.sin(2.7 * omega * seconds + 2.0 * phase));
    }

    /** How fast the flutter phase of the cloths advances (rad/s); accumulate it like a flag's wave phase. */
    public static double flutterSpeed(double wind) {
        return 3.2 + 4.5 * clampWind(wind);
    }

    /**
     * Angle a hanging cloth swings out about the cord.
     *
     * @param flutter accumulated flutter phase (see {@link #flutterSpeed})
     * @param s       arc length of the cloth on the cord (the wave runs along it)
     * @param wind    wind strength
     * @param cross   wind across the cord, -1..1
     * @param give    how readily this cloth moves: 1 for pennants, less for heavy wet laundry
     */
    public static double clothAngle(double flutter, double s, double wind, double cross, double give) {
        double w = clampWind(wind);
        double mean = 0.62 * w * cross * give;
        double amp = (0.1 + 0.22 * w) * give;
        return mean + amp * (Math.sin(flutter - FLUTTER_K * s)
                + 0.35 * Math.sin(2.3 * flutter - 1.7 * FLUTTER_K * s + 0.9));
    }

    /** Wind strengths outside 0..1.6 are clamped. */
    public static double clampWind(double wind) {
        if (!(wind > 0.0)) {
            return 0.0;
        }
        return Math.min(wind, 1.6);
    }
}
