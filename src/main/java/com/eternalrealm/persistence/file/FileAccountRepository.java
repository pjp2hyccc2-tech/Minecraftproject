package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.player.AccountRecord;
import com.eternalrealm.player.AccountRepository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Permanent account data stored as {@code <dataRoot>/accounts/<uuid>.json}.
 */
public final class FileAccountRepository implements AccountRepository {
    private final Path dataRoot;
    private final AtomicFileStore store;

    public FileAccountRepository(Path dataRoot, AtomicFileStore store) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<AccountRecord> load(UUID playerId) throws DataAccessException {
        return store.read(pathFor(playerId), json -> JsonCodecs.decodeAccount(json, playerId));
    }

    @Override
    public void save(AccountRecord record) throws DataAccessException {
        store.write(pathFor(record.playerId()), JsonCodecs.encodeAccount(record));
    }

    Path pathFor(UUID playerId) {
        return dataRoot.resolve("accounts").resolve(playerId + ".json");
    }
}
