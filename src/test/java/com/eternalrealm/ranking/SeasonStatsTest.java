package com.eternalrealm.ranking;

import org.junit.Test;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SeasonStatsTest {
    @Test
    public void emptyStatsAreAllZero() {
        for (SeasonCounter counter : SeasonCounter.all()) {
            assertEquals(counter.id(), 0L, SeasonStats.EMPTY.get(counter));
        }
        assertEquals(0, SeasonStats.EMPTY.level());
        assertEquals(0L, SeasonStats.EMPTY.totalExperience());
    }

    @Test
    public void counterIdsAreTheRecordComponentNamesInOrder() {
        RecordComponent[] components = SeasonStats.class.getRecordComponents();
        List<String> names = new ArrayList<>();
        for (RecordComponent component : components) {
            names.add(component.getName());
        }
        List<String> ids = new ArrayList<>();
        for (SeasonCounter counter : SeasonCounter.all()) {
            ids.add(counter.id());
        }
        ids.add("level");
        ids.add("totalExperience");
        assertEquals("persistence keys must match the record so the JSON reads like the API", names, ids);
    }

    @Test
    public void everyAccessorReadsItsOwnCounter() {
        SeasonStats stats = RankingFixtures.distinctStats(100);
        assertEquals(100, stats.monstersKilled());
        assertEquals(101, stats.eliteKills());
        assertEquals(102, stats.bossesKilled());
        assertEquals(103, stats.damageDealt());
        assertEquals(104, stats.deaths());
        assertEquals(105, stats.dungeonClears());
        assertEquals(106, stats.partyDungeonClears());
        assertEquals(107, stats.questsCompleted());
        assertEquals(108, stats.areasDiscovered());
        assertEquals(109, stats.distanceTravelled());
        assertEquals(110, stats.itemsCrafted());
        assertEquals(111, stats.pvpWins());
        assertEquals(112, stats.pvpLosses());
        assertEquals(113, stats.guildContribution());
        assertEquals(7, stats.level());
        assertEquals(100_000, stats.totalExperience());
        for (SeasonCounter counter : SeasonCounter.all()) {
            assertEquals(counter.id(), 100L + counter.ordinal(), stats.get(counter));
        }
    }

    @Test
    public void negativeValuesAreRejected() {
        for (SeasonCounter counter : SeasonCounter.all()) {
            long[] counters = new long[SeasonCounter.all().size()];
            counters[counter.ordinal()] = -1;
            assertThrows(counter.id(), IllegalArgumentException.class, () -> SeasonStats.of(counters, 1, 0));
        }
        long[] zero = new long[SeasonCounter.all().size()];
        assertThrows(IllegalArgumentException.class, () -> SeasonStats.of(zero, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> SeasonStats.of(zero, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> SeasonStats.of(new long[3], 1, 0));
    }

    @Test
    public void countersHaveStableUniqueIdsAndTranslationKeys() {
        Set<String> ids = new HashSet<>();
        for (SeasonCounter counter : SeasonCounter.all()) {
            assertTrue(ids.add(counter.id()));
            assertEquals(counter, SeasonCounter.byId(counter.id()).orElseThrow());
            assertTrue(counter.translationKey(), counter.translationKey().matches("season_stat\\.eternalrealm\\.[a-z_]+"));
        }
        assertEquals("season_stat.eternalrealm.monsters_killed", SeasonCounter.MONSTERS_KILLED.translationKey());
        assertTrue(SeasonCounter.byId("level").isEmpty());
        assertTrue(SeasonCounter.byId("MONSTERS_KILLED").isEmpty());
    }
}
