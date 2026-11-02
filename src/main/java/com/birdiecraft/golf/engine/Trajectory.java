package com.birdiecraft.golf.engine;

/** Result of a ball simulation: one position per tick plus per-tick event flags. */
public final class Trajectory {
    public enum Result {
        /** Ball stopped on the ground. */
        REST,
        /** Ball dropped into the cup. */
        IN_CUP,
        /** Ball entered water (or lava). */
        WATER,
        /** Ball fell out of the world. */
        OUT_OF_WORLD,
        /** Simulation hit the tick limit (treated as rest where it is when on the ground). */
        TIMEOUT,
        /** Simulation stopped after the first landing (preview only). */
        LANDED
    }

    public static final byte EV_BOUNCE = 1;
    public static final byte EV_TREE = 2;
    public static final byte EV_LIP = 4;
    public static final byte EV_CUP = 8;
    public static final byte EV_SPLASH = 16;
    public static final byte EV_WALL = 32;
    public static final byte EV_LAND = 64;

    public final float[] xs;
    public final float[] ys;
    public final float[] zs;
    public final byte[] events;
    public final Result result;
    public final double endX, endY, endZ;
    /** Lie under the ball at rest (only meaningful for REST / TIMEOUT). */
    public final Lie restLie;
    /** Carry in blocks (horizontal distance to the first landing), -1 when the ball never landed. */
    public final double carry;
    public final int firstLandTick;
    /** Peak height above the start, in blocks. */
    public final double apex;
    public final boolean touchedTree;
    /** Horizontal speed when the ball dropped into the cup (0 when not holed). */
    public final double captureSpeed;

    public Trajectory(float[] xs, float[] ys, float[] zs, byte[] events, Result result,
                      double endX, double endY, double endZ, Lie restLie, double carry,
                      int firstLandTick, double apex, boolean touchedTree, double captureSpeed) {
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.events = events;
        this.result = result;
        this.endX = endX;
        this.endY = endY;
        this.endZ = endZ;
        this.restLie = restLie;
        this.carry = carry;
        this.firstLandTick = firstLandTick;
        this.apex = apex;
        this.touchedTree = touchedTree;
        this.captureSpeed = captureSpeed;
    }

    public int length() {
        return xs.length;
    }

    public double totalDistance() {
        return GolfUnits.horizontalDistance(xs[0], zs[0], endX, endZ);
    }
}
