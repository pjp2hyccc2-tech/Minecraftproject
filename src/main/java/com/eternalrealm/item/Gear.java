package com.eternalrealm.item;

import com.eternalrealm.job.PassiveEffect;
import com.eternalrealm.job.PassiveTotals;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sums the pieces a character wears. Pass the four armor pieces plus the main-hand weapon if it is equipment (its
 * affixes count while it is held); the weapon's {@link EquipmentStats#bonusAttack()} is not part of the sum because
 * it applies per hit ({@link #weaponBonusAttack}).
 *
 * <p>Named armor sets ({@link ArmorSet}) add their two- and four-piece bonuses on top: their modifiers are part of the
 * sum, their perks (like the perks and the element of a named weapon) are resolved by the server when hits land
 * ({@link #perks}).</p>
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
        return total.plus(setModifiers(equipped));
    }

    /**
     * Sum of the pieces {@code wearer} at {@code level} may use ({@link EquipmentData#canBeUsedBy}); others are
     * ignored, so gear above the level (e.g. after a season rule change) or bound to someone else gives nothing.
     */
    public static GearBonus sum(List<EquipmentData> equipped, int level, UUID wearer) {
        Objects.requireNonNull(equipped, "equipped");
        checkSlots(equipped);
        GearBonus total = GearBonus.NONE;
        List<EquipmentData> usable = new ArrayList<>(equipped.size());
        for (EquipmentData piece : equipped) {
            if (piece.canBeUsedBy(level, wearer)) {
                total = total.plus(EquipmentStats.of(piece).gearBonus());
                usable.add(piece);
            }
        }
        return total.plus(setModifiers(usable));
    }

    /** How many pieces of each named set are among {@code equipped} (sets in order of their first piece). */
    public static Map<ArmorSet, Integer> setCounts(List<EquipmentData> equipped) {
        Map<ArmorSet, Integer> counts = new LinkedHashMap<>();
        for (EquipmentData piece : equipped) {
            piece.armorPiece().ifPresent(design -> counts.merge(design.set(), 1, Integer::sum));
        }
        return counts;
    }

    /** The active set bonuses of the pieces (two and four pieces of a set). */
    public static List<ArmorSet.SetBonus> setBonuses(List<EquipmentData> equipped) {
        List<ArmorSet.SetBonus> bonuses = new ArrayList<>();
        for (Map.Entry<ArmorSet, Integer> entry : setCounts(equipped).entrySet()) {
            bonuses.addAll(entry.getKey().active(entry.getValue()));
        }
        return bonuses;
    }

    /**
     * What the server resolves when hits land: the perks of the held named weapon and of the active set bonuses, and
     * the element of the weapon as an on-hit affliction. Only pieces the wearer may use count; never cooldowns or
     * stamina (the client predicts those).
     */
    public static PassiveTotals perks(List<EquipmentData> equipped, int level, UUID wearer) {
        Objects.requireNonNull(equipped, "equipped");
        checkSlots(equipped);
        List<EquipmentData> usable = new ArrayList<>(equipped.size());
        List<PassiveEffect> effects = new ArrayList<>();
        List<PassiveTotals.Proc> procs = new ArrayList<>();
        for (EquipmentData piece : equipped) {
            if (!piece.canBeUsedBy(level, wearer)) {
                continue;
            }
            usable.add(piece);
            piece.weaponDesign().ifPresent(weapon -> {
                effects.addAll(weapon.perks());
                weapon.element().affliction().ifPresent(kind -> procs.add(new PassiveTotals.Proc(kind, weapon.proc(),
                        weapon.element().ticks(), weapon.element().power(), false)));
            });
        }
        for (ArmorSet.SetBonus bonus : setBonuses(usable)) {
            effects.addAll(bonus.perks());
        }
        if (effects.isEmpty() && procs.isEmpty()) {
            return PassiveTotals.NONE;
        }
        PassiveTotals totals = PassiveTotals.ofEffects(effects);
        if (!procs.isEmpty()) {
            totals = totals.plus(new PassiveTotals(com.eternalrealm.combat.CombatModifiers.NONE, 0, 0, 0, 0, procs, 0,
                    0, 0, 0, 0));
        }
        return totals;
    }

    private static GearBonus setModifiers(List<EquipmentData> pieces) {
        GearBonus total = GearBonus.NONE;
        for (ArmorSet.SetBonus bonus : setBonuses(pieces)) {
            total = total.plus(new GearBonus(com.eternalrealm.character.StatBlock.ZERO, bonus.modifiers()));
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
