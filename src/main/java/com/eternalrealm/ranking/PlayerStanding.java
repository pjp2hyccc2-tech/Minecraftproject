package com.eternalrealm.ranking;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Stored ranking state of one player: the season stats the scores derive from, plus the score and the time it was
 * reached for every category the player is ranked in.
 *
 * <p>While the season is open the scores are recomputed from {@link #stats()} on restore (the rules may have
 * changed) and only the timestamps are reused, so tie order survives a restart. Once the rankings are frozen the
 * stored scores are final and restored as they are.</p>
 *
 * @param updatedAtMillis when the stats last changed; the tie-break time for a category without a stored timestamp
 * @param scores          the player categories the player is ranked in (score {@code > 0})
 */
public record PlayerStanding(UUID playerId, String name, SeasonStats stats, long updatedAtMillis,
                             Map<RankingCategory, RankedScore> scores) {
    public PlayerStanding {
        Objects.requireNonNull(playerId, "playerId");
        Check.displayName("name", name);
        Objects.requireNonNull(stats, "stats");
        Check.nonNegative("updatedAtMillis", updatedAtMillis);
        Objects.requireNonNull(scores, "scores");
        Map<RankingCategory, RankedScore> copy = new EnumMap<>(RankingCategory.class);
        for (Map.Entry<RankingCategory, RankedScore> entry : scores.entrySet()) {
            RankingCategory category = Objects.requireNonNull(entry.getKey(), "category");
            if (!category.isPlayerCategory()) {
                throw new IllegalArgumentException(category.id() + " is not a player category");
            }
            copy.put(category, Objects.requireNonNull(entry.getValue(), "score of " + category.id()));
        }
        scores = Collections.unmodifiableMap(copy);
    }

    /**
     * @param score           {@code > 0} (unranked categories are not stored)
     * @param reachedAtMillis when the player reached this score (the tie-break)
     */
    public record RankedScore(long score, long reachedAtMillis) {
        public RankedScore {
            Check.range("score", score, 1L, Long.MAX_VALUE);
            Check.nonNegative("reachedAtMillis", reachedAtMillis);
        }
    }
}
