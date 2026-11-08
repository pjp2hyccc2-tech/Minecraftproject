package com.eternalrealm.character;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.season.SeasonId;

import java.util.Optional;
import java.util.UUID;

/**
 * Storage port for season characters. Implementations live in {@code com.eternalrealm.persistence}.
 *
 * <p>Calls block and must only be made from the persistence I/O executor, never from the server thread.</p>
 */
public interface CharacterRepository {
    /**
     * @return the stored character, or empty if this player has never had a character in {@code season}
     * @throws DataAccessException if data exists but cannot be read; callers must not create a new character then
     */
    Optional<CharacterRecord> load(UUID playerId, SeasonId season) throws DataAccessException;

    void save(CharacterRecord record) throws DataAccessException;
}
