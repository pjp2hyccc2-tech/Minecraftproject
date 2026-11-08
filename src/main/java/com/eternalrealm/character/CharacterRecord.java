package com.eternalrealm.character;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable persistence form of a season character. Created on the server thread and handed to the I/O
 * thread, so the mutable {@link PlayerCharacter} is never shared between threads.
 *
 * <p>Only structural invariants are checked here. Rule-dependent invariants (level cap, earned stat points)
 * are re-established by {@link PlayerCharacter#reconcile(CharacterRules)} after loading, because the
 * server rules may legitimately change between sessions.</p>
 */
public record CharacterRecord(UUID playerId, SeasonId seasonId, int level, long experience, long totalExperience,
                              StatBlock allocatedStats, Instant createdAt, Instant updatedAt) {
    public CharacterRecord {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(allocatedStats, "allocatedStats");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (level < 1 || level > CharacterRules.MAX_LEVEL) {
            throw new IllegalArgumentException("level out of range [1, " + CharacterRules.MAX_LEVEL + "]: " + level);
        }
        if (experience < 0) {
            throw new IllegalArgumentException("experience must be >= 0: " + experience);
        }
        if (totalExperience < 0) {
            throw new IllegalArgumentException("totalExperience must be >= 0: " + totalExperience);
        }
    }
}
