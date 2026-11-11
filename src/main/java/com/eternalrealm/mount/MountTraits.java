package com.eternalrealm.mount;

import com.eternalrealm.core.ModInfo;

import java.util.SplittableRandom;

/**
 * A mount's individual values (個体値, docs/modules/mounts.md): <b>speed</b> (its top speed), <b>acceleration</b> and
 * <b>lift</b> (a horse's jump; a griffin's take-off, climb and glide). Each is {@value #MIN}..{@value #MAX}: the owner
 * asked for a floor, so no mount is a dud. A value scales the species' number by {@link #factor(int)}, from
 * {@value #FACTOR_LOW} at the floor to {@value #FACTOR_HIGH} at the top; the species' own numbers are the middle
 * ({@value #STANDARD_VALUE}). Values are rolled evenly between the floor and the top; the {@link Grade} comes from
 * their sum. Immutable.
 */
public record MountTraits(int speed, int acceleration, int lift) {
    public static final int MIN = 30;
    public static final int MAX = 100;
    public static final int STANDARD_VALUE = 65;
    public static final double FACTOR_LOW = 0.92;
    public static final double FACTOR_HIGH = 1.08;
    /** The middle of every range: the species' own numbers (and the mounts bought before individuality). */
    public static final MountTraits STANDARD = new MountTraits(STANDARD_VALUE, STANDARD_VALUE, STANDARD_VALUE);

    public MountTraits {
        check("speed", speed);
        check("acceleration", acceleration);
        check("lift", lift);
    }

    private static void check(String name, int value) {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException(name + " out of range [" + MIN + ", " + MAX + "]: " + value);
        }
    }

    /** {@code value} clamped into the range (for lenient reading of stored data). */
    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    /** How a value scales its stat: {@value #FACTOR_LOW} at the floor, 1 at {@value #STANDARD_VALUE}, up to 1.08. */
    public static double factor(int value) {
        return FACTOR_LOW + (FACTOR_HIGH - FACTOR_LOW) * (clamp(value) - MIN) / (double) (MAX - MIN);
    }

    public int total() {
        return speed + acceleration + lift;
    }

    public Grade grade() {
        return Grade.of(total());
    }

    /** Three values, each even between the floor and the top. */
    public static MountTraits roll(SplittableRandom random) {
        return new MountTraits(random.nextInt(MIN, MAX + 1), random.nextInt(MIN, MAX + 1),
                random.nextInt(MIN, MAX + 1));
    }

    /**
     * The quality of a mount from the sum of its values. With even rolls about 28 % are C, 48 % B, 19 % A, 4 % S and
     * about one in 177 SS.
     */
    public enum Grade {
        C("c", 0, 0x9A9AA6),
        B("b", 174, 0x6FD36F),
        A("a", 222, 0x4FA3FF),
        S("s", 255, 0xC070FF),
        SS("ss", 279, 0xFFB020);

        private final String id;
        private final int minimumTotal;
        private final int color;

        Grade(String id, int minimumTotal, int color) {
            this.id = id;
            this.minimumTotal = minimumTotal;
            this.color = color;
        }

        public String id() {
            return id;
        }

        /** The least sum of the three values for this grade (an average of 58, 74, 85 and 93). */
        public int minimumTotal() {
            return minimumTotal;
        }

        public int color() {
            return color;
        }

        public String translationKey() {
            return "mount." + ModInfo.MOD_ID + ".grade." + id;
        }

        public static Grade of(int total) {
            Grade best = C;
            for (Grade grade : values()) {
                if (total >= grade.minimumTotal) {
                    best = grade;
                }
            }
            return best;
        }

        public static java.util.Optional<Grade> byId(String id) {
            for (Grade grade : values()) {
                if (grade.id.equals(id)) {
                    return java.util.Optional.of(grade);
                }
            }
            return java.util.Optional.empty();
        }
    }
}
