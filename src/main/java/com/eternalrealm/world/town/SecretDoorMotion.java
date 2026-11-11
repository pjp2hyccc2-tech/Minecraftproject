package com.eternalrealm.world.town;

/**
 * Timing of a secret door: the wall panel first sinks one pixel back into the wall, then slides one block sideways
 * into the wall beside it (0.6 s in all, slow at both ends like heavy stone), stays open for five seconds and closes
 * again the same way once nobody stands in the doorway. Times are game ticks; {@code progress} runs from 0 (closed)
 * to 1 (open).
 */
public final class SecretDoorMotion {
    public static final int IDLE = 0;
    public static final int OPENING = 1;
    public static final int CLOSING = 2;
    /** Length of the slide, ticks (0.6 s). */
    public static final int MOVE_TICKS = 12;
    /** How long it stays open before it tries to close, ticks (5 s). */
    public static final int STAY_OPEN_TICKS = 100;
    /** Wait before trying again while someone stands in the doorway, ticks. */
    public static final int RETRY_TICKS = 10;
    /** How often the doorway is checked while the door closes, ticks. */
    public static final int WATCH_TICKS = 2;
    /** Depth the panel sinks before it slides, blocks. */
    public static final double RECESS = 1.0 / 16.0;
    /** Distance it slides, blocks. */
    public static final double SLIDE = 1.0;
    /** Part of the motion spent sinking. */
    private static final double SINK = 0.3;

    private SecretDoorMotion() {
    }

    /**
     * How open the door is, 0..1.
     *
     * @param anim  {@link #IDLE}, {@link #OPENING} or {@link #CLOSING}
     * @param start game time the current motion started
     * @param now   game time (fractional with the partial tick)
     * @param open  whether the door is (still) open when idle
     */
    public static double progress(int anim, long start, double now, boolean open) {
        double t = clamp01((now - start) / MOVE_TICKS);
        if (anim == OPENING) {
            return t;
        }
        if (anim == CLOSING) {
            return 1.0 - t;
        }
        return open ? 1.0 : 0.0;
    }

    /** Whether a motion that started at {@code start} is over at {@code now}. */
    public static boolean finished(long start, long now) {
        return now - start >= MOVE_TICKS;
    }

    /** How far the panel has sunk back into the wall at a progress, blocks. */
    public static double recess(double progress) {
        return RECESS * ease(clamp01(progress / SINK));
    }

    /** How far the panel has slid sideways at a progress, blocks. */
    public static double slide(double progress) {
        return SLIDE * ease(clamp01((progress - SINK) / (1.0 - SINK)));
    }

    /**
     * Start time for a motion in the other direction that continues from where the current one is at {@code now}
     * (someone stepped into a closing door: it opens again from where it was).
     */
    public static long reversedStart(long start, long now) {
        long elapsed = Math.max(0L, Math.min(MOVE_TICKS, now - start));
        return now - (MOVE_TICKS - elapsed);
    }

    /**
     * Which way the panel slides: +1 = to the right as seen from the front, -1 = to the left. It prefers the right and
     * takes the left only if solid wall waits there and not on the right.
     */
    public static int slideSide(boolean rightSolid, boolean leftSolid) {
        return !rightSolid && leftSolid ? -1 : 1;
    }

    /** Smoothstep: starts and stops gently. */
    public static double ease(double t) {
        double x = clamp01(t);
        return x * x * (3.0 - 2.0 * x);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
