package com.eternalrealm.item;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.CombatRules;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.CombatTuning;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.ComposedStats;
import com.eternalrealm.job.JobType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EquipmentStatsTest {
    private static final double EPS = 1.0e-9;

    private static EquipmentData weapon(WeaponType type, ItemTier tier, ItemRarity rarity, int enhancement) {
        EquipmentData data = EquipmentGenerator.roll(EquipmentSlot.WEAPON, type, tier, rarity, new SplittableRandom(1));
        return data.withEnhancement(enhancement);
    }

    @Test
    public void ironCommonWeaponsAreThePhaseTwoWeapons() {
        for (WeaponType type : WeaponType.values()) {
            EquipmentData iron = weapon(type, ItemTier.IRON, ItemRarity.COMMON, 0);
            EquipmentStats stats = EquipmentStats.of(iron);
            assertEquals(type + " adds nothing", 0.0, stats.bonusAttack(), 0.0);
            assertEquals(WeaponProfiles.of(type).baseAttack(), EquipmentStats.baseAttack(iron), EPS);
            assertEquals(StatBlock.ZERO, stats.stats());
            assertEquals(CombatModifiers.NONE, stats.modifiers());
        }
    }

    @Test
    public void weaponAttackFollowsTierRarityAndEnhancement() {
        // spear base 7: abyss +60 %, legendary ×1.2, +10 → ×1.6
        EquipmentData spear = weapon(WeaponType.SPEAR, ItemTier.ABYSS, ItemRarity.LEGENDARY, 10);
        double expected = 7.0 * 1.6 * 1.2 * 1.6;
        assertEquals(expected, EquipmentStats.baseAttack(spear), EPS);
        assertEquals(expected - 7.0, EquipmentStats.of(spear).bonusAttack(), EPS);
        // the bonus is relative to the weapon type, so every family keeps its balance
        for (WeaponType type : WeaponType.values()) {
            EquipmentData piece = weapon(type, ItemTier.FROST, ItemRarity.RARE, 2);
            double base = WeaponProfiles.of(type).baseAttack();
            assertEquals(type.toString(), base * 1.3 * 1.1 * 1.12, EquipmentStats.baseAttack(piece), EPS);
        }
        assertEquals(1.0, EquipmentStats.enhancementFactor(0), 0.0);
        assertEquals(1.6, EquipmentStats.enhancementFactor(10), EPS);
        assertThrows(IllegalArgumentException.class, () -> EquipmentStats.enhancementFactor(11));
    }

    @Test
    public void armorBaseValuesScaleWithRarityAndEnhancement() {
        EquipmentData chest = EquipmentDataTest.armor(EquipmentSlot.CHEST, ItemTier.ABYSS, ItemRarity.COMMON);
        assertEquals(168.0 * 0.35, EquipmentStats.baseDefense(chest), EPS);
        assertEquals(64.0 * 0.35, EquipmentStats.baseMaxHealth(chest), EPS);
        EquipmentData enhanced = chest.withEnhancement(5);
        assertEquals(168.0 * 0.35 * 1.3, EquipmentStats.baseDefense(enhanced), EPS);
        EquipmentStats stats = EquipmentStats.of(enhanced);
        assertEquals(168.0 * 0.35 * 1.3, stats.modifiers().defenseFlat(), EPS);
        assertEquals(64.0 * 0.35 * 1.3, stats.modifiers().maxHealthFlat(), EPS);
        assertEquals(0.0, stats.bonusAttack(), 0.0);
        assertEquals(0.0, EquipmentStats.baseAttack(chest), 0.0);
        EquipmentData sword = weapon(WeaponType.ONE_HANDED_SWORD, ItemTier.ABYSS, ItemRarity.COMMON, 10);
        assertEquals(0.0, EquipmentStats.baseDefense(sword), 0.0);
        assertEquals(0.0, EquipmentStats.of(sword).modifiers().defenseFlat(), 0.0);
    }

    @Test
    public void affixesMapOntoStatsAndModifiers() {
        EquipmentData head = new EquipmentData(EquipmentSlot.HEAD, null, ItemTier.ABYSS, ItemRarity.LEGENDARY, 0,
                List.of(new Affix(AffixKind.INT, 12), new Affix(AffixKind.CRIT_CHANCE, 0.05),
                        new Affix(AffixKind.HEALING_PERCENT, 0.1), new Affix(AffixKind.DEFENSE, 15)), null, null);
        EquipmentStats stats = EquipmentStats.of(head);
        assertEquals(12, stats.stats().get(StatType.INT));
        assertEquals(12, stats.stats().total());
        CombatModifiers modifiers = stats.modifiers();
        assertEquals(0.05, modifiers.critChance(), EPS);
        assertEquals(0.1, modifiers.healingBonus(), EPS);
        assertEquals(168.0 * 0.2 * 1.2 + 15, modifiers.defenseFlat(), EPS);
        assertEquals(64.0 * 0.2 * 1.2, modifiers.maxHealthFlat(), EPS);
        assertEquals(1.0, modifiers.dodgeCostFactor(), 0.0);

        EquipmentData bow = new EquipmentData(EquipmentSlot.WEAPON, WeaponType.BOW, ItemTier.ABYSS, ItemRarity.EPIC, 0,
                List.of(new Affix(AffixKind.RANGED_PERCENT, 0.12), new Affix(AffixKind.CRIT_DAMAGE, 0.25),
                        new Affix(AffixKind.AGI, 9)), null, null);
        CombatModifiers bowModifiers = EquipmentStats.of(bow).modifiers();
        assertEquals(0.12, bowModifiers.rangedBonus(), EPS);
        assertEquals(0.25, bowModifiers.critDamage(), EPS);
        assertEquals(9, EquipmentStats.of(bow).stats().get(StatType.AGI));

        EquipmentData boots = new EquipmentData(EquipmentSlot.FEET, null, ItemTier.IRON, ItemRarity.UNCOMMON, 0,
                List.of(new Affix(AffixKind.MOVE_SPEED, 0.03)), null, null);
        assertEquals(0.03, EquipmentStats.of(boots).modifiers().moveSpeed(), EPS);

        for (AffixKind kind : List.of(AffixKind.PHYSICAL_PERCENT, AffixKind.MAGIC_PERCENT)) {
            EquipmentData sword = new EquipmentData(EquipmentSlot.WEAPON, WeaponType.ONE_HANDED_SWORD, ItemTier.IRON,
                    ItemRarity.UNCOMMON, 0, List.of(new Affix(kind, 0.03)), null, null);
            CombatModifiers m = EquipmentStats.of(sword).modifiers();
            assertEquals(kind.toString(), 0.03, kind == AffixKind.PHYSICAL_PERCENT ? m.physicalBonus() : m.magicBonus(),
                    EPS);
        }
    }

    @Test
    public void gearSumsOnePiecePerSlot() {
        EquipmentData head = EquipmentDataTest.armor(EquipmentSlot.HEAD, ItemTier.STEEL, ItemRarity.UNCOMMON,
                new Affix(AffixKind.VIT, 3));
        EquipmentData chest = EquipmentDataTest.armor(EquipmentSlot.CHEST, ItemTier.STEEL, ItemRarity.UNCOMMON,
                new Affix(AffixKind.VIT, 4));
        EquipmentData sword = EquipmentDataTest.rareSword();
        GearBonus sum = Gear.sum(List.of(head, chest, sword));
        assertEquals(7, sum.stats().get(StatType.VIT));
        assertEquals(7, sum.stats().get(StatType.STR));
        assertEquals(0.09, sum.modifiers().physicalBonus(), EPS);
        double defense = 30.0 * (0.2 + 0.35) * 1.05;
        assertEquals(defense, sum.modifiers().defenseFlat(), EPS);
        assertEquals(GearBonus.NONE, Gear.sum(List.of()));
        assertThrows("two chests", IllegalArgumentException.class, () -> Gear.sum(List.of(chest, chest)));
        assertThrows(NullPointerException.class, () -> Gear.sum(java.util.Arrays.asList(head, null)));
        // it is a plain sum
        assertEquals(EquipmentStats.of(head).gearBonus().plus(EquipmentStats.of(chest).gearBonus()),
                Gear.sum(List.of(head, chest)));
    }

    @Test
    public void unusablePiecesGiveNothing() {
        EquipmentData sword = EquipmentDataTest.rareSword(); // frost: level 22
        EquipmentData head = EquipmentDataTest.armor(EquipmentSlot.HEAD, ItemTier.IRON, ItemRarity.COMMON)
                .withBoundTo(EquipmentDataTest.OTHER);
        GearBonus atLevel21 = Gear.sum(List.of(sword, head), 21, EquipmentDataTest.PLAYER);
        assertEquals(GearBonus.NONE, atLevel21);
        GearBonus atLevel22 = Gear.sum(List.of(sword, head), 22, EquipmentDataTest.PLAYER);
        assertEquals(EquipmentStats.of(sword).gearBonus(), atLevel22);
        assertEquals(Gear.sum(List.of(sword, head)), Gear.sum(List.of(sword, head), 22, EquipmentDataTest.OTHER));
        assertThrows(IllegalArgumentException.class, () -> Gear.sum(List.of(sword, sword), 50, null));

        EquipmentData spear = weapon(WeaponType.SPEAR, ItemTier.ABYSS, ItemRarity.RARE, 0);
        double bonus = EquipmentStats.of(spear).bonusAttack();
        assertTrue(bonus > 0);
        assertEquals(bonus, Gear.weaponBonusAttack(spear, 44, EquipmentDataTest.PLAYER), 0.0);
        assertEquals(0.0, Gear.weaponBonusAttack(spear, 43, EquipmentDataTest.PLAYER), 0.0);
        assertEquals(0.0, Gear.weaponBonusAttack(spear.withBoundTo(EquipmentDataTest.OTHER), 50,
                EquipmentDataTest.PLAYER), 0.0);
        assertEquals(0.0, Gear.weaponBonusAttack(null, 50, EquipmentDataTest.PLAYER), 0.0);
        assertEquals(0.0, Gear.weaponBonusAttack(head, 50, EquipmentDataTest.OTHER), 0.0);
    }

    // ------------------------------------------------------------------------------------------ balance

    private record Build(JobType job, StatBlock own, WeaponType weapon, DamageType type) {
    }

    /** Level-50 characters with two thirds of their points in the attack stat (docs/modules/item.md). */
    private static List<Build> levelFiftyBuilds() {
        int main = 100;
        int second = 147 - main;
        StatBlock base = StatBlock.uniform(5);
        return List.of(
                new Build(JobType.KNIGHT, base.with(StatType.STR, 5 + main).with(StatType.VIT, 5 + second),
                        WeaponType.ONE_HANDED_SWORD, DamageType.PHYSICAL),
                new Build(JobType.ASSASSIN, base.with(StatType.STR, 5 + main).with(StatType.AGI, 5 + second),
                        WeaponType.DUAL_BLADES, DamageType.PHYSICAL),
                new Build(JobType.MAGE, base.with(StatType.INT, 5 + main).with(StatType.VIT, 5 + second),
                        WeaponType.STAFF, DamageType.MAGIC),
                new Build(JobType.RANGER, base.with(StatType.DEX, 5 + main).with(StatType.AGI, 5 + second),
                        WeaponType.BOW, DamageType.RANGED));
    }

    private static final int LEVEL = 50;
    private static final CombatTuning TUNING = CombatTuning.defaults();

    /** Expected damage of a multiplier-1 hit, critical hits included. */
    private static double expectedHit(Build build, GearBonus gear, double bonusAttack) {
        ComposedStats composed = ComposedStats.compose(build.own(), LEVEL, build.job(), gear.stats(), gear.modifiers());
        CombatStats stats = CombatStats.derive(composed.total(), LEVEL, TUNING, composed.modifiers());
        WeaponProfile profile = WeaponProfiles.of(build.weapon());
        double chance = Math.min(CombatRules.MAX_CRIT_CHANCE, stats.critChance() + profile.critBonus());
        return (profile.baseAttack() + bonusAttack) * build.type().multiplier(stats)
                * (1.0 + chance * (stats.critMultiplier() - 1.0));
    }

    /** Average over seeded full sets: {damage factor, gear defense, gear max health (flat, before job bonuses)}. */
    private static double[] averageFullSet(Build build, ItemTier tier, ItemRarity rarity, int samples) {
        SplittableRandom random = new SplittableRandom(99);
        double withoutGear = expectedHit(build, GearBonus.NONE, 0.0);
        double damage = 0;
        double defense = 0;
        double health = 0;
        for (int i = 0; i < samples; i++) {
            List<EquipmentData> set = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.armor()) {
                set.add(EquipmentGenerator.roll(slot, null, tier, rarity, random));
            }
            EquipmentData weapon = EquipmentGenerator.roll(EquipmentSlot.WEAPON, build.weapon(), tier, rarity, random);
            set.add(weapon);
            GearBonus gear = Gear.sum(set);
            damage += expectedHit(build, gear, EquipmentStats.of(weapon).bonusAttack()) / withoutGear;
            defense += gear.modifiers().defenseFlat() + gear.stats().get(StatType.VIT) * TUNING.defensePerVit();
            health += gear.modifiers().maxHealthFlat() + gear.stats().get(StatType.VIT) * TUNING.healthPerVit();
        }
        return new double[]{damage / samples, defense / samples, health / samples};
    }

    @Test
    public void fullRareTierSevenGearRoughlyDoublesALevelFiftyCharacter() {
        for (Build build : levelFiftyBuilds()) {
            double[] rare = averageFullSet(build, ItemTier.ABYSS, ItemRarity.RARE, 400);
            String name = build.job().toString();
            assertTrue(name + " damage ×" + rare[0], rare[0] >= 1.8 && rare[0] <= 2.25);
            assertTrue(name + " defense " + rare[1], rare[1] >= 150 && rare[1] <= 250);
            assertTrue(name + " max health " + rare[2], rare[2] >= 60 && rare[2] <= 120);

            double[] legendary = averageFullSet(build, ItemTier.ABYSS, ItemRarity.LEGENDARY, 400);
            assertTrue(name + " legendary clearly better ×" + legendary[0], legendary[0] >= rare[0] * 1.12);
            assertTrue(name + " legendary not game-breaking ×" + legendary[0], legendary[0] <= 3.0);
            assertTrue(name + " legendary defense " + legendary[1], legendary[1] > rare[1] && legendary[1] <= 300);
        }
    }

    @Test
    public void gearMattersMoreTierByTierButNeverDominatesEarly() {
        Build knight = levelFiftyBuilds().get(0);
        double previous = 1.0;
        for (ItemTier tier : ItemTier.values()) {
            double[] rare = averageFullSet(knight, tier, ItemRarity.RARE, 150);
            assertTrue(tier + " ×" + rare[0], rare[0] > previous);
            previous = rare[0];
        }
        double[] ironRare = averageFullSet(knight, ItemTier.IRON, ItemRarity.RARE, 150);
        assertTrue("tier 1 rare ×" + ironRare[0], ironRare[0] < 1.15);
        double[] abyssCommon = averageFullSet(knight, ItemTier.ABYSS, ItemRarity.COMMON, 10);
        assertEquals("common: weapon tier bonus only", 1.6, abyssCommon[0], 1.0e-6);
    }
}
