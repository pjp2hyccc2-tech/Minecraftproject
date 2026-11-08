package com.eternalrealm.combat;

import java.util.Objects;

/**
 * Damage formula (docs/COMBAT_SYSTEM.md, section 7):
 * {@code (weapon base attack + bonus attack) × type multiplier × frame multiplier × action scale}, critical with
 * {@code chance = stat chance + weapon bonus} (capped) for {@code × crit multiplier}. The type multiplier is the
 * attacker's STR (physical), DEX (ranged) or INT (magic/holy) scaling.
 */
public final class DamageCalculator {
    public static final double MAX_BONUS_ATTACK = 100_000.0;

    private DamageCalculator() {
    }

    /** The weapon's own damage type, no bonus attack (Phase 2 form). */
    public static DamageRoll roll(CombatStats attacker, WeaponProfile weapon, HitFrame frame, double damageScale,
                                  double random) {
        Objects.requireNonNull(weapon, "weapon");
        return roll(attacker, weapon, frame, damageScale, random, weapon.type().damageType(), 0.0);
    }

    /**
     * @param damageScale charge / derived / riposte / skill level scale of the action, {@code >= 0}
     * @param random      uniform random value in {@code [0, 1)} deciding the critical hit
     * @param type        what the hit scales with
     * @param bonusAttack attack added to the weapon's base attack (the tier and enhancement of the held item)
     */
    public static DamageRoll roll(CombatStats attacker, WeaponProfile weapon, HitFrame frame, double damageScale,
                                  double random, DamageType type, double bonusAttack) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(type, "type");
        Check.range("damageScale", damageScale, 0.0, 1000.0);
        Check.range("random", random, 0.0, 1.0);
        Check.range("bonusAttack", bonusAttack, 0.0, MAX_BONUS_ATTACK);
        double base = (weapon.baseAttack() + bonusAttack) * type.multiplier(attacker) * frame.multiplier()
                * damageScale;
        double chance = Math.min(CombatRules.MAX_CRIT_CHANCE, attacker.critChance() + weapon.critBonus());
        boolean critical = random < chance;
        double amount = critical ? base * attacker.critMultiplier() : base;
        return new DamageRoll(Math.min(CombatRules.MAX_DAMAGE, amount), critical);
    }

    /** Applies a damage reduction fraction (clamped to {@code [0, 0.95]}). */
    public static double reduce(double amount, double reduction) {
        double fraction = Math.max(0.0, Math.min(0.95, reduction));
        return Math.max(0.0, amount) * (1.0 - fraction);
    }

    /** Healing of a heal effect: {@code (fraction × target max health + flat) × healing multiplier}. */
    public static double healing(CombatStats healer, double targetMaxHealth, double fraction, double flat) {
        Objects.requireNonNull(healer, "healer");
        Check.range("targetMaxHealth", targetMaxHealth, 0.0, 1_000_000.0);
        Check.range("fraction", fraction, 0.0, 1.0);
        Check.range("flat", flat, 0.0, 100_000.0);
        return Math.min(CombatRules.MAX_DAMAGE, (targetMaxHealth * fraction + flat) * healer.healingMultiplier());
    }
}
