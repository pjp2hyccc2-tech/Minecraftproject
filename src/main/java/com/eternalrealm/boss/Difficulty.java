package com.eternalrealm.boss;

import com.eternalrealm.core.ModInfo;

import java.util.Optional;

/**
 * Difficulty chosen when a boss instance is created. It never changes during a fight; rankings keep separate records
 * per difficulty (docs/RANKING_SYSTEM.md, boss time attack). {@link #id()} is the stable persistence/wire key.
 */
public enum Difficulty {
    /** The intended experience for a level-matched party (fight length about 2-4 minutes). */
    NORMAL("normal"),
    /** More health and damage, shorter cooldowns. */
    HARD("hard"),
    /** End content: even stronger numbers plus at least one extra ability in every phase. */
    EX("ex");

    private final String id;

    Difficulty(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "difficulty." + ModInfo.MOD_ID + "." + id;
    }

    public static Optional<Difficulty> byId(String id) {
        for (Difficulty difficulty : values()) {
            if (difficulty.id.equals(id)) {
                return Optional.of(difficulty);
            }
        }
        return Optional.empty();
    }
}
