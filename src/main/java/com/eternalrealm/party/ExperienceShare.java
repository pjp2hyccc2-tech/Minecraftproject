package com.eternalrealm.party;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Splits the experience of a kill among the eligible party members (docs/modules/party.md):
 * <pre>
 *   bonus% = min(maxBonus%, perExtraMember% × (eligible − 1))
 *   total  = exp + floor(exp × bonus% / 100)
 *   share  = floor(total / eligible); the remainder goes to the killer
 * </pre>
 * Integer arithmetic only, so no experience is created or lost by rounding: the shares always add up to the total.
 * Which members are eligible (alive, same dimension, within range of the kill) is decided by the Forge layer; a
 * player without a party is simply the only eligible member and receives the experience unchanged.
 */
public final class ExperienceShare {
    private ExperienceShare() {
    }

    /**
     * @param experience      experience of the kill, {@code >= 0}
     * @param eligibleMembers members sharing the kill, without duplicates; must contain {@code killer}
     * @param killer          the member who landed the killing blow
     */
    public static ExperienceSplit split(long experience, List<UUID> eligibleMembers, UUID killer, ShareRules rules) {
        Objects.requireNonNull(killer, "killer");
        Objects.requireNonNull(rules, "rules");
        if (experience < 0) {
            throw new IllegalArgumentException("experience must be >= 0: " + experience);
        }
        List<UUID> members = List.copyOf(eligibleMembers);
        if (new HashSet<>(members).size() != members.size()) {
            throw new IllegalArgumentException("duplicate eligible member in " + members);
        }
        if (!members.contains(killer)) {
            throw new IllegalArgumentException("the killer " + killer + " must be an eligible member");
        }
        int bonusPercent = rules.bonusPercent(members.size());
        long total = saturatedAdd(experience, percentOf(experience, bonusPercent));
        long share = total / members.size();
        long remainder = total % members.size();
        Map<UUID, Long> shares = new LinkedHashMap<>();
        for (UUID member : members) {
            shares.put(member, member.equals(killer) ? share + remainder : share);
        }
        return new ExperienceSplit(experience, bonusPercent, total, shares);
    }

    /** {@code floor(amount * percent / 100)} without overflow for {@code 0 <= percent <= 100}. */
    static long percentOf(long amount, int percent) {
        return amount / 100 * percent + amount % 100 * percent / 100;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
