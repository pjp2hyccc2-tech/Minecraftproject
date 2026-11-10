package com.eternalrealm.monster;

import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.DungeonSite;
import com.eternalrealm.world.LevelRange;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MonstersCatalogTest {
    private static final Set<String> SUN_BURNING_WITH_HELMET_FIX =
            Set.of("minecraft:zombie", "minecraft:drowned", "minecraft:skeleton", "minecraft:stray");

    @Test
    public void idsAreUniqueAndResolvable() {
        Set<String> ids = new HashSet<>();
        for (MonsterTemplate template : Monsters.all()) {
            assertTrue("duplicate " + template.id(), ids.add(template.id()));
            assertSame(template, Monsters.require(template.id()));
            assertEquals("monster.eternalrealm." + template.id(), template.translationKey());
        }
        assertEquals(74, Monsters.all().size());
        assertEquals(List.copyOf(ids).size(), Monsters.ids().size());
        assertTrue(Monsters.byId("nope").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Monsters.require("nope"));
    }

    @Test
    public void everyAreaHasThreeFieldMonstersAndOneEliteInItsTable() {
        for (AreaDefinition area : Areas.all()) {
            MonsterTable table = Monsters.tableFor(area);
            assertEquals(area.monsterTableId(), table.id());
            assertEquals(area.slug(), 3, table.entries().size());
            assertEquals(area.slug(), 1, table.eliteEntries().size());
            assertTrue(table.eliteChance() > 0.0 && table.eliteChance() <= 0.1);
            for (MonsterTable.Entry entry : concat(table.entries(), table.eliteEntries())) {
                MonsterTemplate template = entry.template();
                assertTrue(template.id() + " lives in " + area.slug(), template.livesIn(area.id()));
                LevelRange levels = template.levels();
                assertTrue(template.id() + " levels inside the area range",
                        levels.min() >= area.minLevel() && levels.max() <= area.maxLevel());
            }
        }
        assertEquals(Areas.all().size(), Monsters.tables().size());
        assertTrue(Monsters.table("nope").isEmpty());
    }

    @Test
    public void everyDungeonHasThreeOrFourWaveMonstersWithTheSharedIdScheme() {
        int waves = 0;
        for (DungeonSite dungeon : Areas.dungeons()) {
            List<MonsterTemplate> monsters = Monsters.dungeonMonsters(dungeon.dungeonId());
            assertTrue(dungeon.dungeonId(), monsters.size() >= 3 && monsters.size() <= 4);
            assertTrue(dungeon.dungeonId() + " has a wave captain",
                    monsters.stream().anyMatch(m -> m.role() == MonsterRole.ELITE));
            for (MonsterTemplate monster : monsters) {
                assertTrue(monster.id().matches(dungeon.dungeonId() + "_[a-z_]+"));
                assertTrue(monster.role() != MonsterRole.MINION);
                assertEquals(Set.of(dungeon.areaId()), monster.areaIds());
            }
            waves += monsters.size();
        }
        assertEquals(25, waves);
        assertEquals(4, Monsters.dungeonMonsters("d8").size());
        assertTrue(Monsters.dungeonMonsters("d9").isEmpty());
    }

    @Test
    public void everyBossHasAMinionThatDropsNothing() {
        for (String boss : Areas.bossIds()) {
            MonsterTemplate minion = Monsters.require(Monsters.minionId(boss));
            assertEquals(boss + "_minion", minion.id());
            assertEquals(MonsterRole.MINION, minion.role());
            assertEquals(DropTables.NONE_ID, minion.dropTableId());
            assertEquals(0, minion.baseGoldMax());
            assertEquals(Set.of(Areas.areaOfBoss(boss).getAsInt()), minion.areaIds());
        }
        long minions = Monsters.all().stream().filter(m -> m.role() == MonsterRole.MINION).count();
        assertEquals(9, minions);
    }

    @Test
    public void everyTemplateHasItsOwnDropTableExceptSummons() {
        Set<String> used = new HashSet<>();
        for (MonsterTemplate template : Monsters.all()) {
            DropTable table = DropTables.require(template.dropTableId());
            used.add(table.id());
            if (template.role() != MonsterRole.MINION) {
                assertEquals(template.id(), table.id());
                int area = template.areaIds().iterator().next();
                assertEquals(template.id() + " drops equipment of its area's tier", Areas.require(area).tier(),
                        table.equipment().tier());
            }
        }
        assertEquals("no unused tables", DropTables.all().size(), used.size());
    }

    @Test
    public void bodiesAreEquippedForTheirAiAndProtectedFromTheSun() {
        for (MonsterTemplate template : Monsters.all()) {
            String body = template.baseEntityType();
            SpawnHints hints = template.spawnHints();
            String where = template.id() + " (" + body + ")";
            assertTrue(where, MonsterTemplate.BASE_ENTITY_TYPES.contains(body));
            if (SUN_BURNING_WITH_HELMET_FIX.contains(body)) {
                assertNotNull(where + " burns in daylight without a helmet", hints.headItemId());
            }
            switch (body) {
                case "minecraft:pillager" -> assertEquals(where, "minecraft:crossbow", hints.mainHandItemId());
                case "minecraft:skeleton", "minecraft:wither_skeleton", "minecraft:vindicator",
                        "minecraft:piglin_brute", "minecraft:drowned" -> assertNotNull(where, hints.mainHandItemId());
                case "minecraft:slime", "minecraft:magma_cube" -> assertTrue(where, hints.size() >= 2);
                default -> assertEquals(where, 0, hints.size());
            }
            assertFalse("'any' is reserved by quests", template.id().equals("any"));
            assertTrue(where, template.levels().max() <= 50);
        }
    }

    @Test
    public void invalidTemplatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> template("x", "minecraft:wolf", SpawnHints.NONE, Set.of(1)));
        assertThrows(IllegalArgumentException.class, () -> template("X", "minecraft:spider", SpawnHints.NONE, Set.of(1)));
        assertThrows(IllegalArgumentException.class, () -> template("x", "minecraft:spider", SpawnHints.NONE, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> template("x", "minecraft:spider", SpawnHints.NONE, Set.of(11)));
        assertThrows(IllegalArgumentException.class, () -> template("x", "minecraft:slime", SpawnHints.NONE, Set.of(1)));
        assertThrows(IllegalArgumentException.class, () -> template("x", "minecraft:slime", SpawnHints.sized(1), Set.of(1)));
        assertThrows(IllegalArgumentException.class, () -> template("x", "minecraft:spider", SpawnHints.sized(2), Set.of(1)));
        assertThrows(IllegalArgumentException.class, () -> SpawnHints.helmet("iron_helmet"));
        assertThrows(IllegalArgumentException.class, () -> new SpawnHints(null, null, 5));
        assertEquals(Set.of(1, 2), template("x", "minecraft:slime", SpawnHints.sized(2), Set.of(2, 1)).areaIds());
    }

    private static MonsterTemplate template(String id, String body, SpawnHints hints, Set<Integer> areas) {
        return new MonsterTemplate(id, body, MonsterRole.NORMAL, LevelRange.of(1, 5), 20, 1, 2, 0.1, 0, 0, 1.0, 5,
                1, 2, "none", areas, hints);
    }

    private static List<MonsterTable.Entry> concat(List<MonsterTable.Entry> a, List<MonsterTable.Entry> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
