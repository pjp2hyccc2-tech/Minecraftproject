package com.eternalrealm.ranking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent state of a {@link TimeAttackBoard}: the best run of every party plus the first clear of every boss.
 * Everything else (bracket rankings, personal bests) is derived from these runs when the board is restored.
 *
 * <p>The constructor rejects data no correct board can produce (duplicate parties or run ids, runs that would never
 * have been accepted), so that the repository reports it as corrupt instead of silently ranking it.</p>
 *
 * @param bestRuns    the best accepted run of every distinct party, per boss and difficulty
 * @param firstClears the earliest accepted clear of every boss
 */
public record TimeAttackSnapshot(List<TimeAttackRun> bestRuns, List<TimeAttackRun> firstClears) {
    public static final TimeAttackSnapshot EMPTY = new TimeAttackSnapshot(List.of(), List.of());

    private record PartyKey(String bossId, BossDifficulty difficulty, Set<UUID> members) {
    }

    public TimeAttackSnapshot {
        bestRuns = List.copyOf(bestRuns);
        firstClears = List.copyOf(firstClears);
        Map<UUID, TimeAttackRun> byId = new HashMap<>();
        Map<PartyKey, TimeAttackRun> parties = new HashMap<>();
        for (TimeAttackRun run : bestRuns) {
            requireIntact(run);
            if (byId.put(run.runId(), run) != null) {
                throw new IllegalArgumentException("Duplicate time-attack run " + run.runId());
            }
            if (parties.put(new PartyKey(run.bossId(), run.difficulty(), run.memberIds()), run) != null) {
                throw new IllegalArgumentException("Two best runs for the same party: " + run.runId());
            }
        }
        Map<String, TimeAttackRun> bosses = new HashMap<>();
        for (TimeAttackRun run : firstClears) {
            requireIntact(run);
            if (bosses.put(run.bossId(), run) != null) {
                throw new IllegalArgumentException("Two first clears of boss " + run.bossId());
            }
            TimeAttackRun sameId = byId.get(run.runId());
            if (sameId != null && !sameId.equals(run)) {
                throw new IllegalArgumentException("Run id " + run.runId() + " is used by two different runs");
            }
        }
    }

    static void requireIntact(TimeAttackRun run) {
        Objects.requireNonNull(run, "run");
        List<String> problems = TimeAttackValidator.integrityProblems(run);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Run " + run.runId() + " could not have been accepted: " + problems);
        }
    }
}
