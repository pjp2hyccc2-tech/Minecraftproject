package com.eternalrealm.party;

import java.util.Objects;
import java.util.UUID;

/**
 * Something that happened to a party, returned by every {@link PartyService} operation in the order it happened.
 * The Forge layer turns events into chat messages ({@link PartyMessages}) and re-syncs the party HUD of the affected
 * players. Snapshots carried by events show the party <em>after</em> the change, except {@link Disbanded}, which
 * carries the last state so that every former member can be notified.
 */
public sealed interface PartyEvent permits PartyEvent.Created, PartyEvent.Invited, PartyEvent.InviteExpired,
        PartyEvent.InviteDeclined, PartyEvent.InviteCancelled, PartyEvent.Joined, PartyEvent.Left, PartyEvent.Kicked,
        PartyEvent.LeaderChanged, PartyEvent.ConvertedToRaid, PartyEvent.Disbanded {
    PartyId partyId();

    record Created(Party party) implements PartyEvent {
        public Created {
            Objects.requireNonNull(party, "party");
        }

        @Override
        public PartyId partyId() {
            return party.id();
        }
    }

    record Invited(PartyInvite invite) implements PartyEvent {
        public Invited {
            Objects.requireNonNull(invite, "invite");
        }

        @Override
        public PartyId partyId() {
            return invite.partyId();
        }
    }

    record InviteExpired(PartyInvite invite) implements PartyEvent {
        public InviteExpired {
            Objects.requireNonNull(invite, "invite");
        }

        @Override
        public PartyId partyId() {
            return invite.partyId();
        }
    }

    record InviteDeclined(PartyInvite invite) implements PartyEvent {
        public InviteDeclined {
            Objects.requireNonNull(invite, "invite");
        }

        @Override
        public PartyId partyId() {
            return invite.partyId();
        }
    }

    /** The invite became void: its party was disbanded, or the invitee joined or created another party. */
    record InviteCancelled(PartyInvite invite) implements PartyEvent {
        public InviteCancelled {
            Objects.requireNonNull(invite, "invite");
        }

        @Override
        public PartyId partyId() {
            return invite.partyId();
        }
    }

    record Joined(Party party, UUID member) implements PartyEvent {
        public Joined {
            Objects.requireNonNull(party, "party");
            Objects.requireNonNull(member, "member");
        }

        @Override
        public PartyId partyId() {
            return party.id();
        }
    }

    /**
     * A member left a party that continues to exist. When the last member leaves, only {@link Disbanded} is emitted.
     * If the leader left, a {@link LeaderChanged} follows.
     */
    record Left(Party party, UUID member) implements PartyEvent {
        public Left {
            Objects.requireNonNull(party, "party");
            Objects.requireNonNull(member, "member");
        }

        @Override
        public PartyId partyId() {
            return party.id();
        }
    }

    record Kicked(Party party, UUID member, UUID kickedBy) implements PartyEvent {
        public Kicked {
            Objects.requireNonNull(party, "party");
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(kickedBy, "kickedBy");
        }

        @Override
        public PartyId partyId() {
            return party.id();
        }
    }

    record LeaderChanged(Party party, UUID previousLeader, UUID newLeader) implements PartyEvent {
        public LeaderChanged {
            Objects.requireNonNull(party, "party");
            Objects.requireNonNull(previousLeader, "previousLeader");
            Objects.requireNonNull(newLeader, "newLeader");
        }

        @Override
        public PartyId partyId() {
            return party.id();
        }
    }

    record ConvertedToRaid(Party party) implements PartyEvent {
        public ConvertedToRaid {
            Objects.requireNonNull(party, "party");
        }

        @Override
        public PartyId partyId() {
            return party.id();
        }
    }

    /** @param party the last state of the party, with every member at the time it ended */
    record Disbanded(Party party, Reason reason) implements PartyEvent {
        public enum Reason {
            /** The leader disbanded the party. */
            BY_LEADER,
            /** The only remaining member left. */
            LAST_MEMBER_LEFT
        }

        public Disbanded {
            Objects.requireNonNull(party, "party");
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public PartyId partyId() {
            return party.id();
        }
    }
}
