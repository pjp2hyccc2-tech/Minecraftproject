package com.eternalrealm.item;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.random.RandomGenerator;

/** A generator that returns scripted values, so tests can force exact rolls (and fail if more values are drawn). */
final class ScriptedRandom implements RandomGenerator {
    private final Deque<Double> doubles = new ArrayDeque<>();
    private final Deque<Integer> ints = new ArrayDeque<>();

    static ScriptedRandom ofDoubles(double... values) {
        ScriptedRandom random = new ScriptedRandom();
        for (double value : values) {
            random.doubles.add(value);
        }
        return random;
    }

    static ScriptedRandom ofInts(int... values) {
        ScriptedRandom random = new ScriptedRandom();
        for (int value : values) {
            random.ints.add(value);
        }
        return random;
    }

    boolean exhausted() {
        return doubles.isEmpty() && ints.isEmpty();
    }

    @Override
    public double nextDouble() {
        if (doubles.isEmpty()) {
            throw new IllegalStateException("no scripted double left");
        }
        return doubles.poll();
    }

    @Override
    public int nextInt(int bound) {
        if (ints.isEmpty()) {
            throw new IllegalStateException("no scripted int left");
        }
        int value = ints.poll();
        if (value < 0 || value >= bound) {
            throw new IllegalStateException("scripted int " + value + " outside [0, " + bound + ")");
        }
        return value;
    }

    @Override
    public long nextLong() {
        throw new UnsupportedOperationException("only nextDouble() and nextInt(bound) are scripted");
    }
}
