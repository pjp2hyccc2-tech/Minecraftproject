package com.eternalrealm.combat;

import java.util.Objects;

/**
 * Damage formula (docs/COMBAT_SYSTEM.md, section 7):
 * {@code base attack × attack multiplier × frame multiplier × action scale}, critical with
 * {@code chance = stat chance + weapon bonus} (capped) for {@code × crit multiplier}.
 */
public final class DamageCalculator {
    private DamageCalculator() {
    }

    /**
     * @param damageScale charge / derived / riposte scale of the action, {@code >= 0}
     * @param random      uniform random value in {@code [0, 1)} deciding the critical hit
     */
    public static DamageRoll roll(CombatStats attacker, WeaponProfile weapon, HitFrame frame, double damageScale,
                                  double random) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(frame, "frame");
        Check.range("damageScale", damageScale, 0.0, 1000.0);
        Check.range("random", random, 0.0, 1.0);
        double base = weapon.baseAttack() * attacker.attackMultiplier() * frame.multiplier() * damageScale;
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
}
