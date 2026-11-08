package com.eternalrealm.ranking;

import com.eternalrealm.season.SeasonId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every archived season, in archive order. <b>Append-only:</b> an archive is never replaced or removed, and the
 * first archive of a season is final, so archiving the same season again (e.g. when the archive request is repeated
 * after a restart) changes nothing. Permanent data: survives every season reset.
 *
 * <p>Not thread-safe: the server thread reads it; the file is appended through {@link HallOfFameRepository}.</p>
 */
public final class HallOfFame {
    private final List<SeasonArchive> archives = new ArrayList<>();
    private final Map<SeasonId, SeasonArchive> bySeason = new HashMap<>();

    public HallOfFame() {
    }

    /** @throws IllegalArgumentException if two archives belong to the same season (corrupt data) */
    public HallOfFame(List<SeasonArchive> archives) {
        for (SeasonArchive archive : archives) {
            if (!append(archive)) {
                throw new IllegalArgumentException("Season " + archive.seasonId() + " is archived twice");
            }
        }
    }

    /** @return {@code false} (and no change) if the season is already archived */
    public boolean append(SeasonArchive archive) {
        Objects.requireNonNull(archive, "archive");
        if (bySeason.containsKey(archive.seasonId())) {
            return false;
        }
        archives.add(archive);
        bySeason.put(archive.seasonId(), archive);
        return true;
    }

    public boolean contains(SeasonId seasonId) {
        return bySeason.containsKey(Objects.requireNonNull(seasonId, "seasonId"));
    }

    public Optional<SeasonArchive> archive(SeasonId seasonId) {
        return Optional.ofNullable(bySeason.get(Objects.requireNonNull(seasonId, "seasonId")));
    }

    /** All archives, oldest first. */
    public List<SeasonArchive> archives() {
        return Collections.unmodifiableList(new ArrayList<>(archives));
    }

    /** The heroes of every archived season, oldest first. */
    public List<HeroRecord> heroes() {
        List<HeroRecord> heroes = new ArrayList<>(archives.size());
        for (SeasonArchive archive : archives) {
            heroes.add(archive.heroes());
        }
        return Collections.unmodifiableList(heroes);
    }

    public int size() {
        return archives.size();
    }
}
