package com.eternalrealm.character;

/**
 * Defines how much experience each level requires.
 */
@FunctionalInterface
public interface ExperienceCurve {
    /**
     * @param level current level, {@code 1 <= level < CharacterRules.MAX_LEVEL}
     * @return experience needed to advance from {@code level} to {@code level + 1}; always {@code >= 1}
     */
    long experienceToNext(int level);
}
