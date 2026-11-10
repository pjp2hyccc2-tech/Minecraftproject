package com.eternalrealm.character;

import java.util.Objects;

/**
 * Server-configured progression rules. Immutable; replaced as a whole when the config is reloaded.
 *
 * @param levelCap            maximum level reachable (default and hard limit {@link #MAX_LEVEL})
 * @param statPointsPerLevel  stat points granted per level gained
 * @param baseStatValue       starting value of every stat before allocation
 * @param maxPointsPerRequest upper bound of points a single allocation request may spend (anti-abuse)
 * @param experienceCurve          EXP needed per level
 * @param skillPointsPerLevel      skill points earned with every level gained
 * @param skillPointMilestoneBonus extra skill points at every {@value #SKILL_POINT_MILESTONE}th level
 */
public record CharacterRules(int levelCap, int statPointsPerLevel, int baseStatValue, int maxPointsPerRequest,
                             ExperienceCurve experienceCurve, int skillPointsPerLevel, int skillPointMilestoneBonus) {
    /** Hard upper bound supported by the data model, and the default cap. */
    public static final int MAX_LEVEL = 100;
    public static final int MAX_STAT_POINTS_PER_LEVEL = 20;
    public static final int MAX_BASE_STAT = 100;
    public static final int MAX_POINTS_PER_REQUEST = 100;
    /**
     * Skill points per level: a level-50 character (108 points) masters about 40 % of a job's tree, a level-100
     * character (218 points) about 80 % — never all of it.
     */
    public static final int DEFAULT_SKILL_POINTS_PER_LEVEL = 2;
    public static final int DEFAULT_SKILL_POINT_MILESTONE_BONUS = 2;
    public static final int SKILL_POINT_MILESTONE = 10;
    public static final int MAX_SKILL_POINTS_PER_LEVEL = 10;
    public static final int MAX_SKILL_POINT_MILESTONE_BONUS = 20;

    public CharacterRules {
        checkRange("levelCap", levelCap, 1, MAX_LEVEL);
        checkRange("statPointsPerLevel", statPointsPerLevel, 0, MAX_STAT_POINTS_PER_LEVEL);
        checkRange("baseStatValue", baseStatValue, 0, MAX_BASE_STAT);
        checkRange("maxPointsPerRequest", maxPointsPerRequest, 1, MAX_POINTS_PER_REQUEST);
        Objects.requireNonNull(experienceCurve, "experienceCurve");
        checkRange("skillPointsPerLevel", skillPointsPerLevel, 0, MAX_SKILL_POINTS_PER_LEVEL);
        checkRange("skillPointMilestoneBonus", skillPointMilestoneBonus, 0, MAX_SKILL_POINT_MILESTONE_BONUS);
    }

    /** Rules with the default skill point economy. */
    public CharacterRules(int levelCap, int statPointsPerLevel, int baseStatValue, int maxPointsPerRequest,
                          ExperienceCurve experienceCurve) {
        this(levelCap, statPointsPerLevel, baseStatValue, maxPointsPerRequest, experienceCurve,
                DEFAULT_SKILL_POINTS_PER_LEVEL, DEFAULT_SKILL_POINT_MILESTONE_BONUS);
    }

    /** Default rules (see docs/GAME_DESIGN.md): level cap 100. */
    public static CharacterRules defaults() {
        return new CharacterRules(MAX_LEVEL, 3, 5, 10, new PowerExperienceCurve(100, 1.5), DEFAULT_SKILL_POINTS_PER_LEVEL,
                DEFAULT_SKILL_POINT_MILESTONE_BONUS);
    }

    /**
     * Total skill points a character of {@code level} has earned:
     * {@code (level − 1) × perLevel + ⌊level / 10⌋ × milestoneBonus} (defaults: 20 at level 10, 64 at 30, 108 at 50,
     * 218 at 100).
     */
    public int earnedSkillPoints(int level) {
        checkRange("level", level, 1, MAX_LEVEL);
        return (level - 1) * skillPointsPerLevel + (level / SKILL_POINT_MILESTONE) * skillPointMilestoneBonus;
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
