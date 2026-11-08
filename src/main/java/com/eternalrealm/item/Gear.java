package com.eternalrealm.item;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sums the pieces a character wears. Pass the four armor pieces plus the main-hand weapon if it is equipment (its
 * affixes count while it is held); the weapon's {@link EquipmentStats#bonusAttack()} is not part of the sum because
 * it applies per hit ({@link #weaponBonusAttack}).
 */
public final class Gear {
    private Gear() {
    }

    /**
     * Sum of all {@code equipped} pieces.
     *
     * @throws IllegalArgumentException if two pieces share a slot (an inventory list passed by mistake would otherwise
     *                                  stack bonuses)
     */
    public static GearBonus sum(List<EquipmentData> equipped) {
        Objects.requireNonNull(equipped, "equipped");
        checkSlots(equipped);
        GearBonus total = GearBonus.NONE;
        for (EquipmentData piece : equipped) {
            total = total.plus(EquipmentStats.of(piece).gearBonus());
        }
        return total;
    }

    /**
     * Sum of the pieces {@code wearer} at {@code level} may use ({@link EquipmentData#canBeUsedBy}); others are
     * ignored, so gear above the level (e.g. after a season rule change) or bound to someone else gives nothing.
     */
    public static GearBonus sum(List<EquipmentData> equipped, int level, UUID wearer) {
        Objects.requireNonNull(equipped, "equipped");
        checkSlots(equipped);
        GearBonus total = GearBonus.NONE;
        for (EquipmentData piece : equipped) {
            if (piece.canBeUsedBy(level, wearer)) {
                total = total.plus(EquipmentStats.of(piece).gearBonus());
            }
        }
        return total;
    }

    /**
     * Bonus attack of the held piece for {@code DamageCalculator.roll(..., bonusAttack)}: 0 when nothing (or armor) is
     * held or the wearer may not use it.
     */
    public static double weaponBonusAttack(EquipmentData held, int level, UUID wearer) {
        if (held == null || !held.isWeapon() || !held.canBeUsedBy(level, wearer)) {
            return 0.0;
        }
        return EquipmentStats.of(held).bonusAttack();
    }

    private static void checkSlots(List<EquipmentData> equipped) {
        Set<EquipmentSlot> slots = EnumSet.noneOf(EquipmentSlot.class);
        for (EquipmentData piece : equipped) {
            Objects.requireNonNull(piece, "equipped piece");
            if (!slots.add(piece.slot())) {
                throw new IllegalArgumentException("Two pieces in slot " + piece.slot());
            }
        }
    }
}
