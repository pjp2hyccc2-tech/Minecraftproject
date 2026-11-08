package com.eternalrealm.guild;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Server-configured guild progression. Immutable; replaced as a whole when the config is reloaded
 * ({@link GuildService#setRules(GuildRules)}).
 *
 * <p>The guild level follows the guild's total contribution over all seasons:
 * {@code level = number of thresholds <= total}, so {@code levelThresholds.get(0)} must be 0 (level 1) and the list
 * must be strictly ascending. Member cap: {@code min(maxMemberCap, baseMemberCap + memberCapPerLevel × (level − 1))}.
 * Because the total never decreases, a guild's level and cap never go down unless the rules change.</p>
 *
 * @param inviteExpiry how long a guild invite stays valid
 */
public record GuildRules(Duration inviteExpiry, List<Long> levelThresholds, int baseMemberCap, int memberCapPerLevel,
                         int maxMemberCap) {
    public static final int MAX_LEVELS = 100;
    public static final int MAX_MEMBER_CAP = 200;
    public static final Duration MIN_INVITE_EXPIRY = Duration.ofSeconds(10);
    public static final Duration MAX_INVITE_EXPIRY = Duration.ofDays(1);

    public GuildRules {
        Objects.requireNonNull(inviteExpiry, "inviteExpiry");
        if (inviteExpiry.compareTo(MIN_INVITE_EXPIRY) < 0 || inviteExpiry.compareTo(MAX_INVITE_EXPIRY) > 0) {
            throw new IllegalArgumentException("inviteExpiry out of range [" + MIN_INVITE_EXPIRY + ", "
                    + MAX_INVITE_EXPIRY + "]: " + inviteExpiry);
        }
        levelThresholds = List.copyOf(levelThresholds);
        if (levelThresholds.isEmpty() || levelThresholds.size() > MAX_LEVELS || levelThresholds.get(0) != 0L) {
            throw new IllegalArgumentException("levelThresholds must start with 0 and have 1.." + MAX_LEVELS
                    + " entries: " + levelThresholds);
        }
        for (int i = 1; i < levelThresholds.size(); i++) {
            if (levelThresholds.get(i) <= levelThresholds.get(i - 1)) {
                throw new IllegalArgumentException("levelThresholds must be strictly ascending: " + levelThresholds);
            }
        }
        if (baseMemberCap < 1 || baseMemberCap > MAX_MEMBER_CAP) {
            throw new IllegalArgumentException("baseMemberCap out of range [1, " + MAX_MEMBER_CAP + "]: " + baseMemberCap);
        }
        if (memberCapPerLevel < 0 || memberCapPerLevel > MAX_MEMBER_CAP) {
            throw new IllegalArgumentException("memberCapPerLevel out of range [0, " + MAX_MEMBER_CAP + "]: "
                    + memberCapPerLevel);
        }
        if (maxMemberCap < baseMemberCap || maxMemberCap > MAX_MEMBER_CAP) {
            throw new IllegalArgumentException("maxMemberCap out of range [" + baseMemberCap + ", " + MAX_MEMBER_CAP
                    + "]: " + maxMemberCap);
        }
    }

    /**
     * 30 members at level 1, +5 per level, at most 50 (reached at level 5); ten levels; invites valid for 5 minutes.
     * The thresholds are a first balance guess, to be tuned once contribution sources (quests, bosses) exist.
     */
    public static GuildRules defaults() {
        return new GuildRules(Duration.ofMinutes(5),
                List.of(0L, 1_000L, 3_000L, 7_000L, 15_000L, 30_000L, 60_000L, 100_000L, 160_000L, 250_000L),
                30, 5, 50);
    }

    public int maxLevel() {
        return levelThresholds.size();
    }

    /** Level reached with {@code totalContribution} points ({@code 1..maxLevel()}). */
    public int levelFor(long totalContribution) {
        int level = 0;
        for (long threshold : levelThresholds) {
            if (totalContribution < threshold) {
                break;
            }
            level++;
        }
        return Math.max(1, level);
    }

    public int memberCap(int level) {
        if (level < 1 || level > maxLevel()) {
            throw new IllegalArgumentException("level out of range [1, " + maxLevel() + "]: " + level);
        }
        long cap = baseMemberCap + (long) memberCapPerLevel * (level - 1);
        return (int) Math.min(maxMemberCap, cap);
    }

    /** Total contribution needed for the level after {@code level}, or empty at the maximum level. */
    public OptionalLong contributionForNextLevel(int level) {
        if (level < 1 || level > maxLevel()) {
            throw new IllegalArgumentException("level out of range [1, " + maxLevel() + "]: " + level);
        }
        return level == maxLevel() ? OptionalLong.empty() : OptionalLong.of(levelThresholds.get(level));
    }
}
