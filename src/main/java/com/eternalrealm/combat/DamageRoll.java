package com.eternalrealm.combat;

/** Damage of one hit on one target, before the target's own defenses. */
public record DamageRoll(double amount, boolean critical) {
    public DamageRoll {
        if (!(amount >= 0.0) || amount > CombatRules.MAX_DAMAGE) {
            throw new IllegalArgumentException("amount out of range: " + amount);
        }
    }
}
