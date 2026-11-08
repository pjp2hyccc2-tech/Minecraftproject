package com.eternalrealm.world;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.season.SeasonId;

import java.util.Optional;

/**
 * Storage port for the server-wide {@link WorldProgress} of a season. Implemented in {@code com.eternalrealm.persistence}.
 *
 * <p>Calls block and must only be made from the persistence I/O executor, never from the server thread.</p>
 */
public interface WorldProgressRepository {
    /**
     * @return the stored progress, or empty if nothing was stored for {@code season} yet (start with
     *         {@link WorldProgress#fresh})
     * @throws DataAccessException if data exists but cannot be read; never start a fresh season over it then
     */
    Optional<WorldProgressRecord> load(SeasonId season) throws DataAccessException;

    void save(WorldProgressRecord record) throws DataAccessException;
}
