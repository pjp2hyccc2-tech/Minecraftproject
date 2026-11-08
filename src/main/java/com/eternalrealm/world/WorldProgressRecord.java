package com.eternalrealm.world;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable persistence form of {@link WorldProgress}: the season and the time each boss was first defeated on the
 * server. Unlocked areas are not stored; they are derived from the defeats, so they can never disagree with them.
 *
 * @param bossFirstDefeats boss id ({@link Areas#isBossId}) to the instant of its first defeat, sorted by boss id
 */
public record WorldProgressRecord(SeasonId seasonId, Map<String, Instant> bossFirstDefeats) {
    public WorldProgressRecord {
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(bossFirstDefeats, "bossFirstDefeats");
        TreeMap<String, Instant> copy = new TreeMap<>();
        for (Map.Entry<String, Instant> entry : bossFirstDefeats.entrySet()) {
            if (!Areas.isBossId(entry.getKey())) {
                throw new IllegalArgumentException("Unknown boss id: " + entry.getKey());
            }
            copy.put(entry.getKey(), Objects.requireNonNull(entry.getValue(), "defeat time of " + entry.getKey()));
        }
        bossFirstDefeats = Collections.unmodifiableMap(copy);
    }

    public static WorldProgressRecord empty(SeasonId seasonId) {
        return new WorldProgressRecord(seasonId, Map.of());
    }
}
