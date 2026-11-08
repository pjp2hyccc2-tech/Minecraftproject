package com.eternalrealm.party;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable snapshot of a party. {@link PartyService} replaces the snapshot on every change, so a snapshot handed to
 * the HUD sync or carried by a {@link PartyEvent} never changes afterwards.
 *
 * <p>Invariants: at least one member, no duplicates, and the leader is a member. {@link #members()} is in join order
 * (oldest first); this order decides who inherits the leadership when the leader leaves.</p>
 *
 * @param createdTick server tick at which the party was created
 */
public record Party(PartyId id, PartyType type, UUID leader, List<PartyMember> members, long createdTick) {
    public Party {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(leader, "leader");
        members = List.copyOf(members);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("a party needs at least one member");
        }
        Set<UUID> seen = new HashSet<>();
        for (PartyMember member : members) {
            if (!seen.add(member.playerId())) {
                throw new IllegalArgumentException("duplicate party member " + member.playerId());
            }
        }
        if (!seen.contains(leader)) {
            throw new IllegalArgumentException("the leader " + leader + " is not a member");
        }
    }

    public int size() {
        return members.size();
    }

    public boolean contains(UUID playerId) {
        return member(playerId).isPresent();
    }

    public boolean isLeader(UUID playerId) {
        return leader.equals(playerId);
    }

    public boolean isRaid() {
        return type == PartyType.RAID;
    }

    public Optional<PartyMember> member(UUID playerId) {
        for (PartyMember member : members) {
            if (member.playerId().equals(playerId)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    /** Member UUIDs in join order. */
    public List<UUID> memberIds() {
        List<UUID> ids = new ArrayList<>(members.size());
        for (PartyMember member : members) {
            ids.add(member.playerId());
        }
        return List.copyOf(ids);
    }

    Party withMember(PartyMember member) {
        List<PartyMember> joined = new ArrayList<>(members);
        joined.add(member);
        return new Party(id, type, leader, joined, createdTick);
    }

    /**
     * Removes a member. If the leader is removed, the longest-standing remaining member (the first in join order)
     * becomes the leader. Must not remove the last member.
     */
    Party withoutMember(UUID playerId) {
        List<PartyMember> remaining = new ArrayList<>(members);
        remaining.removeIf(member -> member.playerId().equals(playerId));
        if (remaining.isEmpty()) {
            throw new IllegalStateException("cannot remove the last member of party " + id);
        }
        UUID newLeader = leader.equals(playerId) ? remaining.get(0).playerId() : leader;
        return new Party(id, type, newLeader, remaining, createdTick);
    }

    Party withLeader(UUID newLeader) {
        return new Party(id, type, newLeader, members, createdTick);
    }

    Party withType(PartyType newType) {
        return new Party(id, newType, leader, members, createdTick);
    }
}
