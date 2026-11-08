package com.eternalrealm.combat;

/**
 * Damage left after the guard.
 *
 * @param amount remaining damage, {@code >= 0}
 */
public record GuardResult(double amount, Outcome outcome) {
    public enum Outcome {
        NONE,
        GUARDED,
        /** Stamina ran out: only part of the damage was blocked and the defender is staggered. */
        GUARD_BROKEN
    }

    public GuardResult {
        if (!(amount >= 0.0)) {
            throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }
    }
}
