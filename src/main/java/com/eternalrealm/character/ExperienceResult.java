package com.eternalrealm.character;

/**
 * Outcome of granting experience.
 *
 * @param applied          EXP actually added to the character
 * @param discarded        EXP that could not be applied (level cap reached or grant clamped)
 * @param previousLevel    level before the grant
 * @param newLevel         level after the grant
 * @param statPointsGained stat points granted by the level-ups
 * @param atCap            whether the character is now at the level cap
 */
public record ExperienceResult(long applied, long discarded, int previousLevel, int newLevel, int statPointsGained,
                               boolean atCap) {
    public int levelsGained() {
        return newLevel - previousLevel;
    }

    public boolean leveledUp() {
        return newLevel > previousLevel;
    }
}
