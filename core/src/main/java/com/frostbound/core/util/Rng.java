package com.frostbound.core.util;

/** Deterministic SplitMix64 generator whose whole state is one long, so it survives save and load. */
public final class Rng {
    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    public long state() {
        return state;
    }

    public void setState(long state) {
        this.state = state;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniform integer in [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 1) {
            return 0;
        }
        return (int) Math.floorMod(nextLong(), (long) bound);
    }

    /** Uniform integer in [lo, hi]. */
    public int range(int lo, int hi) {
        return hi <= lo ? lo : lo + nextInt(hi - lo + 1);
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean chance(double p) {
        return p > 0 && nextDouble() < p;
    }
}
