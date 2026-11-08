package com.eternalrealm.guild;

import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GuildServiceTest {
    private static final Instant T0 = Instant.parse("2026-11-08T00:00:00Z");
    private static final SeasonId S1 = SeasonId.of("s1");
    private static final SeasonId S2 = SeasonId.of("s2");
    private static final UUID A = player(1);
    private static final UUID B = player(2);
    private static final UUID C = player(3);
    private static final UUID D = player(4);
    private static final UUID E = player(5);

    private GuildService service = new GuildService(GuildRules.defaults(), new SplittableRandom(1L), List.of());

    private static UUID player(int n) {
        return new UUID(0x6E11DL, n);
    }

    private static GuildInvite invited(GuildResult result) {
        assertEquals(GuildOutcome.OK, result.outcome());
        return ((GuildEvent.Invited) result.events().get(result.events().size() - 1)).invite();
    }

    /** A guild founded by {@code master} that {@code members} joined in this order. */
    private Guild guild(UUID master, String name, String tag, UUID... members) {
        Guild guild = service.create(master, name, tag, T0).guild().orElseThrow();
        for (UUID member : members) {
            assertEquals(GuildOutcome.OK, service.invite(master, member, T0).outcome());
            guild = service.accept(member, guild.id(), T0).guild().orElseThrow();
        }
        return guild;
    }

    // ------------------------------------------------------------------------------------------------ founding

    @Test
    public void createFoundsAGuildLedByTheFounder() {
        GuildResult result = service.create(A, "黒猫団", "cat", T0);
        Guild guild = result.guild().orElseThrow();
        assertEquals("黒猫団", guild.name());
        assertEquals("CAT", guild.tag());
        assertEquals(T0, guild.createdAt());
        assertEquals(Map.of(A, new GuildMember(GuildRank.MASTER, T0)), guild.members());
        assertTrue(guild.contributions().isEmpty());
        assertEquals(List.of(new GuildEvent.Created(guild)), result.events());
        assertEquals(4, guild.id().version());
        assertEquals(2, guild.id().variant());
        assertEquals(Optional.of(guild), service.guildOf(A));
        assertEquals(Optional.of(guild), service.guild(guild.id()));
        assertEquals(Optional.of(guild), service.byName("黒猫団"));
        assertEquals(Optional.of(guild), service.byTag("Cat"));
        assertEquals(1, service.level(guild));
        assertEquals(30, service.memberCap(guild));
        assertTrue(service.isDirty());
    }

    @Test
    public void createRulesAreCheckedInTheDocumentedOrder() {
        service.create(A, "Alpha", "ALP", T0);
        assertEquals(GuildOutcome.ALREADY_IN_GUILD, service.create(A, " x", "!", T0).outcome());
        assertEquals(GuildOutcome.INVALID_NAME, service.create(B, " Beta", "!", T0).outcome());
        assertEquals(GuildOutcome.INVALID_NAME, service.create(B, null, "BET", T0).outcome());
        assertEquals(GuildOutcome.INVALID_TAG, service.create(B, "Alpha", "B", T0).outcome());
        assertEquals(GuildOutcome.NAME_TAKEN, service.create(B, "ALPHA", "ALP", T0).outcome());
        assertEquals(GuildOutcome.NAME_TAKEN, service.create(B, "ａｌｐｈａ", "BET", T0).outcome());
        assertEquals(GuildOutcome.TAG_TAKEN, service.create(B, "Beta", "alp", T0).outcome());
        assertEquals(GuildOutcome.OK, service.checkCreate(B, "Beta", "BET"));
        assertEquals("checkCreate changes nothing", 1, service.size());
        assertFalse(service.create(B, "Beta", "BET", T0).events().isEmpty());
        assertEquals(2, service.size());
    }

    @Test
    public void decomposedInputIsStoredComposed() {
        Guild guild = service.create(A, "がき", "GK", T0).guild().orElseThrow();
        assertEquals("がき", guild.name());
        assertEquals(GuildOutcome.NAME_TAKEN, service.create(B, "がき", "GK2", T0).outcome());
        assertEquals(Optional.of(guild), service.byName("がき"));
    }

    @Test
    public void guildIdsComeFromTheInjectedRandomGenerator() {
        GuildService first = new GuildService(GuildRules.defaults(), new SplittableRandom(7L), List.of());
        GuildService second = new GuildService(GuildRules.defaults(), new SplittableRandom(7L), List.of());
        UUID one = first.create(A, "Alpha", "ALP", T0).guild().orElseThrow().id();
        UUID two = second.create(A, "Alpha", "ALP", T0).guild().orElseThrow().id();
        assertEquals(one, two);
        assertNotEquals(one, first.create(B, "Beta", "BET", T0).guild().orElseThrow().id());
    }

    // ------------------------------------------------------------------------------------------------ invites

    @Test
    public void officersAndTheMasterInvite() {
        guild(A, "Alpha", "ALP", B, C);
        guild(E, "Echo", "ECH");
        assertEquals(GuildOutcome.SELF, service.invite(A, A, T0).outcome());
        assertEquals(GuildOutcome.NOT_IN_GUILD, service.invite(D, C, T0).outcome());
        assertEquals(GuildOutcome.NO_PERMISSION, service.invite(B, D, T0).outcome());
        service.promote(A, B);
        assertEquals(GuildOutcome.OK, service.invite(B, D, T0).outcome());
        assertEquals(GuildOutcome.ALREADY_INVITED, service.invite(A, D, T0).outcome());
        assertEquals(GuildOutcome.TARGET_IN_GUILD, service.invite(A, E, T0).outcome());
        assertEquals(GuildOutcome.TARGET_IN_GUILD, service.invite(A, C, T0).outcome());
    }

    @Test
    public void acceptJoinsAsMemberBeforeTheInviteExpires() {
        Guild guild = guild(A, "Alpha", "ALP");
        long revision = service.revision();
        GuildResult invited = service.invite(A, B, T0);
        GuildInvite invite = new GuildInvite(guild.id(), A, B, T0, T0.plus(Duration.ofMinutes(5)));
        assertEquals(List.of(new GuildEvent.Invited(invite)), invited.events());
        assertEquals(List.of(invite), service.invitesFor(B, T0));
        assertEquals("invites are not persistent state", revision, service.revision());

        GuildResult accepted = service.accept(B, guild.id(), T0.plusSeconds(299));
        Guild joined = accepted.guild().orElseThrow();
        assertEquals(new GuildMember(GuildRank.MEMBER, T0.plusSeconds(299)), joined.members().get(B));
        assertEquals(List.of(new GuildEvent.Joined(joined, B)), accepted.events());
        assertEquals(Optional.of(joined), service.guildOf(B));
        assertTrue(service.revision() > revision);
    }

    @Test
    public void anExpiredInviteCannotBeAccepted() {
        Guild guild = guild(A, "Alpha", "ALP");
        GuildInvite invite = invited(service.invite(A, B, T0));
        GuildResult late = service.accept(B, guild.id(), T0.plus(Duration.ofMinutes(5)));
        assertEquals(GuildOutcome.NO_INVITE, late.outcome());
        assertEquals(List.of(new GuildEvent.InviteExpired(invite)), late.events());
        assertTrue(service.expireInvites(T0.plus(Duration.ofDays(1))).isEmpty());
        assertFalse(service.guildOf(B).isPresent());
    }

    @Test
    public void expireInvitesReportsEachExpiredInviteOnce() {
        guild(A, "Alpha", "ALP");
        GuildInvite first = invited(service.invite(A, B, T0));
        GuildInvite second = invited(service.invite(A, C, T0.plusSeconds(60)));
        assertTrue(service.expireInvites(T0.plusSeconds(299)).isEmpty());
        assertEquals(List.of(new GuildEvent.InviteExpired(first)), service.expireInvites(T0.plusSeconds(300)));
        assertEquals(List.of(new GuildEvent.InviteExpired(second)), service.expireInvites(T0.plusSeconds(400)));
        assertTrue(service.expireInvites(T0.plusSeconds(500)).isEmpty());
        assertTrue(service.invitesFor(C, T0).isEmpty());
    }

    @Test
    public void declineClosesTheInvite() {
        Guild guild = guild(A, "Alpha", "ALP");
        GuildInvite invite = invited(service.invite(A, B, T0));
        GuildResult declined = service.decline(B, guild.id(), T0);
        assertEquals(List.of(new GuildEvent.InviteDeclined(invite)), declined.events());
        assertEquals(GuildOutcome.NO_INVITE, service.decline(B, guild.id(), T0).outcome());
        assertEquals(GuildOutcome.NO_INVITE, service.accept(B, guild.id(), T0).outcome());
        assertEquals(GuildOutcome.NO_INVITE, service.accept(B, UUID.randomUUID(), T0).outcome());
    }

    @Test
    public void joiningOrFoundingCancelsTheOtherInvites() {
        Guild alpha = guild(A, "Alpha", "ALP");
        guild(C, "Gamma", "GAM");
        service.invite(A, B, T0);
        GuildInvite fromGamma = invited(service.invite(C, B, T0));
        GuildResult joined = service.accept(B, alpha.id(), T0);
        assertEquals(new GuildEvent.InviteCancelled(fromGamma), joined.events().get(1));

        GuildInvite toD = invited(service.invite(C, D, T0));
        GuildResult founded = service.create(D, "Delta", "DEL", T0);
        assertEquals(List.of(new GuildEvent.Created(founded.guild().orElseThrow()), new GuildEvent.InviteCancelled(toD)),
                founded.events());
        assertEquals(GuildOutcome.ALREADY_IN_GUILD, service.accept(D, toD.guildId(), T0).outcome());
    }

    @Test
    public void theMemberCapGrowsWithTheGuildLevel() {
        service = new GuildService(new GuildRules(Duration.ofMinutes(5), List.of(0L, 100L), 2, 1, 3),
                new SplittableRandom(1L), List.of());
        Guild guild = guild(A, "Alpha", "ALP");
        service.invite(A, B, T0);
        service.invite(A, C, T0);
        assertEquals(GuildOutcome.OK, service.accept(B, guild.id(), T0).outcome());
        assertEquals(GuildOutcome.GUILD_FULL, service.accept(C, guild.id(), T0).outcome());
        assertEquals("a full guild keeps the invite", 1, service.invitesFor(C, T0).size());
        assertEquals(GuildOutcome.GUILD_FULL, service.invite(A, D, T0).outcome());

        GuildResult levelUp = service.addContribution(B, S1, 100);
        Guild levelled = levelUp.guild().orElseThrow();
        assertEquals(List.of(new GuildEvent.LevelUp(levelled, 1, 2)), levelUp.events());
        assertEquals(3, service.memberCap(levelled));
        assertEquals(GuildOutcome.OK, service.accept(C, guild.id(), T0).outcome());
    }

    // ------------------------------------------------------------------------------------------------ membership

    @Test
    public void theMasterCannotLeaveWithoutHandingOver() {
        guild(A, "Alpha", "ALP", B);
        assertEquals(GuildOutcome.MASTER_MUST_TRANSFER, service.leave(A).outcome());
        GuildResult left = service.leave(B);
        Guild remaining = left.guild().orElseThrow();
        assertEquals(List.of(new GuildEvent.Left(remaining, B)), left.events());
        assertFalse(service.guildOf(B).isPresent());
        assertEquals(GuildOutcome.NOT_IN_GUILD, service.leave(B).outcome());
        assertEquals("even alone", GuildOutcome.MASTER_MUST_TRANSFER, service.leave(A).outcome());
    }

    @Test
    public void officersKickMembersAndTheMasterKicksAnyone() {
        guild(A, "Alpha", "ALP", B, C, D);
        service.promote(A, B);
        service.promote(A, C);
        assertEquals(GuildOutcome.SELF, service.kick(B, B).outcome());
        assertEquals(GuildOutcome.NOT_IN_GUILD, service.kick(E, D).outcome());
        assertEquals(GuildOutcome.NOT_A_MEMBER, service.kick(B, E).outcome());
        assertEquals("member kicks officer", GuildOutcome.NO_PERMISSION, service.kick(D, B).outcome());
        assertEquals("officer kicks officer", GuildOutcome.NO_PERMISSION, service.kick(B, C).outcome());
        assertEquals("officer kicks master", GuildOutcome.NO_PERMISSION, service.kick(B, A).outcome());

        GuildResult kicked = service.kick(B, D);
        Guild remaining = kicked.guild().orElseThrow();
        assertEquals(List.of(new GuildEvent.Kicked(remaining, D, B)), kicked.events());
        assertFalse(service.guildOf(D).isPresent());
        assertEquals(GuildOutcome.OK, service.kick(A, C).outcome());
        assertEquals(List.of(A, B), List.copyOf(service.guildOf(A).orElseThrow().members().keySet()));
    }

    @Test
    public void onlyTheMasterPromotesAndDemotes() {
        guild(A, "Alpha", "ALP", B, C);
        assertEquals(GuildOutcome.SELF, service.promote(A, A).outcome());
        assertEquals(GuildOutcome.NOT_IN_GUILD, service.promote(D, A).outcome());
        assertEquals(GuildOutcome.NO_PERMISSION, service.promote(B, C).outcome());
        assertEquals(GuildOutcome.NOT_A_MEMBER, service.promote(A, D).outcome());

        GuildResult promoted = service.promote(A, B);
        Guild guild = promoted.guild().orElseThrow();
        assertEquals(List.of(new GuildEvent.RankChanged(guild, B, GuildRank.MEMBER, GuildRank.OFFICER)),
                promoted.events());
        assertEquals(GuildOutcome.ALREADY_OFFICER, service.promote(A, B).outcome());
        assertEquals("officers cannot promote", GuildOutcome.NO_PERMISSION, service.promote(B, C).outcome());

        GuildResult demoted = service.demote(A, B);
        assertEquals(GuildRank.MEMBER, demoted.guild().orElseThrow().rankOf(B).orElseThrow());
        assertEquals(GuildOutcome.ALREADY_MEMBER, service.demote(A, B).outcome());
        assertEquals(GuildOutcome.SELF, service.demote(A, A).outcome());
    }

    @Test
    public void transferMasterHandsTheGuildOver() {
        guild(A, "Alpha", "ALP", B);
        assertEquals(GuildOutcome.NO_PERMISSION, service.transferMaster(B, A).outcome());
        assertEquals(GuildOutcome.SELF, service.transferMaster(A, A).outcome());
        assertEquals(GuildOutcome.NOT_A_MEMBER, service.transferMaster(A, C).outcome());

        GuildResult transferred = service.transferMaster(A, B);
        Guild guild = transferred.guild().orElseThrow();
        assertEquals(List.of(new GuildEvent.MasterTransferred(guild, A, B)), transferred.events());
        assertEquals(B, guild.master());
        assertEquals(GuildRank.OFFICER, guild.rankOf(A).orElseThrow());
        assertEquals(GuildOutcome.OK, service.leave(A).outcome());
    }

    @Test
    public void disbandDeletesTheGuildAndFreesNameTagAndMembers() {
        Guild guild = guild(A, "Alpha", "ALP", B);
        GuildInvite invite = invited(service.invite(A, C, T0));
        assertEquals(GuildOutcome.NO_PERMISSION, service.disband(B).outcome());
        assertEquals(GuildOutcome.NOT_IN_GUILD, service.disband(D).outcome());

        GuildResult disbanded = service.disband(A);
        assertFalse(disbanded.guild().isPresent());
        assertEquals(List.of(new GuildEvent.Disbanded(guild), new GuildEvent.InviteCancelled(invite)),
                disbanded.events());
        assertEquals(0, service.size());
        assertFalse(service.guildOf(B).isPresent());
        assertFalse(service.byName("Alpha").isPresent());
        assertFalse(service.byTag("ALP").isPresent());
        assertEquals(GuildOutcome.OK, service.create(B, "alpha", "alp", T0).outcome());
    }

    // ------------------------------------------------------------------------------------------------ contribution

    @Test
    public void contributionIsKeptPerSeasonAndRaisesTheLevel() {
        guild(A, "Alpha", "ALP", B);
        GuildResult first = service.addContribution(B, S1, 600);
        assertTrue(first.events().isEmpty());
        GuildResult second = service.addContribution(A, S1, 500);
        Guild guild = second.guild().orElseThrow();
        assertEquals(List.of(new GuildEvent.LevelUp(guild, 1, 2)), second.events());
        assertEquals(1100, guild.contribution(S1));
        assertEquals(0, guild.contribution(S2));

        service.addContribution(A, S2, Long.MAX_VALUE);
        GuildResult saturated = service.addContribution(A, S2, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, saturated.guild().orElseThrow().contribution(S2));
        assertEquals(Long.MAX_VALUE, saturated.guild().orElseThrow().totalContribution());
        assertTrue("no level up at the maximum level", saturated.events().isEmpty());
        assertEquals(10, service.level(saturated.guild().orElseThrow()));

        assertEquals(GuildOutcome.NOT_IN_GUILD, service.addContribution(C, S1, 1).outcome());
        assertThrows(IllegalArgumentException.class, () -> service.addContribution(A, S1, 0));
        assertThrows(IllegalArgumentException.class, () -> service.addContribution(A, S1, -5));
    }

    // ------------------------------------------------------------------------------------------------ persistence

    @Test
    public void revisionTracksPersistentChangesOnly() {
        assertFalse(service.isDirty());
        Guild guild = guild(A, "Alpha", "ALP");
        long afterCreate = service.revision();
        service.markSaved(afterCreate);
        assertFalse(service.isDirty());
        service.invite(A, B, T0);
        service.expireInvites(T0.plus(Duration.ofHours(1)));
        assertFalse("invites are not persisted", service.isDirty());
        service.invite(A, B, T0.plus(Duration.ofHours(1)));
        service.accept(B, guild.id(), T0.plus(Duration.ofHours(1)));
        assertTrue(service.isDirty());
        service.markSaved(afterCreate);
        assertTrue("an older save does not clean newer changes", service.isDirty());
        service.markSaved(service.revision());
        assertFalse(service.isDirty());
        service.markSaved(afterCreate);
        assertFalse("markSaved never moves backwards", service.isDirty());
    }

    @Test
    public void storedGuildsAreLoadedAndValidated() {
        Guild alpha = new Guild(player(100), "Alpha", "ALP", T0, Map.of(A, new GuildMember(GuildRank.MASTER, T0)),
                Map.of(S1, 2_000L));
        Guild beta = new Guild(player(101), "Beta", "BET", T0, Map.of(B, new GuildMember(GuildRank.MASTER, T0)),
                Map.of());
        GuildService loaded = new GuildService(GuildRules.defaults(), new SplittableRandom(1L), List.of(alpha, beta));
        assertEquals(List.of(alpha, beta), loaded.guilds());
        assertEquals(Optional.of(alpha), loaded.guildOf(A));
        assertEquals(Optional.of(beta), loaded.byTag("bet"));
        assertEquals(2, loaded.level(alpha));
        assertFalse(loaded.isDirty());
        assertEquals(GuildOutcome.NAME_TAKEN, loaded.create(C, "ALPHA", "XYZ", T0).outcome());
        assertEquals(GuildOutcome.ALREADY_IN_GUILD, loaded.create(A, "Gamma", "GAM", T0).outcome());

        Guild sameId = new Guild(alpha.id(), "Other", "OTH", T0, Map.of(C, new GuildMember(GuildRank.MASTER, T0)), Map.of());
        Guild sameName = new Guild(player(102), "ALPHA", "OTH", T0, Map.of(C, new GuildMember(GuildRank.MASTER, T0)), Map.of());
        Guild sameTag = new Guild(player(102), "Other", "ALP", T0, Map.of(C, new GuildMember(GuildRank.MASTER, T0)), Map.of());
        Guild samePlayer = new Guild(player(102), "Other", "OTH", T0, Map.of(A, new GuildMember(GuildRank.MASTER, T0)), Map.of());
        for (Guild conflict : List.of(sameId, sameName, sameTag, samePlayer)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new GuildService(GuildRules.defaults(), new SplittableRandom(1L), List.of(alpha, conflict)));
        }
    }

    @Test
    public void lowerCapsKeepExistingMembers() {
        guild(A, "Alpha", "ALP", B, C);
        GuildRules strict = new GuildRules(Duration.ofMinutes(1), List.of(0L), 2, 0, 2);
        service.setRules(strict);
        assertEquals(strict, service.rules());
        assertEquals(3, service.guildOf(A).orElseThrow().size());
        assertEquals(GuildOutcome.GUILD_FULL, service.invite(A, D, T0).outcome());
    }

    // ------------------------------------------------------------------------------------------------ properties

    @Test
    public void randomOperationsKeepTheInvariants() {
        GuildRules rules = new GuildRules(Duration.ofSeconds(30), List.of(0L, 50L, 100L), 3, 1, 5);
        GuildService random = new GuildService(rules, new SplittableRandom(3L), List.of());
        Random rng = new Random(20261108L);
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            players.add(player(200 + i));
        }
        List<String> names = List.of("Alpha", "alpha", "ＡＬＰＨＡ", "Beta", "黒猫団", "ｸﾛ", "クロ", "Gamma");
        List<String> tags = List.of("AA", "aa", "BB", "CC", "DD", "x");
        Set<List<UUID>> open = new HashSet<>();
        Map<UUID, Integer> levels = new HashMap<>();
        Map<Class<?>, Integer> seen = new HashMap<>();
        Instant now = T0;
        for (int step = 0; step < 20_000; step++) {
            now = now.plusMillis(rng.nextInt(3_000));
            // Pick actors by role so that most operations can succeed; 1 in 10 picks anybody to cover the failures.
            UUID anyone = players.get(rng.nextInt(players.size()));
            List<Guild> guilds = random.guilds();
            Guild guild = guilds.isEmpty() ? null : guilds.get(rng.nextInt(guilds.size()));
            List<UUID> members = guild == null ? List.of() : List.copyOf(guild.members().keySet());
            List<UUID> loners = new ArrayList<>();
            for (UUID player : players) {
                if (random.guildOf(player).isEmpty()) {
                    loners.add(player);
                }
            }
            UUID master = guild == null || rng.nextInt(10) == 0 ? anyone : guild.master();
            UUID member = members.isEmpty() || rng.nextInt(10) == 0 ? anyone : members.get(rng.nextInt(members.size()));
            UUID actor = rng.nextBoolean() ? master : member;
            UUID loner = loners.isEmpty() || rng.nextInt(10) == 0 ? anyone : loners.get(rng.nextInt(loners.size()));
            int op = rng.nextInt(24);
            GuildResult result;
            if (op < 2) {
                result = random.create(loner, names.get(rng.nextInt(names.size())), tags.get(rng.nextInt(tags.size())), now);
            } else if (op < 6) {
                result = random.invite(actor, loner, now);
            } else if (op < 10) {
                result = random.accept(loner, someGuildId(random, rng, loner, now), now);
            } else if (op < 11) {
                result = random.decline(loner, someGuildId(random, rng, loner, now), now);
            } else if (op < 13) {
                result = random.leave(member);
            } else if (op < 15) {
                result = random.kick(actor, member);
            } else if (op < 17) {
                result = random.promote(master, member);
            } else if (op < 19) {
                result = random.demote(master, member);
            } else if (op < 20) {
                result = random.transferMaster(master, member);
            } else if (op < 21) {
                result = random.disband(master);
            } else {
                result = random.addContribution(member, S1, 1 + rng.nextInt(30));
            }
            track(result.events(), open, seen);
            if (step % 5 == 0) {
                track(random.expireInvites(now), open, seen);
            }
            assertConsistent(random, players, open, levels, now);
        }
        track(random.expireInvites(now.plus(Duration.ofDays(1))), open, seen);
        assertTrue("every invite must be closed exactly once: " + open, open.isEmpty());
        for (Class<?> type : GuildEvent.class.getPermittedSubclasses()) {
            assertTrue("the random run never produced " + type.getSimpleName() + ": " + seen,
                    seen.getOrDefault(type, 0) >= 20);
        }
        GuildService reloaded = new GuildService(rules, new SplittableRandom(4L), random.guilds());
        for (UUID player : players) {
            assertEquals(random.guildOf(player), reloaded.guildOf(player));
        }
    }

    private static UUID someGuildId(GuildService service, Random rng, UUID player, Instant now) {
        List<GuildInvite> pending = service.invitesFor(player, now);
        if (!pending.isEmpty() && rng.nextInt(4) > 0) {
            return pending.get(rng.nextInt(pending.size())).guildId();
        }
        List<Guild> guilds = service.guilds();
        return guilds.isEmpty() ? player(999) : guilds.get(rng.nextInt(guilds.size())).id();
    }

    private static List<UUID> key(GuildInvite invite) {
        return List.of(invite.guildId(), invite.invitee());
    }

    private static void track(List<GuildEvent> events, Set<List<UUID>> open, Map<Class<?>, Integer> seen) {
        Set<UUID> disbanded = new HashSet<>();
        for (GuildEvent event : events) {
            seen.merge(event.getClass(), 1, Integer::sum);
            if (event instanceof GuildEvent.Invited invited) {
                assertTrue("second open invite " + invited, open.add(key(invited.invite())));
            } else if (event instanceof GuildEvent.InviteExpired expired) {
                assertTrue("closed twice " + expired, open.remove(key(expired.invite())));
            } else if (event instanceof GuildEvent.InviteDeclined declined) {
                assertTrue("closed twice " + declined, open.remove(key(declined.invite())));
            } else if (event instanceof GuildEvent.InviteCancelled cancelled) {
                assertTrue("closed twice " + cancelled, open.remove(key(cancelled.invite())));
            } else if (event instanceof GuildEvent.Joined joined) {
                assertTrue("joined without an invite " + joined, open.remove(List.of(joined.guildId(), joined.member())));
            } else if (event instanceof GuildEvent.Disbanded gone) {
                disbanded.add(gone.guildId());
            }
        }
        for (List<UUID> key : open) {
            assertFalse("invite of a disbanded guild left open " + key, disbanded.contains(key.get(0)));
        }
    }

    private static void assertConsistent(GuildService service, List<UUID> players, Set<List<UUID>> open,
                                         Map<UUID, Integer> levels, Instant now) {
        GuildService.checkConsistency(service.guilds());
        Set<UUID> members = new HashSet<>();
        for (Guild guild : service.guilds()) {
            assertTrue(guild.size() <= service.memberCap(guild));
            int level = service.level(guild);
            assertTrue("levels never go down", level >= levels.getOrDefault(guild.id(), 1));
            levels.put(guild.id(), level);
            for (UUID member : guild.members().keySet()) {
                members.add(member);
                assertEquals(Optional.of(guild), service.guildOf(member));
            }
            assertEquals(Optional.of(guild), service.byName(guild.name()));
            assertEquals(Optional.of(guild), service.byTag(guild.tag()));
        }
        for (UUID player : players) {
            assertEquals(members.contains(player), service.guildOf(player).isPresent());
            if (members.contains(player)) {
                assertTrue("guild member with a pending invite", service.invitesFor(player, now).isEmpty());
            }
            for (GuildInvite invite : service.invitesFor(player, now)) {
                assertTrue("untracked invite " + invite, open.contains(key(invite)));
            }
        }
        for (List<UUID> key : open) {
            assertTrue("invite of a missing guild " + key, service.guild(key.get(0)).isPresent());
            assertFalse("invite to a guild member " + key, service.guildOf(key.get(1)).isPresent());
        }
    }
}
