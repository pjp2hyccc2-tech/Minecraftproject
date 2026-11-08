package com.eternalrealm.job;

/**
 * Server-configured job rules.
 *
 * @param freeChangeBelowLevel changing the job is free below this level (and always for the first choice)
 * @param goldPerLevel         otherwise a change costs {@code goldPerLevel × level} gold
 */
public record JobRules(int freeChangeBelowLevel, long goldPerLevel) {
    public JobRules {
        Checks.range("freeChangeBelowLevel", freeChangeBelowLevel, 1, 101);
        if (goldPerLevel < 0 || goldPerLevel > 1_000_000L) {
            throw new IllegalArgumentException("goldPerLevel out of range [0, 1000000]: " + goldPerLevel);
        }
    }

    public static JobRules defaults() {
        return new JobRules(10, 100L);
    }

    /** Gold needed to switch from {@code current} at {@code level}. */
    public long changeCost(JobType current, int level) {
        if (!current.isChosen() || level < freeChangeBelowLevel) {
            return 0L;
        }
        return goldPerLevel * level;
    }
}
