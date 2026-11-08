package com.eternalrealm.party;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PartyServiceTest {
    private static final UUID A = player(1);
    private static final UUID B = player(2);
    private static final UUID C = player(3);
    private static final UUID D = player(4);
    private static final UUID E = player(5);
    private static final UUID F = player(6);

    private final PartyService service = new PartyService(PartyRules.defaults());

    private static UUID player(int n) {
        return new UUID(0x5EED_0000_0000_0000L, n);
    }

    /** The invite created by a successful invite operation. */
    private static PartyInvite invited(PartyResult result) {
        assertEquals(PartyOutcome.OK, result.outcome());
        PartyEvent last = result.events().get(result.events().size() - 1);
        return ((PartyEvent.Invited) last).invite();
    }

    /** A party led by {@code leader} that {@code others} joined in this order. */
    private Party party(UUID leader, UUID... others) {
        Party party = service.create(leader, 0).party().orElseThrow();
        for (UUID other : others) {
            assertEquals(PartyOutcome.OK, service.invite(leader, other, 0).outcome());
            party = service.accept(other, party.id(), 0).party().orElseThrow();
        }
        return party;
    }

    // ------------------------------------------------------------------------------------------------ create

    @Test
    public void createMakesTheCreatorTheLeaderAndOnlyMember() {
        PartyResult result = service.create(A, 7);
        assertTrue(result.isSuccess());
        Party party = result.party().orElseThrow();
        assertEquals(A, party.leader());
        assertEquals(List.of(new PartyMember(A, 7)), party.members());
        assertEquals(PartyType.NORMAL, party.type());
        assertEquals(7, party.createdTick());
        assertEquals(List.of(new PartyEvent.Created(party)), result.events());
        assertEquals(Optional.of(party), service.partyOf(A));
        assertEquals(Optional.of(party), service.party(party.id()));
        assertEquals(5, service.capacity(party));

        PartyResult again = service.create(A, 8);
        assertEquals(PartyOutcome.ALREADY_IN_PARTY, again.outcome());
        assertFalse(again.party().isPresent());
        assertTrue(again.events().isEmpty());
        assertEquals(1, service.size());
    }

    @Test
    public void partyIdsAreNeverReused() {
        PartyId first = service.create(A, 0).party().orElseThrow().id();
        service.disband(A);
        PartyId second = service.create(A, 0).party().orElseThrow().id();
        assertTrue(second.value() > first.value());
        assertFalse(service.party(first).isPresent());
    }

    // ------------------------------------------------------------------------------------------------ invites

    @Test
    public void invitedPlayerJoinsAfterTheExistingMembers() {
        Party created = service.create(A, 0).party().orElseThrow();
        PartyResult invited = service.invite(A, B, 10);
        assertEquals(PartyOutcome.OK, invited.outcome());
        PartyInvite invite = new PartyInvite(created.id(), A, B, 10, 1210);
        assertEquals(List.of(new PartyEvent.Invited(invite)), invited.events());
        assertEquals(List.of(invite), service.invitesFor(B, 10));
        assertEquals(List.of(invite), service.invitesOf(created.id(), 10));

        PartyResult accepted = service.accept(B, created.id(), 20);
        Party joined = accepted.party().orElseThrow();
        assertEquals(List.of(A, B), joined.memberIds());
        assertEquals(new PartyMember(B, 20), joined.member(B).orElseThrow());
        assertEquals(List.of(new PartyEvent.Joined(joined, B)), accepted.events());
        assertEquals(Optional.of(joined), service.partyOf(B));
        assertTrue(service.sameParty(A, B));
        assertFalse(service.sameParty(A, C));
        assertFalse(service.sameParty(C, D));
        assertTrue(service.invitesFor(B, 20).isEmpty());
    }

    @Test
    public void inviteRulesAreCheckedInTheDocumentedOrder() {
        assertEquals(PartyOutcome.SELF, service.invite(A, A, 0).outcome());
        assertEquals(PartyOutcome.NOT_IN_PARTY, service.invite(A, B, 0).outcome());
        party(A, B);
        party(F);
        assertEquals(PartyOutcome.NOT_LEADER, service.invite(B, C, 0).outcome());
        assertEquals(PartyOutcome.TARGET_IN_PARTY, service.invite(A, F, 0).outcome());
        assertEquals("members of the same party cannot be invited either",
                PartyOutcome.TARGET_IN_PARTY, service.invite(A, B, 0).outcome());
        assertEquals(PartyOutcome.OK, service.invite(A, C, 0).outcome());
        assertEquals(PartyOutcome.ALREADY_INVITED, service.invite(A, C, 5).outcome());
        assertEquals(1, service.invitesFor(C, 5).size());
        service.accept(C, service.partyOf(A).orElseThrow().id(), 5);
        service.invite(A, D, 5);
        service.accept(D, service.partyOf(A).orElseThrow().id(), 5);
        service.invite(A, E, 5);
        service.accept(E, service.partyOf(A).orElseThrow().id(), 5);
        assertEquals(5, service.partyOf(A).orElseThrow().size());
        assertEquals(PartyOutcome.NOT_LEADER, service.invite(B, player(9), 5).outcome());
        assertEquals("full is checked before the target", PartyOutcome.PARTY_FULL, service.invite(A, F, 5).outcome());
    }

    @Test
    public void inviteIsValidUntilItsExpiryTick() {
        PartyId id = service.create(A, 0).party().orElseThrow().id();
        service.invite(A, B, 100); // expires at 1300
        assertEquals(1, service.invitesFor(B, 1299).size());
        assertTrue(service.expireInvites(1299).isEmpty());
        assertTrue(service.invitesFor(B, 1300).isEmpty());

        PartyResult late = service.accept(B, id, 1300);
        assertEquals(PartyOutcome.NO_INVITE, late.outcome());
        assertEquals(1, late.events().size());
        PartyEvent.InviteExpired expired = (PartyEvent.InviteExpired) late.events().get(0);
        assertEquals(B, expired.invite().invitee());
        assertTrue("an expiry is reported exactly once", service.expireInvites(5000).isEmpty());
        assertFalse(service.partyOf(B).isPresent());
    }

    @Test
    public void inviteCanBeAcceptedOnTheLastValidTick() {
        PartyId id = service.create(A, 0).party().orElseThrow().id();
        service.invite(A, B, 100);
        assertEquals(PartyOutcome.OK, service.accept(B, id, 1299).outcome());
    }

    @Test
    public void expireInvitesReportsEachExpiredInviteOnceInCreationOrder() {
        PartyId first = service.create(A, 0).party().orElseThrow().id();
        PartyId second = service.create(C, 0).party().orElseThrow().id();
        PartyInvite toB = invited(service.invite(A, B, 0));
        PartyInvite toBFromC = invited(service.invite(C, B, 600));
        PartyInvite toD = invited(service.invite(A, D, 600));
        assertEquals(first, toB.partyId());
        assertEquals(second, toBFromC.partyId());

        assertEquals(List.of(new PartyEvent.InviteExpired(toB)), service.expireInvites(1200));
        assertTrue(service.expireInvites(1200).isEmpty());
        assertEquals(List.of(new PartyEvent.InviteExpired(toBFromC), new PartyEvent.InviteExpired(toD)),
                service.expireInvites(1800));
        assertTrue(service.invitesFor(B, 0).isEmpty());
    }

    @Test
    public void expiredInviteIsReplacedByANewOne() {
        service.create(A, 0);
        PartyInvite old = invited(service.invite(A, B, 0));
        PartyResult renewed = service.invite(A, B, 1200);
        assertEquals(PartyOutcome.OK, renewed.outcome());
        assertEquals(2, renewed.events().size());
        assertEquals(new PartyEvent.InviteExpired(old), renewed.events().get(0));
        assertEquals(2400, ((PartyEvent.Invited) renewed.events().get(1)).invite().expiresAtTick());
    }

    @Test
    public void declineClosesTheInvite() {
        PartyId id = service.create(A, 0).party().orElseThrow().id();
        PartyInvite invite = invited(service.invite(A, B, 0));
        PartyResult declined = service.decline(B, id, 10);
        assertEquals(PartyOutcome.OK, declined.outcome());
        assertEquals(List.of(new PartyEvent.InviteDeclined(invite)), declined.events());
        assertEquals(Optional.of(id), declined.party().map(Party::id));
        assertEquals(PartyOutcome.NO_INVITE, service.decline(B, id, 10).outcome());
        assertEquals(PartyOutcome.NO_INVITE, service.accept(B, id, 10).outcome());

        service.invite(A, C, 0);
        PartyResult tooLate = service.decline(C, id, 1200);
        assertEquals(PartyOutcome.NO_INVITE, tooLate.outcome());
        assertTrue(tooLate.events().get(0) instanceof PartyEvent.InviteExpired);
        assertEquals(PartyOutcome.NO_INVITE, service.accept(C, new PartyId(999), 0).outcome());
    }

    @Test
    public void joiningOrCreatingCancelsTheOtherInvites() {
        PartyId fromA = service.create(A, 0).party().orElseThrow().id();
        service.create(C, 0);
        service.invite(A, B, 0);
        PartyInvite fromC = invited(service.invite(C, B, 0));
        PartyResult joined = service.accept(B, fromA, 5);
        assertEquals(2, joined.events().size());
        assertTrue(joined.events().get(0) instanceof PartyEvent.Joined);
        assertEquals(new PartyEvent.InviteCancelled(fromC), joined.events().get(1));
        assertTrue(service.invitesOf(fromC.partyId(), 5).isEmpty());

        PartyInvite toD = invited(service.invite(C, D, 5));
        PartyResult created = service.create(D, 6);
        assertEquals(List.of(new PartyEvent.Created(created.party().orElseThrow()), new PartyEvent.InviteCancelled(toD)),
                created.events());
        assertEquals(PartyOutcome.ALREADY_IN_PARTY, service.accept(D, toD.partyId(), 6).outcome());
    }

    @Test
    public void aFullPartyKeepsTheInviteSoThePlayerCanRetry() {
        PartyService small = new PartyService(new PartyRules(2, 3, 1200));
        PartyId id = small.create(A, 0).party().orElseThrow().id();
        small.invite(A, B, 0);
        small.invite(A, C, 0);
        assertEquals(PartyOutcome.OK, small.accept(B, id, 1).outcome());
        assertEquals(PartyOutcome.PARTY_FULL, small.accept(C, id, 2).outcome());
        assertEquals(1, small.invitesFor(C, 2).size());
        assertEquals(PartyOutcome.PARTY_FULL, small.invite(A, D, 2).outcome());
        small.leave(B);
        assertEquals(PartyOutcome.OK, small.accept(C, id, 3).outcome());
    }

    @Test
    public void invitesBelongToThePartyNotToTheInviter() {
        Party party = party(A, B);
        service.invite(A, C, 0);
        service.promote(A, B);
        service.leave(A);
        assertEquals(PartyOutcome.OK, service.accept(C, party.id(), 10).outcome());
        assertEquals(List.of(B, C), service.partyOf(C).orElseThrow().memberIds());
    }

    // ------------------------------------------------------------------------------------------------ leaving

    @Test
    public void aLeavingLeaderHandsOverToTheLongestStandingMember() {
        party(A, B, C, D);
        Party promoted = service.promote(A, C).party().orElseThrow();
        assertEquals(C, promoted.leader());
        assertEquals("promotion does not change the join order", List.of(A, B, C, D), promoted.memberIds());

        PartyResult left = service.leave(C);
        Party remaining = left.party().orElseThrow();
        assertEquals(A, remaining.leader());
        assertEquals(List.of(new PartyEvent.Left(remaining, C), new PartyEvent.LeaderChanged(remaining, C, A)),
                left.events());

        assertEquals(B, service.leave(A).party().orElseThrow().leader());
        assertEquals(List.of(B, D), service.partyOf(D).orElseThrow().memberIds());
    }

    @Test
    public void aLeavingMemberKeepsTheLeader() {
        party(A, B, C);
        PartyResult left = service.leave(B);
        Party remaining = left.party().orElseThrow();
        assertEquals(List.of(new PartyEvent.Left(remaining, B)), left.events());
        assertEquals(A, remaining.leader());
        assertEquals(List.of(A, C), remaining.memberIds());
        assertFalse(service.partyOf(B).isPresent());
        assertEquals(PartyOutcome.NOT_IN_PARTY, service.leave(B).outcome());
        assertEquals("a former member can found a new party", PartyOutcome.OK, service.create(B, 1).outcome());
    }

    @Test
    public void theLastMemberLeavingDisbandsThePartyAndCancelsItsInvites() {
        Party party = party(A);
        PartyInvite invite = invited(service.invite(A, B, 0));
        PartyResult left = service.leave(A);
        assertTrue(left.isSuccess());
        assertFalse(left.party().isPresent());
        assertEquals(List.of(new PartyEvent.Disbanded(party, PartyEvent.Disbanded.Reason.LAST_MEMBER_LEFT),
                new PartyEvent.InviteCancelled(invite)), left.events());
        assertEquals(0, service.size());
        assertFalse(service.partyOf(A).isPresent());
        assertEquals(PartyOutcome.NO_INVITE, service.accept(B, party.id(), 1).outcome());
    }

    // ------------------------------------------------------------------------------------------------ leader actions

    @Test
    public void onlyTheLeaderKicksOtherMembers() {
        party(A, B, C);
        assertEquals(PartyOutcome.SELF, service.kick(A, A).outcome());
        assertEquals(PartyOutcome.NOT_IN_PARTY, service.kick(D, A).outcome());
        assertEquals(PartyOutcome.NOT_LEADER, service.kick(B, C).outcome());
        assertEquals(PartyOutcome.NOT_A_MEMBER, service.kick(A, D).outcome());

        PartyResult kicked = service.kick(A, B);
        Party remaining = kicked.party().orElseThrow();
        assertEquals(List.of(new PartyEvent.Kicked(remaining, B, A)), kicked.events());
        assertEquals(List.of(A, C), remaining.memberIds());
        assertFalse(service.partyOf(B).isPresent());
    }

    @Test
    public void promoteHandsTheLeadershipToAMember() {
        party(A, B);
        assertEquals(PartyOutcome.SELF, service.promote(A, A).outcome());
        assertEquals(PartyOutcome.NOT_IN_PARTY, service.promote(C, A).outcome());
        assertEquals(PartyOutcome.NOT_LEADER, service.promote(B, A).outcome());
        assertEquals(PartyOutcome.NOT_A_MEMBER, service.promote(A, C).outcome());

        PartyResult promoted = service.promote(A, B);
        Party party = promoted.party().orElseThrow();
        assertEquals(List.of(new PartyEvent.LeaderChanged(party, A, B)), promoted.events());
        assertEquals(B, party.leader());
        assertEquals(PartyOutcome.NOT_LEADER, service.invite(A, C, 0).outcome());
        assertEquals(PartyOutcome.OK, service.invite(B, C, 0).outcome());
    }

    @Test
    public void disbandRemovesEveryMemberAndCancelsInvites() {
        Party party = party(A, B, C);
        PartyInvite invite = invited(service.invite(A, D, 0));
        assertEquals(PartyOutcome.NOT_LEADER, service.disband(B).outcome());
        assertEquals(PartyOutcome.NOT_IN_PARTY, service.disband(D).outcome());

        PartyResult disbanded = service.disband(A);
        assertEquals(List.of(new PartyEvent.Disbanded(party, PartyEvent.Disbanded.Reason.BY_LEADER),
                new PartyEvent.InviteCancelled(invite)), disbanded.events());
        for (UUID member : List.of(A, B, C)) {
            assertFalse(service.partyOf(member).isPresent());
        }
        assertTrue(service.parties().isEmpty());
        assertEquals(PartyOutcome.NOT_IN_PARTY, service.disband(A).outcome());
    }

    @Test
    public void aRaidHoldsTwentyPlayers() {
        party(A, B, C, D, E);
        assertEquals(PartyOutcome.PARTY_FULL, service.invite(A, F, 0).outcome());
        assertEquals(PartyOutcome.NOT_IN_PARTY, service.convertToRaid(F).outcome());
        assertEquals(PartyOutcome.NOT_LEADER, service.convertToRaid(B).outcome());

        PartyResult converted = service.convertToRaid(A);
        Party raid = converted.party().orElseThrow();
        assertEquals(List.of(new PartyEvent.ConvertedToRaid(raid)), converted.events());
        assertTrue(raid.isRaid());
        assertEquals(20, service.capacity(raid));
        assertEquals(PartyOutcome.ALREADY_RAID, service.convertToRaid(A).outcome());

        for (int n = 10; raid.size() < 20; n++) {
            assertEquals(PartyOutcome.OK, service.invite(A, player(n), 0).outcome());
            raid = service.accept(player(n), raid.id(), 0).party().orElseThrow();
        }
        assertEquals(PartyOutcome.PARTY_FULL, service.invite(A, F, 0).outcome());
    }

    @Test
    public void lowerCapacitiesKeepExistingMembers() {
        party(A, B, C);
        PartyRules strict = new PartyRules(2, 2, 100);
        service.setRules(strict);
        assertSame(strict, service.rules());
        assertEquals(3, service.partyOf(A).orElseThrow().size());
        assertEquals(PartyOutcome.PARTY_FULL, service.invite(A, D, 0).outcome());
        service.leave(C);
        service.leave(B);
        PartyResult invited = service.invite(A, D, 0);
        assertEquals(100, ((PartyEvent.Invited) invited.events().get(0)).invite().expiresAtTick());
    }

    @Test
    public void snapshotsNeverChangeAfterwards() {
        Party before = party(A, B);
        PartyResult kicked = service.kick(A, B);
        assertEquals(List.of(A, B), before.memberIds());
        assertThrows(UnsupportedOperationException.class, () -> before.members().clear());
        assertThrows(UnsupportedOperationException.class, () -> kicked.events().clear());
        assertThrows(UnsupportedOperationException.class, () -> service.parties().clear());
    }

    // ------------------------------------------------------------------------------------------------ properties

    @Test
    public void randomOperationsKeepTheInvariantsAndCloseEveryInviteExactlyOnce() {
        PartyService random = new PartyService(new PartyRules(3, 4, 30));
        Random rng = new Random(20261108L);
        List<UUID> players = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            players.add(player(100 + i));
        }
        Set<List<Object>> open = new HashSet<>();
        Map<Class<?>, Integer> seen = new HashMap<>();
        long now = 0;
        for (int step = 0; step < 20_000; step++) {
            now += rng.nextInt(4);
            // Pick actors by role so that most operations can succeed; 1 in 10 picks anybody to cover the failures.
            UUID anyone = players.get(rng.nextInt(players.size()));
            List<Party> parties = random.parties();
            Party party = parties.isEmpty() ? null : parties.get(rng.nextInt(parties.size()));
            List<UUID> solo = new ArrayList<>();
            for (UUID player : players) {
                if (random.partyOf(player).isEmpty()) {
                    solo.add(player);
                }
            }
            UUID leader = party == null || rng.nextInt(10) == 0 ? anyone : party.leader();
            UUID member = party == null || rng.nextInt(10) == 0 ? anyone
                    : party.memberIds().get(rng.nextInt(party.size()));
            UUID loner = solo.isEmpty() || rng.nextInt(10) == 0 ? anyone : solo.get(rng.nextInt(solo.size()));
            int op = rng.nextInt(20);
            PartyResult result;
            if (op < 2) {
                result = random.create(loner, now);
            } else if (op < 6) {
                result = random.invite(leader, loner, now);
            } else if (op < 10) {
                result = random.accept(loner, somePartyId(random, rng, loner, now), now);
            } else if (op < 12) {
                result = random.decline(loner, somePartyId(random, rng, loner, now), now);
            } else if (op < 14) {
                result = random.leave(member);
            } else if (op < 16) {
                result = random.kick(leader, member);
            } else if (op < 18) {
                result = random.promote(leader, member);
            } else if (op < 19) {
                result = random.disband(leader);
            } else {
                result = random.convertToRaid(leader);
            }
            assertEquals(result.isSuccess(), result.party().isPresent() || isDisband(result));
            track(result.events(), open, seen);
            if (step % 7 == 0) {
                track(random.expireInvites(now), open, seen);
            }
            assertConsistent(random, players, open, now);
        }
        track(random.expireInvites(Long.MAX_VALUE), open, seen);
        assertTrue("every invite must be closed exactly once: " + open, open.isEmpty());
        for (Class<?> type : PartyEvent.class.getPermittedSubclasses()) {
            assertTrue("the random run never produced " + type.getSimpleName() + ": " + seen,
                    seen.getOrDefault(type, 0) >= 20);
        }
    }

    private static boolean isDisband(PartyResult result) {
        return result.events().stream().anyMatch(event -> event instanceof PartyEvent.Disbanded);
    }

    private static PartyId somePartyId(PartyService service, Random rng, UUID player, long now) {
        List<PartyInvite> pending = service.invitesFor(player, now);
        if (!pending.isEmpty() && rng.nextInt(4) > 0) {
            return pending.get(rng.nextInt(pending.size())).partyId();
        }
        return new PartyId(1 + rng.nextInt(60));
    }

    private static List<Object> key(PartyInvite invite) {
        return List.of(invite.partyId(), invite.invitee());
    }

    private static void track(List<PartyEvent> events, Set<List<Object>> open, Map<Class<?>, Integer> seen) {
        Set<PartyId> disbanded = new HashSet<>();
        for (PartyEvent event : events) {
            seen.merge(event.getClass(), 1, Integer::sum);
            if (event instanceof PartyEvent.Invited invited) {
                assertTrue("second open invite " + invited, open.add(key(invited.invite())));
            } else if (event instanceof PartyEvent.InviteExpired expired) {
                assertTrue("closed twice " + expired, open.remove(key(expired.invite())));
            } else if (event instanceof PartyEvent.InviteDeclined declined) {
                assertTrue("closed twice " + declined, open.remove(key(declined.invite())));
            } else if (event instanceof PartyEvent.InviteCancelled cancelled) {
                assertTrue("closed twice " + cancelled, open.remove(key(cancelled.invite())));
            } else if (event instanceof PartyEvent.Joined joined) {
                assertTrue("joined without an invite " + joined, open.remove(List.of(joined.partyId(), joined.member())));
            } else if (event instanceof PartyEvent.Disbanded gone) {
                disbanded.add(gone.partyId());
            }
        }
        for (List<Object> key : open) {
            assertFalse("invite of a disbanded party left open " + key, disbanded.contains(key.get(0)));
        }
    }

    private static void assertConsistent(PartyService service, List<UUID> players, Set<List<Object>> open, long now) {
        Set<UUID> seen = new HashSet<>();
        for (Party party : service.parties()) {
            assertTrue(party.contains(party.leader()));
            assertTrue(party.size() <= service.capacity(party));
            for (UUID member : party.memberIds()) {
                assertTrue("player in two parties: " + member, seen.add(member));
                assertEquals(Optional.of(party), service.partyOf(member));
            }
        }
        for (UUID player : players) {
            assertEquals(seen.contains(player), service.partyOf(player).isPresent());
            if (seen.contains(player)) {
                assertTrue("party member with a pending invite", service.invitesFor(player, now).isEmpty());
            }
            for (PartyInvite invite : service.invitesFor(player, now)) {
                assertTrue("untracked invite " + invite, open.contains(key(invite)));
            }
        }
        for (List<Object> key : open) {
            assertTrue("invite of a missing party " + key, service.party((PartyId) key.get(0)).isPresent());
            assertFalse("invite to a party member " + key, service.partyOf((UUID) key.get(1)).isPresent());
        }
    }
}
