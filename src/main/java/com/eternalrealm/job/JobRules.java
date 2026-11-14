package com.eternalrealm.job;

/**
 * Server-configured job rules.
 *
 * @param freeChangeBelowLevel   changing the job (and resetting the skill tree) is free below this level (and the
 *                               first job choice always is)
 * @param goldPerLevel           otherwise a job change costs {@code goldPerLevel × level} gold
 * @param skillResetGoldPerLevel otherwise a skill tree reset at the job master costs
 *                               {@code skillResetGoldPerLevel × level} gold
 */
public record JobRules(int freeChangeBelowLevel, long goldPerLevel, long skillResetGoldPerLevel) {
    public static final long DEFAULT_SKILL_RESET_GOLD_PER_LEVEL = 50L;

    public JobRules {
        Checks.range("freeChangeBelowLevel", freeChangeBelowLevel, 1, 101);
        if (goldPerLevel < 0 || goldPerLevel > 1_000_000L) {
            throw new IllegalArgumentException("goldPerLevel out of range [0, 1000000]: " + goldPerLevel);
        }
        if (skillResetGoldPerLevel < 0 || skillResetGoldPerLevel > 1_000_000L) {
            throw new IllegalArgumentException("skillResetGoldPerLevel out of range [0, 1000000]: "
                    + skillResetGoldPerLevel);
        }
    }

    /** Rules with the default skill reset price. */
    public JobRules(int freeChangeBelowLevel, long goldPerLevel) {
        this(freeChangeBelowLevel, goldPerLevel, DEFAULT_SKILL_RESET_GOLD_PER_LEVEL);
    }

    public static JobRules defaults() {
        return new JobRules(10, 100L, DEFAULT_SKILL_RESET_GOLD_PER_LEVEL);
    }

    /** Gold needed to switch from {@code current} at {@code level}. */
    public long changeCost(JobType current, int level) {
        if (!current.isChosen() || level < freeChangeBelowLevel) {
            return 0L;
        }
        return goldPerLevel * level;
    }

    /**
     * Gold needed to change from one second class to the other at {@code level} (the first advance is free): what a
     * job change costs; the class's skill points are refunded.
     */
    public long classChangeCost(int level) {
        return goldPerLevel * level;
    }

    /** Gold needed to refund every skill point at {@code level} (job master). */
    public long skillResetCost(int level) {
        return level < freeChangeBelowLevel ? 0L : skillResetGoldPerLevel * level;
    }
}
