package com.birdiecraft.golf.engine;

/**
 * Initial conditions of a ball flight, computed from a {@link ShotInput}.
 * Velocities are in blocks per tick.
 */
public final class ShotLaunch {
    public final double x, y, z;
    public final double vx, vy, vz;
    /** Backspin (positive) / topspin (negative). Drives lift and bounce check. */
    public final double back;
    /** Side spin: positive curves to the right of the flight direction. */
    public final double side;
    /** Extra forward kick on bounces from topspin input. */
    public final double topBounce;
    /** Ball starts rolling (putt). */
    public final boolean rolling;
    /** Seed for deterministic tree deflections. */
    public final long seed;

    public ShotLaunch(double x, double y, double z, double vx, double vy, double vz,
                      double back, double side, double topBounce, boolean rolling, long seed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.back = back;
        this.side = side;
        this.topBounce = topBounce;
        this.rolling = rolling;
        this.seed = seed;
    }

    public double speed() {
        return Math.sqrt(vx * vx + vy * vy + vz * vz);
    }
}
