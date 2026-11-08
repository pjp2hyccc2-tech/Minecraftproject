package com.eternalrealm.ranking;

import java.util.Objects;

/**
 * One line of a ranking: a player ({@code K = UUID}) or a guild ({@code K = String}). Archived rankings use
 * {@code String} ids for both ({@link SeasonArchive}).
 *
 * @param rank  1-based position; every entry of a board has a distinct rank (ties are broken, see {@link Leaderboard})
 * @param name  display name at the time of the last update (informational; identity is the id)
 */
public record RankingEntry<K>(int rank, K id, String name, long score) {
    public RankingEntry {
        Check.range("rank", rank, 1, Integer.MAX_VALUE);
        Objects.requireNonNull(id, "id");
        Check.displayName("name", name);
        Check.nonNegative("score", score);
    }
}
