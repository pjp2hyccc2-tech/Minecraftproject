package com.eternalrealm.monster;

import com.eternalrealm.world.Areas;
import com.eternalrealm.world.LevelRange;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MonsterTableTest {
    private final MonsterTable arcelia = Monsters.tableFor(Areas.ARCELIA);

    @Test
    public void picksFollowTheWeightsAndTheEliteChance() {
        Random random = new Random(12345);
        Map<String, Integer> counts = new HashMap<>();
        int samples = 40_000;
        for (int i = 0; i < samples; i++) {
            counts.merge(arcelia.pick(Areas.ARCELIA.levelRange(), random).template().id(), 1, Integer::sum);
        }
        double elite = counts.getOrDefault("bandit_chief", 0) / (double) samples;
        assertEquals("elite chance 5%", 0.05, elite, 0.006);
        // Normal weights 40/35/25 of the remaining 95%.
        assertEquals(0.95 * 0.40, counts.get("meadow_slime") / (double) samples, 0.012);
        assertEquals(0.95 * 0.35, counts.get("wandering_dead") / (double) samples, 0.012);
        assertEquals(0.95 * 0.25, counts.get("highway_bandit") / (double) samples, 0.012);
    }

    @Test
    public void levelsComeFromTheAreaRangePlusTheOffsetClampedToTheTemplate() {
        Random random = new Random(99);
        for (int i = 0; i < 5000; i++) {
            MonsterSpawn spawn = arcelia.pick(Areas.ARCELIA.levelRange(), random);
            assertTrue(spawn.template().levels().contains(spawn.level()));
            assertTrue(Areas.ARCELIA.levelRange().contains(spawn.level()));
        }
        MonsterTemplate slime = Monsters.require("meadow_slime");
        MonsterTable single = new MonsterTable("t", List.of(new MonsterTable.Entry(slime, 1, 5)), 0.0, List.of());
        for (int i = 0; i < 200; i++) {
            MonsterSpawn spawn = single.pick(LevelRange.of(2, 3), random);
            assertTrue("2..3 + 5 = 7..8", spawn.level() >= 7 && spawn.level() <= 8);
        }
        assertEquals("clamped to the template's max", 8, single.pick(LevelRange.of(40, 40), random).level());
        MonsterTable negative = new MonsterTable("t", List.of(new MonsterTable.Entry(slime, 1, -5)), 0.0, List.of());
        assertEquals("clamped to the template's min", 1, negative.pick(LevelRange.of(1, 1), random).level());
    }

    @Test
    public void picksAreDeterministicForASeedAndWorkWithAnyRandomGenerator() {
        List<MonsterSpawn> first = pickMany(new Random(5));
        assertEquals(first, pickMany(new Random(5)));
        MonsterSpawn spawn = arcelia.pick(Areas.ARCELIA.levelRange(), new SplittableRandom(1));
        assertEquals(spawn.stats(), MonsterScaling.stats(spawn.template(), spawn.level()));
    }

    @Test
    public void tablesWithoutEliteChanceNeverPickElites() {
        MonsterTable noElites = new MonsterTable("t", arcelia.entries(), 0.0, arcelia.eliteEntries());
        Random random = new Random(3);
        for (int i = 0; i < 2000; i++) {
            assertEquals(MonsterRole.NORMAL, noElites.pick(Areas.ARCELIA.levelRange(), random).template().role());
        }
        MonsterTable onlyElites = new MonsterTable("t", arcelia.entries(), 1.0, arcelia.eliteEntries());
        assertEquals(MonsterRole.ELITE, onlyElites.pick(Areas.ARCELIA.levelRange(), random).template().role());
    }

    @Test
    public void invalidTablesAreRejected() {
        MonsterTable.Entry normal = arcelia.entries().get(0);
        MonsterTable.Entry elite = arcelia.eliteEntries().get(0);
        MonsterTemplate minion = Monsters.require("b1_minion");
        assertThrows(IllegalArgumentException.class, () -> new MonsterTable("t", List.of(), 0.0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MonsterTable("t", List.of(elite), 0.0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MonsterTable("t", List.of(normal), 0.0, List.of(normal)));
        assertThrows(IllegalArgumentException.class, () -> new MonsterTable("t", List.of(normal), 0.1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MonsterTable("t", List.of(normal), 1.5, List.of(elite)));
        assertThrows(IllegalArgumentException.class,
                () -> new MonsterTable("t", List.of(new MonsterTable.Entry(minion, 1, 0)), 0.0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MonsterTable.Entry(minion, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MonsterTable.Entry(minion, 1, 21));
        assertThrows(IllegalArgumentException.class, () -> new MonsterSpawn(minion, 0));
    }

    private List<MonsterSpawn> pickMany(Random random) {
        return java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> arcelia.pick(Areas.ARCELIA.levelRange(), random)).toList();
    }
}
