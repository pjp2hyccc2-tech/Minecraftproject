package com.eternalrealm.guild.battle;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The guild battle's phases, knock-outs, results and the registry of challenges (docs/modules/guild_battles.md). */
public class GuildBattleTest {
    private static final UUID RED_GUILD = new UUID(1, 1);
    private static final UUID BLUE_GUILD = new UUID(1, 2);
    private static final UUID THIRD_GUILD = new UUID(1, 3);
    private static final GuildBattle.Team RED = new GuildBattle.Team(RED_GUILD, "Crimson", "CRM");
    private static final GuildBattle.Team BLUE = new GuildBattle.Team(BLUE_GUILD, "Azure", "AZR");
    private static final GuildBattle.Team THIRD = new GuildBattle.Team(THIRD_GUILD, "Jade", "JDE");
    private static final UUID R1 = new UUID(2, 1);
    private static final UUID R2 = new UUID(2, 2);
    private static final UUID B1 = new UUID(3, 1);
    private static final BattleRules RULES = new BattleRules(2, 1, 100, 50, 20, 600, 3, 40, 20, 30, 200);

    private static GuildBattle mustered(long now) {
        GuildBattle battle = new GuildBattle(new UUID(9, 9), RED, BLUE, 0, RULES, now);
        assertEquals(GuildBattle.JoinResult.OK, battle.join(R1, "r1", RED_GUILD));
        assertEquals(GuildBattle.JoinResult.OK, battle.join(R2, "r2", RED_GUILD));
        assertEquals(GuildBattle.JoinResult.OK, battle.join(B1, "b1", BLUE_GUILD));
        return battle;
    }

    @Test
    public void musterPrepareFightAndEndAtTheTarget() {
        GuildBattle battle = mustered(0);
        assertEquals(GuildBattle.JoinResult.SIDE_FULL, battle.join(new UUID(2, 3), "r3", RED_GUILD));
        assertEquals(GuildBattle.JoinResult.ALREADY_IN, battle.join(R1, "r1", RED_GUILD));
        assertEquals(GuildBattle.JoinResult.NOT_IN_THESE_GUILDS, battle.join(new UUID(4, 1), "x", THIRD_GUILD));
        assertTrue(battle.tick(49).isEmpty());
        List<GuildBattle.Event> events = battle.tick(50);
        assertTrue(events.get(0) instanceof GuildBattle.Mustered);
        assertEquals(GuildBattle.Phase.PREPARE, battle.phase());
        assertEquals(GuildBattle.JoinResult.NOT_MUSTERING, battle.join(new UUID(3, 2), "b2", BLUE_GUILD));
        assertFalse("no one is hurt before the fight", battle.mayHurt(R1, B1, 60));
        assertTrue(battle.tick(70).get(0) instanceof GuildBattle.Started);
        assertTrue(battle.mayHurt(R1, B1, 71));
        assertFalse("not your own side", battle.mayHurt(R1, R2, 71));
        // knock-outs: the victim recovers, cannot hurt or be hurt, then is sheltered a while
        List<GuildBattle.Event> ko = battle.knockOut(B1, R1, 80);
        GuildBattle.KnockedOut first = (GuildBattle.KnockedOut) ko.get(0);
        assertEquals(GuildBattle.Side.RED, first.scored());
        assertEquals(1, first.red());
        assertEquals(1, first.attacker().knockOuts());
        assertFalse(battle.mayHurt(R1, B1, 100));
        assertFalse("down: cannot hurt", battle.mayHurt(B1, R1, 100));
        assertTrue("back on their feet: may strike", battle.mayHurt(B1, R1, 120));
        assertFalse("still sheltered", battle.mayHurt(R1, B1, 130));
        assertTrue(battle.mayHurt(R1, B1, 140));
        assertTrue("already down: nothing", battle.knockOut(B1, R1, 81).isEmpty());
        battle.knockOut(B1, null, 200);                    // a fall of their own still scores for the others
        List<GuildBattle.Event> last = battle.knockOut(B1, R2, 300);
        GuildBattle.Ended ended = (GuildBattle.Ended) last.get(1);
        assertEquals(Optional.of(GuildBattle.Side.RED), ended.winner());
        assertEquals(3, ended.red());
        assertEquals(0, ended.blue());
        assertEquals("target", ended.why());
        assertEquals(R1, ended.mvp().orElseThrow().id());
        assertEquals(GuildBattle.Phase.ENDED, battle.phase());
        assertTrue(battle.tick(329).isEmpty());
        assertTrue(battle.tick(330).get(0) instanceof GuildBattle.Closed);
        assertTrue(battle.over());
    }

    @Test
    public void timeUpLevelIsADrawAndAnEmptySideCancels() {
        GuildBattle battle = mustered(0);
        battle.tick(50);
        battle.tick(70);
        GuildBattle.Ended ended = (GuildBattle.Ended) battle.tick(670).get(0);
        assertTrue(ended.winner().isEmpty());
        assertEquals("time", ended.why());
        assertTrue(ended.mvp().isEmpty());

        GuildBattle lonely = new GuildBattle(new UUID(9, 8), RED, BLUE, 1, RULES, 0);
        lonely.join(R1, "r1", RED_GUILD);
        GuildBattle.Cancelled cancelled = (GuildBattle.Cancelled) lonely.tick(50).get(0);
        assertEquals("no_blue", cancelled.reason());
        assertTrue(lonely.over());
    }

    @Test
    public void aSideWithNobodyLeftLoses() {
        GuildBattle battle = mustered(0);
        battle.tick(50);
        battle.tick(70);
        assertTrue(battle.withdraw(R1, 80).isEmpty());
        assertFalse(battle.isPresent(R1));
        assertFalse(battle.mayHurt(R1, B1, 81));
        GuildBattle.Ended ended = (GuildBattle.Ended) battle.withdraw(R2, 90).get(0);
        assertEquals(Optional.of(GuildBattle.Side.BLUE), ended.winner());
        assertEquals("forfeit", ended.why());
        // while mustering, leaving just takes one off the list
        GuildBattle mustering = mustered(0);
        mustering.withdraw(B1, 10);
        assertEquals(0, mustering.count(GuildBattle.Side.BLUE));
    }

    @Test
    public void challengesArenasAndRest() {
        GuildBattles battles = new GuildBattles(RULES, 1);
        assertEquals(GuildBattles.Answer.SELF, battles.challenge(RED, RED, 0));
        assertEquals(GuildBattles.Answer.OK, battles.challenge(RED, BLUE, 0));
        assertEquals(GuildBattles.Answer.ALREADY_CHALLENGED, battles.challenge(RED, BLUE, 10));
        assertEquals(GuildBattles.Answer.OK, battles.challenge(THIRD, BLUE, 10));
        assertEquals(2, battles.challengesTo(BLUE_GUILD, 20).size());
        GuildBattles.Accepted accepted = battles.accept(BLUE_GUILD, RED_GUILD, new UUID(7, 7), 20);
        assertEquals(GuildBattles.Answer.OK, accepted.answer());
        assertEquals("the third guild's challenge to a guild now fighting is void", 0,
                battles.challengesTo(BLUE_GUILD, 21).size());
        assertEquals(GuildBattles.Answer.TARGET_BUSY, battles.challenge(THIRD, BLUE, 21));
        assertEquals(GuildBattles.Answer.CHALLENGER_BUSY, battles.challenge(RED, THIRD, 21));
        GuildBattle.Team fourth = new GuildBattle.Team(new UUID(1, 4), "Onyx", "ONX");
        assertEquals(GuildBattles.Answer.OK, battles.challenge(THIRD, fourth, 21));
        assertEquals("one arena only", GuildBattles.Answer.NO_ARENA,
                battles.accept(fourth.guild(), THIRD_GUILD, new UUID(7, 8), 22).answer());
        GuildBattle battle = accepted.battle();
        assertEquals(battle, battles.battleOfGuild(RED_GUILD).orElseThrow());
        battle.join(R1, "r1", RED_GUILD);
        assertEquals(battle, battles.battleOfPlayer(R1).orElseThrow());
        // fought and closed: both rest
        battle.join(B1, "b1", BLUE_GUILD);
        battle.tick(70);
        battle.tick(90);
        battle.tick(690);
        battle.tick(720);
        assertTrue(battle.over());
        battles.close(battle, 720);
        assertTrue(battles.battleOfGuild(RED_GUILD).isEmpty());
        assertEquals(GuildBattles.Answer.CHALLENGER_RESTING, battles.challenge(RED, THIRD, 800));
        assertEquals(GuildBattles.Answer.TARGET_RESTING, battles.challenge(THIRD, BLUE, 800));
        assertEquals("the challenge to the fourth guild ran out", 1, battles.expire(921).size());
        assertEquals(0L, battles.restingUntil(RED_GUILD));
        assertNull(battles.accept(fourth.guild(), THIRD_GUILD, new UUID(7, 9), 922).battle());
    }

    @Test
    public void recordsAddUp() {
        BattleRecord record = BattleRecord.NONE
                .after(GuildBattle.Side.RED, Optional.of(GuildBattle.Side.RED), 5)
                .after(GuildBattle.Side.RED, Optional.empty(), 2)
                .after(GuildBattle.Side.BLUE, Optional.of(GuildBattle.Side.RED), 1);
        assertEquals(new BattleRecord(1, 1, 1, 8), record);
        assertEquals(3, record.battles());
    }
}
