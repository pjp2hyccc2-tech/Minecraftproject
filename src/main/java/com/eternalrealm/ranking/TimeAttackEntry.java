package com.eternalrealm.ranking;

import java.util.Objects;

/**
 * One line of a time-attack ranking.
 *
 * @param rank 1-based position; distinct for every run (ties are broken, see {@link TimeAttackBoard})
 */
public record TimeAttackEntry(int rank, TimeAttackRun run) {
    public TimeAttackEntry {
        Check.range("rank", rank, 1, Integer.MAX_VALUE);
        Objects.requireNonNull(run, "run");
    }

    public long durationTicks() {
        return run.durationTicks();
    }
}
