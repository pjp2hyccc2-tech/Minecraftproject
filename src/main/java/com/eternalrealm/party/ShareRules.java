package com.eternalrealm.party;

/**
 * Party experience bonus (config). Integer percentages keep the split exact and identical on every platform.
 *
 * @param bonusPercentPerExtraMember bonus for each eligible member beyond the first (default 10)
 * @param maxBonusPercent            cap of the total bonus (default 40, reached with 5 eligible members)
 */
public record ShareRules(int bonusPercentPerExtraMember, int maxBonusPercent) {
    /** Upper bound of both values: the bonus never more than doubles the experience. */
    public static final int MAX_PERCENT = 100;

    public ShareRules {
        if (bonusPercentPerExtraMember < 0 || bonusPercentPerExtraMember > MAX_PERCENT) {
            throw new IllegalArgumentException("bonusPercentPerExtraMember out of range [0, " + MAX_PERCENT + "]: "
                    + bonusPercentPerExtraMember);
        }
        if (maxBonusPercent < 0 || maxBonusPercent > MAX_PERCENT) {
            throw new IllegalArgumentException("maxBonusPercent out of range [0, " + MAX_PERCENT + "]: "
                    + maxBonusPercent);
        }
    }

    /** +10% per extra eligible member, at most +40%. */
    public static ShareRules defaults() {
        return new ShareRules(10, 40);
    }

    /** Bonus percentage for {@code eligibleMembers} players sharing a kill ({@code >= 1}). */
    public int bonusPercent(int eligibleMembers) {
        if (eligibleMembers < 1) {
            throw new IllegalArgumentException("eligibleMembers must be >= 1: " + eligibleMembers);
        }
        long bonus = (long) bonusPercentPerExtraMember * (eligibleMembers - 1);
        return (int) Math.min(maxBonusPercent, bonus);
    }
}
