package com.hitori.game;

import com.hitori.HitoriConfig;

/**
 * The night's own clock (docs/DESIGN.md §1), not the sky's: 02:20 when you start; about four times as fast as real
 * time until 3:00 (it waits at 02:59 until the preparation is done, then hurries to 3:00); from 3:00 twice as fast
 * (configurable). Dawn is 5:00.
 */
public final class Clock {
    public static final double START = 2 * 3600 + 20 * 60;
    public static final double THREE = 3 * 3600;
    public static final double HOLD = THREE - 25;
    public static final double CALL = 3 * 3600 + 33 * 60;
    public static final double FOUR = 4 * 3600;
    public static final double DAWN = 5 * 3600;

    private Clock() {}

    /** Advances the clock by one server tick. */
    public static void tick(GameData d) {
        switch (d.phase) {
            case GameData.PHASE_PREP -> {
                boolean ready = Step.allDone(d, Step.PREP);
                double rate = ready ? 30.0 : HitoriConfig.prepClockRate();
                double next = d.clock + rate / 20.0;
                if (!ready) next = Math.min(next, HOLD);
                d.clock = Math.min(next, THREE);
            }
            case GameData.PHASE_RITUAL, GameData.PHASE_HIDING, GameData.PHASE_FINISH -> d.clock += HitoriConfig.nightClockRate() / 20.0;
            default -> {
            }
        }
    }

    public static int hours(double clock) {
        return (int) (clock / 3600) % 24;
    }

    public static int minutes(double clock) {
        return (int) (clock / 60) % 60;
    }

    public static int seconds(double clock) {
        return (int) clock % 60;
    }

    /** 「03:21」 */
    public static String hhmm(double clock) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hours(clock), minutes(clock));
    }

    /** 「2026/11/03 03:21:07」 (the cameras' timestamp; the night of 2 → 3 November). */
    public static String stamp(double clock) {
        return String.format(java.util.Locale.ROOT, "2026/11/03 %02d:%02d:%02d", hours(clock), minutes(clock), seconds(clock));
    }
}
