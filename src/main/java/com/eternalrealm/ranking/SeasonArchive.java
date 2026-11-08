package com.eternalrealm.ranking;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The permanent record of a finished season (docs/SEASON_SYSTEM.md 6, step 4), kept forever in the
 * {@link HallOfFame}. Ids are strings so that players (UUID strings) and guilds share one format.
 *
 * @param rankings            top N of every score category (player categories and GUILD), ranked from 1
 * @param timeAttackBests     record run of every boss, difficulty and party bracket
 * @param finalBossFirstClear the season's first accepted clear of the final boss, or {@code null} if nobody cleared it
 */
public record SeasonArchive(SeasonId seasonId, Instant archivedAt, Map<RankingCategory, List<RankingEntry<String>>> rankings,
                            List<TimeAttackRun> timeAttackBests, TimeAttackRun finalBossFirstClear) {
    private record BestKey(String bossId, BossDifficulty difficulty, PartyBracket bracket) {
    }

    public SeasonArchive {
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(archivedAt, "archivedAt");
        rankings = copyRankings(rankings);
        timeAttackBests = List.copyOf(timeAttackBests);
        Set<BestKey> keys = new HashSet<>();
        for (TimeAttackRun run : timeAttackBests) {
            TimeAttackSnapshot.requireIntact(run);
            if (!keys.add(new BestKey(run.bossId(), run.difficulty(), run.bracket()))) {
                throw new IllegalArgumentException("Two time-attack bests for " + run.bossId() + " "
                        + run.difficulty().id() + " " + run.bracket().id());
            }
        }
        if (finalBossFirstClear != null) {
            TimeAttackSnapshot.requireIntact(finalBossFirstClear);
            if (!finalBossFirstClear.bossId().equals(TimeAttackRules.FINAL_BOSS_ID)) {
                throw new IllegalArgumentException("Final boss first clear is a clear of " + finalBossFirstClear.bossId());
            }
        }
    }

    /** The archived top entries of a score category (empty if none were ranked). */
    public List<RankingEntry<String>> top(RankingCategory category) {
        return rankings.getOrDefault(Objects.requireNonNull(category, "category"), List.of());
    }

    /** The podiums (top 3) of every category and the final boss's first clear. */
    public HeroRecord heroes() {
        Map<RankingCategory, List<RankingEntry<String>>> podiums = new EnumMap<>(RankingCategory.class);
        for (Map.Entry<RankingCategory, List<RankingEntry<String>>> entry : rankings.entrySet()) {
            List<RankingEntry<String>> top = entry.getValue();
            podiums.put(entry.getKey(), top.subList(0, Math.min(HeroRecord.PODIUM_SIZE, top.size())));
        }
        return new HeroRecord(seasonId, podiums, finalBossFirstClear);
    }

    /**
     * Validates and copies archived rankings: only score categories, ranks 1..n in order, scores not increasing, ids
     * unique per category and in the right format (UUID strings for players, guild ids for GUILD).
     */
    static Map<RankingCategory, List<RankingEntry<String>>> copyRankings(
            Map<RankingCategory, List<RankingEntry<String>>> rankings) {
        Objects.requireNonNull(rankings, "rankings");
        Map<RankingCategory, List<RankingEntry<String>>> copy = new EnumMap<>(RankingCategory.class);
        for (Map.Entry<RankingCategory, List<RankingEntry<String>>> entry : rankings.entrySet()) {
            RankingCategory category = Objects.requireNonNull(entry.getKey(), "category");
            if (!category.isScoreCategory()) {
                throw new IllegalArgumentException(category.id() + " is archived as time-attack bests");
            }
            List<RankingEntry<String>> entries = List.copyOf(entry.getValue());
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < entries.size(); i++) {
                RankingEntry<String> current = entries.get(i);
                if (current.rank() != i + 1) {
                    throw new IllegalArgumentException(category.id() + ": expected rank " + (i + 1) + ", got " + current.rank());
                }
                if (i > 0 && current.score() > entries.get(i - 1).score()) {
                    throw new IllegalArgumentException(category.id() + ": scores must not increase with the rank");
                }
                if (!ids.add(current.id())) {
                    throw new IllegalArgumentException(category.id() + ": duplicate id " + current.id());
                }
                if (category.isPlayerCategory()) {
                    UUID.fromString(current.id());
                } else {
                    Check.guildId(current.id());
                }
            }
            copy.put(category, entries);
        }
        return Collections.unmodifiableMap(copy);
    }
}
