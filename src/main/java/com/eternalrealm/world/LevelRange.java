package com.eternalrealm.world;

import com.eternalrealm.character.CharacterRules;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Inclusive range of levels, {@code 1 <= min <= max <= CharacterRules.MAX_LEVEL}. Used for recommended area levels and
 * for the levels a monster template is designed for.
 */
public record LevelRange(int min, int max) {
    public LevelRange {
        Check.range("min", min, 1, CharacterRules.MAX_LEVEL);
        Check.range("max", max, min, CharacterRules.MAX_LEVEL);
    }

    public static LevelRange of(int min, int max) {
        return new LevelRange(min, max);
    }

    public boolean contains(int level) {
        return level >= min && level <= max;
    }

    /** The nearest level inside the range. */
    public int clamp(int level) {
        return Math.max(min, Math.min(max, level));
    }

    /** A uniformly distributed level of the range. */
    public int random(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return min + random.nextInt(max - min + 1);
    }

    @Override
    public String toString() {
        return "Lv" + min + "-" + max;
    }
}
