package com.eternalrealm.dungeon;

/**
 * Server-configurable failure rules of a run (config section {@code dungeon}).
 *
 * @param timeLimitEnabled     the definition's time limit fails the run
 * @param failWhenEveryoneLeft the run fails when no member has been present for {@code emptyGraceTicks}
 * @param emptyGraceTicks      how long an empty instance waits for a member to come back (reconnects)
 * @param deathLimit           the run fails at this many member deaths in total; 0 = unlimited (deaths are already
 *                             punished mildly elsewhere and players may rejoin a boss fight)
 */
public record FailurePolicy(boolean timeLimitEnabled, boolean failWhenEveryoneLeft, int emptyGraceTicks,
                            int deathLimit) {
    public static final int UNLIMITED_DEATHS = 0;

    public FailurePolicy {
        Check.range("emptyGraceTicks", emptyGraceTicks, 0, 20 * 60 * 60);
        Check.range("deathLimit", deathLimit, 0, 10_000);
    }

    /** Time limit on, an empty instance fails after 60 seconds, unlimited deaths. */
    public static FailurePolicy defaults() {
        return new FailurePolicy(true, true, 20 * 60, UNLIMITED_DEATHS);
    }
}
