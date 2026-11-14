package com.eternalrealm.character;

/**
 * {@code expToNext(level) = round(base * level ^ exponent)} (at least 1), precomputed for every level; from
 * {@code lateLevel} on multiplied by {@code lateMultiplier} (the late game: 2.5 times from level 40 by default, owner
 * 2026-11-14: 「レベルがすぐ上がりすぎるからレベル40からは必要EXP量を今の2.5倍とかにして」).
 * {@link StrictMath} keeps the table bit-identical on every JVM/platform; rounding (instead of ceil) keeps exact
 * powers such as {@code 4^1.5 = 8} from turning into 801 because of floating point noise.
 */
public final class PowerExperienceCurve implements ExperienceCurve {
    public static final int DEFAULT_LATE_LEVEL = 40;
    public static final double DEFAULT_LATE_MULTIPLIER = 2.5;
    public static final double MAX_LATE_MULTIPLIER = 10.0;
    public static final long MIN_BASE = 1L;
    public static final long MAX_BASE = 1_000_000L;
    public static final double MIN_EXPONENT = 1.0;
    public static final double MAX_EXPONENT = 3.0;
    /** Upper bound per level; keeps all EXP arithmetic far away from long overflow. */
    public static final long MAX_EXPERIENCE_PER_LEVEL = 1_000_000_000_000L;

    private final long base;
    private final double exponent;
    private final int lateLevel;
    private final double lateMultiplier;
    private final long[] table;

    /** The plain power curve (no late-game multiplier). */
    public PowerExperienceCurve(long base, double exponent) {
        this(base, exponent, CharacterRules.MAX_LEVEL, 1.0);
    }

    /**
     * @param lateLevel      first level whose EXP to the next level is multiplied (2..{@link CharacterRules#MAX_LEVEL};
     *                       {@code MAX_LEVEL}: never)
     * @param lateMultiplier the multiplier (1..{@value #MAX_LATE_MULTIPLIER})
     */
    public PowerExperienceCurve(long base, double exponent, int lateLevel, double lateMultiplier) {
        if (base < MIN_BASE || base > MAX_BASE) {
            throw new IllegalArgumentException("base out of range [" + MIN_BASE + ", " + MAX_BASE + "]: " + base);
        }
        if (Double.isNaN(exponent) || exponent < MIN_EXPONENT || exponent > MAX_EXPONENT) {
            throw new IllegalArgumentException("exponent out of range [" + MIN_EXPONENT + ", " + MAX_EXPONENT + "]: " + exponent);
        }
        if (lateLevel < 2 || lateLevel > CharacterRules.MAX_LEVEL) {
            throw new IllegalArgumentException("lateLevel out of range [2, " + CharacterRules.MAX_LEVEL + "]: "
                    + lateLevel);
        }
        if (Double.isNaN(lateMultiplier) || lateMultiplier < 1.0 || lateMultiplier > MAX_LATE_MULTIPLIER) {
            throw new IllegalArgumentException("lateMultiplier out of range [1, " + MAX_LATE_MULTIPLIER + "]: "
                    + lateMultiplier);
        }
        this.base = base;
        this.exponent = exponent;
        this.lateLevel = lateLevel;
        this.lateMultiplier = lateMultiplier;
        this.table = new long[CharacterRules.MAX_LEVEL];
        for (int level = 1; level < CharacterRules.MAX_LEVEL; level++) {
            double raw = base * StrictMath.pow(level, exponent) * (level >= lateLevel ? lateMultiplier : 1.0);
            if (raw > MAX_EXPERIENCE_PER_LEVEL) {
                throw new IllegalArgumentException("Curve exceeds " + MAX_EXPERIENCE_PER_LEVEL + " EXP at level " + level);
            }
            table[level] = Math.max(1L, Math.round(raw));
        }
    }

    @Override
    public long experienceToNext(int level) {
        if (level < 1 || level >= CharacterRules.MAX_LEVEL) {
            throw new IllegalArgumentException("level out of range [1, " + (CharacterRules.MAX_LEVEL - 1) + "]: " + level);
        }
        return table[level];
    }

    public long base() {
        return base;
    }

    public double exponent() {
        return exponent;
    }

    public int lateLevel() {
        return lateLevel;
    }

    public double lateMultiplier() {
        return lateMultiplier;
    }

    @Override
    public String toString() {
        return "PowerExperienceCurve{base=" + base + ", exponent=" + exponent + ", lateLevel=" + lateLevel
                + ", lateMultiplier=" + lateMultiplier + "}";
    }
}
