package com.eternalrealm.world.town;

/**
 * How a candle lantern floats on a canal: it bobs on the ripples, rocks a little with them, turns slowly and drifts
 * round a small circle inside its block, each lantern on its own phase. The candle flame flickers. Blocks, radians and
 * seconds; everything stays within {@link #MAX_DRIFT} of the block centre and {@link #MAX_BOB} of the water level.
 */
public final class FloatingLanternMotion {
    /** Furthest the lantern drifts from the centre of its block, blocks. */
    public static final double MAX_DRIFT = 0.16;
    /** Largest rise or fall on the ripples, blocks. */
    public static final double MAX_BOB = 0.035;
    /** Largest rocking angle, radians (about 4 degrees). */
    public static final double MAX_TILT = 0.07;

    private FloatingLanternMotion() {
    }

    /** Height above the calm water level. */
    public static double bob(double seconds, long seed) {
        double p = TownWind.phase(seed);
        return 0.025 * Math.sin(1.6 * seconds + p) + 0.01 * Math.sin(2.9 * seconds + 2.0 * p);
    }

    /** Sideways offset from the block centre: {@code out[0]} east, {@code out[1]} south. */
    public static void drift(double seconds, long seed, double[] out) {
        double p = TownWind.phase(seed ^ 0x5bd1e995L);
        double a = seconds * 0.21 + p;
        double r = 0.11 + 0.04 * Math.sin(seconds * 0.13 + p * 1.7);
        out[0] = r * Math.cos(a);
        out[1] = r * Math.sin(a) * 0.8;
    }

    /** Heading of the float, radians. */
    public static double yaw(double seconds, long seed) {
        return TownWind.phase(seed * 31L) + seconds * 0.12 + 0.25 * Math.sin(seconds * 0.4 + TownWind.phase(seed));
    }

    /** Rocking about the two horizontal axes: {@code out[0]} about x, {@code out[1]} about z. */
    public static void tilt(double seconds, long seed, double[] out) {
        double p = TownWind.phase(seed);
        out[0] = 0.05 * Math.sin(1.6 * seconds + p + 1.1);
        out[1] = 0.05 * Math.cos(1.3 * seconds + p * 0.7);
    }

    /** Brightness of the candle flame, about 0.8 .. 1.0. */
    public static double flicker(double seconds, long seed) {
        double p = TownWind.phase(seed);
        double f = 0.92 + 0.05 * Math.sin(9.7 * seconds + p) + 0.03 * Math.sin(23.0 * seconds + 3.0 * p);
        return Math.max(0.8, Math.min(1.0, f));
    }
}
