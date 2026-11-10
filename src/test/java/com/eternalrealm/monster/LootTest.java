package com.eternalrealm.monster;

import org.junit.Test;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LootTest {
    private static final double EPS = 1e-9;

    @Test
    public void rarityBaseWeightsColoursAndMultipliers() {
        int[] weights = {60, 25, 10, 4, 1};
        double previousMultiplier = 0.0;
        for (Rarity rarity : Rarity.values()) {
            assertEquals(weights[rarity.ordinal()], rarity.baseWeight());
            assertEquals(rarity, Rarity.byId(rarity.id()).orElseThrow());
            assertEquals("rarity.eternalrealm." + rarity.id(), rarity.translationKey());
            assertTrue(rarity.statMultiplier() > previousMultiplier);
            assertTrue(rarity.color() >= 0 && rarity.color() <= 0xFFFFFF);
            previousMultiplier = rarity.statMultiplier();
        }
        assertEquals(1.0, Rarity.COMMON.statMultiplier(), EPS);
        assertTrue(Rarity.byId("mythic").isEmpty());
        assertEquals(new RarityWeights(60, 25, 10, 4, 1), RarityWeights.BASE);
    }

    @Test
    public void luckShiftsWeightTowardsRarerResultsWithTheDocumentedFormula() {
        RarityWeights base = RarityWeights.BASE;
        assertEquals(0.01, base.chance(Rarity.LEGENDARY, 0.0), EPS);
        assertEquals(0.60, base.chance(Rarity.COMMON, 0.0), EPS);
        double[] shifted = base.shifted(0.5);
        assertEquals(60.0, shifted[0], EPS);
        assertEquals(25.0 * 1.5, shifted[1], EPS);
        assertEquals(1.0 * Math.pow(1.5, 4), shifted[4], EPS);
        assertEquals(5.0625 / 138.5625, base.chance(Rarity.LEGENDARY, 0.5), EPS);
        assertEquals(16.0 / 198.0, base.chance(Rarity.LEGENDARY, 1.0), EPS);
        assertEquals("luck above the cap is clamped", base.chance(Rarity.LEGENDARY, 1.0), base.chance(Rarity.LEGENDARY, 50.0), EPS);
        double previous = 0.0;
        for (double luck = 0.0; luck <= 1.0; luck += 0.1) {
            double legendary = base.chance(Rarity.LEGENDARY, luck);
            assertTrue(legendary > previous);
            previous = legendary;
        }
        assertThrows(IllegalArgumentException.class, () -> RarityWeights.clampLuck(-0.01));
        assertThrows(IllegalArgumentException.class, () -> RarityWeights.clampLuck(Double.NaN));
    }

    @Test
    public void rarityRollsMatchTheWeightsAndNeverPickZeroWeights() {
        Random random = new Random(2024);
        Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        int samples = 100_000;
        for (int i = 0; i < samples; i++) {
            counts.merge(LootRoller.rollRarity(RarityWeights.BASE, 0.0, random), 1, Integer::sum);
        }
        for (Rarity rarity : Rarity.values()) {
            assertEquals(rarity.name(), rarity.baseWeight() / 100.0, counts.get(rarity) / (double) samples, 0.006);
        }
        RarityWeights onlyEpic = new RarityWeights(0, 0, 0, 7, 0);
        for (int i = 0; i < 500; i++) {
            assertEquals(Rarity.EPIC, LootRoller.rollRarity(onlyEpic, 1.0, random));
        }
        int lucky = 0;
        for (int i = 0; i < samples; i++) {
            if (LootRoller.rollRarity(RarityWeights.BASE, 1.0, random) == Rarity.LEGENDARY) {
                lucky++;
            }
        }
        assertEquals(16.0 / 198.0, lucky / (double) samples, 0.006);
    }

    @Test
    public void itemEntriesRollIndependentlyWithinTheirCountRange() {
        DropTable table = new DropTable("t", List.of(
                new DropEntry("eternalrealm:slime_gel", 1, 3, 1.0),
                new DropEntry("eternalrealm:wolf_fang", 2, 2, 0.25)), null);
        Random random = new Random(11);
        Set<Integer> gelCounts = new HashSet<>();
        int fangs = 0;
        int samples = 20_000;
        for (int i = 0; i < samples; i++) {
            List<LootDrop> drops = LootRoller.roll(table, 0.0, random);
            LootDrop.ItemDrop gel = (LootDrop.ItemDrop) drops.get(0);
            assertEquals("eternalrealm:slime_gel", gel.itemId());
            gelCounts.add(gel.count());
            if (drops.size() == 2) {
                assertEquals(new LootDrop.ItemDrop("eternalrealm:wolf_fang", 2), drops.get(1));
                fangs++;
            }
        }
        assertEquals(Set.of(1, 2, 3), gelCounts);
        assertEquals(0.25, fangs / (double) samples, 0.012);
    }

    @Test
    public void equipmentRollsCarryTheTierAndALuckShiftedRarity() {
        DropTable table = new DropTable("t", List.of(), new EquipmentRoll(5, 1.0, RarityWeights.BASE));
        Random random = new Random(77);
        int legendaryWithoutLuck = 0;
        int legendaryWithLuck = 0;
        for (int i = 0; i < 20_000; i++) {
            LootDrop.EquipmentDrop plain = (LootDrop.EquipmentDrop) LootRoller.roll(table, 0.0, random).get(0);
            assertEquals(5, plain.tier());
            legendaryWithoutLuck += plain.rarity() == Rarity.LEGENDARY ? 1 : 0;
            LootDrop.EquipmentDrop lucky = (LootDrop.EquipmentDrop) LootRoller.roll(table, 1.0, random).get(0);
            legendaryWithLuck += lucky.rarity() == Rarity.LEGENDARY ? 1 : 0;
        }
        assertTrue(legendaryWithLuck > 4 * legendaryWithoutLuck);
        DropTable rare = new DropTable("t", List.of(), new EquipmentRoll(1, 0.02, RarityWeights.BASE));
        int drops = 0;
        for (int i = 0; i < 50_000; i++) {
            drops += LootRoller.roll(rare, 0.0, random).size();
        }
        assertEquals(0.02, drops / 50_000.0, 0.003);
    }

    @Test
    public void rollsAreDeterministicAndEmptyTablesDropNothing() {
        DropTable table = DropTables.require("bandit_chief");
        assertEquals(LootRoller.roll(table, 0.3, new Random(9)), LootRoller.roll(table, 0.3, new Random(9)));
        assertTrue(LootRoller.roll(DropTables.require(DropTables.NONE_ID), 1.0, new Random(1)).isEmpty());
        assertTrue(DropTables.require(DropTables.NONE_ID).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> LootRoller.roll(table, -1, new Random(1)));
        assertThrows(NullPointerException.class, () -> LootRoller.roll(null, 0, new Random(1)));
    }

    @Test
    public void dropTablesUseTheCraftingModuleMaterialIds() {
        assertEquals(List.of("eternalrealm:slime_gel", "eternalrealm:wolf_fang", "eternalrealm:treant_bark",
                "eternalrealm:forest_spore", "eternalrealm:scarab_shell", "eternalrealm:desert_crystal",
                "eternalrealm:frost_shard", "eternalrealm:yeti_fur", "eternalrealm:magma_core",
                "eternalrealm:drake_scale", "eternalrealm:sky_feather", "eternalrealm:star_fragment",
                "eternalrealm:void_essence", "eternalrealm:abyss_crystal"), Materials.areaMaterials());
        assertEquals(List.of("eternalrealm:enhancement_stone", "eternalrealm:reroll_crystal"), Materials.upgradeMaterials());
        assertEquals(4, Materials.areaOf("eternalrealm:yeti_fur").getAsInt());
        assertTrue(Materials.areaOf(Materials.REROLL_CRYSTAL).isEmpty());
        assertTrue(Materials.ofArea(11).isEmpty());

        Set<String> dropped = DropTables.droppableItemIds();
        for (String material : Materials.areaMaterials()) {
            assertTrue(material + " must drop somewhere", dropped.contains(material));
        }
        for (int field = 1; field <= 10; field++) {
            for (String material : Materials.fieldCommons(field)) {
                assertTrue(material + " must drop somewhere", dropped.contains(material));
            }
            assertTrue(dropped.contains(Materials.fieldUncommon(field)));
            assertTrue("every field has an elite that drops " + Materials.fieldElite(field),
                    dropped.contains(Materials.fieldElite(field)));
        }
        // the monster module's ids are the crafting module's ids, in the same order
        List<String> crafting = new ArrayList<>();
        com.eternalrealm.item.Materials.fieldMaterials().forEach(m -> crafting.add(m.id()));
        assertEquals(crafting, Materials.fieldMaterials());
        for (String item : dropped) {
            assertTrue("unknown item " + item, Materials.isKnown(item));
        }
        assertFalse(Materials.isKnown("eternalrealm:slime_jelly"));
    }

    @Test
    public void everyMonsterDropsItsAreaMaterialsAndTheUpgrades() {
        for (MonsterTemplate template : Monsters.all()) {
            DropTable table = DropTables.require(template.dropTableId());
            if (template.role() == MonsterRole.MINION) {
                continue;
            }
            int area = template.areaIds().iterator().next();
            Set<String> items = new HashSet<>();
            table.entries().forEach(entry -> items.add(entry.itemId()));
            Set<String> expected = new HashSet<>(Set.of(Materials.ofArea(area).get(0), Materials.ofArea(area).get(1),
                    Materials.ENHANCEMENT_STONE, Materials.REROLL_CRYSTAL));
            expected.addAll(Materials.fieldCommons(area));
            expected.add(Materials.fieldUncommon(area));
            if (template.role() == MonsterRole.ELITE) {
                expected.add(Materials.fieldElite(area));
            }
            assertEquals(template.id(), expected, items);
            double crystal = table.entries().stream().filter(e -> e.itemId().equals(Materials.REROLL_CRYSTAL))
                    .findFirst().orElseThrow().chance();
            double stone = table.entries().stream().filter(e -> e.itemId().equals(Materials.ENHANCEMENT_STONE))
                    .findFirst().orElseThrow().chance();
            assertTrue(template.id() + ": reroll crystals are rarer than enhancement stones", crystal < stone);
            assertTrue(crystal <= 0.1);
            if (template.role() == MonsterRole.ELITE) {
                assertEquals(DropTables.ELITE_WEIGHTS, table.equipment().weights());
            } else {
                assertEquals(RarityWeights.BASE, table.equipment().weights());
            }
        }
    }

    @Test
    public void invalidDropDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DropEntry("slime_gel", 1, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new DropEntry("eternalrealm:x", 0, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new DropEntry("eternalrealm:x", 3, 2, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new DropEntry("eternalrealm:x", 1, 65, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new DropEntry("eternalrealm:x", 1, 1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new DropEntry("eternalrealm:x", 1, 1, 1.01));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentRoll(0, 0.5, RarityWeights.BASE));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentRoll(8, 0.5, RarityWeights.BASE));
        assertThrows(NullPointerException.class, () -> new EquipmentRoll(1, 0.5, null));
        assertThrows(IllegalArgumentException.class, () -> new RarityWeights(0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RarityWeights(-1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new DropTable("Bad Id", List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new LootDrop.ItemDrop("eternalrealm:x", 0));
        assertThrows(IllegalArgumentException.class, () -> new LootDrop.EquipmentDrop(9, Rarity.RARE));
        assertThrows(NullPointerException.class, () -> new LootDrop.EquipmentDrop(1, null));
        assertNull(DropTable.empty("x").equipment());
    }
}
