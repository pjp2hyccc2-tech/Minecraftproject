package com.eternalrealm.combat;

/**
 * Finer effects of the primary stats beyond the main ones in {@link CombatTuning} (docs/COMBAT_SYSTEM.md, "細かい
 * ステータス効果"). Every stat matters for more than one thing, with caps so no single stat runs away:
 * <ul>
 *     <li>STR: heavy attacks charge faster</li>
 *     <li>VIT: knockback resistance, health regeneration out of combat</li>
 *     <li>AGI: movement speed, longer dodges</li>
 *     <li>INT: shorter skill cooldowns</li>
 *     <li>DEX: critical damage, faster projectiles</li>
 *     <li>LUK: better loot and more gold (besides the critical chance)</li>
 * </ul>
 */
public record StatScaling(double chargeTimePerStr, double minChargeTimeFactor, double knockbackResistPerVit,
                          double maxKnockbackResist, double regenPerVit, double moveSpeedPerAgi, double maxMoveSpeedFromAgi,
                          double dodgeDistancePerAgi, double maxDodgeDistanceBonus, double cooldownPerInt,
                          double minCooldownFactor, double critDamagePerDex, double projectileSpeedPerDex,
                          double maxProjectileSpeedBonus, double lootPerLuk, double maxLootBonus, double goldPerLuk,
                          double maxGoldBonus) {
    public StatScaling {
        Check.range("chargeTimePerStr", chargeTimePerStr, 0.0, 0.1);
        Check.range("minChargeTimeFactor", minChargeTimeFactor, 0.2, 1.0);
        Check.range("knockbackResistPerVit", knockbackResistPerVit, 0.0, 0.1);
        Check.range("maxKnockbackResist", maxKnockbackResist, 0.0, 1.0);
        Check.range("regenPerVit", regenPerVit, 0.0, 1.0);
        Check.range("moveSpeedPerAgi", moveSpeedPerAgi, 0.0, 0.1);
        Check.range("maxMoveSpeedFromAgi", maxMoveSpeedFromAgi, 0.0, 1.0);
        Check.range("dodgeDistancePerAgi", dodgeDistancePerAgi, 0.0, 0.1);
        Check.range("maxDodgeDistanceBonus", maxDodgeDistanceBonus, 0.0, 2.0);
        Check.range("cooldownPerInt", cooldownPerInt, 0.0, 0.1);
        Check.range("minCooldownFactor", minCooldownFactor, 0.2, 1.0);
        Check.range("critDamagePerDex", critDamagePerDex, 0.0, 0.1);
        Check.range("projectileSpeedPerDex", projectileSpeedPerDex, 0.0, 0.1);
        Check.range("maxProjectileSpeedBonus", maxProjectileSpeedBonus, 0.0, 2.0);
        Check.range("lootPerLuk", lootPerLuk, 0.0, 0.1);
        Check.range("maxLootBonus", maxLootBonus, 0.0, 5.0);
        Check.range("goldPerLuk", goldPerLuk, 0.0, 0.1);
        Check.range("maxGoldBonus", maxGoldBonus, 0.0, 10.0);
    }

    public static StatScaling defaults() {
        return new StatScaling(0.002, 0.6, 0.002, 0.5, 0.01, 0.001, 0.15, 0.003, 0.3, 0.0015, 0.7, 0.003, 0.002,
                0.4, 0.002, 0.5, 0.005, 1.0);
    }
}
