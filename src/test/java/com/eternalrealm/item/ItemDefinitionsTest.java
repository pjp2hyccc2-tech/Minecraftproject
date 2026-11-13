package com.eternalrealm.item;

import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.WeaponType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ItemDefinitionsTest {
    private static final double EPS = 1.0e-9;

    @Test
    public void slotsHaveStableIdsAndArmorSharesMakeOneSet() {
        assertEquals(List.of("weapon", "head", "chest", "legs", "feet", "accessory", "charm"),
                List.of(EquipmentSlot.values()).stream().map(EquipmentSlot::id).toList());
        assertEquals(List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET),
                EquipmentSlot.armor());
        assertEquals(List.of(EquipmentSlot.ACCESSORY, EquipmentSlot.CHARM), EquipmentSlot.trinkets());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            assertEquals(slot.toString(), slot.isTrinket(), EquipmentSlot.trinkets().contains(slot));
            assertEquals(slot.toString(), slot.isArmor(), EquipmentSlot.armor().contains(slot));
            assertEquals(slot == EquipmentSlot.ACCESSORY ? 4 : slot == EquipmentSlot.CHARM ? 2 : 1, slot.capacity());
        }
        double total = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            assertEquals(slot, EquipmentSlot.byId(slot.id()).orElseThrow());
            assertEquals("equipment_slot.eternalrealm." + slot.id(), slot.translationKey());
            total += slot.armorShare();
        }
        assertEquals(1.0, total, EPS);
        assertFalse(EquipmentSlot.WEAPON.isArmor());
        assertEquals(0.0, EquipmentSlot.WEAPON.armorShare(), 0.0);
        assertTrue(EquipmentSlot.CHEST.armorShare() > EquipmentSlot.LEGS.armorShare());
        assertTrue(EquipmentSlot.LEGS.armorShare() > EquipmentSlot.HEAD.armorShare());
        assertTrue(EquipmentSlot.HEAD.armorShare() > EquipmentSlot.FEET.armorShare());
        assertTrue(EquipmentSlot.FEET.armorShare() > 0);
        assertTrue(EquipmentSlot.byId("hand").isEmpty());
    }

    @Test
    public void tiersFollowTheAreas() {
        assertEquals(List.of("iron", "steel", "sandstone", "frost", "flame", "celestial", "abyss"),
                List.of(ItemTier.values()).stream().map(ItemTier::id).toList());
        assertEquals(List.of(1, 8, 15, 22, 30, 37, 44),
                List.of(ItemTier.values()).stream().map(ItemTier::requiredLevel).toList());
        assertEquals(List.of("leather", "chainmail", "iron", "golden", "diamond", "netherite", "netherite"),
                List.of(ItemTier.values()).stream().map(ItemTier::armorMaterial).toList());
        assertEquals(ItemTier.COUNT, ItemTier.values().length);
        assertEquals(0.0, ItemTier.IRON.weaponAttackBonus(), 0.0);
        ItemTier previous = null;
        for (ItemTier tier : ItemTier.values()) {
            assertEquals(tier.ordinal() + 1, tier.number());
            assertEquals(tier.number(), tier.area());
            assertEquals(tier, ItemTier.byId(tier.id()).orElseThrow());
            assertEquals(tier, ItemTier.byNumber(tier.number()).orElseThrow());
            assertEquals("item_tier.eternalrealm." + tier.id(), tier.translationKey());
            assertEquals(tier == ItemTier.ABYSS, tier.glint());
            assertTrue(tier.armorDefense(EquipmentSlot.CHEST) > tier.armorDefense(EquipmentSlot.LEGS));
            assertTrue(tier.armorDefense(EquipmentSlot.LEGS) > tier.armorDefense(EquipmentSlot.HEAD));
            assertTrue(tier.armorDefense(EquipmentSlot.HEAD) > tier.armorDefense(EquipmentSlot.FEET));
            assertTrue(tier.armorMaxHealth(EquipmentSlot.HEAD) > tier.armorMaxHealth(EquipmentSlot.FEET));
            assertEquals(0.0, tier.armorDefense(EquipmentSlot.WEAPON), 0.0);
            double setDefense = 0;
            for (EquipmentSlot slot : EquipmentSlot.armor()) {
                setDefense += tier.armorDefense(slot);
            }
            assertEquals(tier.setDefense(), setDefense, 1.0e-6);
            if (previous != null) {
                assertTrue(tier + " weapon bonus", tier.weaponAttackBonus() > previous.weaponAttackBonus());
                assertTrue(tier + " defense", tier.setDefense() > previous.setDefense());
                assertTrue(tier + " health", tier.setMaxHealth() > previous.setMaxHealth());
                assertEquals(tier, previous.next().orElseThrow());
            }
            previous = tier;
        }
        assertTrue(ItemTier.ABYSS.next().isEmpty());
        assertTrue(ItemTier.byNumber(0).isEmpty());
        assertTrue(ItemTier.byNumber(8).isEmpty());
        assertTrue(ItemTier.byId("gold").isEmpty());
    }

    @Test
    public void highestTierFollowsTheRequiredLevels() {
        assertEquals(ItemTier.IRON, ItemTier.highestFor(0));
        assertEquals(ItemTier.IRON, ItemTier.highestFor(7));
        assertEquals(ItemTier.STEEL, ItemTier.highestFor(8));
        assertEquals(ItemTier.FLAME, ItemTier.highestFor(36));
        assertEquals(ItemTier.ABYSS, ItemTier.highestFor(44));
        assertEquals(ItemTier.ABYSS, ItemTier.highestFor(100));
    }

    @Test
    public void raritiesFollowTheTable() {
        assertEquals(List.of(0, 1, 2, 3, 4),
                List.of(ItemRarity.values()).stream().map(ItemRarity::affixCount).toList());
        assertEquals(List.of(60, 25, 10, 4, 1),
                List.of(ItemRarity.values()).stream().map(ItemRarity::dropWeight).toList());
        assertEquals(1.0, ItemRarity.COMMON.baseMultiplier(), 0.0);
        assertEquals(1.0, ItemRarity.COMMON.priceMultiplier(), 0.0);
        assertEquals(0xFFFFFF, ItemRarity.COMMON.color());
        Set<Integer> colors = new HashSet<>();
        ItemRarity previous = null;
        for (ItemRarity rarity : ItemRarity.values()) {
            assertEquals(rarity, ItemRarity.byId(rarity.id()).orElseThrow());
            assertEquals(rarity, ItemRarity.fromName(rarity.name()).orElseThrow());
            assertEquals(rarity, ItemRarity.fromName(rarity.id()).orElseThrow());
            assertEquals("rarity.eternalrealm." + rarity.id(), rarity.translationKey());
            assertTrue(rarity.color() >= 0 && rarity.color() <= 0xFFFFFF);
            assertTrue("distinct colour", colors.add(rarity.color()));
            assertEquals(rarity.ordinal(), rarity.rank());
            if (previous != null) {
                assertTrue(rarity.baseMultiplier() > previous.baseMultiplier());
                assertTrue(rarity.priceMultiplier() > previous.priceMultiplier());
                assertTrue(rarity.dropWeight() < previous.dropWeight());
            }
            previous = rarity;
        }
        assertEquals(ItemRarity.MAX_AFFIXES, ItemRarity.LEGENDARY.affixCount());
        assertTrue(ItemRarity.fromName("MYTHIC").isEmpty());
        assertTrue(ItemRarity.fromName("").isEmpty());
        assertTrue(ItemRarity.fromName(null).isEmpty());
        assertTrue(ItemRarity.byId("RARE").isEmpty());
    }

    @Test
    public void affixRangesGrowWithTheTierAndStayOnTheGrid() {
        for (AffixKind kind : AffixKind.values()) {
            assertEquals(kind, AffixKind.byId(kind.id()).orElseThrow());
            assertEquals("affix.eternalrealm." + kind.id(), kind.translationKey());
            assertEquals(kind.isPrimaryStat(), kind.stat().isPresent());
            assertEquals(!kind.isPercent(), kind.step() == 1.0);
            ItemTier previous = null;
            for (ItemTier tier : ItemTier.values()) {
                double min = kind.min(tier);
                double max = kind.max(tier);
                assertTrue(kind + " " + tier, min > 0 && min <= max);
                assertTrue(kind.isOnGrid(min) && kind.isOnGrid(max));
                assertTrue(kind + " tolerance", kind.maxStored(tier) >= max && kind.maxStored(tier) <= kind.cap());
                assertTrue(kind.isOnGrid(kind.maxStored(tier)));
                if (previous != null) {
                    assertTrue(kind + " min grows", min >= kind.min(previous));
                    assertTrue(kind + " max grows", max >= kind.max(previous));
                }
                previous = tier;
            }
            assertTrue(kind + " tier 7 beats tier 1", kind.max(ItemTier.ABYSS) > kind.max(ItemTier.IRON));
        }
        assertEquals(StatType.STR, AffixKind.STR.stat().orElseThrow());
        assertEquals(StatType.LUK, AffixKind.LUK.stat().orElseThrow());
        assertEquals(1.0, AffixKind.STR.min(ItemTier.IRON), 0.0);
        assertEquals(3.0, AffixKind.STR.max(ItemTier.IRON), 0.0);
        assertEquals(10.0, AffixKind.STR.min(ItemTier.ABYSS), 0.0);
        assertEquals(16.0, AffixKind.STR.max(ItemTier.ABYSS), 0.0);
        assertEquals(24.0, AffixKind.STR.maxStored(ItemTier.ABYSS), 0.0);
        assertEquals(0.01, AffixKind.CRIT_CHANCE.min(ItemTier.IRON), 0.0);
        assertEquals(0.07, AffixKind.CRIT_CHANCE.max(ItemTier.ABYSS), 0.0);
        assertTrue(AffixKind.byId("STR").isEmpty());
    }

    @Test
    public void gridFormattingAndQuality() {
        assertTrue(AffixKind.CRIT_CHANCE.isOnGrid(0.075));
        assertFalse(AffixKind.CRIT_CHANCE.isOnGrid(0.0755));
        assertFalse(AffixKind.STR.isOnGrid(2.5));
        assertFalse(AffixKind.STR.isOnGrid(Double.NaN));
        assertEquals("7.5", AffixKind.CRIT_CHANCE.format(0.075));
        assertEquals("7", AffixKind.PHYSICAL_PERCENT.format(0.07));
        assertEquals("12", AffixKind.STR.format(12));
        assertEquals(0.0, AffixKind.STR.quality(ItemTier.ABYSS, 10), EPS);
        assertEquals(1.0, AffixKind.STR.quality(ItemTier.ABYSS, 16), EPS);
        assertEquals(0.5, AffixKind.STR.quality(ItemTier.ABYSS, 13), EPS);
        assertEquals(1.0, AffixKind.STR.quality(ItemTier.ABYSS, 20), EPS);
    }

    @Test
    public void rollsCoverExactlyTheTierRange() {
        SplittableRandom random = new SplittableRandom(7);
        for (AffixKind kind : List.of(AffixKind.STR, AffixKind.CRIT_DAMAGE, AffixKind.MOVE_SPEED)) {
            Set<Double> seen = new HashSet<>();
            for (int i = 0; i < 2000; i++) {
                double value = kind.roll(ItemTier.FROST, random);
                assertTrue(kind + " " + value, value >= kind.min(ItemTier.FROST) && value <= kind.max(ItemTier.FROST));
                assertTrue(kind.isOnGrid(value));
                seen.add(value);
            }
            assertTrue(kind + " minimum reachable", seen.contains(kind.min(ItemTier.FROST)));
            assertTrue(kind + " maximum reachable", seen.contains(kind.max(ItemTier.FROST)));
        }
    }

    @Test
    public void poolsFavourDamageOnWeaponsAndDefenseOnArmor() {
        Set<AffixKind> armorOnly = Set.of(AffixKind.DEFENSE, AffixKind.MAX_HEALTH, AffixKind.MOVE_SPEED);
        Set<AffixKind> weaponOnly = Set.of(AffixKind.CRIT_DAMAGE, AffixKind.PHYSICAL_PERCENT,
                AffixKind.RANGED_PERCENT, AffixKind.MAGIC_PERCENT);
        for (WeaponType type : WeaponType.values()) {
            AffixPool pool = AffixPool.of(EquipmentSlot.WEAPON, type);
            assertTrue(pool.kinds().size() >= ItemRarity.MAX_AFFIXES);
            for (AffixKind kind : armorOnly) {
                assertFalse(type + " " + kind, pool.allows(kind));
            }
            assertTrue(pool.allows(AffixKind.CRIT_CHANCE) && pool.allows(AffixKind.CRIT_DAMAGE));
        }
        for (EquipmentSlot slot : EquipmentSlot.armor()) {
            AffixPool pool = AffixPool.of(slot, null);
            assertTrue(pool.kinds().size() >= ItemRarity.MAX_AFFIXES);
            for (AffixKind kind : weaponOnly) {
                assertFalse(slot + " " + kind, pool.allows(kind));
            }
            assertTrue(pool.allows(AffixKind.DEFENSE) && pool.allows(AffixKind.MAX_HEALTH) && pool.allows(AffixKind.VIT));
            for (AffixKind kind : pool.kinds()) {
                assertTrue(slot + " favours defense over " + kind,
                        pool.weight(AffixKind.DEFENSE) >= pool.weight(kind) || kind == AffixKind.AGI
                                || kind == AffixKind.MOVE_SPEED);
            }
        }
        assertTrue(AffixPool.of(EquipmentSlot.FEET, null).allows(AffixKind.MOVE_SPEED));
        assertFalse(AffixPool.of(EquipmentSlot.CHEST, null).allows(AffixKind.MOVE_SPEED));

        assertHighest(AffixPool.of(EquipmentSlot.WEAPON, WeaponType.ONE_HANDED_SWORD), AffixKind.STR,
                AffixKind.PHYSICAL_PERCENT);
        assertHighest(AffixPool.of(EquipmentSlot.WEAPON, WeaponType.SPEAR), AffixKind.STR, AffixKind.PHYSICAL_PERCENT);
        assertHighest(AffixPool.of(EquipmentSlot.WEAPON, WeaponType.STAFF), AffixKind.INT, AffixKind.MAGIC_PERCENT);
        assertHighest(AffixPool.of(EquipmentSlot.WEAPON, WeaponType.BOW), AffixKind.DEX, AffixKind.RANGED_PERCENT);
        assertTrue("spellblade swords", AffixPool.of(EquipmentSlot.WEAPON, WeaponType.TWO_HANDED_SWORD)
                .allows(AffixKind.MAGIC_PERCENT));
        assertFalse(AffixPool.of(EquipmentSlot.WEAPON, WeaponType.DUAL_BLADES).allows(AffixKind.INT));
        assertTrue(AffixPool.of(EquipmentSlot.WEAPON, WeaponType.STAFF).allows(AffixKind.HEALING_PERCENT));
    }

    @Test
    public void armorRollsAreFairForEveryAttackStat() {
        Map<AffixKind, Double> expected = new EnumMap<>(AffixKind.class);
        for (EquipmentSlot slot : EquipmentSlot.armor()) {
            AffixPool pool = AffixPool.of(slot, null);
            for (AffixKind kind : List.of(AffixKind.STR, AffixKind.INT, AffixKind.DEX)) {
                expected.merge(kind, pool.chance(kind), Double::sum);
            }
        }
        double str = expected.get(AffixKind.STR);
        assertEquals(str, expected.get(AffixKind.INT), 0.02);
        assertEquals(str, expected.get(AffixKind.DEX), 0.02);
        // the stat percentages too, exactly: every armor piece rolls all six alike
        for (EquipmentSlot slot : EquipmentSlot.armor()) {
            AffixPool pool = AffixPool.of(slot, null);
            for (StatType stat : StatType.values()) {
                assertEquals(slot + " " + stat, 1, pool.weight(AffixKind.percentOf(stat)));
            }
        }
        // every primary stat but VIT sits flat on three armor pieces, VIT on all four: the same number of sources
        for (StatType stat : StatType.values()) {
            AffixKind flat = flatOf(stat);
            int pieces = 0;
            for (EquipmentSlot slot : EquipmentSlot.armor()) {
                pieces += AffixPool.of(slot, null).rolls(flat) ? 1 : 0;
            }
            assertEquals(stat.toString(), stat == StatType.VIT ? 4 : 3, pieces);
        }
        // weapons keep their flat attack stat and damage percentages
        for (WeaponType type : WeaponType.values()) {
            for (AffixKind kind : AffixKind.values()) {
                assertFalse(type + " " + kind, kind.isStatPercent() && AffixPool.of(EquipmentSlot.WEAPON, type)
                        .allows(kind));
            }
        }
    }

    @Test
    public void statPercentKindsRaiseTheirStat() {
        for (StatType stat : StatType.values()) {
            AffixKind kind = AffixKind.percentOf(stat);
            assertTrue(kind.isStatPercent() && kind.isPercent() && !kind.isPrimaryStat());
            assertEquals(stat, kind.percentStat().orElseThrow());
            assertTrue(kind.stat().isEmpty());
            assertEquals(stat.id() + "_percent", kind.id());
        }
        assertEquals(0.02, AffixKind.STR_PERCENT.min(ItemTier.IRON), 0.0);
        assertEquals(0.12, AffixKind.STR_PERCENT.max(ItemTier.ABYSS), 0.0);
        assertEquals("12", AffixKind.STR_PERCENT.format(0.12));
        assertFalse(AffixKind.STR.isStatPercent());
        assertTrue(AffixKind.STR.percentStat().isEmpty());
    }

    @Test
    public void poolsRejectInvalidPieces() {
        assertThrows(NullPointerException.class, () -> AffixPool.of(EquipmentSlot.WEAPON, null));
        assertThrows(IllegalArgumentException.class, () -> AffixPool.of(EquipmentSlot.HEAD, WeaponType.BOW));
        assertThrows(NullPointerException.class, () -> AffixPool.of(null, null));
        AffixPool pool = AffixPool.of(EquipmentSlot.HEAD, null);
        assertThrows(IllegalArgumentException.class, () -> pool.roll(ItemTier.IRON, pool.kinds().size() + 1,
                new SplittableRandom(1)));
        assertThrows(IllegalArgumentException.class, () -> pool.roll(ItemTier.IRON, -1, new SplittableRandom(1)));
        assertEquals(0, pool.weight(AffixKind.MAGIC_PERCENT));
        assertEquals(0.0, pool.chance(AffixKind.MAGIC_PERCENT), 0.0);
    }

    @Test
    public void weightedDrawsFollowTheScript() {
        AffixPool pool = AffixPool.of(EquipmentSlot.FEET, null);
        // FEET in enum order: VIT 1, AGI 3, DEX 1, LUK 1, DEFENSE 2, MAX_HEALTH 2, MOVE_SPEED 3, then the six stat
        // percentages 1 each (total 19)
        assertEquals(19, pool.totalWeight());
        // draw 0 → VIT (value index 0), then draw 11 of the remaining 18 → MOVE_SPEED (value index 0)
        List<Affix> affixes = pool.roll(ItemTier.IRON, 2, ScriptedRandom.ofInts(0, 0, 11, 0));
        assertEquals(List.of(new Affix(AffixKind.VIT, 1), new Affix(AffixKind.MOVE_SPEED, 0.02)), affixes);
        // draw 1 → AGI (weight 3 covers 1..3)
        assertEquals(AffixKind.AGI, pool.roll(ItemTier.IRON, 1, ScriptedRandom.ofInts(1, 0)).get(0).kind());
    }

    @Test
    public void basesNameEveryPieceOnce() {
        List<EquipmentBase> all = EquipmentBase.all();
        assertEquals(70, all.size());
        Set<String> paths = new HashSet<>();
        for (EquipmentBase base : all) {
            assertTrue(base.itemPath(), paths.add(base.itemPath()));
            assertTrue(base.itemPath().matches("[a-z_]+"));
            assertEquals("eternalrealm:" + base.itemPath(), base.itemId());
            assertEquals("equipment_type.eternalrealm." + base.typeId(), base.nounKey());
            assertEquals(base, EquipmentBase.byItemPath(base.itemPath()).orElseThrow());
            assertEquals(base.tier().requiredLevel(), base.requiredLevel());
        }
        // the Phase 2 weapons are the tier-1 bases
        for (String phase2 : List.of("iron_longsword", "iron_greatsword", "iron_twin_blades", "iron_spear")) {
            EquipmentBase base = EquipmentBase.byItemPath(phase2).orElseThrow();
            assertEquals(ItemTier.IRON, base.tier());
            assertTrue(base.isWeapon());
        }
        assertEquals("abyss_boots", EquipmentBase.armor(EquipmentSlot.FEET, ItemTier.ABYSS).itemPath());
        assertTrue(EquipmentBase.byItemPath("gold_sword").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new EquipmentBase(EquipmentSlot.WEAPON, null, ItemTier.IRON));
        assertThrows(IllegalArgumentException.class,
                () -> new EquipmentBase(EquipmentSlot.CHEST, WeaponType.BOW, ItemTier.IRON));
        assertThrows(IllegalArgumentException.class, () -> EquipmentBase.armor(EquipmentSlot.WEAPON, ItemTier.IRON));
        assertThrows(NullPointerException.class, () -> new EquipmentBase(EquipmentSlot.HEAD, null, null));
    }

    @Test
    public void fieldMaterialsGiveEveryFieldItsOwnSet() {
        List<Materials.FieldMaterial> field = Materials.fieldMaterials();
        assertEquals(50, field.size());
        for (int f = 1; f <= 10; f++) {
            assertEquals("field " + f, 2, Materials.ofField(f, Materials.Grade.COMMON).size());
            assertEquals(1, Materials.ofField(f, Materials.Grade.UNCOMMON).size());
            assertEquals(1, Materials.ofField(f, Materials.Grade.ELITE).size());
            assertEquals(1, Materials.ofField(f, Materials.Grade.BOSS).size());
        }
        for (Materials.FieldMaterial material : field) {
            assertEquals(material.field(), Materials.areaOf(material.id()).orElseThrow());
            assertTrue(Materials.isKnown(material.id()));
            assertEquals(material, Materials.fieldMaterial(material.id()).orElseThrow());
        }
        assertEquals("eternalrealm:moonleaf", Materials.ofField(2, Materials.Grade.COMMON).get(0));
        assertEquals("eternalrealm:tide_soul", Materials.ofField(10, Materials.Grade.BOSS).get(0));
        assertEquals(2, Materials.tierOfField(8));
        assertEquals(4, Materials.tierOfField(10));
        assertEquals(7, Materials.tierOfField(7));
        // prices rise with the field's tier and the grade
        long common = ItemPrices.materialSellValue("eternalrealm:meadow_fiber").orElseThrow();
        long elite = ItemPrices.materialSellValue("eternalrealm:alpha_mane").orElseThrow();
        long boss = ItemPrices.materialSellValue("eternalrealm:verdant_soul").orElseThrow();
        long endBoss = ItemPrices.materialSellValue("eternalrealm:end_soul").orElseThrow();
        assertTrue(common < elite && elite < boss && boss < endBoss);
        assertTrue(ItemPrices.materialSellValue("eternalrealm:tide_soul").isPresent());
    }

    @Test
    public void materialsCoverEveryAreaOnce() {
        List<String> all = Materials.all();
        assertEquals(66, all.size());
        assertEquals(66, new HashSet<>(all).size());
        List<String> areaMaterials = new ArrayList<>();
        for (int area = 1; area <= ItemTier.COUNT; area++) {
            List<String> materials = Materials.forArea(area);
            assertEquals(2, materials.size());
            for (String id : materials) {
                assertEquals(area, Materials.areaOf(id).orElseThrow());
                assertTrue(id.startsWith("eternalrealm:"));
                assertTrue(Materials.isKnown(id));
                areaMaterials.add(id);
            }
        }
        assertEquals(List.of(Materials.SLIME_GEL, Materials.WOLF_FANG), Materials.forArea(1));
        assertEquals(List.of(Materials.VOID_ESSENCE, Materials.ABYSS_CRYSTAL), Materials.forArea(7));
        assertEquals("eternalrealm:desert_crystal", Materials.weaponMaterial(ItemTier.SANDSTONE));
        assertEquals("eternalrealm:scarab_shell", Materials.armorMaterial(ItemTier.SANDSTONE));
        assertTrue(Materials.areaOf(Materials.ENHANCEMENT_STONE).isEmpty());
        assertTrue(Materials.areaOf(Materials.REROLL_CRYSTAL).isEmpty());
        assertTrue(Materials.areaOf("minecraft:diamond").isEmpty());
        assertFalse(Materials.isKnown("eternalrealm:unknown"));
        assertFalse(areaMaterials.contains(Materials.ENHANCEMENT_STONE));
        assertEquals("item.eternalrealm.reroll_crystal", Materials.translationKey(Materials.REROLL_CRYSTAL));
        assertThrows(IllegalArgumentException.class, () -> Materials.forArea(0));
        assertThrows(IllegalArgumentException.class, () -> Materials.forArea(8));
        assertThrows(IllegalArgumentException.class, () -> Materials.translationKey("no namespace"));
    }

    private static AffixKind flatOf(StatType stat) {
        for (AffixKind kind : AffixKind.values()) {
            if (kind.stat().filter(stat::equals).isPresent()) {
                return kind;
            }
        }
        throw new AssertionError(stat);
    }

    private static void assertHighest(AffixPool pool, AffixKind... favourites) {
        int top = 0;
        for (AffixKind kind : pool.kinds()) {
            top = Math.max(top, pool.weight(kind));
        }
        for (AffixKind favourite : favourites) {
            assertEquals(pool + " favours " + favourite, top, pool.weight(favourite));
        }
    }
}
