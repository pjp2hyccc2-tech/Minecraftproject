package com.eternalrealm.character;

import java.util.Objects;

/**
 * Server-configured progression rules. Immutable; replaced as a whole when the config is reloaded.
 *
 * @param levelCap            maximum level reachable this season (initial season: 50, hard limit {@link #MAX_LEVEL})
 * @param statPointsPerLevel  stat points granted per level gained
 * @param baseStatValue       starting value of every stat before allocation
 * @param maxPointsPerRequest upper bound of points a single allocation request may spend (anti-abuse)
 * @param experienceCurve     EXP needed per level
 * @param skillPointInterval  one skill point is earned every this many levels ({@code (level - 1) / interval})
 */
public record CharacterRules(int levelCap, int statPointsPerLevel, int baseStatValue, int maxPointsPerRequest,
                             ExperienceCurve experienceCurve, int skillPointInterval) {
    /** Hard upper bound supported by the data model (design target: level 100). */
    public static final int MAX_LEVEL = 100;
    public static final int MAX_STAT_POINTS_PER_LEVEL = 20;
    public static final int MAX_BASE_STAT = 100;
    public static final int MAX_POINTS_PER_REQUEST = 100;
    public static final int DEFAULT_SKILL_POINT_INTERVAL = 3;

    public CharacterRules {
        checkRange("levelCap", levelCap, 1, MAX_LEVEL);
        checkRange("statPointsPerLevel", statPointsPerLevel, 0, MAX_STAT_POINTS_PER_LEVEL);
        checkRange("baseStatValue", baseStatValue, 0, MAX_BASE_STAT);
        checkRange("maxPointsPerRequest", maxPointsPerRequest, 1, MAX_POINTS_PER_REQUEST);
        Objects.requireNonNull(experienceCurve, "experienceCurve");
        checkRange("skillPointInterval", skillPointInterval, 1, MAX_LEVEL);
    }

    /** Rules with the default skill point interval. */
    public CharacterRules(int levelCap, int statPointsPerLevel, int baseStatValue, int maxPointsPerRequest,
                          ExperienceCurve experienceCurve) {
        this(levelCap, statPointsPerLevel, baseStatValue, maxPointsPerRequest, experienceCurve,
                DEFAULT_SKILL_POINT_INTERVAL);
    }

    /** Defaults of the initial season (see docs/GAME_DESIGN.md). */
    public static CharacterRules defaults() {
        return new CharacterRules(50, 3, 5, 10, new PowerExperienceCurve(100, 1.5), DEFAULT_SKILL_POINT_INTERVAL);
    }

    /** Total skill points a character of {@code level} has earned. */
    public int earnedSkillPoints(int level) {
        checkRange("level", level, 1, MAX_LEVEL);
        return (level - 1) / skillPointInterval;
    }

    public boolean isAtCap(int level) {
        return level >= levelCap;
    }

    /** @return EXP needed for the next level, or 0 when the level is at or above the cap */
    public long experienceToNext(int level) {
        checkRange("level", level, 1, MAX_LEVEL);
        return isAtCap(level) ? 0L : experienceCurve.experienceToNext(level);
    }

    /** Total stat points a character of {@code level} has earned. */
    public int earnedStatPoints(int level) {
        checkRange("level", level, 1, MAX_LEVEL);
        return (level - 1) * statPointsPerLevel;
    }

    public StatBlock baseStats() {
        return StatBlock.uniform(baseStatValue);
    }

    private static void checkRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range [" + min + ", " + max + "]: " + value);
        }
    }
}
