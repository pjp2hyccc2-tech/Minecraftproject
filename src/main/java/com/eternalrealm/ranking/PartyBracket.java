package com.eternalrealm.ranking;

import com.eternalrealm.core.ModInfo;

import java.util.List;
import java.util.Optional;

/**
 * Party-size brackets of the boss time attack: a solo clear and a 20-player raid are ranked separately.
 * {@link #id()} is the stable persistence key.
 */
public enum PartyBracket {
    SOLO("solo", 1, 1),
    PARTY("party", 2, 5),
    RAID("raid", 6, 20);

    /** Largest party a time-attack run may have (the upper bound of {@link #RAID}). */
    public static final int MAX_MEMBERS = 20;

    private static final List<PartyBracket> ALL = List.of(values());

    private final String id;
    private final int minMembers;
    private final int maxMembers;

    PartyBracket(String id, int minMembers, int maxMembers) {
        this.id = id;
        this.minMembers = minMembers;
        this.maxMembers = maxMembers;
    }

    /** @throws IllegalArgumentException outside {@code [1, MAX_MEMBERS]} */
    public static PartyBracket of(int memberCount) {
        for (PartyBracket bracket : ALL) {
            if (memberCount >= bracket.minMembers && memberCount <= bracket.maxMembers) {
                return bracket;
            }
        }
        throw new IllegalArgumentException("No party bracket for " + memberCount + " members (1 to " + MAX_MEMBERS + ")");
    }

    public String id() {
        return id;
    }

    public int minMembers() {
        return minMembers;
    }

    public int maxMembers() {
        return maxMembers;
    }

    public String translationKey() {
        return "party_bracket." + ModInfo.MOD_ID + "." + id;
    }

    public static List<PartyBracket> all() {
        return ALL;
    }

    public static Optional<PartyBracket> byId(String id) {
        for (PartyBracket bracket : ALL) {
            if (bracket.id.equals(id)) {
                return Optional.of(bracket);
            }
        }
        return Optional.empty();
    }
}
