package com.eternalrealm.ranking;

import com.eternalrealm.core.data.DataAccessException;

/**
 * Storage port for the permanent, append-only {@link HallOfFame}. Implementations live in
 * {@code com.eternalrealm.persistence}. Calls block: persistence I/O executor only, one call at a time.
 */
public interface HallOfFameRepository {
    /** @return the stored hall of fame, empty if nothing has been archived yet */
    HallOfFame load() throws DataAccessException;

    /**
     * Appends an archive to the stored hall of fame. Works on the stored data, not on an in-memory copy, so a stale
     * copy can never drop archived seasons. Unreadable stored data is an error, never overwritten.
     *
     * @return {@code false} (and nothing written) if the season is already archived
     */
    boolean append(SeasonArchive archive) throws DataAccessException;
}
