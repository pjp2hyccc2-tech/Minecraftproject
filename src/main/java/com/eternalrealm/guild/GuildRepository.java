package com.eternalrealm.guild;

import com.eternalrealm.core.data.DataAccessException;

import java.util.List;

/**
 * Storage port for the permanent guild directory. Implementations live in {@code com.eternalrealm.persistence}.
 *
 * <p>Calls block and must only be made from the persistence I/O executor. The whole directory is read and written at
 * once (a few hundred guilds fit comfortably in one document), so a save is atomic for all guilds.</p>
 */
public interface GuildRepository {
    /**
     * @return every stored guild, or an empty list if guild data has never been saved
     * @throws DataAccessException if data exists but cannot be read; callers must then keep the guild features
     *                             disabled instead of starting empty, which would delete every guild at the next save
     */
    List<Guild> loadAll() throws DataAccessException;

    void saveAll(List<Guild> guilds) throws DataAccessException;
}
