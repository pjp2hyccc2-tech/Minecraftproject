package com.eternalrealm.party;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of {@link ExperienceShare#split}.
 *
 * @param baseExperience  experience of the kill before the party bonus
 * @param bonusPercent    party bonus applied
 * @param totalExperience experience handed out, {@code base + floor(base * bonus / 100)} (saturating)
 * @param shares          experience per eligible member, in the order the members were given; the shares add up to
 *                        {@code totalExperience}
 */
public record ExperienceSplit(long baseExperience, int bonusPercent, long totalExperience, Map<UUID, Long> shares) {
    public ExperienceSplit {
        if (baseExperience < 0 || totalExperience < baseExperience) {
            throw new IllegalArgumentException("invalid experience " + baseExperience + " -> " + totalExperience);
        }
        if (bonusPercent < 0) {
            throw new IllegalArgumentException("bonusPercent must be >= 0: " + bonusPercent);
        }
        Map<UUID, Long> copy = new LinkedHashMap<>();
        long sum = 0;
        for (Map.Entry<UUID, Long> entry : shares.entrySet()) {
            long share = Objects.requireNonNull(entry.getValue(), "share");
            if (share < 0 || share > totalExperience - sum) {
                throw new IllegalArgumentException("share of " + entry.getKey() + " is negative or exceeds the total");
            }
            sum += share;
            copy.put(Objects.requireNonNull(entry.getKey(), "member"), share);
        }
        if (copy.isEmpty() || sum != totalExperience) {
            throw new IllegalArgumentException("shares must be non-empty and add up to " + totalExperience + ": " + sum);
        }
        shares = Collections.unmodifiableMap(copy);
    }

    /** Experience of one member, 0 if the player was not eligible. */
    public long shareOf(UUID member) {
        return shares.getOrDefault(member, 0L);
    }
}
