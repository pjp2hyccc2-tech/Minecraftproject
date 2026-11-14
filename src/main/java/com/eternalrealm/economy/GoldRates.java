package com.eternalrealm.economy;

/**
 * How much gold play brings in. Gold earned by playing — monsters, lords and calamities, dungeon and raid rewards,
 * quest rewards — is {@value #EARNED} times what the formulas of each source give (owner, 2026-11-14: 「お金の入手率を
 * 1.2倍にして」). Prices, refunds, sales between players and what the shop pays for materials are not earnings and keep
 * their values. Pure.
 */
public final class GoldRates {
    /** Multiplier of gold earned in play. */
    public static final double EARNED = 1.2;

    private GoldRates() {
    }

    /** {@code base} gold of a source, as earned now (rounded). */
    public static long earned(long base) {
        return Math.round(base * EARNED);
    }

    /** {@code base} gold of a source, as earned now (unrounded, for formulas that round later). */
    public static double earned(double base) {
        return base * EARNED;
    }
}
