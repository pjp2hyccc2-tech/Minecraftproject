package com.eternalrealm.world.town;

/**
 * The gentle swing of a hanging shop board about its top edge (the bracket arm or the chains' hooks): a slow pendulum
 * nudged by the town wind, leaning a little downwind when the wind meets the board's face. Radians; positive angles
 * move the bottom of the board towards the side its rest normal points to.
 */
public final class SignSwing {
    /** Swing frequency, rad/s (a board on short hooks: a bit over two seconds per swing). */
    public static final double OMEGA = 2.6;

    private SignSwing() {
    }

    /**
     * @param seconds game time in seconds
     * @param seed    per-sign seed (its position)
     * @param wind    wind strength (see {@link TownWind})
     * @param push    how squarely the wind meets the board, -1..1 (its rest normal dotted with the wind direction)
     */
    public static double angle(double seconds, long seed, double wind, double push) {
        double w = BuntingSway.clampWind(wind);
        double p = Math.max(-1.0, Math.min(1.0, push));
        double phase = TownWind.phase(seed);
        double mean = 0.1 * w * p;
        double amp = (0.025 + 0.055 * w) * (0.35 + 0.65 * Math.abs(p));
        return mean + amp * (Math.sin(OMEGA * seconds + phase) + 0.3 * Math.sin(1.9 * OMEGA * seconds + 1.7 * phase));
    }

    /** The largest angle {@link #angle} can reach for a wind strength. */
    public static double maxAngle(double wind) {
        double w = BuntingSway.clampWind(wind);
        return 0.1 * w + (0.025 + 0.055 * w) * 1.3;
    }
}
