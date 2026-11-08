package com.eternalrealm.ranking;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SeasonStatsTrackerTest {
    @Test
    public void startsEmptyOrFromStoredStats() {
        assertEquals(SeasonStats.EMPTY, new SeasonStatsTracker().snapshot());
        SeasonStats stored = RankingFixtures.distinctStats(5);
        SeasonStatsTracker tracker = new SeasonStatsTracker(stored);
        assertEquals(stored, tracker.snapshot());
        assertEquals(0, tracker.revision());
        assertEquals(stored.level(), tracker.level());
        assertEquals(stored.totalExperience(), tracker.totalExperience());
    }

    @Test
    public void addIncrementsAndCountsRevisionsOnlyForChanges() {
        SeasonStatsTracker tracker = new SeasonStatsTracker();
        assertEquals(3, tracker.add(SeasonCounter.QUESTS_COMPLETED, 3));
        assertEquals(5, tracker.add(SeasonCounter.QUESTS_COMPLETED, 2));
        assertEquals(2, tracker.revision());
        assertEquals("adding 0 changes nothing", 5, tracker.add(SeasonCounter.QUESTS_COMPLETED, 0));
        assertEquals(2, tracker.revision());
        assertEquals(5, tracker.get(SeasonCounter.QUESTS_COMPLETED));
        assertEquals(5, tracker.snapshot().questsCompleted());
    }

    @Test
    public void negativeIncrementsAreRejectedWithoutChange() {
        SeasonStatsTracker tracker = new SeasonStatsTracker();
        tracker.add(SeasonCounter.DEATHS, 4);
        assertThrows(IllegalArgumentException.class, () -> tracker.add(SeasonCounter.DEATHS, -1));
        assertThrows(IllegalArgumentException.class, () -> tracker.add(SeasonCounter.DEATHS, Long.MIN_VALUE));
        assertThrows(NullPointerException.class, () -> tracker.add(null, 1));
        assertEquals(4, tracker.get(SeasonCounter.DEATHS));
        assertEquals(1, tracker.revision());
    }

    @Test
    public void incrementsSaturateInsteadOfOverflowing() {
        SeasonStatsTracker tracker = new SeasonStatsTracker();
        tracker.add(SeasonCounter.DISTANCE_TRAVELLED, Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, tracker.add(SeasonCounter.DISTANCE_TRAVELLED, 5));
        long revision = tracker.revision();
        assertEquals(Long.MAX_VALUE, tracker.add(SeasonCounter.DISTANCE_TRAVELLED, Long.MAX_VALUE));
        assertEquals("a saturated counter does not change any more", revision, tracker.revision());
        assertEquals(Long.MAX_VALUE, tracker.snapshot().distanceTravelled());
    }

    @Test
    public void monsterKillsCountElitesAsMonstersToo() {
        SeasonStatsTracker tracker = new SeasonStatsTracker();
        tracker.recordMonsterKill(false);
        tracker.recordMonsterKill(true);
        tracker.recordMonsterKill(true);
        assertEquals(3, tracker.get(SeasonCounter.MONSTERS_KILLED));
        assertEquals(2, tracker.get(SeasonCounter.ELITE_KILLS));
        assertEquals(0, tracker.get(SeasonCounter.BOSSES_KILLED));
    }

    @Test
    public void damageIsStoredInTenthsAndRounded() {
        SeasonStatsTracker tracker = new SeasonStatsTracker();
        assertEquals(70, tracker.addDamageDealt(7.0));
        assertEquals(13, tracker.addDamageDealt(1.25));
        assertEquals("below half a tenth rounds away", 0, tracker.addDamageDealt(0.04));
        assertEquals(83, tracker.get(SeasonCounter.DAMAGE_DEALT));
        for (double invalid : new double[]{-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(String.valueOf(invalid), IllegalArgumentException.class, () -> tracker.addDamageDealt(invalid));
        }
        assertEquals(83, tracker.get(SeasonCounter.DAMAGE_DEALT));
        tracker.addDamageDealt(1e300);
        assertEquals(Long.MAX_VALUE, tracker.get(SeasonCounter.DAMAGE_DEALT));
    }

    @Test
    public void dungeonClearsCountAsPartyClearsFromTwoMembers() {
        SeasonStatsTracker tracker = new SeasonStatsTracker();
        tracker.recordDungeonClear(1);
        assertEquals(1, tracker.get(SeasonCounter.DUNGEON_CLEARS));
        assertEquals(0, tracker.get(SeasonCounter.PARTY_DUNGEON_CLEARS));
        tracker.recordDungeonClear(SeasonStatsTracker.PARTY_MIN_MEMBERS);
        tracker.recordDungeonClear(5);
        assertEquals(3, tracker.get(SeasonCounter.DUNGEON_CLEARS));
        assertEquals(2, tracker.get(SeasonCounter.PARTY_DUNGEON_CLEARS));
        assertThrows(IllegalArgumentException.class, () -> tracker.recordDungeonClear(0));
        assertEquals(3, tracker.get(SeasonCounter.DUNGEON_CLEARS));
    }

    @Test
    public void pvpResultsCountWinsAndLosses() {
        SeasonStatsTracker tracker = new SeasonStatsTracker();
        tracker.recordPvpResult(true);
        tracker.recordPvpResult(false);
        tracker.recordPvpResult(true);
        assertEquals(2, tracker.get(SeasonCounter.PVP_WINS));
        assertEquals(1, tracker.get(SeasonCounter.PVP_LOSSES));
    }

    @Test
    public void progressIsSetNotAdded() {
        SeasonStatsTracker tracker = new SeasonStatsTracker();
        assertTrue(tracker.setProgress(10, 5_000));
        assertTrue("an administrative level change may lower the values", tracker.setProgress(4, 4_000));
        assertEquals(4, tracker.level());
        assertEquals(4_000, tracker.totalExperience());
        long revision = tracker.revision();
        assertFalse(tracker.setProgress(4, 4_000));
        assertEquals(revision, tracker.revision());
        assertThrows(IllegalArgumentException.class, () -> tracker.setProgress(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> tracker.setProgress(1, -1));
        assertEquals(4, tracker.snapshot().level());
    }

    @Test
    public void snapshotsAreIndependentOfLaterChanges() {
        SeasonStatsTracker tracker = new SeasonStatsTracker();
        tracker.add(SeasonCounter.ITEMS_CRAFTED, 2);
        SeasonStats before = tracker.snapshot();
        tracker.add(SeasonCounter.ITEMS_CRAFTED, 2);
        assertEquals(2, before.itemsCrafted());
        assertEquals(4, tracker.snapshot().itemsCrafted());
    }
}
