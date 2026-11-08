package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.ranking.HallOfFame;
import com.eternalrealm.ranking.HallOfFameRepository;
import com.eternalrealm.ranking.SeasonArchive;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The permanent hall of fame stored as {@code <dataRoot>/hall_of_fame.json}, outside every season directory.
 *
 * <p><b>Append-only:</b> {@link #append} re-reads the stored file and writes it back with the new archive added;
 * there is no way to write a hall of fame with fewer seasons. If the stored file cannot be read (corrupt with no
 * usable previous generation, newer schema, I/O error), nothing is written: the archive request fails and is
 * retried, instead of replacing the history with a file that contains only the new season.</p>
 *
 * <p>Read-modify-write: call from one I/O thread at a time (the hall of fame changes once per season).</p>
 */
public final class FileHallOfFameRepository implements HallOfFameRepository {
    public static final String FILE_NAME = "hall_of_fame.json";

    private final Path dataRoot;
    private final AtomicFileStore store;

    public FileHallOfFameRepository(Path dataRoot, AtomicFileStore store) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public HallOfFame load() throws DataAccessException {
        return store.read(path(), SeasonJsonCodecs::decodeHallOfFame).orElseGet(HallOfFame::new);
    }

    @Override
    public boolean append(SeasonArchive archive) throws DataAccessException {
        Objects.requireNonNull(archive, "archive");
        HallOfFame stored = load();
        if (!stored.append(archive)) {
            return false;
        }
        store.write(path(), SeasonJsonCodecs.encodeHallOfFame(stored.archives()));
        return true;
    }

    Path path() {
        return dataRoot.resolve(FILE_NAME);
    }
}
