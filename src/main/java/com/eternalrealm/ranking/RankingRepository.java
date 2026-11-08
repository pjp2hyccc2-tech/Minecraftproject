package com.eternalrealm.ranking;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.season.SeasonId;

import java.util.Optional;

/**
 * Storage port for a season's rankings. Implementations live in {@code com.eternalrealm.persistence}.
 *
 * <p>Calls block and must only be made from the persistence I/O executor, never from the server thread.</p>
 */
public interface RankingRepository {
    /**
     * @return the stored rankings, or empty if the season has none yet
     * @throws DataAccessException if data exists but cannot be read; callers must not start from an empty board
     *                             then, or the next save would overwrite the season's rankings
     */
    Optional<RankingSnapshot> load(SeasonId season) throws DataAccessException;

    void save(RankingSnapshot snapshot) throws DataAccessException;
}
