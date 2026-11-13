package com.eternalrealm.combat;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;

import java.util.Objects;

/**
 * Combat values derived from a character's total stats, level and secondary modifiers (job, equipment). Computed by
 * the server and sent to the owning client, which uses them for prediction and display (it never derives them itself,
 * so server tuning changes apply everywhere).
 *
 * @param attackMultiplier    multiplies physical weapon damage (STR)
 * @param maxHealthBonus      added to the vanilla max health of 20 (VIT, level, modifiers)
 * @param damageReduction     fraction of damage from attackers that is negated (VIT, defense)
 * @param actionSpeedFactor   multiplies light/heavy attack timings, {@code (0, 1]} (AGI)
 * @param critChance          base critical chance before weapon bonuses (LUK)
 * @param critMultiplier      damage multiplier of a critical hit
 * @param maxStamina          stamina pool (AGI)
 * @param staminaRegenPerTick stamina regained per tick once regeneration started (AGI)
 * @param rangedMultiplier    multiplies ranged damage (DEX)
 * @param magicMultiplier     multiplies magic and holy damage (INT)
 * @param healingMultiplier   multiplies healing done (INT)
 * @param guardBonus          added to the weapon's guard reduction
 * @param dodgeCostFactor     multiplies the dodge stamina cost
 * @param moveSpeedBonus      +fraction of movement speed (AGI, job, equipment)
 * @param chargeTimeFactor    multiplies the charge time of heavy attacks, {@code (0, 1]} (STR)
 * @param cooldownFactor      multiplies skill cooldowns, {@code (0, 1]} (INT)
 * @param dodgeDistanceFactor multiplies the dodge impulse, {@code >= 1} (AGI)
 * @param knockbackResistance vanilla knockback resistance granted, {@code [0, 1]} (VIT)
 * @param potionBonus         +fraction of the health a potion restores, {@code [0, 1]} (VIT)
 * @param projectileSpeedFactor multiplies projectile speed, {@code >= 1} (DEX)
 * @param lootBonus           luck bonus of loot rolls (LUK)
 * @param goldBonus           +fraction of gold found (LUK)
 * @param evasion             chance to slip an attacker's hit, from attack speed beyond its cap (AGI)
 *
 * <p><b>Beyond the caps</b> (owner's request of 2026-11-13: every stat a build specialises in should keep paying off up
 * to about 4 000): what a stat would add past its cap turns into something else instead of being lost —</p>
 * <pre>
 * LUK  critical chance above the cap (75 %)   → critical damage, 1 : 1        (within the ×10 cap)
 * AGI  attack speed above the cap (×0.6 time) → evasion, 30 % × e / (e + 4)    (e = the speed beyond the cap)
 * VIT  max health above the cap (+1 000)      → damage reduction on top of the 75 % cap, 15 % × h / (h + 600)
 * </pre>
 * <p>Below the caps nothing changes.</p>
 */
public record CombatStats(double attackMultiplier, double maxHealthBonus, double damageReduction,
                          double actionSpeedFactor, double critChance, double critMultiplier, double maxStamina,
                          double staminaRegenPerTick, double rangedMultiplier, double magicMultiplier,
                          double healingMultiplier, double guardBonus, double dodgeCostFactor, double moveSpeedBonus,
                          double chargeTimeFactor, double cooldownFactor, double dodgeDistanceFactor,
                          double knockbackResistance, double potionBonus, double projectileSpeedFactor,
                          double lootBonus, double goldBonus, double evasion) {
    public static final double MAX_HEALTH_BONUS = 1000.0;
    public static final double MAX_GUARD_BONUS = 0.3;
    /** Most evasion attack speed beyond its cap can give (AGI). */
    public static final double MAX_OVERFLOW_EVASION = 0.30;
    /** Attack speed beyond the cap that gives half of {@link #MAX_OVERFLOW_EVASION} (about 1 333 AGI). */
    public static final double OVERFLOW_EVASION_HALF = 4.0;
    /** Most damage reduction max health beyond its cap can add on top of the tuning's cap (VIT). */
    public static final double MAX_OVERFLOW_REDUCTION = 0.15;
    /** Max health beyond the cap that gives half of {@link #MAX_OVERFLOW_REDUCTION} (about 1 500 VIT). */
    public static final double OVERFLOW_REDUCTION_HALF = 600.0;

    public CombatStats {
        Check.range("attackMultiplier", attackMultiplier, 0.0, 10_000.0);
        Check.range("maxHealthBonus", maxHealthBonus, 0.0, MAX_HEALTH_BONUS);
        Check.range("damageReduction", damageReduction, 0.0, 0.95);
        Check.range("actionSpeedFactor", actionSpeedFactor, 0.05, 1.0);
        Check.range("critChance", critChance, 0.0, 1.0);
        Check.range("critMultiplier", critMultiplier, 1.0, 10.0);
        Check.range("maxStamina", maxStamina, 1.0, 1_000_000.0);
        Check.range("staminaRegenPerTick", staminaRegenPerTick, 0.0, 10_000.0);
        Check.range("rangedMultiplier", rangedMultiplier, 0.0, 10_000.0);
        Check.range("magicMultiplier", magicMultiplier, 0.0, 10_000.0);
        Check.range("healingMultiplier", healingMultiplier, 0.0, 10_000.0);
        Check.range("guardBonus", guardBonus, 0.0, MAX_GUARD_BONUS);
        Check.range("dodgeCostFactor", dodgeCostFactor, 0.2, 2.0);
        Check.range("moveSpeedBonus", moveSpeedBonus, -0.5, 1.0);
        Check.range("chargeTimeFactor", chargeTimeFactor, 0.2, 1.0);
        Check.range("cooldownFactor", cooldownFactor, 0.2, 1.0);
        Check.range("dodgeDistanceFactor", dodgeDistanceFactor, 1.0, 3.0);
        Check.range("knockbackResistance", knockbackResistance, 0.0, 1.0);
        Check.range("potionBonus", potionBonus, 0.0, 1.0);
        Check.range("projectileSpeedFactor", projectileSpeedFactor, 1.0, 3.0);
        Check.range("lootBonus", lootBonus, 0.0, 5.0);
        Check.range("goldBonus", goldBonus, 0.0, 10.0);
        Check.range("evasion", evasion, 0.0, MAX_OVERFLOW_EVASION);
    }

    /** Values without evasion (the form before the overflow of 2026-11-13). */
    public CombatStats(double attackMultiplier, double maxHealthBonus, double damageReduction,
                       double actionSpeedFactor, double critChance, double critMultiplier, double maxStamina,
                       double staminaRegenPerTick, double rangedMultiplier, double magicMultiplier,
                       double healingMultiplier, double guardBonus, double dodgeCostFactor, double moveSpeedBonus,
                       double chargeTimeFactor, double cooldownFactor, double dodgeDistanceFactor,
                       double knockbackResistance, double potionBonus, double projectileSpeedFactor,
                       double lootBonus, double goldBonus) {
        this(attackMultiplier, maxHealthBonus, damageReduction, actionSpeedFactor, critChance, critMultiplier,
                maxStamina, staminaRegenPerTick, rangedMultiplier, magicMultiplier, healingMultiplier, guardBonus,
                dodgeCostFactor, moveSpeedBonus, chargeTimeFactor, cooldownFactor, dodgeDistanceFactor,
                knockbackResistance, potionBonus, projectileSpeedFactor, lootBonus, goldBonus, 0.0);
    }

    /** Values without the finer stat effects (Phase 3 form). */
    public CombatStats(double attackMultiplier, double maxHealthBonus, double damageReduction,
                       double actionSpeedFactor, double critChance, double critMultiplier, double maxStamina,
                       double staminaRegenPerTick, double rangedMultiplier, double magicMultiplier,
                       double healingMultiplier, double guardBonus, double dodgeCostFactor, double moveSpeedBonus) {
        this(attackMultiplier, maxHealthBonus, damageReduction, actionSpeedFactor, critChance, critMultiplier,
                maxStamina, staminaRegenPerTick, rangedMultiplier, magicMultiplier, healingMultiplier, guardBonus,
                dodgeCostFactor, moveSpeedBonus, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0);
    }

    /** Phase 2 form: no ranged/magic scaling and no secondary modifiers. */
    public CombatStats(double attackMultiplier, double maxHealthBonus, double damageReduction,
                       double actionSpeedFactor, double critChance, double critMultiplier, double maxStamina,
                       double staminaRegenPerTick) {
        this(attackMultiplier, maxHealthBonus, damageReduction, actionSpeedFactor, critChance, critMultiplier,
                maxStamina, staminaRegenPerTick, 1.0, 1.0, 1.0, 0.0, 1.0, 0.0);
    }

    public static CombatStats derive(StatBlock total, int level, CombatTuning tuning) {
        return derive(total, level, tuning, CombatModifiers.NONE);
    }

    /**
     * The combat values of a character. Every primary attack stat uses the same scaling
     * ({@code 1 + stat × attackPerStr}): STR for physical, DEX for ranged and INT for magic damage.
     */
    public static CombatStats derive(StatBlock total, int level, CombatTuning tuning, CombatModifiers modifiers) {
        return derive(total, level, tuning, modifiers, StatScaling.defaults());
    }

    public static CombatStats derive(StatBlock total, int level, CombatTuning tuning, CombatModifiers modifiers,
                                     StatScaling scaling) {
        Objects.requireNonNull(scaling, "scaling");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(tuning, "tuning");
        Objects.requireNonNull(modifiers, "modifiers");
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1: " + level);
        }
        int str = total.get(StatType.STR);
        int vit = total.get(StatType.VIT);
        int agi = total.get(StatType.AGI);
        int intel = total.get(StatType.INT);
        int dex = total.get(StatType.DEX);
        int luk = total.get(StatType.LUK);

        double physicalStat = str + intel * modifiers.intToPhysical();
        double attack = cap(scaled(physicalStat, tuning) * (1.0 + modifiers.physicalBonus()));
        double ranged = cap(scaled(dex, tuning) * (1.0 + modifiers.rangedBonus()));
        double magic = cap(scaled(intel, tuning) * (1.0 + modifiers.magicBonus()));
        double healing = cap((1.0 + intel * tuning.attackPerStr() * 0.75) * (1.0 + modifiers.healingBonus()));

        double flatHealth = vit * tuning.healthPerVit() + (level - 1) * tuning.healthPerLevel()
                + modifiers.maxHealthFlat();
        double rawHealth = (20.0 + Math.max(0.0, flatHealth)) * (1.0 + modifiers.maxHealthPercent()) - 20.0;
        double health = Math.max(0.0, Math.min(MAX_HEALTH_BONUS, rawHealth));
        double defense = Math.max(0.0, vit * tuning.defensePerVit() + modifiers.defenseFlat());
        // max health beyond its cap toughens instead (VIT)
        double reduction = Math.min(tuning.maxDamageReduction(), defense / (defense + tuning.defenseConstant()))
                + overflow(rawHealth - MAX_HEALTH_BONUS, MAX_OVERFLOW_REDUCTION, OVERFLOW_REDUCTION_HALF);
        double rawSpeed = agi * tuning.speedPerAgi() + modifiers.attackSpeed();
        double speed = Math.max(tuning.minActionTimeFactor(), 1.0 / (1.0 + rawSpeed));
        // attack speed beyond its cap becomes evasion (AGI)
        double evasion = overflow(rawSpeed - (1.0 / tuning.minActionTimeFactor() - 1.0), MAX_OVERFLOW_EVASION,
                OVERFLOW_EVASION_HALF);
        double rawCrit = tuning.baseCritChance() + luk * tuning.critPerLuk() + modifiers.critChance();
        double crit = clamp(rawCrit, 0.0, tuning.maxCritChance());
        // critical chance beyond its cap becomes critical damage, 1 : 1 (LUK)
        double critMultiplier = clamp(tuning.critMultiplier() + modifiers.critDamage()
                + dex * scaling.critDamagePerDex() + Math.max(0.0, rawCrit - tuning.maxCritChance()), 1.0, 10.0);
        double stamina = clamp(tuning.baseStamina() + agi * tuning.staminaPerAgi() + modifiers.staminaBonus(),
                10.0, 1_000_000.0);
        double regen = Math.min(10_000.0, tuning.baseStaminaRegen() + agi * tuning.staminaRegenPerAgi());
        double moveSpeed = modifiers.moveSpeed() + Math.min(scaling.maxMoveSpeedFromAgi(), agi * scaling.moveSpeedPerAgi());
        double chargeTime = Math.max(scaling.minChargeTimeFactor(), 1.0 - str * scaling.chargeTimePerStr());
        double cooldown = Math.max(scaling.minCooldownFactor(), 1.0 - intel * scaling.cooldownPerInt());
        double dodgeDistance = 1.0 + Math.min(scaling.maxDodgeDistanceBonus(), agi * scaling.dodgeDistancePerAgi());
        double knockbackResist = Math.min(scaling.maxKnockbackResist(), vit * scaling.knockbackResistPerVit());
        double potion = Math.min(scaling.maxPotionBonus(), vit * scaling.potionPerVit());
        double projectileSpeed = 1.0 + Math.min(scaling.maxProjectileSpeedBonus(), dex * scaling.projectileSpeedPerDex());
        double loot = Math.min(scaling.maxLootBonus(), luk * scaling.lootPerLuk());
        double gold = Math.min(scaling.maxGoldBonus(), luk * scaling.goldPerLuk());
        return new CombatStats(attack, health, reduction, speed, crit, critMultiplier, stamina, regen, ranged, magic,
                healing, clamp(modifiers.guardBonus(), 0.0, MAX_GUARD_BONUS),
                clamp(modifiers.dodgeCostFactor(), 0.2, 2.0), clamp(moveSpeed, -0.5, 1.0),
                chargeTime, cooldown, dodgeDistance, knockbackResist, potion, projectileSpeed, loot, gold, evasion);
    }

    /** What an excess beyond a cap turns into: {@code max × excess / (excess + half)}, 0 without an excess. */
    static double overflow(double excess, double max, double half) {
        return excess > 0.0 ? max * excess / (excess + half) : 0.0;
    }

    private static double scaled(double stat, CombatTuning tuning) {
        return 1.0 + stat * tuning.attackPerStr();
    }

    private static double cap(double value) {
        return clamp(value, 0.0, 10_000.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
