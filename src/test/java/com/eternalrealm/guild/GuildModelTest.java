package com.eternalrealm.guild;

import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GuildModelTest {
    private static final UUID ID = new UUID(4L, 1L);
    private static final UUID MASTER = new UUID(4L, 10L);
    private static final UUID OFFICER = new UUID(4L, 11L);
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final SeasonId S1 = SeasonId.of("s1");
    private static final SeasonId S2 = SeasonId.of("s2");

    private static Map<UUID, GuildMember> members(GuildRank... ranks) {
        Map<UUID, GuildMember> members = new LinkedHashMap<>();
        for (int i = 0; i < ranks.length; i++) {
            members.put(new UUID(4L, 10L + i), new GuildMember(ranks[i], NOW));
        }
        return members;
    }

    private static Guild guild(Map<UUID, GuildMember> members, Map<SeasonId, Long> contributions) {
        return new Guild(ID, "黒猫団", "CAT", NOW, members, contributions);
    }

    @Test
    public void guildNeedsValidNameTagAndExactlyOneMaster() {
        Map<UUID, GuildMember> ok = members(GuildRank.MASTER, GuildRank.OFFICER);
        assertEquals(MASTER, guild(ok, Map.of()).master());
        assertThrows(IllegalArgumentException.class, () -> new Guild(ID, " bad", "CAT", NOW, ok, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Guild(ID, "黒猫団", "cat", NOW, ok, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> guild(Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> guild(members(GuildRank.OFFICER), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> guild(members(GuildRank.MASTER, GuildRank.MASTER), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> guild(ok, Map.of(S1, -1L)));
    }

    @Test
    public void snapshotsAreImmutableAndQueriesWork() {
        Map<UUID, GuildMember> source = members(GuildRank.MASTER, GuildRank.OFFICER);
        Guild guild = guild(source, Map.of(S1, 400L, S2, 700L));
        source.clear();
        assertEquals("defensive copy", 2, guild.size());
        assertThrows(UnsupportedOperationException.class, () -> guild.members().clear());
        assertThrows(UnsupportedOperationException.class, () -> guild.contributions().clear());
        assertEquals(List.of(MASTER, OFFICER), List.copyOf(guild.members().keySet()));
        assertEquals(GuildRank.OFFICER, guild.rankOf(OFFICER).orElseThrow());
        assertTrue(guild.isMember(OFFICER));
        assertFalse(guild.isMember(new UUID(0L, 0L)));
        assertEquals(400, guild.contribution(S1));
        assertEquals(0, guild.contribution(SeasonId.of("s3")));
        assertEquals(1100, guild.totalContribution());
        assertEquals(2, guild.level(GuildRules.defaults()));
        assertEquals(35, guild.memberCap(GuildRules.defaults()));
    }

    @Test
    public void totalContributionSaturates() {
        Guild guild = guild(members(GuildRank.MASTER), Map.of(S1, Long.MAX_VALUE, S2, 5L));
        assertEquals(Long.MAX_VALUE, guild.totalContribution());
        assertEquals(10, guild.level(GuildRules.defaults()));
    }

    @Test
    public void rankPermissions() {
        assertTrue(GuildRank.MASTER.canInvite());
        assertTrue(GuildRank.OFFICER.canInvite());
        assertFalse(GuildRank.MEMBER.canInvite());
        assertTrue(GuildRank.MASTER.canKick(GuildRank.OFFICER));
        assertTrue(GuildRank.MASTER.canKick(GuildRank.MEMBER));
        assertFalse(GuildRank.MASTER.canKick(GuildRank.MASTER));
        assertTrue(GuildRank.OFFICER.canKick(GuildRank.MEMBER));
        assertFalse(GuildRank.OFFICER.canKick(GuildRank.OFFICER));
        assertFalse(GuildRank.OFFICER.canKick(GuildRank.MASTER));
        assertFalse(GuildRank.MEMBER.canKick(GuildRank.MEMBER));
        assertTrue(GuildRank.MASTER.canManageRanks());
        assertFalse(GuildRank.OFFICER.canManageRanks());
        assertEquals(GuildRank.OFFICER, GuildRank.byId("officer").orElseThrow());
        assertFalse(GuildRank.byId("OFFICER").isPresent());
        assertEquals("guild_rank.eternalrealm.master", GuildRank.MASTER.translationKey());
    }

    @Test
    public void defaultLevelsAndMemberCaps() {
        GuildRules rules = GuildRules.defaults();
        assertEquals(10, rules.maxLevel());
        assertEquals(1, rules.levelFor(0));
        assertEquals(1, rules.levelFor(999));
        assertEquals(2, rules.levelFor(1_000));
        assertEquals(5, rules.levelFor(15_000));
        assertEquals(10, rules.levelFor(Long.MAX_VALUE));
        assertEquals(30, rules.memberCap(1));
        assertEquals(35, rules.memberCap(2));
        assertEquals(50, rules.memberCap(5));
        assertEquals("never above 50", 50, rules.memberCap(10));
        assertEquals(OptionalLong.of(1_000), rules.contributionForNextLevel(1));
        assertEquals(OptionalLong.empty(), rules.contributionForNextLevel(10));
        assertThrows(IllegalArgumentException.class, () -> rules.memberCap(0));
        assertThrows(IllegalArgumentException.class, () -> rules.memberCap(11));
    }

    @Test
    public void rulesAreValidated() {
        Duration minute = Duration.ofMinutes(1);
        assertThrows(IllegalArgumentException.class, () -> new GuildRules(minute, List.of(), 30, 5, 50));
        assertThrows(IllegalArgumentException.class, () -> new GuildRules(minute, List.of(5L, 10L), 30, 5, 50));
        assertThrows(IllegalArgumentException.class, () -> new GuildRules(minute, List.of(0L, 10L, 10L), 30, 5, 50));
        assertThrows(IllegalArgumentException.class, () -> new GuildRules(minute, List.of(0L, 10L, 5L), 30, 5, 50));
        assertThrows(IllegalArgumentException.class, () -> new GuildRules(minute, List.of(0L), 0, 5, 50));
        assertThrows(IllegalArgumentException.class, () -> new GuildRules(minute, List.of(0L), 30, -1, 50));
        assertThrows(IllegalArgumentException.class, () -> new GuildRules(minute, List.of(0L), 30, 5, 29));
        assertThrows(IllegalArgumentException.class, () -> new GuildRules(minute, List.of(0L), 30, 5, 201));
        assertThrows(IllegalArgumentException.class, () -> new GuildRules(Duration.ofSeconds(9), List.of(0L), 30, 5, 50));
        assertThrows(IllegalArgumentException.class, () -> new GuildRules(Duration.ofDays(2), List.of(0L), 30, 5, 50));
        GuildRules single = new GuildRules(minute, List.of(0L), 2, 0, 2);
        assertEquals(1, single.levelFor(Long.MAX_VALUE));
        assertEquals(2, single.memberCap(1));
    }

    @Test
    public void invitesExpireAtTheirExpiryInstant() {
        GuildInvite invite = new GuildInvite(ID, MASTER, OFFICER, NOW, NOW.plusSeconds(60));
        assertFalse(invite.isExpired(NOW.plusSeconds(59)));
        assertTrue(invite.isExpired(NOW.plusSeconds(60)));
        assertThrows(IllegalArgumentException.class, () -> new GuildInvite(ID, MASTER, MASTER, NOW, NOW.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new GuildInvite(ID, MASTER, OFFICER, NOW, NOW));
    }
}
