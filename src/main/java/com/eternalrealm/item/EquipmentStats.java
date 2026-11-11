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
 *
 * named designs ({@link Armory}) use their own level instead of the tier, and their profile:
 * weapon attack      = weapon type base attack × (1 + {@link Armory#weaponBonusAt}(level)) × design attack × rarity × enh.
 * armor defense      = {@link Armory#setDefenseAt}(level) × slot share × style × set × rarity × enhancement factor
 * armor max health   = {@link Armory#setHealthAt}(level)  × slot share × style × set × rarity × enhancement factor
 * modifiers          + the weapon's crit, attack speed and fixed bonuses / the armor piece's bonus
 * </pre>
 *
 * Enhancement scales only these base values (and a trinket's fixed stats), never the affixes, so enhancing (強化) and
 * rerolling (厳選) are independent ways to improve a piece. The rarity's {@link ItemRarity#power()} multiplies the
 * special performance: every affix value (primary stats rounded), a named design's fixed bonuses (a weapon's critical
 * chance, attack speed and bonuses, an armor piece's bonus) and a trinket's stats and bonuses.
 *
 * <pre>
 * trinket stats      = design stats     × rarity power × enhancement factor (primary stats rounded)
 * trinket modifiers  = design modifiers × rarity power × enhancement factor
 * </pre>
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
        double power = data.rarity().power();
        Map<StatType, Integer> stats = new EnumMap<>(StatType.class);
        CombatModifiers.Builder modifiers = CombatModifiers.builder()
                .defense(baseDefense(data))
                .healthFlat(baseMaxHealth(data));
        for (Affix affix : data.affixes()) {
            affix.kind().stat().ifPresent(stat -> stats.merge(stat, scaledStat(affix.intValue(), power),
                    Integer::sum));
            affix.kind().addTo(modifiers, affixValue(affix, data.rarity()));
        }
        data.trinketDesign().ifPresent(trinket -> {
            double factor = power * enhancementFactor(data.enhancement());
            for (StatType type : StatType.values()) {
                int value = trinket.stats().get(type);
                if (value > 0) {
                    stats.merge(type, scaledStat(value, factor), Integer::sum);
                }
            }
        });
        CombatModifiers total = modifiers.build().plus(designModifiers(data));
        double bonus = data.isWeapon() ? Math.max(0.0, baseAttack(data) - weaponTypeAttack(data)) : 0.0;
        return new EquipmentStats(StatBlock.of(stats), total, Math.min(DamageCalculator.MAX_BONUS_ATTACK, bonus));
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
        double base = data.weaponDesign()
                .map(design -> (1.0 + Armory.weaponBonusAt(design.level())) * design.attack())
                .orElse(1.0 + data.tier().weaponAttackBonus());
        return weaponTypeAttack(data) * base * data.rarity().baseMultiplier() * enhancementFactor(data.enhancement());
    }

    /** Defense of an armor piece before affixes; 0 for weapons. */
    public static double baseDefense(EquipmentData data) {
        double base = data.armorPiece()
                .map(piece -> Armory.setDefenseAt(piece.level()) * piece.armorSlot().armorShare()
                        * piece.set().style().defense() * piece.set().defense())
                .orElse(data.tier().armorDefense(data.slot()));
        return base * data.rarity().baseMultiplier() * enhancementFactor(data.enhancement());
    }

    /** Max health of an armor piece before affixes; 0 for weapons. */
    public static double baseMaxHealth(EquipmentData data) {
        double base = data.armorPiece()
                .map(piece -> Armory.setHealthAt(piece.level()) * piece.armorSlot().armorShare()
                        * piece.set().style().health() * piece.set().health())
                .orElse(data.tier().armorMaxHealth(data.slot()));
        return base * data.rarity().baseMultiplier() * enhancementFactor(data.enhancement());
    }

    /**
     * What a named design adds on its own: a weapon's critical chance, attack speed and fixed bonuses, an armor piece's
     * bonus (set bonuses are {@link Gear}'s, they depend on the other pieces); nothing for plain pieces.
     */
    public static CombatModifiers designModifiers(EquipmentData data) {
        double power = data.rarity().power();
        if (data.design() instanceof WeaponDesign weapon) {
            return weapon.bonus().plus(CombatModifiers.builder().crit(weapon.crit()).attackSpeed(weapon.speed())
                    .build()).scaled(power);
        }
        if (data.design() instanceof ArmorPiece piece) {
            return piece.set().piece().scaled(power);
        }
        if (data.design() instanceof TrinketDesign trinket) {
            return trinket.modifiers().scaled(power * enhancementFactor(data.enhancement()));
        }
        return CombatModifiers.NONE;
    }

    /** An affix's value as it counts (and is shown): the rolled value × the rarity's power, on the kind's grid. */
    public static double affixValue(Affix affix, ItemRarity rarity) {
        if (affix.kind().isPrimaryStat()) {
            return scaledStat(affix.intValue(), rarity.power());
        }
        return affix.kind().snap(affix.value() * rarity.power());
    }

    /** A primary stat × a factor, rounded half up (never below the value itself). */
    public static int scaledStat(int value, double factor) {
        return Math.max(value, (int) Math.floor(value * factor + 0.5 + 1.0e-9));
    }

    private static double weaponTypeAttack(EquipmentData data) {
        return WeaponProfiles.of(data.weaponType()).baseAttack();
    }
}
