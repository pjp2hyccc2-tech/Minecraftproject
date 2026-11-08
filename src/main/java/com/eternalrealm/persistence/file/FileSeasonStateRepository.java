package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.season.SeasonState;
import com.eternalrealm.season.SeasonStateRepository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The current season's lifecycle state stored as {@code <dataRoot>/season_state.json}. It lives outside
 * {@code seasons/<id>/} because it decides which season is current, including the rollover to the next one.
 */
public final class FileSeasonStateRepository implements SeasonStateRepository {
    public static final String FILE_NAME = "season_state.json";

    private final Path dataRoot;
    private final AtomicFileStore store;

    public FileSeasonStateRepository(Path dataRoot, AtomicFileStore store) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<SeasonState> load() throws DataAccessException {
        return store.read(path(), SeasonJsonCodecs::decodeSeasonState);
    }

    @Override
    public void save(SeasonState state) throws DataAccessException {
        store.write(path(), SeasonJsonCodecs.encodeSeasonState(Objects.requireNonNull(state, "state")));
    }

    Path path() {
        return dataRoot.resolve(FILE_NAME);
    }
}
