package com.eternalrealm.character;

/**
 * What changes in a server config file of an older balance revision (Forge keeps the values a file has, so a world
 * made before a balance change would never see it). Only a value still at the default of its time moves to the new
 * default; a value the server chose stays. Pure.
 *
 * <ol>
 *     <li>2026-11-14 (「スキルポイント獲得率をもう少し上げて」, with the second classes at level 40): skill points
 *     2 → 3 per level and at every 10th level.</li>
 * </ol>
 */
public final class BalanceRevisions {
    /** The revision this build writes. */
    public static final int CURRENT = 1;
    /** Skill points per level and at every 10th level before revision 1. */
    public static final int OLD_SKILL_POINTS = 2;

    private BalanceRevisions() {
    }

    /** The skill points per level a file of revision {@code from} should have. */
    public static int skillPointsPerLevel(int from, int value) {
        return from < 1 && value == OLD_SKILL_POINTS ? CharacterRules.DEFAULT_SKILL_POINTS_PER_LEVEL : value;
    }

    /** The extra skill points at every 10th level a file of revision {@code from} should have. */
    public static int skillPointMilestoneBonus(int from, int value) {
        return from < 1 && value == OLD_SKILL_POINTS ? CharacterRules.DEFAULT_SKILL_POINT_MILESTONE_BONUS : value;
    }
}
