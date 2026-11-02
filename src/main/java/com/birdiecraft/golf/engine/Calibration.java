package com.birdiecraft.golf.engine;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a wanted carry distance to a launch speed for a given launch angle and backspin, by
 * sampling flights on flat, windless ground. Results are cached per (angle, spin) bucket.
 */
public final class Calibration {
    private static final int SAMPLES = 160;
    private static final double MIN_SPEED = 0.01;
    private static final double MAX_SPEED = 3.2;
    private static final FlatTerrain FLAT = new FlatTerrain(Lie.FAIRWAY);
    private static final ConcurrentHashMap<Long, double[]> CURVES = new ConcurrentHashMap<>();

    private Calibration() {
    }

    private static long key(double angleDeg, double back) {
        long a = Math.round(angleDeg * 4.0);
        long b = Math.round(back * 50.0);
        return (a << 32) ^ (b & 0xffffffffL);
    }

    /** Carry in blocks for the given launch on flat ground without wind. */
    public static double carryFor(double angleDeg, double back, double speed) {
        double r = Math.toRadians(angleDeg);
        ShotLaunch l = new ShotLaunch(0, 0.02, 0, 0, speed * Math.sin(r), speed * Math.cos(r),
                back, 0, 0, false, 0L);
        Trajectory tr = BallPhysics.simulateCarry(FLAT, l, Wind.CALM, BallPhysics.MAX_TICKS);
        return tr.carry < 0 ? GolfUnits.horizontalDistance(0, 0, tr.endX, tr.endZ) : tr.carry;
    }

    private static double[] curve(double angleDeg, double back) {
        double qa = Math.round(angleDeg * 4.0) / 4.0;
        double qb = Math.round(back * 50.0) / 50.0;
        return CURVES.computeIfAbsent(key(qa, qb), k -> {
            double[] c = new double[SAMPLES];
            for (int i = 0; i < SAMPLES; i++) {
                c[i] = carryFor(qa, qb, speedAt(i));
            }
            // enforce monotonic growth for a clean inverse
            for (int i = 1; i < SAMPLES; i++) {
                if (c[i] < c[i - 1]) c[i] = c[i - 1] + 1.0e-6;
            }
            return c;
        });
    }

    private static double speedAt(int i) {
        return MIN_SPEED + (MAX_SPEED - MIN_SPEED) * i / (SAMPLES - 1.0);
    }

    /** Launch speed (blocks/tick) that produces the wanted carry (blocks). */
    public static double speedFor(double angleDeg, double back, double carry) {
        if (carry <= 0) return 0;
        double[] c = curve(angleDeg, back);
        if (carry <= c[0]) return speedAt(0) * carry / Math.max(1.0e-6, c[0]);
        if (carry >= c[SAMPLES - 1]) return speedAt(SAMPLES - 1);
        int lo = 0;
        int hi = SAMPLES - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (c[mid] < carry) lo = mid;
            else hi = mid;
        }
        double f = (carry - c[lo]) / (c[hi] - c[lo]);
        return speedAt(lo) + (speedAt(hi) - speedAt(lo)) * f;
    }

    /** Initial speed of a putt that rolls the given distance on a flat green. */
    public static double puttSpeedFor(double distanceBlocks) {
        // v^2 = 2 a d  (sub-stepped friction converges to the continuous result)
        return Math.sqrt(2.0 * Lie.GREEN.rollDecel * Math.max(0, distanceBlocks));
    }
}
