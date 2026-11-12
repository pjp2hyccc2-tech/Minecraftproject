package com.eternalrealm.item;

import com.eternalrealm.character.StatPercents;
import com.eternalrealm.character.StatType;
import com.eternalrealm.job.PassiveTotals;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The trinkets (装身具): four accessories and two charms are worn next to the armor and add their stat percentages; the
 * same named trinket counts once; their percentages grow with rarity and (faster) with enhancement; their bases exist
 * only for the named trinkets.
 */
public class TrinketTest {
    private static final double EPS = 1.0e-9;
    private static final UUID WEARER = UUID.nameUUIDFromBytes("trinket".getBytes());

    private static EquipmentData piece(TrinketDesign design, ItemRarity rarity) {
        return EquipmentGenerator.roll(design, rarity, new SplittableRandom(design.model()));
    }

    @Test
    public void fourAccessoriesAndTwoCharmsAddTheirPercentages() {
        TrinketDesign ring = Relics.source("b1").orElseThrow().accessory();
        TrinketDesign amulet = Relics.source("b3").orElseThrow().accessory();
        TrinketDesign bracelet = Trinkets.of(Trinkets.Grade.WARRIOR, Trinkets.Focus.STR, false);
        TrinketDesign earring = Trinkets.of(Trinkets.Grade.WARRIOR, Trinkets.Focus.AGI, false);
        TrinketDesign pouch = Relics.source("b1").orElseThrow().charm();
        TrinketDesign stone = Trinkets.of(Trinkets.Grade.WARRIOR, Trinkets.Focus.STR, true);
        List<EquipmentData> worn = new ArrayList<>();
        for (TrinketDesign design : List.of(ring, amulet, bracelet, earring, pouch, stone)) {
            worn.add(piece(design, ItemRarity.LEGENDARY));
        }
        GearBonus sum = Gear.sum(worn, 99, WEARER);
        StatPercents expected = StatPercents.ZERO;
        for (EquipmentData piece : worn) {
            expected = expected.plus(EquipmentStats.of(piece).percents());
        }
        for (StatType type : StatType.values()) {
            assertEquals(type.toString(), expected.get(type), sum.percents().get(type), EPS);
        }
        assertTrue(sum.percents().get(StatType.STR) > 0.0 && sum.percents().get(StatType.AGI) > 0.0);
        // trinkets add no flat primary stats any more (only legacy affixes did)
        assertEquals(0L, Gear.sum(List.of(worn.get(2)), 99, WEARER).stats().total());
        assertTrue(sum.modifiers().maxHealthFlat() > 0 && sum.modifiers().defenseFlat() > 0);
        // the relic charm's perk counts (×1.30 for legendary)
        PassiveTotals perks = Gear.perks(worn, 99, WEARER);
        assertEquals(((com.eternalrealm.job.PassiveEffect.Evasion) pouch.perks().get(0)).perLevel() * 1.30,
                perks.evasion(), EPS);
        // too low a level: nothing
        assertEquals(GearBonus.NONE, Gear.sum(worn, 1, WEARER));
        // the same trinket twice counts once
        EquipmentData a = worn.get(0);
        EquipmentData twin = piece(ring, ItemRarity.LEGENDARY).withUniqueId(UUID.randomUUID());
        assertEquals(Gear.sum(List.of(a), 99, WEARER), Gear.sum(List.of(a, twin), 99, WEARER));
        // four accessories fit, five do not; two charms fit, three do not
        assertEquals(4, EquipmentSlot.ACCESSORY.capacity());
        assertEquals(2, EquipmentSlot.CHARM.capacity());
        List<EquipmentData> five = new ArrayList<>(worn.subList(0, 4));
        five.add(twin);
        assertThrows(IllegalArgumentException.class, () -> Gear.sum(five, 99, WEARER));
        EquipmentData third = piece(Trinkets.of(Trinkets.Grade.NOVICE, Trinkets.Focus.LUK, true), ItemRarity.COMMON);
        assertThrows(IllegalArgumentException.class,
                () -> Gear.sum(List.of(worn.get(4), worn.get(5), third), 99, WEARER));
    }

    @Test
    public void trinketPercentagesGrowWithRarityAndFasterWithEnhancement() {
        TrinketDesign ring = Relics.source("final").orElseThrow().accessory();
        EquipmentData common = EquipmentData.of(ring, ItemRarity.COMMON, List.of());
        for (StatType type : StatType.values()) {
            assertEquals(type.toString(), ring.percents().get(type), EquipmentStats.of(common).percents().get(type),
                    EPS);
        }
        EquipmentData legendary = EquipmentData.of(ring, ItemRarity.LEGENDARY,
                List.of(new Affix(AffixKind.CRIT_CHANCE, 0.04), new Affix(AffixKind.CRIT_DAMAGE, 0.2),
                        new Affix(AffixKind.VIT_PERCENT, 0.1), new Affix(AffixKind.DEX_PERCENT, 0.1)));
        StatType main = StatType.LUK;
        assertEquals(EquipmentStats.snapPercent(ring.percents().get(main) * 1.30),
                EquipmentStats.of(legendary).percents().get(main), EPS);
        // the percent affix adds to the design's own percentage of the same stat
        assertEquals(EquipmentStats.snapPercent(ring.percents().get(StatType.VIT) * 1.30) + 0.13,
                EquipmentStats.of(legendary).percents().get(StatType.VIT), EPS);
        // +10: percentages ×2.0, the other bonuses ×1.6
        EquipmentData enhanced = common.withEnhancement(10);
        assertEquals(EquipmentStats.snapPercent(ring.percents().get(main) * 2.0),
                EquipmentStats.of(enhanced).percents().get(main), EPS);
        assertEquals(ring.modifiers().critDamage() * 1.6,
                EquipmentStats.designModifiers(enhanced).critDamage(), EPS);
        assertEquals(2.0, EquipmentStats.percentFactor(10), EPS);
        assertEquals(1.6, EquipmentStats.enhancementFactor(10), EPS);
        assertEquals(0.0, EquipmentStats.baseDefense(common), 0.0);
        assertEquals(0.0, EquipmentStats.baseAttack(common), 0.0);
    }

    @Test
    public void trinketTooltipShowsItsPercentagesAndTheGoldHeader() {
        TrinketDesign charm = Relics.source("l_garm").orElseThrow().charm();
        List<TooltipLine> lines = EquipmentTooltip.lines(piece(charm, ItemRarity.LEGENDARY));
        assertTrue(lines.stream().anyMatch(line -> line.translationKey().equals(EquipmentTooltip.RELIC_TYPE)
                && line.color() == TooltipLine.COLOR_RELIC));
        assertTrue(lines.stream().anyMatch(line -> line.translationKey().equals(EquipmentTooltip.FROM_RELIC)));
        TooltipLine stats = lines.stream().filter(line -> line.translationKey().equals(EquipmentTooltip.TRINKET_STATS))
                .findFirst().orElseThrow();
        assertTrue(stats.toString().contains(EquipmentTooltip.STAT_PERCENT));
        assertTrue(lines.stream().anyMatch(line -> line.kind() == TooltipLine.Kind.PERK));
        assertTrue(lines.stream().noneMatch(line -> line.translationKey().equals(EquipmentTooltip.DEFENSE)));
        assertEquals(TooltipLine.COLOR_RELIC, EquipmentTooltip.name(piece(charm, ItemRarity.LEGENDARY)).color());
        // an enhanced piece with percentages says both how its base values and its percentages grew
        EquipmentData enhanced = piece(charm, ItemRarity.LEGENDARY).withEnhancement(5);
        TooltipLine enhancement = EquipmentTooltip.lines(enhanced).stream()
                .filter(line -> line.kind() == TooltipLine.Kind.ENHANCEMENT).findFirst().orElseThrow();
        assertEquals(EquipmentTooltip.ENHANCEMENT_PERCENT, enhancement.translationKey());
        assertTrue(enhancement.toString().contains("50"));
    }

    @Test
    public void trinketBasesOnlyCarryNamedTrinkets() {
        assertEquals(70, EquipmentBase.all().size());
        assertEquals(14, EquipmentBase.trinkets().size());
        assertEquals(84, EquipmentBase.everything().size());
        assertTrue(EquipmentBase.all().stream().noneMatch(EquipmentBase::isTrinket));
        assertTrue(EquipmentBase.byItemPath("abyss_accessory").orElseThrow().isTrinket());
        assertEquals(EquipmentSlot.CHARM, EquipmentBase.byItemPath("iron_charm").orElseThrow().slot());
        assertFalse(Recipes.forOutput(EquipmentBase.trinket(EquipmentSlot.ACCESSORY, ItemTier.IRON)).isPresent());
        for (EquipmentSlot slot : EquipmentSlot.trinkets()) {
            assertTrue(AffixPool.of(slot, null).kinds().size() >= ItemRarity.MAX_AFFIXES);
        }
        for (TrinketKind kind : TrinketKind.values()) {
            assertTrue(kind.slot().isTrinket());
            assertEquals(kind, TrinketKind.byId(kind.id()).orElseThrow());
        }
    }

    @Test
    public void olderTrinketsWithFlatStatsStayValidButNeverRollThemAgain() {
        AffixPool accessory = AffixPool.of(EquipmentSlot.ACCESSORY, null);
        AffixPool charm = AffixPool.of(EquipmentSlot.CHARM, null);
        for (AffixKind kind : List.of(AffixKind.STR, AffixKind.INT, AffixKind.DEX, AffixKind.AGI, AffixKind.VIT,
                AffixKind.LUK)) {
            assertTrue(kind + " stays valid", accessory.allows(kind) && accessory.isLegacy(kind));
            assertFalse(kind + " never rolls", accessory.rolls(kind));
            assertEquals(0, accessory.weight(kind));
            assertTrue(accessory.rolls(AffixKind.percentOf(kind.stat().orElseThrow())));
        }
        assertTrue(charm.allows(AffixKind.VIT) && !charm.rolls(AffixKind.VIT));
        // an accessory rolled before keeps its flat STR, which still counts
        TrinketDesign ring = Relics.source("b1").orElseThrow().accessory();
        EquipmentData old = EquipmentData.of(ring, ItemRarity.UNCOMMON, List.of(new Affix(AffixKind.STR, 3)));
        assertEquals(EquipmentStats.scaledStat(3, ItemRarity.UNCOMMON.power()),
                EquipmentStats.of(old).stats().get(StatType.STR));
        EquipmentData decoded = assertDecodes(EquipmentCodec.encode(old));
        assertEquals(old, decoded);
        // rerolls and new drops only ever give percentages and the critical bonuses
        SplittableRandom random = new SplittableRandom(5);
        for (int i = 0; i < 300; i++) {
            for (Affix affix : Reroll.reroll(piece(ring, ItemRarity.LEGENDARY), random).affixes()) {
                assertFalse(affix.toString(), affix.kind().isPrimaryStat());
            }
        }
    }

    private static EquipmentData assertDecodes(String json) {
        try {
            return EquipmentCodec.decode(json);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
