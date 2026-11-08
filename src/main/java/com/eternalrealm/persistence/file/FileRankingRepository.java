package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.ranking.RankingRepository;
import com.eternalrealm.ranking.RankingSnapshot;
import com.eternalrealm.season.SeasonId;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A season's rankings stored as {@code <dataRoot>/seasons/<seasonId>/rankings.json}, beside the season's characters.
 *
 * <p>What is stored: every player's season stats with their ranked scores and tie-break timestamps (player scores are
 * recomputed from the stats while the season is open, so no character files have to be read at startup), the guild
 * scores (their membership belongs to the guild module), the time-attack party bests and first clears, and whether
 * the rankings are frozen.</p>
 */
public final class FileRankingRepository implements RankingRepository {
    public static final String FILE_NAME = "rankings.json";

    private final Path dataRoot;
    private final AtomicFileStore store;

    public FileRankingRepository(Path dataRoot, AtomicFileStore store) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<RankingSnapshot> load(SeasonId season) throws DataAccessException {
        return store.read(pathFor(season), json -> SeasonJsonCodecs.decodeRankings(json, season));
    }

    @Override
    public void save(RankingSnapshot snapshot) throws DataAccessException {
        store.write(pathFor(snapshot.seasonId()), SeasonJsonCodecs.encodeRankings(snapshot));
    }

    /** SeasonId cannot contain path separators, so the path cannot escape {@code dataRoot}. */
    Path pathFor(SeasonId season) {
        return dataRoot.resolve("seasons").resolve(season.value()).resolve(FILE_NAME);
    }
}
