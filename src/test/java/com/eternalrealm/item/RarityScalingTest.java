package com.eternalrealm.item;

import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.PassiveTotals;
import org.junit.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The rarer a piece, the stronger its special performance and its weapon art (owner's request of 2026-11-11), and the
 * more levels it asks for: affix values, element chance, fixed bonuses and perks ×{@link ItemRarity#power()}, the art
 * ×(base multiplier × power), required level +{@link ItemRarity#levelOffset()}; relics keep their own level.
 */
public class RarityScalingTest {
    private static final double EPS = 1.0e-9;
    private static final UUID WEARER = UUID.nameUUIDFromBytes("rarity".getBytes());

    @Test
    public void rarerPiecesAreStrongerAndAskForMoreLevels() {
        assertEquals(1.0, ItemRarity.COMMON.power(), 0.0);
        assertEquals(0, ItemRarity.COMMON.levelOffset());
        assertEquals(1.30, ItemRarity.LEGENDARY.power(), EPS);
        assertEquals(5, ItemRarity.LEGENDARY.levelOffset());
        ItemRarity previous = null;
        for (ItemRarity rarity : ItemRarity.values()) {
            if (previous != null) {
                assertTrue(rarity + " power", rarity.power() > previous.power());
                assertTrue(rarity + " level", rarity.levelOffset() >= previous.levelOffset());
            }
            previous = rarity;
        }
        // tier pieces: the tier's level + the offset; named designs: the design's level + the offset
        int[] iron = {1, 1, 2, 4, 6};
        for (ItemRarity rarity : ItemRarity.values()) {
            EquipmentData piece = EquipmentGenerator.roll(EquipmentBase.armor(EquipmentSlot.HEAD, ItemTier.IRON),
                    rarity, new SplittableRandom(3));
            assertEquals(rarity.toString(), iron[rarity.ordinal()], piece.requiredLevel());
        }
        WeaponDesign king = Signatures.bossWeapon("final").orElseThrow();
        assertEquals(55, EquipmentGenerator.roll(king, ItemRarity.LEGENDARY, new SplittableRandom(1)).requiredLevel());
        assertEquals(50, EquipmentGenerator.roll(king, ItemRarity.COMMON, new SplittableRandom(1)).requiredLevel());
    }

    @Test
    public void rarityRaisesAffixesElementBonusesAndPerks() {
        // a lord's weapon: element, fixed bonuses and perks
        WeaponDesign garm = Signatures.lordWeapon("garm").orElseThrow();
        assertTrue(garm.proc() > 0 && !garm.perks().isEmpty());
        EquipmentData common = EquipmentData.of(garm, ItemRarity.COMMON, List.of());
        EquipmentData legendary = EquipmentGenerator.roll(garm, ItemRarity.LEGENDARY, new SplittableRandom(5));
        CombatModifiers low = EquipmentStats.designModifiers(common);
        CombatModifiers high = EquipmentStats.designModifiers(legendary);
        assertEquals(low.critChance() * 1.30, high.critChance(), EPS);
        assertEquals(low.attackSpeed() * 1.30, high.attackSpeed(), EPS);
        assertEquals(low.physicalBonus() * 1.30, high.physicalBonus(), EPS);
        assertEquals(garm.proc(), Gear.elementChance(garm, ItemRarity.COMMON), EPS);
        assertEquals(Math.round(garm.proc() * 1.30 * 1000.0) / 1000.0, Gear.elementChance(garm, ItemRarity.LEGENDARY),
                EPS);
        PassiveTotals plain = Gear.perks(List.of(common), garm.level(), WEARER);
        PassiveTotals rare = Gear.perks(List.of(legendary), 99, WEARER);
        assertEquals(plain.lifesteal() * 1.30, rare.lifesteal(), EPS);
        assertEquals(plain.lastStandDamage() * 1.30, rare.lastStandDamage(), EPS);
        assertEquals(plain.evasion() * 1.30, rare.evasion(), EPS);
        assertEquals(plain.healOnKill() * 1.30, rare.healOnKill(), EPS);
        assertTrue(rare.procs().get(0).chance() > plain.procs().get(0).chance());
        // affixes: the same roll counts ×power
        Affix str = new Affix(AffixKind.STR, 10);
        Affix crit = new Affix(AffixKind.CRIT_CHANCE, 0.05);
        assertEquals(10.0, EquipmentStats.affixValue(str, ItemRarity.COMMON), EPS);
        assertEquals(13.0, EquipmentStats.affixValue(str, ItemRarity.LEGENDARY), EPS);
        assertEquals(0.065, EquipmentStats.affixValue(crit, ItemRarity.LEGENDARY), EPS);
        assertEquals(0.057, EquipmentStats.affixValue(crit, ItemRarity.RARE), EPS);
    }

    @Test
    public void rarityRaisesTheArtAndBossArtsStayOnTop() {
        // what one use of the art deals, × the type skill, by rarity: the weapon's attack (base multiplier) × power
        for (ItemRarity rarity : ItemRarity.values()) {
            double artFactor = rarity.baseMultiplier() * rarity.power();
            if (rarity == ItemRarity.LEGENDARY) {
                assertTrue("legendary art ×" + artFactor, artFactor >= 1.5);
            }
            if (rarity != ItemRarity.COMMON) {
                ItemRarity lower = ItemRarity.values()[rarity.ordinal() - 1];
                assertTrue(rarity + " art", artFactor > lower.baseMultiplier() * lower.power());
            }
        }
        Map<WeaponType, Double> namedBest = new EnumMap<>(WeaponType.class);
        for (WeaponArt art : GeneratedArts.all()) {
            if (!GeneratedArts.isTierArt(art.id()) && !GeneratedArts.isRelicArt(art.id())) {
                namedBest.merge(art.type(), ArtBudget.burst(art) / ArtBudget.typeBurst(art.type()), Math::max);
            }
        }
        for (ItemRarity rarity : ItemRarity.values()) {
            for (WeaponArt boss : WeaponArts.all()) {
                double bossBurst = ArtBudget.burst(boss) / ArtBudget.typeBurst(boss.type()) * rarity.power();
                assertTrue(boss.id() + " at " + rarity, bossBurst > namedBest.get(boss.type()) * rarity.power());
            }
        }
        // the art line of the tooltip says how much the rarity adds
        EquipmentData legendary = EquipmentGenerator.roll(EquipmentBase.weapon(WeaponType.SPEAR, ItemTier.FROST),
                ItemRarity.LEGENDARY, new SplittableRandom(2));
        assertTrue(EquipmentTooltip.lines(legendary).stream()
                .anyMatch(line -> line.translationKey().equals(EquipmentTooltip.ART_POWER)));
        EquipmentData common = EquipmentGenerator.roll(EquipmentBase.weapon(WeaponType.SPEAR, ItemTier.FROST),
                ItemRarity.COMMON, new SplittableRandom(2));
        assertTrue(EquipmentTooltip.lines(common).stream()
                .noneMatch(line -> line.translationKey().equals(EquipmentTooltip.ART_POWER)));
    }
}
