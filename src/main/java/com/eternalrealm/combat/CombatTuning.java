package com.eternalrealm.combat;

/**
 * Server-configured conversion of character stats into combat values (config section {@code combat}).
 * Immutable; replaced as a whole when the config is reloaded. Ranges match the config spec.
 */
public record CombatTuning(double attackPerStr, double healthPerVit, double healthPerLevel, double defensePerVit,
                           double defenseConstant, double maxDamageReduction, double speedPerAgi,
                           double minActionTimeFactor, double baseCritChance, double critPerLuk, double maxCritChance,
                           double critMultiplier, double baseStamina, double staminaPerAgi, double baseStaminaRegen,
                           double staminaRegenPerAgi) {
    public CombatTuning {
        Check.range("attackPerStr", attackPerStr, 0.0, 1.0);
        Check.range("healthPerVit", healthPerVit, 0.0, 10.0);
        Check.range("healthPerLevel", healthPerLevel, 0.0, 20.0);
        Check.range("defensePerVit", defensePerVit, 0.0, 10.0);
        Check.range("defenseConstant", defenseConstant, 1.0, 10_000.0);
        Check.range("maxDamageReduction", maxDamageReduction, 0.0, 0.95);
        Check.range("speedPerAgi", speedPerAgi, 0.0, 0.1);
        Check.range("minActionTimeFactor", minActionTimeFactor, 0.2, 1.0);
        Check.range("baseCritChance", baseCritChance, 0.0, 1.0);
        Check.range("critPerLuk", critPerLuk, 0.0, 0.1);
        Check.range("maxCritChance", maxCritChance, 0.0, 1.0);
        Check.range("critMultiplier", critMultiplier, 1.0, 10.0);
        Check.range("baseStamina", baseStamina, 10.0, 10_000.0);
        Check.range("staminaPerAgi", staminaPerAgi, 0.0, 100.0);
        Check.range("baseStaminaRegen", baseStaminaRegen, 0.0, 100.0);
        Check.range("staminaRegenPerAgi", staminaRegenPerAgi, 0.0, 10.0);
    }

    /** Initial balance (docs/COMBAT_SYSTEM.md, section 7). */
    public static CombatTuning defaults() {
        return new CombatTuning(0.02, 0.4, 0.5, 0.5, 100.0, 0.75, 0.003, 0.6, 0.05, 0.002, 0.75, 1.5,
                100.0, 0.4, 1.0, 0.005);
    }
}
