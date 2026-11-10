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
    public void tenFieldsWithTheLevelsOfTheFieldMap() {
        List<AreaDefinition> areas = Areas.all();
        assertEquals(10, areas.size());
        int[] minLevel = {5, 10, 15, 20, 30, 40, 50, 10, 15, 20};
        int[] maxLevel = {15, 20, 25, 30, 40, 50, 50, 20, 25, 30};
        boolean[] open = {false, false, false, false, false, true, true, false, false, false};
        int[] tier = {1, 2, 3, 4, 5, 6, 7, 2, 3, 4};
        String[] slugs = {"arcelia", "elder_forest", "sandria_desert", "nordia_snowfield", "dragonia_volcano",
                "celestia", "tower_of_the_end", "fertile_farm", "lakeside", "wind_harbour"};
        for (int i = 0; i < 10; i++) {
            AreaDefinition area = areas.get(i);
            assertEquals(i + 1, area.id());
            assertEquals(slugs[i], area.slug());
            assertEquals(minLevel[i], area.minLevel());
            assertEquals(maxLevel[i], area.maxLevel());
            assertEquals(open[i], area.openEnded());
            assertEquals(tier[i], area.tier());
            assertEquals(LevelRange.of(minLevel[i], maxLevel[i]), area.levelRange());
            assertEquals("area.eternalrealm." + slugs[i], area.translationKey());
            assertEquals("area.eternalrealm." + slugs[i] + ".desc", area.descriptionKey());
            assertSame(area, Areas.require(area.id()));
        }
        assertEquals(AreaDefinition.Kind.DANGER, Areas.TOWER_OF_THE_END.kind());
        assertEquals(AreaDefinition.Kind.QUEST, Areas.WIND_HARBOUR.kind());
    }

    @Test
    public void unlockChainFollowsTheBossesWithTheSecondFallsDungeonOpeningTheRuins() {
        String[] expected = {null, "b1", "b2", "b3", "b5", "b6", "b7", "b1", "b2", "b3"};
        for (int i = 0; i < 10; i++) {
            assertEquals("area " + (i + 1), expected[i], Areas.all().get(i).unlockedByBossId());
        }
        assertTrue(Areas.ARCELIA.isOpenFromStart());
        assertFalse(Areas.ELDER_FOREST.isOpenFromStart());
        Set<String> unlocking = new HashSet<>();
        for (AreaDefinition area : Areas.all().subList(0, 7)) {
            if (area.unlockedByBossId() != null) {
                assertTrue(Areas.isBossId(area.unlockedByBossId()));
                assertTrue("one boss opens one story field", unlocking.add(area.unlockedByBossId()));
            }
        }
        assertFalse("b4 unlocks nothing", unlocking.contains("b4"));
        assertFalse("b8 unlocks nothing", unlocking.contains("b8"));
        for (AreaDefinition side : Areas.all().subList(7, 10)) {
            assertTrue("side fields open with the story field of their level band",
                    unlocking.contains(side.unlockedByBossId()));
        }
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
        assertTrue(Areas.byId(11).isEmpty());
        assertFalse(Areas.exists(0));
        assertFalse(Areas.exists(11));
        assertTrue(Areas.exists(1) && Areas.exists(10));
        assertThrows(IllegalArgumentException.class, () -> Areas.require(11));
    }

    @Test
    public void coloursAndTranslationKeysAreDistinct() {
        Set<Integer> colours = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (AreaDefinition area : Areas.all()) {
            assertTrue(colours.add(area.color()));
            assertTrue(keys.add(area.translationKey()));
        }
    }

    @Test
    public void invalidDefinitionsAreRejected() {
        AreaDefinition.Kind main = AreaDefinition.Kind.MAIN;
        List<Runnable> invalid = Arrays.asList(
                () -> new AreaDefinition(0, "x", 1, 2, false, null, "t", 0, main, 1),
                () -> new AreaDefinition(1, "Bad Slug", 1, 2, false, null, "t", 0, main, 1),
                () -> new AreaDefinition(1, "x", 0, 2, false, null, "t", 0, main, 1),
                () -> new AreaDefinition(1, "x", 5, 4, false, null, "t", 0, main, 1),
                () -> new AreaDefinition(1, "x", 1, 101, false, null, "t", 0, main, 1),
                () -> new AreaDefinition(1, "x", 1, 2, false, "B1", "t", 0, main, 1),
                () -> new AreaDefinition(1, "x", 1, 2, false, null, "", 0, main, 1),
                () -> new AreaDefinition(1, "x", 1, 2, false, null, "t", 0x1000000, main, 1),
                () -> new AreaDefinition(1, "x", 1, 2, false, null, "t", 0, null, 1),
                () -> new AreaDefinition(1, "x", 1, 2, false, null, "t", 0, main, 0),
                () -> new AreaDefinition(1, "x", 1, 2, false, null, "t", 0, main, 8),
                () -> new DungeonSite("d1", 0, "b1"),
                () -> new DungeonSite("D1", 1, "b1"));
        for (Runnable definition : invalid) {
            assertThrows(IllegalArgumentException.class, definition::run);
        }
        AreaDefinition valid = new AreaDefinition(1, "x", 1, 2, false, null, "t", 0xFFFFFF, main, 7);
        assertNull(valid.unlockedByBossId());
    }
}
