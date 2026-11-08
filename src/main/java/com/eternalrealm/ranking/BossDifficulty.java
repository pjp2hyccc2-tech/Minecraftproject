package com.eternalrealm.ranking;

import com.eternalrealm.core.ModInfo;

import java.util.List;
import java.util.Optional;

/**
 * Difficulty of a boss fight instance, fixed when the instance is created. Each difficulty has its own time-attack
 * boards. {@link #id()} is the stable persistence key.
 */
public enum BossDifficulty {
    NORMAL("normal"),
    HARD("hard"),
    EX("ex");

    private static final List<BossDifficulty> ALL = List.of(values());

    private final String id;

    BossDifficulty(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "boss_difficulty." + ModInfo.MOD_ID + "." + id;
    }

    public static List<BossDifficulty> all() {
        return ALL;
    }

    public static Optional<BossDifficulty> byId(String id) {
        for (BossDifficulty difficulty : ALL) {
            if (difficulty.id.equals(id)) {
                return Optional.of(difficulty);
            }
        }
        return Optional.empty();
    }
}
