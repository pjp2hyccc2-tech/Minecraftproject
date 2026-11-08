package com.eternalrealm.combat;

/**
 * Guard properties of a weapon.
 *
 * @param reduction           fraction of blocked damage, in {@code [0, 1]}
 * @param parryWindowTicks    an attack received within this many ticks after the guard started is parried
 * @param arcHalfAngleDegrees attacks from within this angle of the facing direction can be guarded
 * @param staminaPerDamage    stamina spent per point of damage blocked
 */
public record GuardProfile(double reduction, int parryWindowTicks, double arcHalfAngleDegrees, double staminaPerDamage) {
    public GuardProfile {
        Check.range("reduction", reduction, 0.0, 1.0);
        Check.range("parryWindowTicks", parryWindowTicks, 0, 40);
        Check.range("arcHalfAngleDegrees", arcHalfAngleDegrees, 1.0, 180.0);
        Check.range("staminaPerDamage", staminaPerDamage, 0.0, 100.0);
    }
}
