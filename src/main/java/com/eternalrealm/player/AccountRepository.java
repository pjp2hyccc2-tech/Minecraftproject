package com.eternalrealm.player;

import com.eternalrealm.core.data.DataAccessException;

import java.util.Optional;
import java.util.UUID;

/**
 * Storage port for permanent account data. Blocking; call only from the persistence I/O executor.
 */
public interface AccountRepository {
    Optional<AccountRecord> load(UUID playerId) throws DataAccessException;

    void save(AccountRecord record) throws DataAccessException;
}
