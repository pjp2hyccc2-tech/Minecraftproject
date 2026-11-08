package com.eternalrealm.ranking;

import com.eternalrealm.season.SeasonId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The heroes of one finished season (英雄の記録): the podium of every ranking category and the party that first
 * defeated the final boss, with the time. What the hall-of-fame NPC, board and statues show. Derived from a
 * {@link SeasonArchive} ({@link SeasonArchive#heroes()}), which keeps the longer lists.
 *
 * @param podiums             top {@link #PODIUM_SIZE} of every score category (names, ids and scores)
 * @param finalBossFirstClear first accepted clear of the final boss (party, clear time, duration), or {@code null}
 */
public record HeroRecord(SeasonId seasonId, Map<RankingCategory, List<RankingEntry<String>>> podiums,
                         TimeAttackRun finalBossFirstClear) {
    public static final int PODIUM_SIZE = 3;

    public HeroRecord {
        Objects.requireNonNull(seasonId, "seasonId");
        podiums = SeasonArchive.copyRankings(podiums);
        for (Map.Entry<RankingCategory, List<RankingEntry<String>>> entry : podiums.entrySet()) {
            if (entry.getValue().size() > PODIUM_SIZE) {
                throw new IllegalArgumentException(entry.getKey().id() + ": a podium has at most " + PODIUM_SIZE + " places");
            }
        }
        if (finalBossFirstClear != null && !finalBossFirstClear.bossId().equals(TimeAttackRules.FINAL_BOSS_ID)) {
            throw new IllegalArgumentException("Final boss first clear is a clear of " + finalBossFirstClear.bossId());
        }
    }

    /** The podium of a category (empty if nobody was ranked). */
    public List<RankingEntry<String>> podium(RankingCategory category) {
        return podiums.getOrDefault(Objects.requireNonNull(category, "category"), List.of());
    }
}
