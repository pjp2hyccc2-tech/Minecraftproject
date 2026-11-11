package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.estate.EstateData;
import com.eternalrealm.estate.EstateRepository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The estate registry stored as one document {@code <dataRoot>/estates.json} (outside {@code seasons/}: rooms and
 * guild houses are kept across season resets, like the guilds). One document keeps every change atomic: a redesign
 * moves an estate between two plots, which can never be half-written.
 */
public final class FileEstateRepository implements EstateRepository {
    public static final String FILE_NAME = "estates.json";

    private final Path dataRoot;
    private final AtomicFileStore store;

    public FileEstateRepository(Path dataRoot, AtomicFileStore store) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<EstateData> load() throws DataAccessException {
        return store.read(path(), EstateCodec::decode);
    }

    @Override
    public void save(EstateData data) throws DataAccessException {
        store.write(path(), EstateCodec.encode(data));
    }

    Path path() {
        return dataRoot.resolve(FILE_NAME);
    }
}
