package com.eternalrealm.item;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatPercents;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.ComposedStats;
import com.eternalrealm.job.JobType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How far the stat percentages push a build (owner's request of 2026-11-13: "工夫しだいでSTRを4000位まで"). A perfect
 * level-100 STR knight — every point in STR, legendary +10 gear with perfect rolls, four different STR accessories and
 * two STR charms of the best grades — lands near STR 4 000; the same flat stats without percentages stay at 485.
 */
public class StatGrowthTest {
    private static final UUID WEARER = UUID.nameUUIDFromBytes("growth".getBytes());

    @Test
    public void percentagesMultiplyEveryFlatSource() {
        StatPercents percents = StatPercents.of(StatType.STR, 0.5).plus(StatPercents.of(StatType.STR, 0.25));
        assertEquals(0.75, percents.get(StatType.STR), 1.0e-12);
        StatBlock flat = StatBlock.ZERO.with(StatType.STR, 100).with(StatType.VIT, 40);
        StatBlock total = percents.applyTo(flat);
        assertEquals(175, total.get(StatType.STR));
        assertEquals(40, total.get(StatType.VIT));
        assertEquals(75, percents.bonusOn(flat).get(StatType.STR));
        // rounding down, never below the flat value, never above the stat cap
        assertEquals(10, StatPercents.apply(10, 0.05));
        assertEquals(StatBlock.MAX_VALUE, StatPercents.apply(9_000, 0.5));
        assertEquals(StatPercents.ZERO, StatPercents.ZERO.scaled(3.0));
        assertEquals(0.3, StatPercents.of(StatType.INT, 0.1).scaled(3.0).get(StatType.INT), 1.0e-12);
        // composed: own + job + gear flat first, then the percentages
        StatBlock own = StatBlock.uniform(5).with(StatType.STR, 50);
        ComposedStats composed = ComposedStats.compose(own, 11, JobType.KNIGHT, StatBlock.ZERO.with(StatType.STR, 10),
                StatPercents.of(StatType.STR, 1.0), CombatModifiers.NONE, CombatModifiers.NONE);
        assertEquals(140, composed.total().get(StatType.STR)); // (50 + 10 job + 10 gear) × 2
        assertEquals(70, composed.percentBonus().get(StatType.STR));
        assertEquals(10 + 10 + 70, composed.bonus().get(StatType.STR));
        assertEquals(composed.total(), own.plus(composed.bonus()));
    }

    @Test
    public void aPerfectStrBuildReachesAboutFourThousand() {
        StatBlock own = StatBlock.uniform(5).with(StatType.STR, 5 + 99 * 3);
        List<EquipmentData> gear = perfectStrGear();
        GearBonus bonus = Gear.sum(gear, 100, WEARER);
        ComposedStats composed = ComposedStats.compose(own, 100, JobType.KNIGHT, bonus.stats(), bonus.percents(),
                bonus.modifiers(), CombatModifiers.NONE);
        int flat = own.get(StatType.STR) + composed.jobBonus().get(StatType.STR) + bonus.stats().get(StatType.STR);
        assertEquals("own 302 + knight 99 + four flat STR affixes", 485, flat);
        int str = composed.total().get(StatType.STR);
        assertTrue("STR " + str + " with +" + Math.round(bonus.percents().get(StatType.STR) * 100) + "%",
                str >= 3_600 && str <= 4_800);
        assertTrue(bonus.percents().get(StatType.STR) > 6.5);
        // a second copy of the best bracelet instead of a different one counts once: the variety is the trick
        List<EquipmentData> lazy = new ArrayList<>(gear);
        lazy.set(8, gear.get(5).withUniqueId(UUID.randomUUID()));
        GearBonus lazyBonus = Gear.sum(lazy, 100, WEARER);
        assertTrue(lazyBonus.percents().get(StatType.STR) < bonus.percents().get(StatType.STR));
    }

    @Test
    public void aMidGameBuildStaysFarBelowTheTop() {
        // level 55, epic +5 trinkets of the field grades, no percent affixes
        StatBlock own = StatBlock.uniform(5).with(StatType.STR, 5 + 54 * 3);
        List<EquipmentData> gear = new ArrayList<>();
        for (Trinkets.Grade grade : List.of(Trinkets.Grade.CHAMPION, Trinkets.Grade.HEROIC, Trinkets.Grade.ELITE,
                Trinkets.Grade.VALIANT)) {
            gear.add(EquipmentData.of(Trinkets.of(grade, Trinkets.Focus.STR, false), ItemRarity.EPIC,
                    List.of(new Affix(AffixKind.CRIT_CHANCE, 0.05), new Affix(AffixKind.CRIT_DAMAGE, 0.2),
                            new Affix(AffixKind.LUK_PERCENT, 0.08))).withEnhancement(5));
        }
        GearBonus bonus = Gear.sum(gear, 55, WEARER);
        assertEquals(4, Gear.usable(gear, 55, WEARER).size());
        ComposedStats composed = ComposedStats.compose(own, 55, JobType.KNIGHT, bonus.stats(), bonus.percents(),
                bonus.modifiers(), CombatModifiers.NONE);
        int str = composed.total().get(StatType.STR);
        assertTrue("mid game STR " + str, str > 300 && str < 1_000);
    }

    /** Weapon, head, chest, legs, feet, four accessories, two charms: legendary, +10, perfect rolls. */
    static List<EquipmentData> perfectStrGear() {
        ItemTier abyss = ItemTier.ABYSS;
        double str = AffixKind.STR.max(abyss);
        double strPercent = AffixKind.STR_PERCENT.max(abyss);
        List<EquipmentData> gear = new ArrayList<>();
        gear.add(piece(EquipmentBase.weapon(WeaponType.ONE_HANDED_SWORD, abyss), new Affix(AffixKind.STR, str),
                new Affix(AffixKind.PHYSICAL_PERCENT, 0.14), new Affix(AffixKind.CRIT_CHANCE, 0.07),
                new Affix(AffixKind.CRIT_DAMAGE, 0.3)));
        gear.add(piece(EquipmentBase.armor(EquipmentSlot.HEAD, abyss), new Affix(AffixKind.STR, str),
                new Affix(AffixKind.STR_PERCENT, strPercent), new Affix(AffixKind.DEFENSE, 18),
                new Affix(AffixKind.MAX_HEALTH, 12)));
        gear.add(piece(EquipmentBase.armor(EquipmentSlot.CHEST, abyss), new Affix(AffixKind.STR, str),
                new Affix(AffixKind.STR_PERCENT, strPercent), new Affix(AffixKind.DEFENSE, 18),
                new Affix(AffixKind.VIT, 16)));
        gear.add(piece(EquipmentBase.armor(EquipmentSlot.LEGS, abyss), new Affix(AffixKind.STR, str),
                new Affix(AffixKind.STR_PERCENT, strPercent), new Affix(AffixKind.DEFENSE, 18),
                new Affix(AffixKind.MAX_HEALTH, 12)));
        gear.add(piece(EquipmentBase.armor(EquipmentSlot.FEET, abyss), new Affix(AffixKind.STR_PERCENT, strPercent),
                new Affix(AffixKind.DEFENSE, 18), new Affix(AffixKind.MAX_HEALTH, 12), new Affix(AffixKind.AGI, 16)));
        for (Trinkets.Grade grade : List.of(Trinkets.Grade.ETERNAL, Trinkets.Grade.AEON, Trinkets.Grade.DIVINE,
                Trinkets.Grade.CELESTIAL)) {
            gear.add(trinket(Trinkets.of(grade, Trinkets.Focus.STR, false), new Affix(AffixKind.STR_PERCENT, strPercent),
                    new Affix(AffixKind.CRIT_CHANCE, 0.07), new Affix(AffixKind.CRIT_DAMAGE, 0.3),
                    new Affix(AffixKind.VIT_PERCENT, strPercent)));
        }
        for (Trinkets.Grade grade : List.of(Trinkets.Grade.ETERNAL, Trinkets.Grade.AEON)) {
            gear.add(trinket(Trinkets.of(grade, Trinkets.Focus.STR, true), new Affix(AffixKind.STR_PERCENT, strPercent),
                    new Affix(AffixKind.VIT_PERCENT, strPercent), new Affix(AffixKind.MAX_HEALTH, 12),
                    new Affix(AffixKind.DEFENSE, 18)));
        }
        return gear;
    }

    private static EquipmentData piece(EquipmentBase base, Affix... affixes) {
        return EquipmentData.of(base, ItemRarity.LEGENDARY, List.of(affixes)).withEnhancement(10);
    }

    private static EquipmentData trinket(TrinketDesign design, Affix... affixes) {
        return EquipmentData.of(design, ItemRarity.LEGENDARY, List.of(affixes)).withEnhancement(10);
    }
}
