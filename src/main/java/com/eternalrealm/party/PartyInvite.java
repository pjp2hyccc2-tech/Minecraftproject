package com.eternalrealm.party;

import java.util.Objects;
import java.util.UUID;

/**
 * A pending invitation into a party. It is valid while {@code nowTick < expiresAtTick}.
 *
 * <p>Invites belong to the party, not to the inviter: if the inviting leader leaves or hands over the leadership, the
 * invite stays valid. It ends with exactly one of {@link PartyEvent.Joined}, {@link PartyEvent.InviteDeclined},
 * {@link PartyEvent.InviteExpired} or {@link PartyEvent.InviteCancelled}.</p>
 */
public record PartyInvite(PartyId partyId, UUID inviter, UUID invitee, long createdTick, long expiresAtTick) {
    public PartyInvite {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(inviter, "inviter");
        Objects.requireNonNull(invitee, "invitee");
        if (inviter.equals(invitee)) {
            throw new IllegalArgumentException("a player cannot invite themselves");
        }
        if (expiresAtTick <= createdTick) {
            throw new IllegalArgumentException("invite must expire after it was created: " + createdTick + " -> "
                    + expiresAtTick);
        }
    }

    public boolean isExpired(long nowTick) {
        return nowTick >= expiresAtTick;
    }

    /** Ticks left before the invite expires, {@code 0} once expired (for "expires in N seconds" texts). */
    public long remainingTicks(long nowTick) {
        return isExpired(nowTick) ? 0L : expiresAtTick - nowTick;
    }
}
