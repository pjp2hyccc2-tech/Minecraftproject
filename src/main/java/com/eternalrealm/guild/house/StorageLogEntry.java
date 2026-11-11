package com.eternalrealm.guild.house;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of a guild house's storage log: {@code playerName} put {@code count} × {@code itemId} into (deposit) or took
 * them out of a storage piece ({@code chest.2}, {@code vault.1}).
 */
public record StorageLogEntry(Instant at, UUID player, String playerName, String itemId, int count, boolean deposit,
                              String piece) {
    public StorageLogEntry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(piece, "piece");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
    }
}
