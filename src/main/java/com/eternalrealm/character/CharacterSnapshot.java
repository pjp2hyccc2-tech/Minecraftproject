package com.eternalrealm.character;

import com.eternalrealm.season.SeasonId;

import java.util.Objects;

/**
 * Read-only view of a character sent to the owning client (HUD, character screen) and used by commands.
 * The client never computes game state; it only displays the latest snapshot from the server.
 *
 * @param experienceToNext 0 when the character is at the level cap
 */
public record CharacterSnapshot(SeasonId seasonId, int level, int levelCap, long experience, long experienceToNext,
                                int unspentStatPoints, StatBlock allocatedStats, StatBlock totalStats) {
    public CharacterSnapshot {
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(allocatedStats, "allocatedStats");
        Objects.requireNonNull(totalStats, "totalStats");
        if (level < 1 || level > CharacterRules.MAX_LEVEL) {
            throw new IllegalArgumentException("level out of range: " + level);
        }
        if (levelCap < 1 || levelCap > CharacterRules.MAX_LEVEL) {
            throw new IllegalArgumentException("levelCap out of range: " + levelCap);
        }
        if (experience < 0 || experienceToNext < 0 || unspentStatPoints < 0) {
            throw new IllegalArgumentException("negative progression value");
        }
    }

    public boolean atCap() {
        return level >= levelCap;
    }

    /** @return progress towards the next level in {@code [0, 1]}; 1 at the cap */
    public double progress() {
        if (atCap() || experienceToNext <= 0) {
            return 1.0;
        }
        return Math.min(1.0, (double) experience / (double) experienceToNext);
    }
}
