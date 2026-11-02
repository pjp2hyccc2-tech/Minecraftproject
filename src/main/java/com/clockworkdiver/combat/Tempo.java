package com.clockworkdiver.combat;

/**
 * The beat of the clock interior. A beat falls on every game tick t with (t - anchor) % period == 0.
 * The same maths runs on both sides so the HUD pendulum and the server's judgement agree.
 */
public final class Tempo {
    private Tempo() {}

    /** Fractional beats elapsed since the anchor. */
    public static double beats(long gameTime, float partialTick, long anchor, int period) {
        return ((gameTime - anchor) + (double) partialTick) / Math.max(1, period);
    }

    /** Distance in ticks (fractional) to the nearest beat. */
    public static double distanceToBeat(long gameTime, float partialTick, long anchor, int period) {
        double t = (gameTime - anchor) + (double) partialTick;
        double p = Math.max(1, period);
        double d = t - Math.floor(t / p) * p;
        return Math.min(d, p - d);
    }

    public static double perfectWindow(int lensLevel) {
        return 2.6 + 0.6 * Math.max(0, lensLevel);
    }

    public static double goodWindow(int period) {
        return Math.max(4.0, period * 0.3);
    }

    public static Quality judge(long gameTime, float partialTick, long anchor, int period, int lensLevel) {
        double dist = distanceToBeat(gameTime, partialTick, anchor, period);
        if (dist <= perfectWindow(lensLevel)) return Quality.PERFECT;
        if (dist <= goodWindow(period)) return Quality.GOOD;
        return Quality.MISS;
    }
}
