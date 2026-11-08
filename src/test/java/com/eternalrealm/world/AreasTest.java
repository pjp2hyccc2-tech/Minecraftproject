package com.eternalrealm.world;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AreasTest {
    @Test
    public void sevenRingsAroundTheSpawnWithTheOfficialLayout() {
        List<AreaDefinition> areas = Areas.all();
        assertEquals(7, areas.size());
        int[] inner = {0, 300, 600, 900, 1200, 1500, 1800};
        int[] minLevel = {1, 8, 15, 22, 30, 37, 44};
        int[] maxLevel = {8, 15, 22, 30, 37, 44, 50};
        String[] slugs = {"arcelia", "elder_forest", "sandria_desert", "nordia_snowfield", "dragonia_volcano",
                "celestia", "tower_of_the_end"};
        for (int i = 0; i < 7; i++) {
            AreaDefinition area = areas.get(i);
            assertEquals(i + 1, area.id());
            assertEquals(slugs[i], area.slug());
            assertEquals(inner[i], area.innerRadius());
            assertEquals(i < 6 ? inner[i + 1] : AreaDefinition.UNBOUNDED, area.outerRadius());
            assertEquals(minLevel[i], area.minLevel());
            assertEquals(maxLevel[i], area.maxLevel());
            assertEquals(LevelRange.of(minLevel[i], maxLevel[i]), area.levelRange());
            assertEquals("area.eternalrealm." + slugs[i], area.translationKey());
            assertEquals("area.eternalrealm." + slugs[i] + ".desc", area.descriptionKey());
            assertSame(area, Areas.require(area.id()));
        }
        assertTrue(Areas.TOWER_OF_THE_END.isUnbounded());
        assertFalse(Areas.CELESTIA.isUnbounded());
    }

    @Test
    public void unlockChainFollowsTheBossesWithTheSecondSnowDungeonOpeningTheVolcano() {
        String[] expected = {null, "b1", "b2", "b3", "b5", "b6", "b7"};
        for (int i = 0; i < 7; i++) {
            assertEquals("area " + (i + 1), expected[i], Areas.all().get(i).unlockedByBossId());
        }
        assertTrue(Areas.ARCELIA.isOpenFromStart());
        assertFalse(Areas.ELDER_FOREST.isOpenFromStart());
        Set<String> unlocking = new HashSet<>();
        for (AreaDefinition area : Areas.all()) {
            if (area.unlockedByBossId() != null) {
                assertTrue(Areas.isBossId(area.unlockedByBossId()));
                assertTrue("one boss unlocks one area", unlocking.add(area.unlockedByBossId()));
            }
        }
        assertFalse("b4 unlocks nothing", unlocking.contains("b4"));
        assertFalse("b8 unlocks nothing", unlocking.contains("b8"));
    }

    @Test
    public void dungeonsAndBossesUseTheSharedIds() {
        List<String> dungeonIds = new ArrayList<>();
        List<Integer> dungeonAreas = new ArrayList<>();
        for (DungeonSite site : Areas.dungeons()) {
            dungeonIds.add(site.dungeonId());
            dungeonAreas.add(site.areaId());
            assertEquals("b" + site.dungeonId().substring(1), site.bossId());
            assertEquals(site.areaId(), Areas.areaOfBoss(site.bossId()).getAsInt());
            assertSame(site, Areas.dungeon(site.dungeonId()).orElseThrow());
        }
        assertEquals(List.of("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8"), dungeonIds);
        assertEquals(List.of(1, 2, 3, 4, 4, 5, 6, 7), dungeonAreas);
        assertEquals(2, Areas.dungeonsIn(4).size());
        assertEquals(List.of("d8"), Areas.dungeonsIn(7).stream().map(DungeonSite::dungeonId).toList());
        assertTrue(Areas.dungeonsIn(99).isEmpty());
        assertTrue(Areas.dungeon("d9").isEmpty());

        assertEquals(List.of("b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8"), Areas.regularBossIds());
        assertEquals(List.of("b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "final"), Areas.bossIds());
        assertTrue(Areas.isBossId("final"));
        assertFalse(Areas.isBossId("b9"));
        assertFalse(Areas.isBossId(null));
        assertEquals(7, Areas.areaOfBoss(Areas.FINAL_BOSS_ID).getAsInt());
        assertTrue(Areas.areaOfBoss("nobody").isEmpty());
    }

    @Test
    public void lookupsHandleUnknownIds() {
        assertTrue(Areas.byId(0).isEmpty());
        assertTrue(Areas.byId(8).isEmpty());
        assertFalse(Areas.exists(0));
        assertFalse(Areas.exists(8));
        assertTrue(Areas.exists(1) && Areas.exists(7));
        assertThrows(IllegalArgumentException.class, () -> Areas.require(8));
    }

    @Test
    public void coloursAndTranslationKeysAreDistinct() {
        Set<Integer> colours = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (AreaDefinition area : Areas.all()) {
            assertTrue(colours.add(area.color()));
            assertTrue(keys.add(area.translationKey()));
            assertTrue(area.containsDistance(area.innerRadius()));
            assertFalse(area.containsDistance(area.innerRadius() - 0.5));
        }
    }

    @Test
    public void invalidDefinitionsAreRejected() {
        List<Runnable> invalid = Arrays.asList(
                () -> new AreaDefinition(0, "x", 0, 10, 1, 2, null, "t", 0),
                () -> new AreaDefinition(1, "Bad Slug", 0, 10, 1, 2, null, "t", 0),
                () -> new AreaDefinition(1, "x", -1, 10, 1, 2, null, "t", 0),
                () -> new AreaDefinition(1, "x", 10, 10, 1, 2, null, "t", 0),
                () -> new AreaDefinition(1, "x", 0, 10, 0, 2, null, "t", 0),
                () -> new AreaDefinition(1, "x", 0, 10, 5, 4, null, "t", 0),
                () -> new AreaDefinition(1, "x", 0, 10, 1, 101, null, "t", 0),
                () -> new AreaDefinition(1, "x", 0, 10, 1, 2, "B1", "t", 0),
                () -> new AreaDefinition(1, "x", 0, 10, 1, 2, null, "", 0),
                () -> new AreaDefinition(1, "x", 0, 10, 1, 2, null, "t", 0x1000000),
                () -> new DungeonSite("d1", 0, "b1"),
                () -> new DungeonSite("D1", 1, "b1"));
        for (Runnable definition : invalid) {
            assertThrows(IllegalArgumentException.class, definition::run);
        }
        AreaDefinition valid = new AreaDefinition(1, "x", 0, 10, 1, 2, null, "t", 0xFFFFFF);
        assertNull(valid.unlockedByBossId());
    }
}
