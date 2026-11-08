package com.eternalrealm.combat;

import java.util.Objects;

/**
 * Charged heavy attack. Charging starts on key press; the strike fires on release, but never before
 * {@code minChargeTicks}, and automatically at {@code maxChargeTicks}.
 *
 * @param minMultiplier damage scale at minimum charge (applied on top of the frame multipliers)
 * @param maxMultiplier damage scale at full charge
 * @param derivedBonus  added to the scale when the charge started during a light combo ("派生")
 * @param strike        the strike executed after charging; its stamina cost is the heavy attack's cost
 */
public record HeavyAttack(int minChargeTicks, int maxChargeTicks, double minMultiplier, double maxMultiplier,
                          double derivedBonus, ActionDefinition strike) {
    public HeavyAttack {
        Check.range("minChargeTicks", minChargeTicks, 1, 200);
        Check.range("maxChargeTicks", maxChargeTicks, minChargeTicks, 200);
        Check.range("minMultiplier", minMultiplier, 0.1, 20.0);
        Check.range("maxMultiplier", maxMultiplier, minMultiplier, 20.0);
        Check.range("derivedBonus", derivedBonus, 0.0, 10.0);
        Objects.requireNonNull(strike, "strike");
        if (strike.kind() != ActionKind.HEAVY) {
            throw new IllegalArgumentException("heavy strike must be of kind HEAVY");
        }
    }

    /** Damage scale for a charge of {@code chargedTicks} (clamped to the charge range). */
    public double chargeMultiplier(long chargedTicks) {
        if (maxChargeTicks == minChargeTicks) {
            return maxMultiplier;
        }
        double t = (double) (chargedTicks - minChargeTicks) / (double) (maxChargeTicks - minChargeTicks);
        t = Math.max(0.0, Math.min(1.0, t));
        return minMultiplier + (maxMultiplier - minMultiplier) * t;
    }
}
