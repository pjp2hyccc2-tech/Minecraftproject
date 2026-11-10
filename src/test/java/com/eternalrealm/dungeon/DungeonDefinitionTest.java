package com.eternalrealm.dungeon;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Dungeon content (ids shared with other modules) and validation of the definitions. */
public class DungeonDefinitionTest {
    private static final List<Integer> AREAS = List.of(1, 2, 3, 4, 4, 5, 6, 7);
    private static final List<Integer> LEVELS = List.of(6, 13, 20, 26, 29, 35, 42, 47);
    private static final List<DungeonTheme> THEMES = List.of(DungeonTheme.UNDERGROUND_CITY, DungeonTheme.FOREST,
            DungeonTheme.DESERT_TEMPLE, DungeonTheme.ICE_PRISON, DungeonTheme.ICE_CASTLE, DungeonTheme.VOLCANO,
            DungeonTheme.SKY_RUINS, DungeonTheme.DARK_TOWER);

    @Test
    public void dungeonsLineUpWithAreasAndBosses() {
        List<DungeonDefinition> all = Dungeons.all();
        assertEquals(8, all.size());
        for (int i = 0; i < all.size(); i++) {
            DungeonDefinition dungeon = all.get(i);
            String n = String.valueOf(i + 1);
            assertEquals("d" + n, dungeon.id());
            assertEquals(dungeon.id(), AREAS.get(i).intValue(), dungeon.areaId());
            assertEquals(dungeon.id(), LEVELS.get(i).intValue(), dungeon.recommendedLevel());
            assertEquals(THEMES.get(i), dungeon.theme());
            assertEquals("b" + n, dungeon.bossId());
            assertEquals("dungeon.eternalrealm.d" + n, dungeon.translationKey());
            assertEquals(1, dungeon.minPartySize());
            assertEquals(5, dungeon.maxPartySize());
            assertTrue(dungeon.roomCount() >= 3 && dungeon.roomCount() <= 6);
            assertTrue(dungeon.timeLimitTicks() >= 20L * 60 * 20);
            BossDefinition boss = Bosses.byId(dungeon.bossId()).orElseThrow();
            assertEquals("the boss is balanced for the dungeon's level", dungeon.recommendedLevel(), boss.level());
            assertEquals(dungeon, Dungeons.byId(dungeon.id()).orElseThrow());
        }
        assertEquals(List.of(Dungeons.D4, Dungeons.D5), Dungeons.inArea(4));
        assertEquals(List.of(Dungeons.D8), Dungeons.inArea(7));
        assertFalse(Dungeons.byId("d9").isPresent());
    }

    @Test
    public void monsterTemplatesFollowTheNamingRule() {
        for (DungeonDefinition dungeon : Dungeons.all()) {
            Set<String> templates = dungeon.monsterTemplateIds();
            assertTrue(dungeon.id() + ": 3-4 monster kinds", templates.size() >= 3 && templates.size() <= 4);
            for (String template : templates) {
                assertTrue(template, template.matches(dungeon.id() + "_[a-z][a-z_]*"));
            }
        }
        assertEquals(Set.of("d1_bandit", "d1_crawler", "d1_canal_drowned"), Dungeons.D1.monsterTemplateIds());
    }

    @Test
    public void laterRoomsAreHarder() {
        for (DungeonDefinition dungeon : Dungeons.all()) {
            int previous = 0;
            for (RoomPlan room : dungeon.rooms()) {
                int total = 0;
                for (Wave wave : room.waves()) {
                    total += wave.totalFor(1);
                }
                assertTrue(dungeon.id() + ": rooms get harder", total > previous);
                previous = total;
            }
        }
    }

    @Test
    public void theFinalRaidIsForTheWholeServer() {
        RaidDefinition raid = Dungeons.FINAL_RAID;
        assertEquals("final", raid.id());
        assertEquals("final", raid.bossId());
        assertEquals(7, raid.areaId());
        assertEquals(20, raid.maxPartySize());
        assertEquals(Bosses.FINAL.level(), raid.recommendedLevel());
        assertEquals(DungeonTheme.END_THRONE, raid.theme());
        assertEquals("raid.eternalrealm.final", raid.translationKey());
        assertTrue(raid.allowsPartySize(20));
        assertFalse(raid.allowsPartySize(21));
    }

    @Test
    public void wavesScaleWithThePartySize() {
        MonsterGroup grunts = new MonsterGroup("d1_bandit", 3, 1.0);
        MonsterGroup elite = new MonsterGroup("d1_ghoul", 1, 0.0);
        MonsterGroup half = new MonsterGroup("d1_cave_rat", 2, 0.5);
        assertEquals(3, grunts.countFor(1));
        assertEquals(7, grunts.countFor(5));
        assertEquals("fixed elite", 1, elite.countFor(5));
        assertEquals(2, half.countFor(1));
        assertEquals("0.5 rounds half up", 3, half.countFor(2));
        assertEquals(3, half.countFor(3));
        assertEquals(4, half.countFor(5));
        Wave wave = Wave.of(grunts, elite, half);
        assertEquals(List.of(new MonsterCount("d1_bandit", 5), new MonsterCount("d1_ghoul", 1),
                new MonsterCount("d1_cave_rat", 3)), wave.spawnsFor(3));
        assertEquals(9, wave.totalFor(3));
        assertThrows(IllegalArgumentException.class, () -> grunts.countFor(0));
    }

    @Test
    public void invalidDefinitionsAreRejected() {
        RoomPlan room = RoomPlan.of(Wave.of(new MonsterGroup("dx_slime", 2, 0.5)));
        List<RoomPlan> three = List.of(room, room, room);
        new DungeonDefinition("dx", 1, 5, 1, 5, DungeonTheme.FOREST, three, "bx", 36_000);
        assertThrows("too few rooms", IllegalArgumentException.class, () -> new DungeonDefinition("dx", 1, 5, 1, 5,
                DungeonTheme.FOREST, List.of(room, room), "bx", 36_000));
        assertThrows("too many rooms", IllegalArgumentException.class, () -> new DungeonDefinition("dx", 1, 5, 1, 5,
                DungeonTheme.FOREST, List.of(room, room, room, room, room, room, room), "bx", 36_000));
        assertThrows("party of 6", IllegalArgumentException.class, () -> new DungeonDefinition("dx", 1, 5, 1, 6,
                DungeonTheme.FOREST, three, "bx", 36_000));
        assertThrows("min above max", IllegalArgumentException.class, () -> new DungeonDefinition("dx", 1, 5, 4, 3,
                DungeonTheme.FOREST, three, "bx", 36_000));
        assertThrows("template of another dungeon", IllegalArgumentException.class, () -> new DungeonDefinition("dy",
                1, 5, 1, 5, DungeonTheme.FOREST, three, "bx", 36_000));
        assertThrows("time limit", IllegalArgumentException.class, () -> new DungeonDefinition("dx", 1, 5, 1, 5,
                DungeonTheme.FOREST, three, "bx", 10));
        assertThrows(NullPointerException.class, () -> new DungeonDefinition("dx", 1, 5, 1, 5, null, three, "bx",
                36_000));
        assertThrows(IllegalArgumentException.class, () -> new Wave(List.of()));
        assertThrows("same template twice in a wave", IllegalArgumentException.class, () -> Wave.of(
                new MonsterGroup("dx_slime", 1, 0), new MonsterGroup("dx_slime", 2, 0)));
        assertThrows(IllegalArgumentException.class, () -> new RoomPlan(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MonsterGroup("dx_slime", 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new MonsterGroup("Bad Id", 1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new RaidDefinition("r", 7, 50, 10, 5,
                DungeonTheme.END_THRONE, "final", 36_000));
    }

    @Test
    public void themesAndRolesHaveStableIds() {
        assertEquals("underground_city", DungeonTheme.UNDERGROUND_CITY.id());
        assertEquals("end_throne", DungeonTheme.END_THRONE.id());
        for (DungeonTheme theme : DungeonTheme.values()) {
            assertEquals(theme, DungeonTheme.byId(theme.id()).orElseThrow());
        }
        assertFalse(DungeonTheme.byId("moon").isPresent());
        assertEquals(List.of(BlockRole.FLOOR, BlockRole.WALL, BlockRole.PILLAR, BlockRole.LIGHT, BlockRole.ACCENT,
                BlockRole.DOOR, BlockRole.AIR), List.of(BlockRole.values()));
    }
}
