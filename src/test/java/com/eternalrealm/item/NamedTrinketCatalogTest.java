package com.eternalrealm.item;

import com.eternalrealm.character.StatType;
import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.monster.MonsterScaling;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.world.field.FieldThreats;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The named trinkets of the lords and dungeons ({@link NamedTrinkets}, owner's request of 2026-11-13): one per lord, an
 * accessory and a charm per dungeon and the final raid, in the eight newer kinds, each with an effect; their sources,
 * recipes, art and names.
 */
public class NamedTrinketCatalogTest {
    private static final Path ASSETS = Paths.get("src/main/resources/assets/eternalrealm");
    private static final double EPS = 1.0e-9;
    private static final Set<TrinketKind> NEW_KINDS = EnumSet.of(TrinketKind.BROOCH, TrinketKind.CIRCLET,
            TrinketKind.ANKLET, TrinketKind.MEDAL, TrinketKind.MIRROR, TrinketKind.FEATHER, TrinketKind.BEADS,
            TrinketKind.DOLL);

    @Test
    public void everyLordAndDungeonHasItsTrinkets() {
        List<TrinketDesign> all = NamedTrinkets.all();
        assertEquals(FieldThreats.lords().size() + 2 * (Dungeons.all().size() + 1), all.size());
        assertEquals(48, all.size());
        Set<String> ids = new HashSet<>();
        Set<TrinketKind> kinds = EnumSet.noneOf(TrinketKind.class);
        for (int i = 0; i < all.size(); i++) {
            TrinketDesign design = all.get(i);
            assertTrue(design.id(), ids.add(design.id()) && NamedTrinkets.isNamedId(design.id()));
            assertEquals(NamedTrinkets.MODELS + i, design.model());
            assertEquals(design, Armory.design(design.id()).orElseThrow());
            assertEquals(design, Armory.byModel(design.model()).orElseThrow());
            assertTrue(Trinkets.byModel(design.model()).isEmpty() && Relics.byModel(design.model()).isEmpty());
            assertFalse(design.isRelic() || Trinkets.isCatalogue(design));
            assertTrue(design.id(), NEW_KINDS.contains(design.kind()));
            kinds.add(design.kind());
            assertTrue(design.sources().contains(DesignSource.CRAFT));
            assertTrue(NamedTrinkets.effectOf(design).isPresent());
            assertTrue(design.id() + " has an effect",
                    !design.modifiers().equals(com.eternalrealm.combat.CombatModifiers.NONE) || !design.perks().isEmpty());
        }
        assertEquals("all eight newer kinds are worn by someone", NEW_KINDS, kinds);
        for (FieldThreats.Lord lord : FieldThreats.lords()) {
            TrinketDesign design = NamedTrinkets.forLord(lord.id()).orElseThrow(() -> new AssertionError(lord.id()));
            assertEquals(lord.id(), design.origin());
            assertEquals(lord.field(), design.field());
            int level = Math.min(MonsterScaling.MAX_LEVEL,
                    Monsters.byId(lord.template()).orElseThrow().levels().max() + FieldThreats.LORD_LEVEL_BONUS);
            assertEquals(lord.id() + " asks for its lord's level", level, design.level());
            assertTrue(design.sources().contains(DesignSource.LORD));
        }
        for (DungeonDefinition dungeon : Dungeons.all()) {
            checkDungeon(dungeon.id(), dungeon.areaId(), dungeon.recommendedLevel());
        }
        checkDungeon(Dungeons.FINAL_RAID.id(), Dungeons.FINAL_RAID.areaId(), Dungeons.FINAL_RAID.recommendedLevel());
        assertTrue(NamedTrinkets.forLord("nobody").isEmpty());
        assertTrue(NamedTrinkets.forDungeon("nowhere").isEmpty());
    }

    private static void checkDungeon(String id, int area, int level) {
        List<TrinketDesign> designs = NamedTrinkets.forDungeon(id);
        assertEquals(id, 2, designs.size());
        assertEquals(EquipmentSlot.ACCESSORY, designs.get(0).slot());
        assertEquals(EquipmentSlot.CHARM, designs.get(1).slot());
        for (TrinketDesign design : designs) {
            assertEquals(id, design.origin());
            assertEquals(area, design.field());
            assertEquals(level, design.level());
            assertTrue(design.sources().contains(DesignSource.DUNGEON));
        }
    }

    @Test
    public void twoStatsByPercentagesAndAnEffectThatGrows() {
        TrinketDesign garm = NamedTrinkets.forLord("garm").orElseThrow();
        assertEquals(TrinketKind.MEDAL, garm.kind());
        double base = Trinkets.accessoryPercent(17);
        assertEquals(Math.round(1.1 * base * 1000.0) / 1000.0, garm.percents().get(StatType.STR), EPS);
        assertEquals(Math.round(0.4 * base * 1000.0) / 1000.0, garm.percents().get(StatType.AGI), EPS);
        assertEquals(NamedTrinkets.Effect.LIFESTEAL, NamedTrinkets.effectOf(garm).orElseThrow());
        assertTrue(garm.perks().get(0) instanceof com.eternalrealm.job.PassiveEffect.Lifesteal);
        TrinketDesign mirror = NamedTrinkets.forDungeon("final").get(1);
        assertEquals(TrinketKind.MIRROR, mirror.kind());
        assertEquals(Math.round(1.1 * Trinkets.charmPercent(50) * 1000.0) / 1000.0,
                mirror.percents().get(StatType.VIT), EPS);
        // worn together with others they count like every trinket; twice the same counts once
        java.util.UUID wearer = java.util.UUID.nameUUIDFromBytes("named".getBytes(StandardCharsets.UTF_8));
        EquipmentData a = EquipmentGenerator.roll(garm, ItemRarity.RARE, new SplittableRandom(1));
        EquipmentData b = EquipmentGenerator.roll(garm, ItemRarity.RARE, new SplittableRandom(2))
                .withUniqueId(java.util.UUID.randomUUID());
        assertEquals(Gear.sum(List.of(a), 99, wearer), Gear.sum(List.of(a, b), 99, wearer));
        assertTrue(Gear.perks(List.of(a), 99, wearer).lifesteal() > 0.0);
        // the tooltip names the lord and shows the percentages
        List<TooltipLine> lines = EquipmentTooltip.lines(a);
        assertTrue(lines.stream().anyMatch(line -> line.translationKey().equals(EquipmentTooltip.FROM_LORD)));
        assertTrue(lines.stream().anyMatch(line -> line.translationKey().equals(EquipmentTooltip.TRINKET_STATS)));
        assertTrue(EquipmentTooltip.lines(EquipmentGenerator.roll(mirror, ItemRarity.COMMON, new SplittableRandom(3)))
                .stream().anyMatch(line -> line.translationKey().equals(EquipmentTooltip.FROM_DUNGEON)));
    }

    @Test
    public void lordsDungeonsAndTheSmithyHandThemOut() {
        assertEquals(0.06, NamedTrinkets.lordDropChance(false), EPS);
        assertEquals(0.15, NamedTrinkets.lordDropChance(true), EPS);
        assertEquals(0.12, NamedTrinkets.Difficulty.of("normal").chance(), EPS);
        assertEquals(0.20, NamedTrinkets.Difficulty.of("HARD").chance(), EPS);
        assertEquals(0.30, NamedTrinkets.Difficulty.of("EX").chance(), EPS);
        SplittableRandom random = new SplittableRandom(4);
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < 2000; i++) {
            seen.merge(NamedTrinkets.dungeonDrop("d3", random).orElseThrow().id(), 1, Integer::sum);
        }
        assertEquals(2, seen.size());
        assertTrue(NamedTrinkets.dungeonDrop("nowhere", random).isEmpty());
        assertEquals(48, Recipes.namedTrinketRecipes().size());
        int byField = 0;
        for (int field = 1; field <= 10; field++) {
            byField += Recipes.namedTrinketRecipes(field).size();
        }
        assertEquals(48, byField);
        for (TrinketDesign design : NamedTrinkets.all()) {
            Recipe recipe = Recipes.forDesign(design).orElseThrow(() -> new AssertionError("no recipe " + design.id()));
            assertEquals(design.level(), recipe.requiredLevel());
            String soul = Materials.ofField(design.field(), Materials.Grade.BOSS).get(0);
            assertEquals(design.id(), 1, (int) recipe.ingredients().get(soul));
            double expected = 0.0;
            for (ItemRarity rarity : ItemRarity.values()) {
                expected += EquipmentGenerator.rarityChance(rarity, EquipmentGenerator.MAX_LUCK_BONUS)
                        * rarity.priceMultiplier();
            }
            assertTrue(design.id(), recipe.goldCost()
                    > ItemPrices.equipmentSellValue(design.tier(), ItemRarity.COMMON, 0) * expected);
        }
        assertEquals(0.15, Trinkets.REWARD_SHARE, EPS);
    }

    @Test
    public void everyNamedTrinketIsNamedAndDrawn() throws IOException {
        Map<String, String> ja = RelicCatalogTest.lang("ja_jp");
        Map<String, String> en = RelicCatalogTest.lang("en_us");
        Set<String> jaNames = new HashSet<>();
        Set<String> enNames = new HashSet<>();
        Map<String, String> models = new HashMap<>();
        for (TrinketDesign design : NamedTrinkets.all()) {
            for (String key : List.of(design.translationKey(), design.loreKey(), design.kind().translationKey(),
                    design.sources().contains(DesignSource.LORD) ? "threat.eternalrealm.lord." + design.origin()
                            : "dungeon.eternalrealm." + design.origin())) {
                assertTrue("ja " + key, ja.containsKey(key));
                assertTrue("en " + key, en.containsKey(key));
            }
            assertTrue(ja.get(design.translationKey()), jaNames.add(ja.get(design.translationKey())));
            assertTrue(en.get(design.translationKey()), enNames.add(en.get(design.translationKey())));
            for (String path : List.of("textures/item/trinket/" + design.id() + ".png",
                    "models/item/trinket/" + design.id() + ".json")) {
                assertTrue("missing " + path, Files.isRegularFile(ASSETS.resolve(path)));
            }
            String item = design.base().itemPath();
            String model = models.computeIfAbsent(item, path -> {
                try {
                    return Files.readString(ASSETS.resolve("models/item/" + path + ".json"), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            });
            assertTrue(item + " lacks the override of " + design.id(),
                    model.contains("\"custom_model_data\": " + design.model()));
        }
        // names of their own: no other design shares one
        for (Design design : Armory.designs()) {
            assertFalse(design.id(), jaNames.contains(ja.get(design.translationKey())));
        }
        for (Design design : Relics.designs()) {
            assertFalse(design.id(), jaNames.contains(ja.get(design.translationKey())));
        }
    }
}
