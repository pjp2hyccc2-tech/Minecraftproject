package com.clockworkdiver.combat;

/** How well an action matched the clock's beat. */
public enum Quality {
    MISS, GOOD, PERFECT;

    public static Quality byOrdinal(int i) {
        Quality[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
