package com.eternalrealm.character;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Immutable percentage bonuses of the primary stats ({@code 0.10} = +10 %), the second half of a character's stats.
 *
 * <p>They apply after every flat source, so they grow with everything else a character has:</p>
 *
 * <pre>
 * final stat = min(9999, floor((own + job + equipment flat) × (1 + Σ percent)))
 * </pre>
 *
 * <p>Percent bonuses add up (two +10 % pieces give +20 %, not ×1.21). Every value is within {@code [0, MAX]}; sums
 * saturate there.</p>
 */
public final class StatPercents {
    /** Largest bonus of one stat: +10 000 %. Far above anything a build reaches; a sanity limit for stored data. */
    public static final double MAX = 100.0;
    public static final StatPercents ZERO = new StatPercents(new double[StatType.COUNT]);

    private final double[] values;

    private StatPercents(double[] values) {
        this.values = values;
    }

    /** Builds from a map; missing stats are 0. */
    public static StatPercents of(Map<StatType, Double> source) {
        Objects.requireNonNull(source, "source");
        double[] values = new double[StatType.COUNT];
        for (StatType type : StatType.values()) {
            Double value = source.get(type);
            values[type.ordinal()] = value == null ? 0.0 : check(value);
        }
        return new StatPercents(values);
    }

    public static StatPercents of(StatType type, double value) {
        return ZERO.with(type, value);
    }

    public double get(StatType type) {
        return values[type.ordinal()];
    }

    public StatPercents with(StatType type, double value) {
        double[] copy = values.clone();
        copy[type.ordinal()] = check(value);
        return new StatPercents(copy);
    }

    /** Element-wise sum, saturating at {@link #MAX}. */
    public StatPercents plus(StatPercents other) {
        if (other.isZero()) {
            return this;
        }
        if (isZero()) {
            return other;
        }
        double[] sum = new double[StatType.COUNT];
        for (int i = 0; i < sum.length; i++) {
            sum[i] = Math.min(MAX, values[i] + other.values[i]);
        }
        return new StatPercents(sum);
    }

    /** Every value × {@code factor} (≥ 0), saturating at {@link #MAX}. */
    public StatPercents scaled(double factor) {
        if (!(factor >= 0.0) || Double.isInfinite(factor)) {
            throw new IllegalArgumentException("Invalid factor: " + factor);
        }
        double[] scaled = new double[StatType.COUNT];
        for (int i = 0; i < scaled.length; i++) {
            scaled[i] = Math.min(MAX, values[i] * factor);
        }
        return new StatPercents(scaled);
    }

    public boolean isZero() {
        for (double value : values) {
            if (value != 0.0) {
                return false;
            }
        }
        return true;
    }

    /** {@code flat} with these bonuses applied: {@code min(9999, floor(flat × (1 + percent)))} per stat. */
    public StatBlock applyTo(StatBlock flat) {
        Objects.requireNonNull(flat, "flat");
        if (isZero()) {
            return flat;
        }
        Map<StatType, Integer> out = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            out.put(type, apply(flat.get(type), get(type)));
        }
        return StatBlock.of(out);
    }

    /** What these bonuses add on top of {@code flat} ({@code applyTo(flat) − flat}). */
    public StatBlock bonusOn(StatBlock flat) {
        Objects.requireNonNull(flat, "flat");
        if (isZero()) {
            return StatBlock.ZERO;
        }
        Map<StatType, Integer> out = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            out.put(type, apply(flat.get(type), get(type)) - flat.get(type));
        }
        return StatBlock.of(out);
    }

    /** One stat: {@code min(9999, floor(flat × (1 + percent)))} (a hair of tolerance for floating point). */
    public static int apply(int flat, double percent) {
        double value = Math.floor(flat * (1.0 + percent) + 1.0e-6);
        return (int) Math.min(StatBlock.MAX_VALUE, Math.max(flat, value));
    }

    public Map<StatType, Double> asMap() {
        Map<StatType, Double> map = new EnumMap<>(StatType.class);
        for (StatType type : StatType.values()) {
            map.put(type, get(type));
        }
        return Collections.unmodifiableMap(map);
    }

    private static double check(double value) {
        if (!(value >= 0.0) || value > MAX) {
            throw new IllegalArgumentException("Stat percent out of range [0, " + MAX + "]: " + value);
        }
        return value + 0.0; // never −0.0, so equal bonuses are equal
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof StatPercents other && Arrays.equals(values, other.values));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "StatPercents{", "}");
        for (StatType type : StatType.values()) {
            if (get(type) != 0.0) {
                joiner.add(type.id() + "=" + get(type));
            }
        }
        return joiner.toString();
    }
}
