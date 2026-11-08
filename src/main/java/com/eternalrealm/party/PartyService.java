package com.eternalrealm.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * All parties and pending party invites of one logical server. Server thread only.
 *
 * <p><b>Not persisted:</b> parties are session state and all end when the server stops (players simply form a new
 * party after a restart). Members stay in their party while offline; whether logging out means leaving is the Forge
 * layer's choice.</p>
 *
 * <p>Invariants (kept by every operation):</p>
 * <ul>
 *     <li>a player is in at most one party, and every party has at least one member including its leader;</li>
 *     <li>pending invites only exist for players who are in no party (creating or joining a party cancels the
 *         player's other invites), at most one per party and player, and only for existing parties;</li>
 *     <li>members are kept in join order; a leaving leader is replaced by the longest-standing member.</li>
 * </ul>
 *
 * <p>Every operation returns a {@link PartyResult} with the {@link PartyEvent}s to turn into messages and HUD
 * updates. Time is the server tick passed by the caller; it must not go backwards.</p>
 */
public final class PartyService {
    private final Map<PartyId, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, PartyId> membership = new HashMap<>();
    private final Map<InviteKey, PartyInvite> invites = new LinkedHashMap<>();
    private PartyRules rules;
    private long nextPartyId = 1;

    private record InviteKey(PartyId partyId, UUID invitee) {
    }

    public PartyService(PartyRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    // ------------------------------------------------------------------------------------------------ operations

    /** Creates a normal party led by {@code leader}. Cancels the leader's pending invites from other parties. */
    public PartyResult create(UUID leader, long nowTick) {
        Objects.requireNonNull(leader, "leader");
        if (membership.containsKey(leader)) {
            return PartyResult.failure(PartyOutcome.ALREADY_IN_PARTY);
        }
        Party party = new Party(new PartyId(nextPartyId++), PartyType.NORMAL, leader,
                List.of(new PartyMember(leader, nowTick)), nowTick);
        parties.put(party.id(), party);
        membership.put(leader, party.id());
        List<PartyEvent> events = new ArrayList<>();
        events.add(new PartyEvent.Created(party));
        cancelInvitesTo(leader, events);
        return PartyResult.ok(party, events);
    }

    /**
     * The leader invites a player who is in no party. The invite is valid for {@link PartyRules#inviteExpiryTicks()}.
     * Checks, in order: {@code SELF}, {@code NOT_IN_PARTY}, {@code NOT_LEADER}, {@code PARTY_FULL},
     * {@code TARGET_IN_PARTY}, {@code ALREADY_INVITED}. An expired invite to the same player is replaced.
     */
    public PartyResult invite(UUID inviter, UUID invitee, long nowTick) {
        Objects.requireNonNull(inviter, "inviter");
        Objects.requireNonNull(invitee, "invitee");
        if (inviter.equals(invitee)) {
            return PartyResult.failure(PartyOutcome.SELF);
        }
        Party party = find(inviter);
        if (party == null) {
            return PartyResult.failure(PartyOutcome.NOT_IN_PARTY);
        }
        if (!party.isLeader(inviter)) {
            return PartyResult.failure(PartyOutcome.NOT_LEADER);
        }
        if (isFull(party)) {
            return PartyResult.failure(PartyOutcome.PARTY_FULL);
        }
        if (membership.containsKey(invitee)) {
            return PartyResult.failure(PartyOutcome.TARGET_IN_PARTY);
        }
        List<PartyEvent> events = new ArrayList<>();
        if (liveInvite(party.id(), invitee, nowTick, events) != null) {
            return PartyResult.failure(PartyOutcome.ALREADY_INVITED);
        }
        PartyInvite invite = new PartyInvite(party.id(), inviter, invitee, nowTick,
                saturatedAdd(nowTick, rules.inviteExpiryTicks()));
        invites.put(new InviteKey(party.id(), invitee), invite);
        events.add(new PartyEvent.Invited(invite));
        return PartyResult.ok(party, events);
    }

    /**
     * Joins the party that invited {@code invitee}. A failure because the party is full keeps the invite, so the player
     * can retry while it is valid. Joining cancels the player's invites from other parties.
     */
    public PartyResult accept(UUID invitee, PartyId partyId, long nowTick) {
        Objects.requireNonNull(invitee, "invitee");
        Objects.requireNonNull(partyId, "partyId");
        if (membership.containsKey(invitee)) {
            return PartyResult.failure(PartyOutcome.ALREADY_IN_PARTY);
        }
        List<PartyEvent> events = new ArrayList<>();
        PartyInvite invite = liveInvite(partyId, invitee, nowTick, events);
        if (invite == null) {
            return PartyResult.failure(PartyOutcome.NO_INVITE, events);
        }
        Party party = parties.get(partyId);
        if (isFull(party)) {
            return PartyResult.failure(PartyOutcome.PARTY_FULL, events);
        }
        invites.remove(new InviteKey(partyId, invitee));
        Party joined = party.withMember(new PartyMember(invitee, nowTick));
        parties.put(partyId, joined);
        membership.put(invitee, partyId);
        events.add(new PartyEvent.Joined(joined, invitee));
        cancelInvitesTo(invitee, events);
        return PartyResult.ok(joined, events);
    }

    public PartyResult decline(UUID invitee, PartyId partyId, long nowTick) {
        Objects.requireNonNull(invitee, "invitee");
        Objects.requireNonNull(partyId, "partyId");
        List<PartyEvent> events = new ArrayList<>();
        PartyInvite invite = liveInvite(partyId, invitee, nowTick, events);
        if (invite == null) {
            return PartyResult.failure(PartyOutcome.NO_INVITE, events);
        }
        invites.remove(new InviteKey(partyId, invitee));
        events.add(new PartyEvent.InviteDeclined(invite));
        return PartyResult.ok(parties.get(partyId), events);
    }

    /**
     * Leaves the party. A leaving leader hands the leadership to the longest-standing member ({@link PartyEvent.Left}
     * then {@link PartyEvent.LeaderChanged}); the last member leaving disbands the party ({@link PartyEvent.Disbanded}
     * only, plus {@link PartyEvent.InviteCancelled} for its open invites).
     */
    public PartyResult leave(UUID member) {
        Objects.requireNonNull(member, "member");
        Party party = find(member);
        if (party == null) {
            return PartyResult.failure(PartyOutcome.NOT_IN_PARTY);
        }
        List<PartyEvent> events = new ArrayList<>();
        if (party.size() == 1) {
            disband(party, PartyEvent.Disbanded.Reason.LAST_MEMBER_LEFT, events);
            return PartyResult.ok(null, events);
        }
        Party remaining = party.withoutMember(member);
        parties.put(remaining.id(), remaining);
        membership.remove(member);
        events.add(new PartyEvent.Left(remaining, member));
        if (!remaining.leader().equals(party.leader())) {
            events.add(new PartyEvent.LeaderChanged(remaining, party.leader(), remaining.leader()));
        }
        return PartyResult.ok(remaining, events);
    }

    public PartyResult kick(UUID leader, UUID member) {
        Objects.requireNonNull(leader, "leader");
        Objects.requireNonNull(member, "member");
        if (leader.equals(member)) {
            return PartyResult.failure(PartyOutcome.SELF);
        }
        Party party = find(leader);
        PartyOutcome denied = checkLeader(party, leader);
        if (denied != null) {
            return PartyResult.failure(denied);
        }
        if (!party.contains(member)) {
            return PartyResult.failure(PartyOutcome.NOT_A_MEMBER);
        }
        Party remaining = party.withoutMember(member);
        parties.put(remaining.id(), remaining);
        membership.remove(member);
        return PartyResult.ok(remaining, List.of(new PartyEvent.Kicked(remaining, member, leader)));
    }

    /** Hands the leadership to another member. */
    public PartyResult promote(UUID leader, UUID member) {
        Objects.requireNonNull(leader, "leader");
        Objects.requireNonNull(member, "member");
        if (leader.equals(member)) {
            return PartyResult.failure(PartyOutcome.SELF);
        }
        Party party = find(leader);
        PartyOutcome denied = checkLeader(party, leader);
        if (denied != null) {
            return PartyResult.failure(denied);
        }
        if (!party.contains(member)) {
            return PartyResult.failure(PartyOutcome.NOT_A_MEMBER);
        }
        Party promoted = party.withLeader(member);
        parties.put(promoted.id(), promoted);
        return PartyResult.ok(promoted, List.of(new PartyEvent.LeaderChanged(promoted, leader, member)));
    }

    public PartyResult disband(UUID leader) {
        Objects.requireNonNull(leader, "leader");
        Party party = find(leader);
        PartyOutcome denied = checkLeader(party, leader);
        if (denied != null) {
            return PartyResult.failure(denied);
        }
        List<PartyEvent> events = new ArrayList<>();
        disband(party, PartyEvent.Disbanded.Reason.BY_LEADER, events);
        return PartyResult.ok(null, events);
    }

    /** Raises the capacity to {@link PartyRules#maxRaidSize()}. One-way. */
    public PartyResult convertToRaid(UUID leader) {
        Objects.requireNonNull(leader, "leader");
        Party party = find(leader);
        PartyOutcome denied = checkLeader(party, leader);
        if (denied != null) {
            return PartyResult.failure(denied);
        }
        if (party.isRaid()) {
            return PartyResult.failure(PartyOutcome.ALREADY_RAID);
        }
        Party raid = party.withType(PartyType.RAID);
        parties.put(raid.id(), raid);
        return PartyResult.ok(raid, List.of(new PartyEvent.ConvertedToRaid(raid)));
    }

    /** Removes every invite that has expired at {@code nowTick}. Call once per server tick. */
    public List<PartyEvent> expireInvites(long nowTick) {
        List<PartyEvent> events = new ArrayList<>();
        Iterator<PartyInvite> iterator = invites.values().iterator();
        while (iterator.hasNext()) {
            PartyInvite invite = iterator.next();
            if (invite.isExpired(nowTick)) {
                iterator.remove();
                events.add(new PartyEvent.InviteExpired(invite));
            }
        }
        return events.isEmpty() ? List.of() : List.copyOf(events);
    }

    // ------------------------------------------------------------------------------------------------ queries

    public Optional<Party> partyOf(UUID player) {
        return Optional.ofNullable(find(player));
    }

    public Optional<Party> party(PartyId id) {
        return Optional.ofNullable(parties.get(id));
    }

    /** Whether both players are in the same party (e.g. for friendly fire or shared loot). */
    public boolean sameParty(UUID first, UUID second) {
        PartyId party = membership.get(first);
        return party != null && party.equals(membership.get(second));
    }

    /** All parties in creation order. */
    public List<Party> parties() {
        return List.copyOf(parties.values());
    }

    /** Valid invites the player can still accept, oldest first. */
    public List<PartyInvite> invitesFor(UUID invitee, long nowTick) {
        List<PartyInvite> result = new ArrayList<>();
        for (PartyInvite invite : invites.values()) {
            if (invite.invitee().equals(invitee) && !invite.isExpired(nowTick)) {
                result.add(invite);
            }
        }
        return List.copyOf(result);
    }

    /** Valid invites the party has sent, oldest first. */
    public List<PartyInvite> invitesOf(PartyId partyId, long nowTick) {
        List<PartyInvite> result = new ArrayList<>();
        for (PartyInvite invite : invites.values()) {
            if (invite.partyId().equals(partyId) && !invite.isExpired(nowTick)) {
                result.add(invite);
            }
        }
        return List.copyOf(result);
    }

    public int capacity(Party party) {
        return rules.capacity(party.type());
    }

    public int size() {
        return parties.size();
    }

    public PartyRules rules() {
        return rules;
    }

    /**
     * Applies reloaded rules. Existing parties keep members beyond a lowered capacity (nobody is removed) but accept
     * nobody new until they are below it; pending invites keep their expiry tick.
     */
    public void setRules(PartyRules newRules) {
        this.rules = Objects.requireNonNull(newRules, "newRules");
    }

    // ------------------------------------------------------------------------------------------------ internals

    private Party find(UUID player) {
        PartyId id = membership.get(player);
        return id == null ? null : parties.get(id);
    }

    private static PartyOutcome checkLeader(Party party, UUID actor) {
        if (party == null) {
            return PartyOutcome.NOT_IN_PARTY;
        }
        return party.isLeader(actor) ? null : PartyOutcome.NOT_LEADER;
    }

    private boolean isFull(Party party) {
        return party.size() >= rules.capacity(party.type());
    }

    /** The invite if it is still valid; an expired one is removed and reported, as {@link #expireInvites} would. */
    private PartyInvite liveInvite(PartyId partyId, UUID invitee, long nowTick, List<PartyEvent> events) {
        InviteKey key = new InviteKey(partyId, invitee);
        PartyInvite invite = invites.get(key);
        if (invite != null && invite.isExpired(nowTick)) {
            invites.remove(key);
            events.add(new PartyEvent.InviteExpired(invite));
            return null;
        }
        return invite;
    }

    private void cancelInvitesTo(UUID invitee, List<PartyEvent> events) {
        Iterator<PartyInvite> iterator = invites.values().iterator();
        while (iterator.hasNext()) {
            PartyInvite invite = iterator.next();
            if (invite.invitee().equals(invitee)) {
                iterator.remove();
                events.add(new PartyEvent.InviteCancelled(invite));
            }
        }
    }

    private void disband(Party party, PartyEvent.Disbanded.Reason reason, List<PartyEvent> events) {
        parties.remove(party.id());
        for (PartyMember member : party.members()) {
            membership.remove(member.playerId());
        }
        events.add(new PartyEvent.Disbanded(party, reason));
        Iterator<PartyInvite> iterator = invites.values().iterator();
        while (iterator.hasNext()) {
            PartyInvite invite = iterator.next();
            if (invite.partyId().equals(party.id())) {
                iterator.remove();
                events.add(new PartyEvent.InviteCancelled(invite));
            }
        }
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
