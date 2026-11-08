package com.eternalrealm.character;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable persistence form of a season character. Created on the server thread and handed to the I/O
 * thread, so the mutable {@link PlayerCharacter} is never shared between threads.
 *
 * <p>Only structural invariants are checked here. Rule-dependent invariants (level cap, earned stat and skill points,
 * skills of the job) are re-established after loading, because the server rules may legitimately change between
 * sessions.</p>
 *
 * @param jobId            chosen job ({@link #NO_JOB} until the player picks one)
 * @param skillInvestments skill points invested per skill id (a learned skill's level is {@code 1 + investment})
 * @param gold             season currency
 * @param extensions       per-module season data as compact JSON documents (quest log, discovery, statistics...),
 *                         keyed by module id; the character module never interprets them
 */
public record CharacterRecord(UUID playerId, SeasonId seasonId, int level, long experience, long totalExperience,
                              StatBlock allocatedStats, Instant createdAt, Instant updatedAt, String jobId,
                              Map<String, Integer> skillInvestments, long gold, Map<String, String> extensions) {
    public static final String NO_JOB = "none";
    public static final long MAX_GOLD = 9_999_999_999L;
    public static final int MAX_SKILL_INVESTMENT = 100;
    public static final int MAX_EXTENSION_LENGTH = 1 << 20;
    private static final Pattern JOB_ID = Pattern.compile("[a-z_]{1,32}");
    private static final Pattern SKILL_ID = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern EXTENSION_KEY = Pattern.compile("[a-z_]{1,32}");

    public CharacterRecord {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(allocatedStats, "allocatedStats");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (level < 1 || level > CharacterRules.MAX_LEVEL) {
            throw new IllegalArgumentException("level out of range [1, " + CharacterRules.MAX_LEVEL + "]: " + level);
        }
        if (experience < 0) {
            throw new IllegalArgumentException("experience must be >= 0: " + experience);
        }
        if (totalExperience < 0) {
            throw new IllegalArgumentException("totalExperience must be >= 0: " + totalExperience);
        }
        checkJobId(jobId);
        skillInvestments = copySkills(skillInvestments);
        if (gold < 0 || gold > MAX_GOLD) {
            throw new IllegalArgumentException("gold out of range [0, " + MAX_GOLD + "]: " + gold);
        }
        extensions = copyExtensions(extensions);
    }

    /** A character without job, skills, gold or extensions (schema 1 data). */
    public CharacterRecord(UUID playerId, SeasonId seasonId, int level, long experience, long totalExperience,
                           StatBlock allocatedStats, Instant createdAt, Instant updatedAt) {
        this(playerId, seasonId, level, experience, totalExperience, allocatedStats, createdAt, updatedAt, NO_JOB,
                Map.of(), 0L, Map.of());
    }

    static String checkJobId(String jobId) {
        if (jobId == null || !JOB_ID.matcher(jobId).matches()) {
            throw new IllegalArgumentException("invalid job id: " + jobId);
        }
        return jobId;
    }

    /** Validated, sorted, unmodifiable copy; zero investments are dropped. */
    static Map<String, Integer> copySkills(Map<String, Integer> source) {
        Objects.requireNonNull(source, "skillInvestments");
        Map<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String id = entry.getKey();
            Integer points = entry.getValue();
            if (id == null || !SKILL_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("invalid skill id: " + id);
            }
            if (points == null || points < 0 || points > MAX_SKILL_INVESTMENT) {
                throw new IllegalArgumentException("invalid investment in " + id + ": " + points);
            }
            if (points > 0) {
                copy.put(id, points);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    static Map<String, String> copyExtensions(Map<String, String> source) {
        Objects.requireNonNull(source, "extensions");
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            checkExtension(entry.getKey(), entry.getValue());
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    static void checkExtension(String key, String json) {
        if (key == null || !EXTENSION_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid extension key: " + key);
        }
        if (json == null || json.isEmpty() || json.length() > MAX_EXTENSION_LENGTH) {
            throw new IllegalArgumentException("invalid extension data for " + key);
        }
    }
}
