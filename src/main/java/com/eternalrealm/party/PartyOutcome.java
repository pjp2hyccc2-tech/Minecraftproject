package com.eternalrealm.party;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Optional;

/**
 * Result code of a {@link PartyService} operation. Failures are normal gameplay (typos, races between two players'
 * clicks) and change nothing except possibly closing an invite that turned out to be expired.
 *
 * <p>Message arguments ({@code %s}) are listed per constant; the Forge layer fills in player names.</p>
 */
public enum PartyOutcome {
    OK,
    /** The target is the acting player (inviting, kicking or promoting yourself). No arguments. */
    SELF,
    /** The acting player is not in a party. No arguments. */
    NOT_IN_PARTY,
    /** Only the leader may do this. No arguments. */
    NOT_LEADER,
    /** The party is at its capacity. {@code %s} = capacity. */
    PARTY_FULL,
    /** The invited player already belongs to a party (possibly this one). {@code %s} = target name. */
    TARGET_IN_PARTY,
    /** This party already has a pending invite for the player. {@code %s} = target name. */
    ALREADY_INVITED,
    /** The acting player already belongs to a party (create, accept). No arguments. */
    ALREADY_IN_PARTY,
    /** No pending invite from that party: never invited, declined, expired or cancelled. No arguments. */
    NO_INVITE,
    /** The target is not a member of the acting player's party. {@code %s} = target name. */
    NOT_A_MEMBER,
    /** The party already is a raid. No arguments. */
    ALREADY_RAID;

    public boolean isSuccess() {
        return this == OK;
    }

    /** Translation key of the failure message; empty for {@link #OK} (successes are reported through events). */
    public Optional<String> messageKey() {
        if (this == OK) {
            return Optional.empty();
        }
        return Optional.of("message." + ModInfo.MOD_ID + ".party.error." + name().toLowerCase(Locale.ROOT));
    }
}
