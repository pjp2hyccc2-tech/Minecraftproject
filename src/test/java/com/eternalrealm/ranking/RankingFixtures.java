package com.eternalrealm.ranking;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic test data for the ranking and season tests (also used by the persistence tests). */
public final class RankingFixtures {
    public static final SeasonId SEASON = SeasonId.of("s1");
    public static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    public static final long T0 = NOW.toEpochMilli();

    private RankingFixtures() {
    }

    /** {@code 00000000-0000-0000-0000-<n>}: ids sort like their numbers. */
    public static UUID uuid(int n) {
        return new UUID(0L, n);
    }

    public static TimeAttackMember member(int n) {
        return new TimeAttackMember(uuid(n), "Player" + n, "knight", 30);
    }

    /**
     * An honest run: starts at tick 1000, lasts {@code ticks}, and its wall clock is exactly {@code ticks × 50 ms},
     * ending at {@code T0 + clearOffsetMillis}. The run id is {@code uuid(1000 + runNumber)}.
     */
    public static TimeAttackRun run(int runNumber, String bossId, BossDifficulty difficulty, long ticks,
                                    long clearOffsetMillis, int... memberNumbers) {
        List<TimeAttackMember> members = new ArrayList<>();
        for (int n : memberNumbers) {
            members.add(member(n));
        }
        long clearedAt = T0 + clearOffsetMillis;
        return new TimeAttackRun(bossId, difficulty, uuid(1000 + runNumber), 1000, 1000 + ticks,
                clearedAt - ticks * TimeAttackRun.MILLIS_PER_TICK, clearedAt, members, false, false, false);
    }

    /** Stats with a distinct value in every field: counter {@code i} is {@code base + i}. */
    public static SeasonStats distinctStats(long base) {
        long[] counters = new long[SeasonCounter.all().size()];
        for (int i = 0; i < counters.length; i++) {
            counters[i] = base + i;
        }
        return SeasonStats.of(counters, 7, base * 1000);
    }

    /** Stats where only the given counters are set (pairs of counter and value). */
    public static SeasonStats statsWith(int level, long totalExperience, Object... counterValuePairs) {
        SeasonStatsTracker tracker = new SeasonStatsTracker();
        for (int i = 0; i < counterValuePairs.length; i += 2) {
            tracker.add((SeasonCounter) counterValuePairs[i], ((Number) counterValuePairs[i + 1]).longValue());
        }
        tracker.setProgress(level, totalExperience);
        return tracker.snapshot();
    }

    /** An archived player line: id {@code uuid(id)}, name {@code Player<id>}. */
    public static RankingEntry<String> archivedPlayer(int rank, int id, long score) {
        return new RankingEntry<>(rank, uuid(id).toString(), "Player" + id, score);
    }

    /** An archive with a four-player LEVEL ranking, a BOSS and a GUILD ranking, and the final boss's first clear. */
    public static SeasonArchive archive(String seasonId) {
        Map<RankingCategory, List<RankingEntry<String>>> rankings = new EnumMap<>(RankingCategory.class);
        rankings.put(RankingCategory.LEVEL, List.of(archivedPlayer(1, 4, 900), archivedPlayer(2, 3, 450),
                archivedPlayer(3, 2, 300), archivedPlayer(4, 1, 200)));
        rankings.put(RankingCategory.BOSS, List.of(archivedPlayer(1, 4, 5)));
        rankings.put(RankingCategory.GUILD, List.of(new RankingEntry<>(1, "g1", "Knights", 70)));
        TimeAttackRun firstClear = run(1, "final", BossDifficulty.HARD, 22_000, 40, 1, 2, 3, 4, 5, 6);
        TimeAttackRun best = run(2, "b1", BossDifficulty.NORMAL, 2_000, 20, 1, 2);
        return new SeasonArchive(SeasonId.of(seasonId), NOW, rankings, List.of(best, firstClear), firstClear);
    }
}
