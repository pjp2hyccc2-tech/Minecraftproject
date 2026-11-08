package com.eternalrealm.world;

import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorldProgressTest {
    private static final SeasonId SEASON = SeasonId.of("s1");
    private static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");

    private static List<Integer> ids(List<AreaDefinition> areas) {
        return areas.stream().map(AreaDefinition::id).toList();
    }

    @Test
    public void aFreshSeasonOnlyOpensTheStartingArea() {
        WorldProgress progress = WorldProgress.fresh(SEASON);
        assertEquals(Set.of(1), progress.unlockedAreaIds());
        assertEquals(List.of(Areas.ARCELIA), progress.unlockedAreas());
        assertTrue(progress.defeatedBossIds().isEmpty());
        assertFalse(progress.finalBossUnlocked());
        assertFalse(progress.finalBossDefeated());
        assertFalse("nothing to save yet", progress.isDirty());
        assertFalse(progress.isAreaUnlocked(0));
        assertFalse(progress.isAreaUnlocked(8));
    }

    @Test
    public void firstDefeatsUnlockTheNextAreaForEveryoneExactlyOnce() {
        WorldProgress progress = WorldProgress.fresh(SEASON);
        BossDefeatResult first = progress.recordBossDefeat("b1", T0);
        assertTrue(first.firstDefeat());
        assertEquals(List.of(2), ids(first.newlyUnlockedAreas()));
        assertFalse(first.finalBossNewlyUnlocked());
        assertTrue(progress.isAreaUnlocked(2));
        assertTrue(progress.isDirty());
        long revision = progress.revision();

        BossDefeatResult again = progress.recordBossDefeat("b1", T0.plusSeconds(3600));
        assertFalse(again.firstDefeat());
        assertTrue(again.newlyUnlockedAreas().isEmpty());
        assertEquals("a repeat defeat changes nothing", revision, progress.revision());
        assertEquals("the first defeat time is kept", T0, progress.firstDefeat("b1").orElseThrow());
    }

    @Test
    public void theFullStoryUnlocksEveryAreaAndThenTheFinalBoss() {
        WorldProgress progress = WorldProgress.fresh(SEASON);
        assertEquals(List.of(2), ids(progress.recordBossDefeat("b1", T0).newlyUnlockedAreas()));
        assertEquals(List.of(3), ids(progress.recordBossDefeat("b2", T0).newlyUnlockedAreas()));
        assertEquals(List.of(4), ids(progress.recordBossDefeat("b3", T0).newlyUnlockedAreas()));
        assertEquals("the first snow dungeon boss opens nothing", List.of(),
                ids(progress.recordBossDefeat("b4", T0).newlyUnlockedAreas()));
        assertEquals(List.of(5), ids(progress.recordBossDefeat("b5", T0).newlyUnlockedAreas()));
        assertEquals(List.of(6), ids(progress.recordBossDefeat("b6", T0).newlyUnlockedAreas()));
        assertEquals(List.of(7), ids(progress.recordBossDefeat("b7", T0).newlyUnlockedAreas()));
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7), progress.unlockedAreaIds());
        assertFalse(progress.finalBossUnlocked());

        BossDefeatResult tower = progress.recordBossDefeat("b8", T0);
        assertTrue(tower.newlyUnlockedAreas().isEmpty());
        assertTrue(tower.finalBossNewlyUnlocked());
        assertTrue(progress.finalBossUnlocked());
        assertFalse(progress.finalBossDefeated());

        BossDefeatResult end = progress.recordBossDefeat(Areas.FINAL_BOSS_ID, T0);
        assertTrue(end.firstDefeat());
        assertFalse("the final boss was already unlocked", end.finalBossNewlyUnlocked());
        assertTrue(progress.finalBossDefeated());
        assertEquals(Set.copyOf(Areas.bossIds()), progress.defeatedBossIds());
    }

    @Test
    public void theFinalBossNeedsAllEightDungeonBossesInAnyOrder() {
        WorldProgress progress = WorldProgress.fresh(SEASON);
        for (String boss : List.of("b8", "b7", "b6", "b5", "b4", "b3", "b2")) {
            assertFalse(progress.recordBossDefeat(boss, T0).finalBossNewlyUnlocked());
        }
        assertFalse(progress.finalBossUnlocked());
        assertTrue(progress.recordBossDefeat("b1", T0).finalBossNewlyUnlocked());
    }

    @Test
    public void eachAreaDependsOnlyOnItsOwnBoss() {
        WorldProgress progress = WorldProgress.fresh(SEASON);
        assertEquals("an administrator may record b3 first", List.of(4),
                ids(progress.recordBossDefeat("b3", T0).newlyUnlockedAreas()));
        assertFalse(progress.isAreaUnlocked(2));
        assertEquals(Set.of(1, 4), progress.unlockedAreaIds());
    }

    @Test
    public void unknownBossesAreProgrammingErrors() {
        WorldProgress progress = WorldProgress.fresh(SEASON);
        assertThrows(IllegalArgumentException.class, () -> progress.recordBossDefeat("b9", T0));
        assertThrows(IllegalArgumentException.class, () -> progress.recordBossDefeat(null, T0));
        assertThrows(NullPointerException.class, () -> progress.recordBossDefeat("b1", null));
        assertEquals(0, progress.revision());
    }

    @Test
    public void recordsRoundTripAndAreImmutable() {
        WorldProgress progress = WorldProgress.fresh(SEASON);
        progress.recordBossDefeat("b2", T0.plusSeconds(10));
        progress.recordBossDefeat("b1", T0);
        WorldProgressRecord record = progress.toRecord();
        assertEquals(List.of("b1", "b2"), List.copyOf(record.bossFirstDefeats().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> record.bossFirstDefeats().put("b3", T0));

        WorldProgress restored = WorldProgress.fromRecord(record);
        assertEquals(record, restored.toRecord());
        assertEquals(Set.of(1, 2, 3), restored.unlockedAreaIds());
        assertFalse("a loaded state is clean", restored.isDirty());
        assertEquals(SEASON, restored.seasonId());

        progress.recordBossDefeat("b3", T0);
        assertEquals("the record is a snapshot", 2, record.bossFirstDefeats().size());
        assertThrows(IllegalArgumentException.class, () -> new WorldProgressRecord(SEASON, Map.of("b0", T0)));
        assertEquals(Map.of(), WorldProgressRecord.empty(SEASON).bossFirstDefeats());
    }

    @Test
    public void savedRevisionsNeverMoveBackwards() {
        WorldProgress progress = WorldProgress.fresh(SEASON);
        progress.recordBossDefeat("b1", T0);
        long first = progress.revision();
        progress.recordBossDefeat("b2", T0);
        progress.markSaved(progress.revision());
        assertFalse(progress.isDirty());
        progress.markSaved(first);
        assertFalse("an older save completing late does not make it dirty", progress.isDirty());
        progress.recordBossDefeat("b3", T0);
        assertTrue(progress.isDirty());
    }
}
