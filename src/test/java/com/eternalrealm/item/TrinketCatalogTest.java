package com.eternalrealm.item;

import com.eternalrealm.character.StatType;
import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The smithy's trinket catalogue ({@link Trinkets}): 13 grades × 6 focuses × an accessory and a charm, stable ids and
 * models, a recipe each, field drops for the first eight grades, and the art and names of all 156.
 */
public class TrinketCatalogTest {
    private static final Path ASSETS = Paths.get("src/main/resources/assets/eternalrealm");
    private static final double EPS = 1.0e-9;

    @Test
    public void catalogueHasEveryGradeFocusAndSlot() {
        List<TrinketDesign> all = Trinkets.all();
        assertEquals(156, all.size());
        Set<String> ids = new HashSet<>();
        Set<Integer> models = new HashSet<>();
        for (TrinketDesign design : all) {
            assertTrue(design.id(), design.id().startsWith(Trinkets.PREFIX) && ids.add(design.id()));
            assertTrue(models.add(design.model()));
            assertTrue(design.model() >= Trinkets.MODELS && design.model() < Trinkets.MODELS + 156);
            assertEquals(design, Armory.design(design.id()).orElseThrow());
            assertEquals(design, Armory.byModel(design.model()).orElseThrow());
            assertTrue(Relics.byModel(design.model()).isEmpty());
            assertFalse(design.isRelic());
            assertTrue(Trinkets.isCatalogue(design));
            Trinkets.Grade grade = Trinkets.gradeOf(design).orElseThrow();
            Trinkets.Focus focus = Trinkets.focusOf(design).orElseThrow();
            boolean charm = design.slot() == EquipmentSlot.CHARM;
            assertEquals(design, Trinkets.of(grade, focus, charm));
            assertEquals(grade.level(), design.level());
            assertEquals(grade.ordinal() < ItemTier.COUNT ? grade.number() : ItemTier.COUNT,
                    design.base().tier().number());
            assertEquals(focus.kind(charm), design.kind());
            // one stat, by percentage
            double expected = charm ? Trinkets.charmPercent(grade.level()) : Trinkets.accessoryPercent(grade.level());
            for (StatType type : StatType.values()) {
                assertEquals(design.id() + " " + type, type == focus.stat() ? expected : 0.0,
                        design.percents().get(type), EPS);
            }
            assertTrue(design.sources().contains(DesignSource.CRAFT));
            assertEquals(grade.ordinal() <= Trinkets.LAST_FIELD_GRADE.ordinal(),
                    design.sources().contains(DesignSource.FIELD));
            assertTrue(design.bias().containsKey(AffixKind.percentOf(focus.stat())));
        }
        assertEquals(0.33, Trinkets.accessoryPercent(100), EPS);
        assertEquals(0.198, Trinkets.charmPercent(100), EPS);
        assertEquals(0.033, Trinkets.accessoryPercent(1), EPS);
        assertEquals(12, Trinkets.ofGrade(Trinkets.Grade.HEROIC).size());
        assertEquals("trinket_eternal_str_bracelet", Trinkets.of(Trinkets.Grade.ETERNAL, Trinkets.Focus.STR, false).id());
        assertEquals(5200, Trinkets.of(Trinkets.Grade.NOVICE, Trinkets.Focus.STR, false).model());
        assertEquals(5355, Trinkets.of(Trinkets.Grade.ETERNAL, Trinkets.Focus.LUK, true).model());
        // a higher grade is always better
        for (Trinkets.Focus focus : Trinkets.Focus.values()) {
            double previous = 0.0;
            for (Trinkets.Grade grade : Trinkets.Grade.values()) {
                double percent = Trinkets.of(grade, focus, false).percents().get(focus.stat());
                assertTrue(percent > previous);
                previous = percent;
            }
        }
    }

    @Test
    public void everyTrinketHasARecipeThatGrowsWithItsGrade() {
        assertEquals(156, Recipes.trinketRecipes().size());
        long previousGold = 0;
        for (Trinkets.Grade grade : Trinkets.Grade.values()) {
            List<Recipe> recipes = Recipes.trinketRecipes(grade);
            assertEquals(12, recipes.size());
            long gold = recipes.get(0).goldCost();
            assertTrue(grade + " costs more", gold > previousGold);
            previousGold = gold;
        }
        for (TrinketDesign design : Trinkets.all()) {
            Recipe recipe = Recipes.forDesign(design).orElseThrow(() -> new AssertionError("no recipe " + design.id()));
            assertEquals(design.level(), recipe.requiredLevel());
            assertEquals(design.base(), recipe.output());
            Trinkets.Grade grade = Trinkets.gradeOf(design).orElseThrow();
            // the gold alone is worth more than what an NPC pays for the expected piece at the best luck
            double expectedMultiplier = 0.0;
            for (ItemRarity rarity : ItemRarity.values()) {
                expectedMultiplier += EquipmentGenerator.rarityChance(rarity, EquipmentGenerator.MAX_LUCK_BONUS)
                        * rarity.priceMultiplier();
            }
            assertTrue(design.id(), recipe.goldCost()
                    > ItemPrices.equipmentSellValue(design.tier(), ItemRarity.COMMON, 0) * expectedMultiplier);
            for (String material : recipe.ingredients().keySet()) {
                assertTrue(material, Materials.isKnown(material));
            }
            if (grade.ordinal() >= ItemTier.COUNT) {
                Trinkets.Focus focus = Trinkets.focusOf(design).orElseThrow();
                assertTrue(design.id(), recipe.ingredients().containsKey(focus.eliteMaterial()));
                assertTrue(recipe.ingredients().containsKey(Materials.REROLL_CRYSTAL));
            }
        }
        Recipe top = Recipes.forDesign(Trinkets.of(Trinkets.Grade.ETERNAL, Trinkets.Focus.STR, false)).orElseThrow();
        assertEquals(69_000L, top.goldCost());
        assertEquals(16, (int) top.ingredients().get(Materials.VOID_ESSENCE));
        assertEquals(12, (int) top.ingredients().get("eternalrealm:veined_steel"));
    }

    @Test
    public void fieldDropsFollowTheTierAndLeanTowardsTheJob() {
        SplittableRandom random = new SplittableRandom(11);
        for (ItemTier tier : ItemTier.values()) {
            for (int i = 0; i < 50; i++) {
                TrinketDesign design = Trinkets.forDrop(tier, 20, Set.of(), random);
                Trinkets.Grade grade = Trinkets.gradeOf(design).orElseThrow();
                assertEquals(Trinkets.Grade.ofTier(tier), grade);
            }
        }
        int champion = 0;
        int draws = 20_000;
        Map<StatType, Integer> focus = new EnumMap<>(StatType.class);
        int charms = 0;
        for (int i = 0; i < draws; i++) {
            TrinketDesign design = Trinkets.forDrop(ItemTier.ABYSS, 50, Set.of(StatType.STR, StatType.VIT), random);
            if (Trinkets.gradeOf(design).orElseThrow() == Trinkets.Grade.CHAMPION) {
                champion++;
            }
            focus.merge(Trinkets.focusOf(design).orElseThrow().stat(), 1, Integer::sum);
            if (design.slot() == EquipmentSlot.CHARM) {
                charms++;
            }
        }
        assertEquals(Trinkets.CHAMPION_SHARE, champion / (double) draws, 0.02);
        assertEquals(0.5, charms / (double) draws, 0.02);
        // weights 2, 2, 1, 1, 1, 1 of 8
        assertEquals(0.25, focus.get(StatType.STR) / (double) draws, 0.015);
        assertEquals(0.125, focus.get(StatType.DEX) / (double) draws, 0.015);
        // a level-49 monster never drops a champion trinket
        for (int i = 0; i < 200; i++) {
            assertEquals(Trinkets.Grade.HEROIC,
                    Trinkets.gradeOf(Trinkets.forDrop(ItemTier.ABYSS, 49, Set.of(), random)).orElseThrow());
        }
        assertEquals(0.10, Trinkets.dropShare(false), EPS);
        assertEquals(0.18, Trinkets.dropShare(true), EPS);
    }

    @Test
    public void catalogueTrinketsSurviveTheCodec() throws CorruptDataException, IncompatibleDataException {
        for (TrinketDesign design : List.of(Trinkets.of(Trinkets.Grade.NOVICE, Trinkets.Focus.LUK, true),
                Trinkets.of(Trinkets.Grade.ETERNAL, Trinkets.Focus.INT, false))) {
            EquipmentData piece = EquipmentGenerator.roll(design, ItemRarity.EPIC, new SplittableRandom(3))
                    .withEnhancement(7).withUniqueId(java.util.UUID.nameUUIDFromBytes(design.id().getBytes(
                            StandardCharsets.UTF_8)));
            EquipmentData read = EquipmentCodec.decode(EquipmentCodec.encode(piece));
            assertEquals(piece, read);
            assertEquals(design, read.design());
            for (Affix affix : read.affixes()) {
                assertFalse(affix.toString(), affix.kind().isPrimaryStat());
            }
        }
    }

    @Test
    public void everyTrinketIsNamedAndDrawn() throws IOException {
        Map<String, String> ja = RelicCatalogTest.lang("ja_jp");
        Map<String, String> en = RelicCatalogTest.lang("en_us");
        Map<String, String> bases = new java.util.HashMap<>();
        for (TrinketDesign design : Trinkets.all()) {
            for (String key : List.of(design.translationKey(), design.loreKey())) {
                assertTrue("ja " + key, ja.containsKey(key));
                assertTrue("en " + key, en.containsKey(key));
            }
            for (String path : List.of("textures/item/trinket/" + design.id() + ".png",
                    "models/item/trinket/" + design.id() + ".json")) {
                assertTrue("missing " + path, Files.isRegularFile(ASSETS.resolve(path)));
            }
            String item = design.base().itemPath();
            String model = bases.computeIfAbsent(item, path -> read("models/item/" + path + ".json"));
            assertTrue(item + " lacks the override of " + design.id(),
                    model.contains("\"custom_model_data\": " + design.model()));
        }
        // names are unique in both languages
        Set<String> jaNames = new HashSet<>();
        Set<String> enNames = new HashSet<>();
        for (TrinketDesign design : Trinkets.all()) {
            assertTrue(ja.get(design.translationKey()), jaNames.add(ja.get(design.translationKey())));
            assertTrue(en.get(design.translationKey()), enNames.add(en.get(design.translationKey())));
        }
        for (Trinkets.Grade grade : Trinkets.Grade.values()) {
            String key = "smith.eternalrealm.trinket_grade." + grade.id();
            assertTrue(key, ja.containsKey(key) && en.containsKey(key));
        }
    }

    private static String read(String path) {
        try {
            return Files.readString(ASSETS.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
