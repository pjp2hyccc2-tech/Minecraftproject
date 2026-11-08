package com.eternalrealm.ranking;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server-configured plausibility rules of the boss time attack. Immutable; replaced as a whole on config reload.
 * Already accepted records are not re-validated when the rules change (operators remove bad records explicitly).
 *
 * @param bossIds             bosses that have time-attack boards; runs for other ids are rejected
 * @param defaultMinimumTicks fastest plausible clear of a boss without its own minimum
 * @param minimumTicksByBoss  per-boss fastest plausible clear (the "theoretical minimum" of docs/RANKING_SYSTEM.md 3)
 * @param minWallClockPercent the wall-clock duration must be at least this percentage of {@code ticks × 50 ms}. Lag
 *                            only makes wall time longer, so a much shorter wall time means manipulated ticks
 */
public record TimeAttackRules(List<String> bossIds, long defaultMinimumTicks, Map<String, Long> minimumTicksByBoss,
                              int minWallClockPercent) {
    public static final String FINAL_BOSS_ID = "final";
    /** The eight main bosses and the final boss of the initial release (docs/GAME_DESIGN.md 2). */
    public static final List<String> DEFAULT_BOSS_IDS =
            List.of("b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", FINAL_BOSS_ID);
    public static final long DEFAULT_MINIMUM_TICKS = 200;
    public static final int DEFAULT_MIN_WALL_CLOCK_PERCENT = 90;
    /** One day of ticks: a larger minimum is certainly a configuration error. */
    public static final long MAX_MINIMUM_TICKS = 20L * 60 * 60 * 24;

    public TimeAttackRules {
        bossIds = List.copyOf(bossIds);
        if (bossIds.isEmpty()) {
            throw new IllegalArgumentException("bossIds must not be empty");
        }
        Set<String> seen = new HashSet<>();
        for (String bossId : bossIds) {
            Check.id("bossId", bossId);
            if (!seen.add(bossId)) {
                throw new IllegalArgumentException("Duplicate boss id " + bossId);
            }
        }
        Check.range("defaultMinimumTicks", defaultMinimumTicks, 1L, MAX_MINIMUM_TICKS);
        Objects.requireNonNull(minimumTicksByBoss, "minimumTicksByBoss");
        Map<String, Long> minimums = new LinkedHashMap<>();
        for (String bossId : bossIds) {
            Long minimum = minimumTicksByBoss.get(bossId);
            if (minimum != null) {
                minimums.put(bossId, Check.range("minimumTicks of " + bossId, minimum, 1L, MAX_MINIMUM_TICKS));
            }
        }
        for (String bossId : minimumTicksByBoss.keySet()) {
            if (!seen.contains(bossId)) {
                throw new IllegalArgumentException("Minimum ticks for unknown boss " + bossId);
            }
        }
        minimumTicksByBoss = Collections.unmodifiableMap(minimums);
        Check.range("minWallClockPercent", minWallClockPercent, 1, 100);
    }

    /** The bosses of the initial release, a 200-tick (10 s) minimum for each, and a 90 % wall-clock tolerance. */
    public static TimeAttackRules defaults() {
        return new TimeAttackRules(DEFAULT_BOSS_IDS, DEFAULT_MINIMUM_TICKS, Map.of(), DEFAULT_MIN_WALL_CLOCK_PERCENT);
    }

    public boolean isKnownBoss(String bossId) {
        return bossIds.contains(bossId);
    }

    public long minimumTicks(String bossId) {
        Long minimum = minimumTicksByBoss.get(bossId);
        return minimum != null ? minimum : defaultMinimumTicks;
    }
}
