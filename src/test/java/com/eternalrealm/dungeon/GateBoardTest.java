package com.eternalrealm.dungeon;

import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.WorldProgress;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GateBoardTest {
    private static final SeasonId SEASON = SeasonId.of("s1");
    private static final Instant AT = Instant.parse("2026-11-01T00:00:00Z");

    private static WorldProgress beaten(String... bosses) {
        WorldProgress progress = WorldProgress.fresh(SEASON);
        for (String boss : bosses) {
            progress.recordBossDefeat(boss, AT);
        }
        return progress;
    }

    private static GateBoard.Entry entry(List<GateBoard.Entry> entries, String id) {
        return GateBoard.find(entries, id).orElseThrow();
    }

    @Test
    public void theBoardListsTheEightDungeonsThenTheRaid() {
        List<GateBoard.Entry> entries = GateBoard.entries(beaten());
        assertEquals(9, entries.size());
        for (int i = 0; i < 8; i++) {
            DungeonDefinition dungeon = Dungeons.all().get(i);
            GateBoard.Entry entry = entries.get(i);
            assertEquals(dungeon.id(), entry.id());
            assertEquals(dungeon.translationKey(), entry.nameKey());
            assertEquals(dungeon.bossId(), entry.bossId());
            assertEquals(dungeon.recommendedLevel(), entry.recommendedLevel());
            assertEquals(dungeon.roomCount(), entry.rooms());
            assertEquals(dungeon.timeLimitTicks(), entry.timeLimitTicks());
            assertFalse(entry.raid());
        }
        GateBoard.Entry raid = entries.get(8);
        assertEquals("final", raid.id());
        assertTrue(raid.raid());
        assertEquals(0, raid.rooms());
        assertEquals(Dungeons.FINAL_RAID.maxPartySize(), raid.maxParty());
    }

    @Test
    public void aFreshSeasonOpensOnlyTheFirstDungeonAndNamesWhatBreaksEachSeal() {
        List<GateBoard.Entry> entries = GateBoard.entries(beaten());
        assertTrue(entry(entries, "d1").open());
        assertNull(entry(entries, "d1").unlockBossId());
        for (GateBoard.Entry entry : entries.subList(1, 8)) {
            assertFalse(entry.id(), entry.open());
            String expected = Areas.byId(entry.areaId()).orElseThrow().unlockedByBossId();
            assertEquals(entry.id(), expected, entry.unlockBossId());
        }
        assertFalse(entry(entries, "final").open());
        assertNull("the raid needs every dungeon boss, not one", entry(entries, "final").unlockBossId());
        assertFalse(entry(entries, "d1").conquered());
    }

    @Test
    public void beatingBossesOpensAreasAndMarksTheirDungeonsConquered() {
        WorldProgress progress = beaten("b1", "b2");
        List<GateBoard.Entry> entries = GateBoard.entries(progress);
        assertTrue(entry(entries, "d1").conquered());
        assertTrue(entry(entries, "d2").open());
        assertTrue(entry(entries, "d2").conquered());
        assertTrue(entry(entries, "d3").open());
        assertFalse(entry(entries, "d3").conquered());
        assertEquals(2, GateBoard.conqueredDungeons(progress));
    }

    @Test
    public void theRaidOpensOnceEveryDungeonBossHasFallen() {
        WorldProgress progress = beaten("b1", "b2", "b3", "b4", "b5", "b6", "b7");
        assertFalse(entry(GateBoard.entries(progress), "final").open());
        assertEquals(7, GateBoard.conqueredDungeons(progress));
        progress.recordBossDefeat("b8", AT);
        assertTrue(entry(GateBoard.entries(progress), "final").open());
        assertEquals(8, GateBoard.conqueredDungeons(progress));
    }

    @Test
    public void theScreenOpensOnTheEntranceThenTheLastRunThenWhatTheLevelIsReadyFor() {
        List<GateBoard.Entry> entries = GateBoard.entries(beaten("b1", "b2", "b3", "b5"));
        assertEquals("the entrance's own dungeon, even sealed", "d8", GateBoard.focus(entries, "d8", "d2", 40));
        assertEquals("d2", GateBoard.focus(entries, null, "d2", 40));
        assertEquals("a sealed last run is skipped", "d6", GateBoard.focus(entries, null, "d7", 40));
        assertEquals("unknown ids are skipped", "d1", GateBoard.focus(entries, "nope", "nope", 1));
        // d4 (26) is open and within two levels of 24; d5 (29) and d6 (35) are not
        assertEquals("d4", GateBoard.focus(entries, null, null, 24));
        assertEquals("d5", GateBoard.focus(entries, null, null, 27));
        assertEquals("the hardest open one for a high level", "d6", GateBoard.focus(entries, null, null, 60));
        assertEquals("the first open one below every dungeon", "d1", GateBoard.focus(entries, null, null, 1));
    }

    @Test
    public void onlyAnOpenDungeonWithTheLeaderAndARightSizedPartyMaySetOut() {
        List<GateBoard.Entry> entries = GateBoard.entries(beaten());
        GateBoard.Entry d1 = entry(entries, "d1");
        GateBoard.Entry d2 = entry(entries, "d2");
        assertEquals(GateBoard.Refusal.NONE, GateBoard.check(d1, false, true, 1, false));
        assertEquals(GateBoard.Refusal.NONE, GateBoard.check(d1, true, true, d1.maxParty(), false));
        assertEquals(GateBoard.Refusal.SEALED, GateBoard.check(d2, false, true, 1, false));
        assertEquals(GateBoard.Refusal.NOT_LEADER, GateBoard.check(d1, true, false, 2, false));
        assertEquals(GateBoard.Refusal.PARTY_SIZE, GateBoard.check(d1, true, true, d1.maxParty() + 1, false));
        assertEquals("the server may let any size in", GateBoard.Refusal.NONE,
                GateBoard.check(d1, true, true, d1.maxParty() + 1, true));
        assertEquals("", GateBoard.Refusal.NONE.id());
        assertEquals("leader", GateBoard.Refusal.NOT_LEADER.id());
    }

    @Test
    public void aSessionHoldsForFiveMinutesWithinEightBlocksOfTheGatekeeper() {
        GateBoard.Session session = new GateBoard.Session(1000, "minecraft:overworld", 10, 64, 10);
        assertTrue(session.allows(1000, "minecraft:overworld", 10, 64, 10));
        assertTrue(session.allows(1000 + GateBoard.SESSION_TICKS, "minecraft:overworld", 17.9, 64, 10));
        assertFalse("too late", session.allows(1001 + GateBoard.SESSION_TICKS, "minecraft:overworld", 10, 64, 10));
        assertFalse("wandered off", session.allows(1100, "minecraft:overworld", 18.1, 64, 10));
        assertFalse("another dimension", session.allows(1100, "eternalrealm:astoria", 10, 64, 10));
        assertFalse("a restarted clock", session.allows(999, "minecraft:overworld", 10, 64, 10));
        assertTrue(session.expired(1001 + GateBoard.SESSION_TICKS));
        assertFalse(session.expired(1500));
    }

    @Test
    public void harderRunsPayRarerPiecesAndMoreStones() {
        assertEquals(ItemRarity.UNCOMMON, DungeonRewards.rarityFloor(Difficulty.NORMAL));
        assertEquals(ItemRarity.RARE, DungeonRewards.rarityFloor(Difficulty.HARD));
        assertEquals(ItemRarity.EPIC, DungeonRewards.rarityFloor(Difficulty.EX));
        int previous = 0;
        for (Difficulty difficulty : Difficulty.values()) {
            assertTrue(DungeonRewards.stones(difficulty) > previous);
            previous = DungeonRewards.stones(difficulty);
        }
    }
}
