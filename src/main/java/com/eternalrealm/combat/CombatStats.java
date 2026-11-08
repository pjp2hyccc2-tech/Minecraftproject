package com.eternalrealm.combat;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;

import java.util.Objects;

/**
 * Combat values derived from a character's total stats and level. Computed by the server and sent to the owning client,
 * which uses them for prediction and display (it never derives them itself, so server tuning changes apply everywhere).
 *
 * @param attackMultiplier  multiplies weapon damage (STR)
 * @param maxHealthBonus    added to the vanilla max health of 20 (VIT and level)
 * @param damageReduction   fraction of damage from attackers that is negated (VIT)
 * @param actionSpeedFactor multiplies light/heavy attack timings, {@code (0, 1]} (AGI)
 * @param critChance        base critical chance before weapon bonuses (LUK)
 * @param critMultiplier    damage multiplier of a critical hit
 * @param maxStamina        stamina pool (AGI)
 * @param staminaRegenPerTick stamina regained per tick once regeneration started (AGI)
 */
public record CombatStats(double attackMultiplier, double maxHealthBonus, double damageReduction,
                          double actionSpeedFactor, double critChance, double critMultiplier, double maxStamina,
                          double staminaRegenPerTick) {
    public static final double MAX_HEALTH_BONUS = 1000.0;

    public CombatStats {
        Check.range("attackMultiplier", attackMultiplier, 0.0, 10_000.0);
        Check.range("maxHealthBonus", maxHealthBonus, 0.0, MAX_HEALTH_BONUS);
        Check.range("damageReduction", damageReduction, 0.0, 0.95);
        Check.range("actionSpeedFactor", actionSpeedFactor, 0.05, 1.0);
        Check.range("critChance", critChance, 0.0, 1.0);
        Check.range("critMultiplier", critMultiplier, 1.0, 10.0);
        Check.range("maxStamina", maxStamina, 1.0, 1_000_000.0);
        Check.range("staminaRegenPerTick", staminaRegenPerTick, 0.0, 10_000.0);
    }

    public static CombatStats derive(StatBlock total, int level, CombatTuning tuning) {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(tuning, "tuning");
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1: " + level);
        }
        int str = total.get(StatType.STR);
        int vit = total.get(StatType.VIT);
        int agi = total.get(StatType.AGI);
        int luk = total.get(StatType.LUK);

        double attack = Math.min(10_000.0, 1.0 + str * tuning.attackPerStr());
        double health = Math.min(MAX_HEALTH_BONUS, vit * tuning.healthPerVit() + (level - 1) * tuning.healthPerLevel());
        double defense = vit * tuning.defensePerVit();
        double reduction = Math.min(tuning.maxDamageReduction(), defense / (defense + tuning.defenseConstant()));
        double speed = Math.max(tuning.minActionTimeFactor(), 1.0 / (1.0 + agi * tuning.speedPerAgi()));
        double crit = Math.min(tuning.maxCritChance(), tuning.baseCritChance() + luk * tuning.critPerLuk());
        double stamina = Math.min(1_000_000.0, tuning.baseStamina() + agi * tuning.staminaPerAgi());
        double regen = Math.min(10_000.0, tuning.baseStaminaRegen() + agi * tuning.staminaRegenPerAgi());
        return new CombatStats(attack, health, reduction, speed, crit, tuning.critMultiplier(), stamina, regen);
    }
}
