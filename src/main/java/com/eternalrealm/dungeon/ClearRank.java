package com.eternalrealm.dungeon;

import java.util.Locale;

/**
 * The grade of a dungeon clear on the result screen: how much of the time limit the party used
 * and how often its members fell. A better grade makes the bonus chest richer ({@link #bonusTier()}).
 */
public enum ClearRank {
    S(0.40, 0),
    A(0.60, 1),
    B(0.80, 3),
    C(1.00, Integer.MAX_VALUE);

    private final double maxTimeFraction;
    private final int maxFalls;

    ClearRank(double maxTimeFraction, int maxFalls) {
        this.maxTimeFraction = maxTimeFraction;
        this.maxFalls = maxFalls;
    }

    /**
     * @param durationTicks  time from the start to the killing blow
     * @param timeLimitTicks the dungeon's time limit (0 or less: no limit, judged against 20 minutes)
     * @param falls          how often members fell (戦闘不能) during the run
     */
    public static ClearRank of(long durationTicks, long timeLimitTicks, int falls) {
        long limit = timeLimitTicks > 0 ? timeLimitTicks : 20L * 60L * 20L;
        double fraction = Math.max(0.0, (double) durationTicks / limit);
        for (ClearRank rank : values()) {
            if (fraction <= rank.maxTimeFraction && falls <= rank.maxFalls) {
                return rank;
            }
        }
        return C;
    }

    /** 3 for S down to 0 for C: how good the bonus chest's contents are. */
    public int bonusTier() {
        return 3 - ordinal();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
