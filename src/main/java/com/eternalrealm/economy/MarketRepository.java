package com.eternalrealm.economy;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.season.SeasonId;

import java.util.Optional;

/**
 * Storage port for a season's market ({@link MarketData}). Implementations live in {@code com.eternalrealm.persistence}.
 * Calls block and must only be made from the persistence I/O executor.
 */
public interface MarketRepository {
    /**
     * @return the stored market, or empty if this season's market has never been saved
     * @throws DataAccessException if data exists but cannot be read; callers must then keep the market closed instead
     *                             of starting empty, which would destroy listed items and pending gold at the next save
     */
    Optional<MarketData> load(SeasonId season) throws DataAccessException;

    void save(MarketData data) throws DataAccessException;
}
