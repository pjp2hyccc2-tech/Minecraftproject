package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EquipmentDataTest {
    static final UUID PLAYER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-000000000002");

    static EquipmentData rareSword() {
        return new EquipmentData(EquipmentSlot.WEAPON, WeaponType.ONE_HANDED_SWORD, ItemTier.FROST, ItemRarity.RARE, 0,
                List.of(new Affix(AffixKind.PHYSICAL_PERCENT, 0.09), new Affix(AffixKind.STR, 7)), null, null);
    }

    static EquipmentData armor(EquipmentSlot slot, ItemTier tier, ItemRarity rarity, Affix... affixes) {
        return new EquipmentData(slot, null, tier, rarity, 0, Arrays.asList(affixes), null, null);
    }

    @Test
    public void affixesAreSortedByKindSoEqualityIgnoresRollOrder() {
        EquipmentData data = rareSword();
        assertEquals(List.of(new Affix(AffixKind.STR, 7), new Affix(AffixKind.PHYSICAL_PERCENT, 0.09)), data.affixes());
        EquipmentData reordered = new EquipmentData(EquipmentSlot.WEAPON, WeaponType.ONE_HANDED_SWORD, ItemTier.FROST,
                ItemRarity.RARE, 0, List.of(new Affix(AffixKind.STR, 7), new Affix(AffixKind.PHYSICAL_PERCENT, 0.09)),
                null, null);
        assertEquals(data, reordered);
        assertEquals(data.hashCode(), reordered.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> data.affixes().add(new Affix(AffixKind.LUK, 1)));
        assertEquals(7.0, data.affix(AffixKind.STR).orElseThrow().value(), 0.0);
        assertTrue(data.affix(AffixKind.LUK).isEmpty());
    }

    @Test
    public void defensiveCopyOfTheAffixList() {
        List<Affix> source = new ArrayList<>(List.of(new Affix(AffixKind.VIT, 2)));
        EquipmentData data = armor(EquipmentSlot.HEAD, ItemTier.IRON, ItemRarity.UNCOMMON, source.get(0));
        EquipmentData copied = new EquipmentData(EquipmentSlot.HEAD, null, ItemTier.IRON, ItemRarity.UNCOMMON, 0,
                source, null, null);
        source.set(0, new Affix(AffixKind.VIT, 3));
        assertEquals(data, copied);
    }

    @Test
    public void weaponTypeMustMatchTheSlot() {
        assertThrows(IllegalArgumentException.class, () -> new EquipmentData(EquipmentSlot.WEAPON, null, ItemTier.IRON,
                ItemRarity.COMMON, 0, List.of(), null, null));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentData(EquipmentSlot.CHEST, WeaponType.SPEAR,
                ItemTier.IRON, ItemRarity.COMMON, 0, List.of(), null, null));
        assertThrows(NullPointerException.class, () -> new EquipmentData(null, null, ItemTier.IRON, ItemRarity.COMMON,
                0, List.of(), null, null));
        assertThrows(NullPointerException.class, () -> new EquipmentData(EquipmentSlot.HEAD, null, null,
                ItemRarity.COMMON, 0, List.of(), null, null));
        assertThrows(NullPointerException.class, () -> new EquipmentData(EquipmentSlot.HEAD, null, ItemTier.IRON, null,
                0, List.of(), null, null));
        assertThrows(NullPointerException.class, () -> new EquipmentData(EquipmentSlot.HEAD, null, ItemTier.IRON,
                ItemRarity.COMMON, 0, null, null, null));
    }

    @Test
    public void enhancementStaysWithinZeroToTen() {
        EquipmentData data = rareSword();
        assertEquals(10, data.withEnhancement(10).enhancement());
        assertThrows(IllegalArgumentException.class, () -> data.withEnhancement(11));
        assertThrows(IllegalArgumentException.class, () -> data.withEnhancement(-1));
    }

    @Test
    public void affixCountMustMatchTheRarity() {
        assertThrows(IllegalArgumentException.class, () -> armor(EquipmentSlot.HEAD, ItemTier.IRON, ItemRarity.RARE,
                new Affix(AffixKind.VIT, 2)));
        assertThrows(IllegalArgumentException.class, () -> armor(EquipmentSlot.HEAD, ItemTier.IRON, ItemRarity.COMMON,
                new Affix(AffixKind.VIT, 2)));
        assertEquals(0, armor(EquipmentSlot.HEAD, ItemTier.IRON, ItemRarity.COMMON).affixes().size());
    }

    @Test
    public void affixKindsMustBeDistinctAndAllowedOnThePiece() {
        assertThrows(IllegalArgumentException.class, () -> armor(EquipmentSlot.HEAD, ItemTier.IRON, ItemRarity.RARE,
                new Affix(AffixKind.VIT, 2), new Affix(AffixKind.VIT, 3)));
        // move speed only rolls on boots, physical damage only on weapons
        assertThrows(IllegalArgumentException.class, () -> armor(EquipmentSlot.CHEST, ItemTier.IRON,
                ItemRarity.UNCOMMON, new Affix(AffixKind.MOVE_SPEED, 0.02)));
        assertThrows(IllegalArgumentException.class, () -> armor(EquipmentSlot.LEGS, ItemTier.IRON,
                ItemRarity.UNCOMMON, new Affix(AffixKind.PHYSICAL_PERCENT, 0.02)));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentData(EquipmentSlot.WEAPON, WeaponType.BOW,
                ItemTier.IRON, ItemRarity.UNCOMMON, 0, List.of(new Affix(AffixKind.DEFENSE, 2)), null, null));
        assertThrows(NullPointerException.class, () -> armor(EquipmentSlot.HEAD, ItemTier.IRON, ItemRarity.UNCOMMON,
                (Affix) null));
    }

    @Test
    public void valuesAboveWhatTheTierCanRollAreRejected() {
        // tier 1 STR rolls 1..3; up to 1.5 × 3 = 4 is tolerated (later rebalancing), 5 is forged
        assertEquals(4.0, armor(EquipmentSlot.CHEST, ItemTier.IRON, ItemRarity.UNCOMMON, new Affix(AffixKind.STR, 4))
                .affixes().get(0).value(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> armor(EquipmentSlot.CHEST, ItemTier.IRON,
                ItemRarity.UNCOMMON, new Affix(AffixKind.STR, 5)));
        assertEquals(24.0, armor(EquipmentSlot.CHEST, ItemTier.ABYSS, ItemRarity.UNCOMMON,
                new Affix(AffixKind.STR, 24)).affixes().get(0).value(), 0.0);
    }

    @Test
    public void affixValuesArePositiveFiniteAndOnTheGrid() {
        assertThrows(IllegalArgumentException.class, () -> new Affix(AffixKind.STR, 0));
        assertThrows(IllegalArgumentException.class, () -> new Affix(AffixKind.STR, -3));
        assertThrows(IllegalArgumentException.class, () -> new Affix(AffixKind.STR, 2.5));
        assertThrows(IllegalArgumentException.class, () -> new Affix(AffixKind.CRIT_CHANCE, 0.0125));
        assertThrows(IllegalArgumentException.class, () -> new Affix(AffixKind.CRIT_CHANCE, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Affix(AffixKind.STR, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new Affix(AffixKind.CRIT_CHANCE, 1.001));
        assertThrows(NullPointerException.class, () -> new Affix(null, 1));
        // tiny floating point noise is snapped to the grid
        assertEquals(0.075, new Affix(AffixKind.CRIT_CHANCE, 0.07500000000000001).value(), 0.0);
        assertEquals(12, new Affix(AffixKind.STR, 12).intValue());
    }

    @Test
    public void bindingAndLevelDecideWhoMayUseIt() {
        EquipmentData free = rareSword();
        assertTrue(free.isTradeable());
        assertFalse(free.isBound());
        // a rare frost piece: the tier's level 22 plus one for the rarity
        assertEquals(23, free.requiredLevel());
        assertTrue(free.canBeUsedBy(23, PLAYER));
        assertFalse("level 23 needed", free.canBeUsedBy(22, PLAYER));

        EquipmentData bound = free.withBoundTo(PLAYER);
        assertTrue(bound.isBound());
        assertFalse(bound.isTradeable());
        assertTrue(bound.canBeUsedBy(50, PLAYER));
        assertFalse(bound.canBeUsedBy(50, OTHER));
        assertFalse(bound.canBeUsedBy(50, null));
        assertEquals(free, bound.withBoundTo(null));
    }

    @Test
    public void uniqueIdsAreStampedOnceAndSurviveChanges() {
        EquipmentData fresh = rareSword();
        assertNull(fresh.uniqueId());
        EquipmentData stamped = fresh.ensureUniqueId(() -> PLAYER);
        assertEquals(PLAYER, stamped.uniqueId());
        assertSame(stamped, stamped.ensureUniqueId(() -> OTHER));
        assertThrows(NullPointerException.class, () -> fresh.ensureUniqueId(() -> null));
        EquipmentData changed = stamped.withEnhancement(3).withBoundTo(OTHER)
                .withAffixes(List.of(new Affix(AffixKind.LUK, 5), new Affix(AffixKind.CRIT_DAMAGE, 0.12)));
        assertEquals(PLAYER, changed.uniqueId());
        assertEquals(3, changed.enhancement());
        assertEquals(OTHER, changed.boundTo());
        assertNotEquals(stamped, changed);
        assertEquals(ItemTier.FROST, changed.tier());
        assertEquals(ItemRarity.RARE, changed.rarity());
    }

    @Test
    public void baseAndFactory() {
        EquipmentBase base = EquipmentBase.armor(EquipmentSlot.LEGS, ItemTier.STEEL);
        EquipmentData data = EquipmentData.of(base, ItemRarity.UNCOMMON, List.of(new Affix(AffixKind.AGI, 2)));
        assertEquals(base, data.base());
        assertEquals(0, data.enhancement());
        assertNull(data.uniqueId());
        assertNull(data.boundTo());
        assertFalse(data.isWeapon());
        assertEquals(8, data.requiredLevel());
        assertTrue(rareSword().isWeapon());
    }
}
