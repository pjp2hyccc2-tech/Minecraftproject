package com.eternalrealm.guild.house;

import com.eternalrealm.core.data.DataAccessException;

import java.util.List;

/**
 * Storage port of the guild houses' data ({@link GuildHouse}: owned design, fund, board, storage log). Implemented in
 * {@code com.eternalrealm.persistence}; calls block and are made from the persistence I/O executor.
 */
public interface GuildHouseRepository {
    /**
     * @return every stored guild's house data, or an empty list if none was ever saved
     * @throws DataAccessException if data exists but cannot be read (the guild houses then stay disabled)
     */
    List<GuildHouse> loadAll() throws DataAccessException;

    void saveAll(List<GuildHouse> houses) throws DataAccessException;
}
