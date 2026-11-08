package com.eternalrealm.persistence.file;

import com.eternalrealm.character.CharacterRecord;
import com.eternalrealm.character.CharacterRepository;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.season.SeasonId;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Season characters stored as {@code <dataRoot>/seasons/<seasonId>/characters/<uuid>.json}.
 * Season data is physically separated per season, so a season reset never touches older seasons.
 */
public final class FileCharacterRepository implements CharacterRepository {
    private final Path dataRoot;
    private final AtomicFileStore store;

    public FileCharacterRepository(Path dataRoot, AtomicFileStore store) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<CharacterRecord> load(UUID playerId, SeasonId season) throws DataAccessException {
        return store.read(pathFor(playerId, season), json -> JsonCodecs.decodeCharacter(json, playerId, season));
    }

    @Override
    public void save(CharacterRecord record) throws DataAccessException {
        store.write(pathFor(record.playerId(), record.seasonId()), JsonCodecs.encodeCharacter(record));
    }

    /** UUID and SeasonId formats cannot contain path separators, so the path cannot escape {@code dataRoot}. */
    Path pathFor(UUID playerId, SeasonId season) {
        return dataRoot.resolve("seasons").resolve(season.value()).resolve("characters").resolve(playerId + ".json");
    }
}
