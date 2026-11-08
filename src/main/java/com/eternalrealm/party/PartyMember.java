package com.eternalrealm.party;

import java.util.Objects;
import java.util.UUID;

/**
 * One member of a party.
 *
 * @param joinedTick server tick at which the player joined (or created) the party; informational, the canonical join
 *                   order is the order of {@link Party#members()}
 */
public record PartyMember(UUID playerId, long joinedTick) {
    public PartyMember {
        Objects.requireNonNull(playerId, "playerId");
    }
}
