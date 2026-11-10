package com.eternalrealm.character;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 *     <li>the invested skill points never exceed {@code earnedSkillPoints(level)} (which skills exist for the job is
 *         checked by the job module)</li>
 *     <li>{@code 0 <= gold <= MAX_GOLD}</li>
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
    private String jobId = CharacterRecord.NO_JOB;
    private Map<String, Integer> skillInvestments = Map.of();
    private List<String> skillLoadout = List.of();
    private long gold;
    private final Map<String, String> extensions = new LinkedHashMap<>();
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
        character.jobId = record.jobId();
        character.skillInvestments = record.skillInvestments();
        character.skillLoadout = record.skillLoadout();
        character.gold = record.gold();
        character.extensions.putAll(record.extensions());
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
        if (investedSkillPoints() > rules.earnedSkillPoints(level)) {
            skillInvestments = Map.of();
            skillLoadout = List.of();
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
        if (investedSkillPoints() > rules.earnedSkillPoints(level)) {
            skillInvestments = Map.of();
            skillLoadout = List.of();
        }
        revision++;
        return SetLevelResult.SUCCESS;
    }

    /**
     * Death penalty: loses {@code fraction} of the EXP needed for the next level, but never more than the progress
     * within the current level (a death never costs a level). Nothing is lost at the cap.
     *
     * @return the EXP lost
     */
    public long applyDeathPenalty(double fraction, CharacterRules rules) {
        if (!(fraction >= 0.0) || fraction > 1.0) {
            throw new IllegalArgumentException("fraction out of range [0, 1]: " + fraction);
        }
        if (rules.isAtCap(level) || experience == 0) {
            return 0L;
        }
        long lost = Math.min(experience, (long) Math.floor(rules.experienceToNext(level) * fraction));
        if (lost > 0) {
            experience -= lost;
            revision++;
        }
        return lost;
    }

    // ------------------------------------------------------------------ job & skills

    public String jobId() {
        return jobId;
    }

    /**
     * Changes the job; every invested skill point is refunded and the skill loadout goes back to the job's default.
     *
     * @return {@code false} if it was already the job
     */
    public boolean changeJob(String newJobId) {
        CharacterRecord.checkJobId(newJobId);
        if (newJobId.equals(jobId)) {
            return false;
        }
        jobId = newJobId;
        skillInvestments = Map.of();
        skillLoadout = List.of();
        revision++;
        return true;
    }

    /** The stored skill loadout (empty = the job's default slots). */
    public List<String> skillLoadout() {
        return skillLoadout;
    }

    /**
     * Stores a skill loadout (the job module validated it). The character only becomes dirty if it changed.
     *
     * @return {@code true} if it changed
     */
    public boolean setSkillLoadout(List<String> loadout) {
        List<String> copy = CharacterRecord.copyLoadout(loadout);
        if (copy.equals(skillLoadout)) {
            return false;
        }
        skillLoadout = copy;
        revision++;
        return true;
    }

    public Map<String, Integer> skillInvestments() {
        return skillInvestments;
    }

    public int investment(String skillId) {
        return skillInvestments.getOrDefault(skillId, 0);
    }

    public int investedSkillPoints() {
        int total = 0;
        for (int points : skillInvestments.values()) {
            total += points;
        }
        return total;
    }

    public int unspentSkillPoints(CharacterRules rules) {
        return Math.max(0, rules.earnedSkillPoints(level) - investedSkillPoints());
    }

    /**
     * Invests one more point into {@code skillId}. The caller (job module) has already checked that the skill belongs
     * to the job, is unlocked and is below its maximum level.
     *
     * @return {@code false} if no skill point is left
     */
    public boolean investSkillPoint(String skillId, CharacterRules rules) {
        if (unspentSkillPoints(rules) < 1) {
            return false;
        }
        Map<String, Integer> next = new LinkedHashMap<>(skillInvestments);
        next.merge(skillId, 1, Integer::sum);
        skillInvestments = CharacterRecord.copySkills(next);
        revision++;
        return true;
    }

    /**
     * Refunds every skill point; the skill loadout goes back to the job's default.
     *
     * @return {@code false} if nothing was invested
     */
    public boolean resetSkills() {
        if (skillInvestments.isEmpty()) {
            return false;
        }
        skillInvestments = Map.of();
        skillLoadout = List.of();
        revision++;
        return true;
    }

    // ------------------------------------------------------------------ gold

    public long gold() {
        return gold;
    }

    /** Adds gold, saturating at {@link CharacterRecord#MAX_GOLD}. @return the amount actually added */
    public long addGold(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }
        long added = Math.min(amount, CharacterRecord.MAX_GOLD - gold);
        if (added > 0) {
            gold += added;
            revision++;
        }
        return added;
    }

    /** Spends gold if the balance covers it. @return {@code false} (and nothing changes) if it does not */
    public boolean spendGold(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }
        if (amount > gold) {
            return false;
        }
        if (amount > 0) {
            gold -= amount;
            revision++;
        }
        return true;
    }

    // ------------------------------------------------------------------ extensions

    /** Season data of another module (compact JSON), if present. */
    public Optional<String> extension(String key) {
        return Optional.ofNullable(extensions.get(key));
    }

    /** Stores another module's season data; the character only becomes dirty if the document changed. */
    public void setExtension(String key, String json) {
        CharacterRecord.checkExtension(key, json);
        if (!json.equals(extensions.put(key, json))) {
            revision++;
        }
    }

    public Map<String, String> extensions() {
        return Collections.unmodifiableMap(extensions);
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
                unspentStatPoints(rules), allocated, totalStats(rules), jobId, gold, unspentSkillPoints(rules),
                skillInvestments, skillLoadout);
    }

    public CharacterRecord toRecord(Instant now) {
        return new CharacterRecord(playerId, seasonId, level, experience, totalExperience, allocated, createdAt, now,
                jobId, skillInvestments, gold, extensions, skillLoadout);
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
