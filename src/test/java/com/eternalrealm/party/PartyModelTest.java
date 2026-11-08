package com.eternalrealm.party;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PartyModelTest {
    private static final UUID A = new UUID(1L, 1L);
    private static final UUID B = new UUID(1L, 2L);
    private static final UUID C = new UUID(1L, 3L);
    private static final PartyId ID = new PartyId(1);

    private static Party party(UUID leader, UUID... members) {
        List<PartyMember> list = new ArrayList<>();
        for (int i = 0; i < members.length; i++) {
            list.add(new PartyMember(members[i], i));
        }
        return new Party(ID, PartyType.NORMAL, leader, list, 0);
    }

    @Test
    public void partyValidatesItsInvariants() {
        assertThrows(IllegalArgumentException.class, () -> party(A));
        assertThrows(IllegalArgumentException.class, () -> party(C, A, B));
        assertThrows(IllegalArgumentException.class, () -> party(A, A, B, A));
        assertThrows(NullPointerException.class, () -> new Party(ID, null, A, List.of(new PartyMember(A, 0)), 0));
        assertThrows(IllegalArgumentException.class, () -> new PartyId(0));
        assertEquals("12", new PartyId(12).toString());
    }

    @Test
    public void queriesFollowTheJoinOrder() {
        Party party = party(B, A, B, C);
        assertEquals(3, party.size());
        assertEquals(List.of(A, B, C), party.memberIds());
        assertTrue(party.contains(C));
        assertFalse(party.contains(new UUID(9L, 9L)));
        assertTrue(party.isLeader(B));
        assertFalse(party.isLeader(A));
        assertFalse(party.isRaid());
        assertEquals(Optional.of(new PartyMember(C, 2)), party.member(C));
        assertTrue(party.withType(PartyType.RAID).isRaid());
    }

    @Test
    public void removingTheLeaderPromotesTheOldestRemainingMember() {
        Party party = party(B, A, B, C);
        assertEquals(A, party.withoutMember(B).leader());
        assertEquals(B, party.withoutMember(C).leader());
        Party alone = party(A, A);
        assertThrows(IllegalStateException.class, () -> alone.withoutMember(A));
    }

    @Test
    public void invitesExpireAtTheirExpiryTick() {
        PartyInvite invite = new PartyInvite(ID, A, B, 100, 1300);
        assertFalse(invite.isExpired(1299));
        assertTrue(invite.isExpired(1300));
        assertEquals(1, invite.remainingTicks(1299));
        assertEquals(0, invite.remainingTicks(2000));
        assertThrows(IllegalArgumentException.class, () -> new PartyInvite(ID, A, A, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new PartyInvite(ID, A, B, 10, 10));
    }

    @Test
    public void rulesAreValidated() {
        PartyRules rules = PartyRules.defaults();
        assertEquals(5, rules.capacity(PartyType.NORMAL));
        assertEquals(20, rules.capacity(PartyType.RAID));
        assertEquals(1200, rules.inviteExpiryTicks());
        assertThrows(IllegalArgumentException.class, () -> new PartyRules(1, 20, 1200));
        assertThrows(IllegalArgumentException.class, () -> new PartyRules(11, 20, 1200));
        assertThrows(IllegalArgumentException.class, () -> new PartyRules(5, 4, 1200));
        assertThrows(IllegalArgumentException.class, () -> new PartyRules(5, 41, 1200));
        assertThrows(IllegalArgumentException.class, () -> new PartyRules(5, 20, 0));
        assertThrows(IllegalArgumentException.class, () -> new PartyRules(5, 20, 72_001));
    }

    @Test
    public void failedResultsCarryNoParty() {
        Party party = party(A, A);
        assertThrows(IllegalArgumentException.class,
                () -> new PartyResult(PartyOutcome.NOT_LEADER, Optional.of(party), List.of()));
        assertFalse(PartyOutcome.OK.messageKey().isPresent());
        assertEquals(Optional.of("message.eternalrealm.party.error.not_leader"), PartyOutcome.NOT_LEADER.messageKey());
        assertEquals("party_type.eternalrealm.raid", PartyType.RAID.translationKey());
    }
}
