package com.eternalrealm.item;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.DamageCalculator;
import com.eternalrealm.combat.WeaponProfiles;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * What one piece contributes.
 *
 * <pre>
 * enhancement factor = 1 + 0.06 × enhancement
 * weapon attack      = weapon type base attack × (1 + tier weapon bonus) × rarity base multiplier × enhancement factor
 * bonus attack       = weapon attack − weapon type base attack          (passed to DamageCalculator per hit)
 * armor defense      = tier set defense × slot share × rarity base multiplier × enhancement factor
 * armor max health   = tier set health  × slot share × rarity base multiplier × enhancement factor
 * </pre>
 *
 * Enhancement and rarity scale only these base values, never the affixes, so enhancing (強化) and rerolling (厳選) are
 * independent ways to improve a piece.
 *
 * @param stats       primary stats from affixes
 * @param modifiers   armor defense / max health and the secondary affixes
 * @param bonusAttack weapons: attack added to the weapon type's base attack on every hit; 0 for armor
 */
public record EquipmentStats(StatBlock stats, CombatModifiers modifiers, double bonusAttack) {
    public static final double ENHANCEMENT_BONUS_PER_LEVEL = 0.06;

    public EquipmentStats {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(modifiers, "modifiers");
        Checks.range("bonusAttack", bonusAttack, 0.0, DamageCalculator.MAX_BONUS_ATTACK);
    }

    public static EquipmentStats of(EquipmentData data) {
        Objects.requireNonNull(data, "data");
        Map<StatType, Integer> stats = new EnumMap<>(StatType.class);
        CombatModifiers.Builder modifiers = CombatModifiers.builder()
                .defense(baseDefense(data))
                .healthFlat(baseMaxHealth(data));
        for (Affix affix : data.affixes()) {
            affix.kind().stat().ifPresent(stat -> stats.merge(stat, affix.intValue(), Integer::sum));
            affix.kind().addTo(modifiers, affix.value());
        }
        double bonus = data.isWeapon() ? Math.max(0.0, baseAttack(data) - weaponTypeAttack(data)) : 0.0;
        return new EquipmentStats(StatBlock.of(stats), modifiers.build(), bonus);
    }

    public GearBonus gearBonus() {
        return new GearBonus(stats, modifiers);
    }

    public static double enhancementFactor(int enhancement) {
        return 1.0 + ENHANCEMENT_BONUS_PER_LEVEL
                * Checks.range("enhancement", enhancement, 0, EquipmentData.MAX_ENHANCEMENT);
    }

    /**
     * Total attack of a weapon (the weapon type's base attack included), as the tooltip shows it; 0 for armor.
     * Damage = this × the attacker's multipliers.
     */
    public static double baseAttack(EquipmentData data) {
        if (!data.isWeapon()) {
            return 0.0;
        }
        return weaponTypeAttack(data) * (1.0 + data.tier().weaponAttackBonus()) * data.rarity().baseMultiplier()
                * enhancementFactor(data.enhancement());
    }

    /** Defense of an armor piece before affixes; 0 for weapons. */
    public static double baseDefense(EquipmentData data) {
        return data.tier().armorDefense(data.slot()) * data.rarity().baseMultiplier()
                * enhancementFactor(data.enhancement());
    }

    /** Max health of an armor piece before affixes; 0 for weapons. */
    public static double baseMaxHealth(EquipmentData data) {
        return data.tier().armorMaxHealth(data.slot()) * data.rarity().baseMultiplier()
                * enhancementFactor(data.enhancement());
    }

    private static double weaponTypeAttack(EquipmentData data) {
        return WeaponProfiles.of(data.weaponType()).baseAttack();
    }
}
