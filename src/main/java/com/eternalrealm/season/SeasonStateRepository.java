package com.eternalrealm.season;

import com.eternalrealm.core.data.DataAccessException;

import java.util.Optional;

/**
 * Storage port for the current season's {@link SeasonState}. Implementations live in
 * {@code com.eternalrealm.persistence}. Calls block: persistence I/O executor only.
 */
public interface SeasonStateRepository {
    /**
     * @return the stored state, or empty if no season was ever prepared
     * @throws DataAccessException if a state exists but cannot be read; callers must not start a new season then,
     *                             or the next save would forget which events were already emitted
     */
    Optional<SeasonState> load() throws DataAccessException;

    void save(SeasonState state) throws DataAccessException;
}
