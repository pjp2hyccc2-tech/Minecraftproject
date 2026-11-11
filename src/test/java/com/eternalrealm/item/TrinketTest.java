package com.eternalrealm.item;

import com.eternalrealm.character.StatType;
import com.eternalrealm.job.PassiveTotals;
import org.junit.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The trinkets (装身具): two accessories and one charm are worn next to the armor and add their stats; the same named
 * trinket counts once; their stats grow with rarity and enhancement; their bases exist only for the named trinkets.
 */
public class TrinketTest {
    private static final double EPS = 1.0e-9;
    private static final UUID WEARER = UUID.nameUUIDFromBytes("trinket".getBytes());

    private static EquipmentData piece(TrinketDesign design, ItemRarity rarity) {
        return EquipmentGenerator.roll(design, rarity, new SplittableRandom(design.model()));
    }

    @Test
    public void twoAccessoriesAndACharmAddTheirStats() {
        TrinketDesign ring = Relics.source("b1").orElseThrow().accessory();
        TrinketDesign amulet = Relics.source("b3").orElseThrow().accessory();
        TrinketDesign pouch = Relics.source("b1").orElseThrow().charm();
        EquipmentData a = piece(ring, ItemRarity.LEGENDARY);
        EquipmentData b = piece(amulet, ItemRarity.LEGENDARY);
        EquipmentData c = piece(pouch, ItemRarity.LEGENDARY);
        GearBonus sum = Gear.sum(List.of(a, b, c), 99, WEARER);
        for (StatType type : StatType.values()) {
            int expected = EquipmentStats.of(a).stats().get(type) + EquipmentStats.of(b).stats().get(type)
                    + EquipmentStats.of(c).stats().get(type);
            assertEquals(type.toString(), expected, sum.stats().get(type));
        }
        assertTrue(sum.stats().get(StatType.AGI) >= ring.stats().get(StatType.AGI));
        assertTrue(sum.modifiers().maxHealthFlat() > 0 && sum.modifiers().defenseFlat() > 0);
        // the charm's perk counts (×1.30 for legendary)
        PassiveTotals perks = Gear.perks(List.of(a, b, c), 99, WEARER);
        assertEquals(((com.eternalrealm.job.PassiveEffect.Evasion) pouch.perks().get(0)).perLevel() * 1.30,
                perks.evasion(), EPS);
        // too low a level: nothing
        assertEquals(GearBonus.NONE, Gear.sum(List.of(a, b, c), 1, WEARER));
        // the same trinket twice counts once
        EquipmentData twin = piece(ring, ItemRarity.LEGENDARY).withUniqueId(UUID.randomUUID());
        assertEquals(Gear.sum(List.of(a), 99, WEARER), Gear.sum(List.of(a, twin), 99, WEARER));
        // two accessories fit, three do not; one charm
        assertThrows(IllegalArgumentException.class, () -> Gear.sum(List.of(a, b, twin), 99, WEARER));
        assertThrows(IllegalArgumentException.class, () -> Gear.sum(List.of(c, c), 99, WEARER));
    }

    @Test
    public void trinketStatsGrowWithRarityAndEnhancement() {
        TrinketDesign ring = Relics.source("final").orElseThrow().accessory();
        EquipmentData common = EquipmentData.of(ring, ItemRarity.COMMON, List.of());
        EquipmentData legendary = piece(ring, ItemRarity.LEGENDARY).withAffixes(
                List.of(new Affix(AffixKind.STR, 1), new Affix(AffixKind.VIT, 1), new Affix(AffixKind.AGI, 1),
                        new Affix(AffixKind.DEX, 1)));
        for (StatType type : StatType.values()) {
            int base = ring.stats().get(type);
            assertEquals(type.toString(), base, EquipmentStats.of(common).stats().get(type));
            if (base > 0) {
                assertTrue(type.toString(), EquipmentStats.of(legendary).stats().get(type)
                        >= Math.round(base * 1.30));
            }
        }
        EquipmentData enhanced = common.withEnhancement(10);
        StatType main = StatType.LUK;
        assertEquals(EquipmentStats.scaledStat(ring.stats().get(main), 1.6),
                EquipmentStats.of(enhanced).stats().get(main));
        assertEquals(ring.modifiers().critDamage() * 1.6,
                EquipmentStats.designModifiers(enhanced).critDamage(), EPS);
        assertEquals(0.0, EquipmentStats.baseDefense(common), 0.0);
        assertEquals(0.0, EquipmentStats.baseAttack(common), 0.0);
    }

    @Test
    public void trinketTooltipShowsItsStatsAndTheGoldHeader() {
        TrinketDesign charm = Relics.source("l_garm").orElseThrow().charm();
        List<TooltipLine> lines = EquipmentTooltip.lines(piece(charm, ItemRarity.LEGENDARY));
        assertTrue(lines.stream().anyMatch(line -> line.translationKey().equals(EquipmentTooltip.RELIC_TYPE)
                && line.color() == TooltipLine.COLOR_RELIC));
        assertTrue(lines.stream().anyMatch(line -> line.translationKey().equals(EquipmentTooltip.FROM_RELIC)));
        assertTrue(lines.stream().anyMatch(line -> line.translationKey().equals(EquipmentTooltip.TRINKET_STATS)));
        assertTrue(lines.stream().anyMatch(line -> line.kind() == TooltipLine.Kind.PERK));
        assertTrue(lines.stream().noneMatch(line -> line.translationKey().equals(EquipmentTooltip.DEFENSE)));
        assertEquals(TooltipLine.COLOR_RELIC, EquipmentTooltip.name(piece(charm, ItemRarity.LEGENDARY)).color());
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
}
