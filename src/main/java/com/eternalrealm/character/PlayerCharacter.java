package com.eternalrealm.character;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mutable, server-authoritative state of one player's season character.
 *
 * <p><b>Threading:</b> confined to the server thread. Persistence receives immutable
 * {@link CharacterRecord}s from {@link #toRecord(Instant)}.</p>
 *
 * <p><b>Invariants</b> (re-established by {@link #reconcile(CharacterRules)}):</p>
 * <ul>
 *     <li>{@code 1 <= level <= MAX_LEVEL}</li>
 *     <li>below the cap: {@code 0 <= experience < experienceToNext(level)}; at/above the cap: {@code experience == 0}</li>
 *     <li>{@code allocated.total() <= earnedStatPoints(level)}; unspent points are derived, never stored,
 *         so they cannot drift out of sync</li>
 * </ul>
 *
 * <p>Every mutation increments {@link #revision()}; the character is dirty while
 * {@code revision > savedRevision}.</p>
 */
public final class PlayerCharacter {
    /** Largest single grant accepted; the rest is discarded. Protects arithmetic from overflow. */
    public static final long MAX_SINGLE_GRANT = 1_000_000_000_000L;

    private final UUID playerId;
    private final SeasonId seasonId;
    private final Instant createdAt;
    private int level;
    private long experience;
    private long totalExperience;
    private StatBlock allocated;
    private long revision;
    private long savedRevision;

    private PlayerCharacter(UUID playerId, SeasonId seasonId, Instant createdAt, int level, long experience,
                            long totalExperience, StatBlock allocated) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.seasonId = Objects.requireNonNull(seasonId, "seasonId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.level = level;
        this.experience = experience;
        this.totalExperience = totalExperience;
        this.allocated = Objects.requireNonNull(allocated, "allocated");
    }

    /** A brand-new level 1 character. It starts dirty so that it is persisted at the next save. */
    public static PlayerCharacter createNew(UUID playerId, SeasonId seasonId, Instant now) {
        PlayerCharacter character = new PlayerCharacter(playerId, seasonId, now, 1, 0L, 0L, StatBlock.ZERO);
        character.revision = 1;
        return character;
    }

    /** Restores a stored character and re-establishes invariants under the current rules (dirty if adjusted). */
    public static PlayerCharacter fromRecord(CharacterRecord record, CharacterRules rules) {
        PlayerCharacter character = new PlayerCharacter(record.playerId(), record.seasonId(), record.createdAt(),
                record.level(), record.experience(), record.totalExperience(), record.allocatedStats());
        character.reconcile(rules);
        return character;
    }

    /**
     * Re-establishes all invariants under {@code rules}. Called after loading and after a config reload.
     * The policy is conservative: progress beyond the new limits is clamped, allocations that no longer fit
     * are refunded (never deleted).
     *
     * @return {@code true} if anything had to be adjusted
     */
    public boolean reconcile(CharacterRules rules) {
        boolean changed = false;
        if (rules.isAtCap(level)) {
            if (experience != 0) {
                experience = 0;
                changed = true;
            }
        } else {
            long toNext = rules.experienceToNext(level);
            if (experience >= toNext) {
                experience = toNext - 1;
                changed = true;
            }
        }
        if (!allocationFits(allocated, level, rules)) {
            allocated = StatBlock.ZERO;
            changed = true;
        }
        if (changed) {
            revision++;
        }
        return changed;
    }

    /**
     * Adds experience, levelling up as many times as the amount allows (up to the cap).
     *
     * @param amount must be positive; amounts above {@link #MAX_SINGLE_GRANT} are clamped
     */
    public ExperienceResult addExperience(long amount, CharacterRules rules) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        int previousLevel = level;
        long remaining = Math.min(amount, MAX_SINGLE_GRANT);
        long applied = 0;
        while (remaining > 0 && !rules.isAtCap(level)) {
            long needed = rules.experienceToNext(level) - experience;
            if (remaining >= needed) {
                remaining -= needed;
                applied += needed;
                level++;
                experience = 0;
            } else {
                experience += remaining;
                applied += remaining;
                remaining = 0;
            }
        }
        if (rules.isAtCap(level)) {
            experience = 0;
        }
        if (applied > 0) {
            totalExperience = saturatedAdd(totalExperience, applied);
            revision++;
        }
        int levelsGained = level - previousLevel;
        return new ExperienceResult(applied, amount - applied, previousLevel, level,
                levelsGained * rules.statPointsPerLevel(), rules.isAtCap(level));
    }

    public AllocationResult allocate(StatType stat, int amount, CharacterRules rules) {
        Objects.requireNonNull(stat, "stat");
        if (amount < 1 || amount > rules.maxPointsPerRequest()) {
            return AllocationResult.INVALID_AMOUNT;
        }
        if (unspentStatPoints(rules) < amount) {
            return AllocationResult.NOT_ENOUGH_POINTS;
        }
        long newAllocated = (long) allocated.get(stat) + amount;
        if (newAllocated + rules.baseStatValue() > StatBlock.MAX_VALUE) {
            return AllocationResult.STAT_LIMIT_REACHED;
        }
        allocated = allocated.with(stat, (int) newAllocated);
        revision++;
        return AllocationResult.SUCCESS;
    }

    /** Refunds every allocated point. @return {@code false} if nothing was allocated */
    public boolean resetAllocation() {
        if (allocated.total() == 0) {
            return false;
        }
        allocated = StatBlock.ZERO;
        revision++;
        return true;
    }

    /**
     * Administrative level change. Progress within the level is reset; allocations that exceed the points of the
     * new level are refunded. {@code totalExperience} is not changed because it records EXP actually earned.
     */
    public SetLevelResult setLevel(int newLevel, CharacterRules rules) {
        if (newLevel < 1 || newLevel > rules.levelCap()) {
            return SetLevelResult.OUT_OF_RANGE;
        }
        level = newLevel;
        experience = 0;
        if (!allocationFits(allocated, level, rules)) {
            allocated = StatBlock.ZERO;
        }
        revision++;
        return SetLevelResult.SUCCESS;
    }

    public int unspentStatPoints(CharacterRules rules) {
        long unspent = rules.earnedStatPoints(level) - allocated.total();
        return (int) Math.max(0L, unspent);
    }

    public StatBlock totalStats(CharacterRules rules) {
        return rules.baseStats().plus(allocated);
    }

    public CharacterSnapshot snapshot(CharacterRules rules) {
        return new CharacterSnapshot(seasonId, level, rules.levelCap(), experience, rules.experienceToNext(level),
                unspentStatPoints(rules), allocated, totalStats(rules));
    }

    public CharacterRecord toRecord(Instant now) {
        return new CharacterRecord(playerId, seasonId, level, experience, totalExperience, allocated, createdAt, now);
    }

    public boolean isDirty() {
        return revision > savedRevision;
    }

    /** Records that {@code persistedRevision} has been durably stored. Never moves backwards. */
    public void markSaved(long persistedRevision) {
        if (persistedRevision > savedRevision) {
            savedRevision = persistedRevision;
        }
    }

    /** Forces the next save (e.g. the data came from an in-memory copy whose save had failed). */
    public void markDirty() {
        revision++;
    }

    public long revision() {
        return revision;
    }

    public UUID playerId() {
        return playerId;
    }

    public SeasonId seasonId() {
        return seasonId;
    }

    public int level() {
        return level;
    }

    public long experience() {
        return experience;
    }

    public long totalExperience() {
        return totalExperience;
    }

    public StatBlock allocatedStats() {
        return allocated;
    }

    private static boolean allocationFits(StatBlock allocation, int level, CharacterRules rules) {
        if (allocation.total() > rules.earnedStatPoints(level)) {
            return false;
        }
        for (StatType type : StatType.values()) {
            if (allocation.get(type) + rules.baseStatValue() > StatBlock.MAX_VALUE) {
                return false;
            }
        }
        return true;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
