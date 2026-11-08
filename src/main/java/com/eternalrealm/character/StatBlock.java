package com.eternalrealm.character;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Immutable set of values for all {@link StatType}s. Every value is within {@code [0, MAX_VALUE]}.
 */
public final class StatBlock {
    public static final int MAX_VALUE = 9_999;
    public static final StatBlock ZERO = new StatBlock(new int[StatType.COUNT]);

    private final int[] values;

    private StatBlock(int[] values) {
        this.values = values;
    }

    public static StatBlock uniform(int value) {
        checkValue(value);
        int[] values = new int[StatType.COUNT];
        Arrays.fill(values, value);
        return new StatBlock(values);
    }

    /** Builds a block from a map; missing stats default to 0. */
    public static StatBlock of(Map<StatType, Integer> source) {
        Objects.requireNonNull(source, "source");
        int[] values = new int[StatType.COUNT];
        for (StatType type : StatType.values()) {
            Integer value = source.get(type);
            values[type.ordinal()] = value == null ? 0 : checkValue(value);
        }
        return new StatBlock(values);
    }

    public int get(StatType type) {
        return values[type.ordinal()];
    }

    public StatBlock with(StatType type, int value) {
        checkValue(value);
        int[] copy = values.clone();
        copy[type.ordinal()] = value;
        return new StatBlock(copy);
    }

    /** Element-wise sum, saturating at {@link #MAX_VALUE}. */
    public StatBlock plus(StatBlock other) {
        int[] sum = new int[StatType.COUNT];
        for (int i = 0; i < sum.length; i++) {
            sum[i] = Math.min(MAX_VALUE, values[i] + other.values[i]);
        }
        return new StatBlock(sum);
    }

    public long total() {
        long total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    public Map<StatType, Integer> asMap() {
        Map<StatType, Integer> map = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            map.put(type, get(type));
        }
        return Collections.unmodifiableMap(map);
    }

    private static int checkValue(int value) {
        if (value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException("Stat value out of range [0, " + MAX_VALUE + "]: " + value);
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof StatBlock other && Arrays.equals(values, other.values));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "StatBlock{", "}");
        for (StatType type : StatType.values()) {
            joiner.add(type.id() + "=" + get(type));
        }
        return joiner.toString();
    }
}
