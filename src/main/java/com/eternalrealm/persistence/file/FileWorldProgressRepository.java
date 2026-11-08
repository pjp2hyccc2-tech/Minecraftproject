package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.world.WorldProgressCodec;
import com.eternalrealm.world.WorldProgressRecord;
import com.eternalrealm.world.WorldProgressRepository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Server-wide world progress stored as {@code <dataRoot>/seasons/<seasonId>/world_progress.json}. Being season data,
 * it starts over with every season and never touches the progress of older seasons.
 */
public final class FileWorldProgressRepository implements WorldProgressRepository {
    public static final String FILE_NAME = "world_progress.json";

    private final Path dataRoot;
    private final AtomicFileStore store;

    public FileWorldProgressRepository(Path dataRoot, AtomicFileStore store) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<WorldProgressRecord> load(SeasonId season) throws DataAccessException {
        return store.read(pathFor(season), json -> WorldProgressCodec.decode(json, season));
    }

    @Override
    public void save(WorldProgressRecord record) throws DataAccessException {
        store.write(pathFor(record.seasonId()), WorldProgressCodec.encode(record));
    }

    /** The SeasonId format cannot contain path separators, so the path cannot escape {@code dataRoot}. */
    Path pathFor(SeasonId season) {
        return dataRoot.resolve("seasons").resolve(season.value()).resolve(FILE_NAME);
    }
}
