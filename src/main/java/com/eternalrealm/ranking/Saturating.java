package com.eternalrealm.ranking;

/**
 * Overflow-free arithmetic for non-negative counters and scores. A season counter or score that would overflow stays
 * at {@link Long#MAX_VALUE} instead of wrapping to a negative value, which would silently drop a player to the bottom
 * of every ranking.
 */
final class Saturating {
    private Saturating() {
    }

    /** @param a non-negative @param b non-negative */
    static long add(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }

    /** @param a non-negative @param b non-negative */
    static long multiply(long a, long b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }
}
