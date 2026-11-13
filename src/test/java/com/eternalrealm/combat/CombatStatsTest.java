package com.eternalrealm.combat;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CombatStatsTest {
    private static final double EPS = 1.0e-9;
    private static final CombatTuning TUNING = CombatTuning.defaults();

    @Test
    public void startingCharacterValues() {
        CombatStats stats = CombatStats.derive(StatBlock.uniform(5), 1, TUNING);
        assertEquals(1.10, stats.attackMultiplier(), EPS);
        assertEquals(2.0, stats.maxHealthBonus(), EPS);
        assertEquals(2.5 / 102.5, stats.damageReduction(), EPS);
        assertEquals(1.0 / 1.015, stats.actionSpeedFactor(), EPS);
        assertEquals(0.06, stats.critChance(), EPS);
        assertEquals(1.5 + 5 * 0.003, stats.critMultiplier(), EPS); // DEX 5
        assertEquals(102.0, stats.maxStamina(), EPS);
        assertEquals(1.025, stats.staminaRegenPerTick(), EPS);
    }

    @Test
    public void finerStatEffectsHaveCaps() {
        StatScaling scaling = StatScaling.defaults();
        CombatStats base = CombatStats.derive(StatBlock.uniform(5), 1, TUNING);
        assertEquals(1.0 - 5 * 0.002, base.chargeTimeFactor(), EPS);
        assertEquals(1.0 - 5 * 0.0015, base.cooldownFactor(), EPS);
        assertEquals(1.0 + 5 * 0.003, base.dodgeDistanceFactor(), EPS);
        assertEquals(5 * 0.002, base.knockbackResistance(), EPS);
        assertEquals(5 * 0.004, base.potionBonus(), EPS);
        assertEquals(1.0 + 5 * 0.002, base.projectileSpeedFactor(), EPS);
        assertEquals(5 * 0.002, base.lootBonus(), EPS);
        assertEquals(5 * 0.005, base.goldBonus(), EPS);
        assertEquals(5 * 0.001, base.moveSpeedBonus(), EPS);

        CombatStats maxed = CombatStats.derive(StatBlock.uniform(StatBlock.MAX_VALUE), 100, TUNING);
        assertEquals(scaling.minChargeTimeFactor(), maxed.chargeTimeFactor(), EPS);
        assertEquals(scaling.minCooldownFactor(), maxed.cooldownFactor(), EPS);
        assertEquals(1.0 + scaling.maxDodgeDistanceBonus(), maxed.dodgeDistanceFactor(), EPS);
        assertEquals(scaling.maxKnockbackResist(), maxed.knockbackResistance(), EPS);
        assertEquals(scaling.maxPotionBonus(), maxed.potionBonus(), EPS);
        assertEquals(1.0 + scaling.maxProjectileSpeedBonus(), maxed.projectileSpeedFactor(), EPS);
        assertEquals(scaling.maxLootBonus(), maxed.lootBonus(), EPS);
        assertEquals(scaling.maxGoldBonus(), maxed.goldBonus(), EPS);
        assertEquals(scaling.maxMoveSpeedFromAgi(), maxed.moveSpeedBonus(), EPS);
    }

    @Test
    public void eachStatDrivesItsOwnValues() {
        StatBlock base = StatBlock.uniform(5);
        CombatStats reference = CombatStats.derive(base, 10, TUNING);
        assertEquals(2.0 + 9 * 0.5, reference.maxHealthBonus(), EPS);

        CombatStats strong = CombatStats.derive(base.with(StatType.STR, 105), 10, TUNING);
        assertEquals(1.0 + 105 * 0.02, strong.attackMultiplier(), EPS);
        assertEquals(reference.maxHealthBonus(), strong.maxHealthBonus(), EPS);

        CombatStats sturdy = CombatStats.derive(base.with(StatType.VIT, 105), 10, TUNING);
        assertTrue(sturdy.maxHealthBonus() > reference.maxHealthBonus());
        assertTrue(sturdy.damageReduction() > reference.damageReduction());
        assertTrue(sturdy.potionBonus() > reference.potionBonus());

        CombatStats quick = CombatStats.derive(base.with(StatType.AGI, 105), 10, TUNING);
        assertTrue(quick.actionSpeedFactor() < reference.actionSpeedFactor());
        assertTrue(quick.maxStamina() > reference.maxStamina());
        assertTrue(quick.staminaRegenPerTick() > reference.staminaRegenPerTick());

        CombatStats lucky = CombatStats.derive(base.with(StatType.LUK, 105), 10, TUNING);
        assertEquals(0.05 + 105 * 0.002, lucky.critChance(), EPS);

        // INT and DEX only drive magic, healing and ranged damage
        CombatStats caster = CombatStats.derive(base.with(StatType.INT, 105).with(StatType.DEX, 105), 10, TUNING);
        assertEquals(1.0 + 105 * 0.02, caster.magicMultiplier(), EPS);
        assertEquals(1.0 + 105 * 0.02, caster.rangedMultiplier(), EPS);
        assertTrue(caster.healingMultiplier() > reference.healingMultiplier());
        assertEquals(reference.attackMultiplier(), caster.attackMultiplier(), EPS);
        assertEquals(reference.maxHealthBonus(), caster.maxHealthBonus(), EPS);
        assertEquals(reference.critChance(), caster.critChance(), EPS);
    }

    @Test
    public void modifiersAddSecondaryBonuses() {
        StatBlock base = StatBlock.uniform(5);
        CombatStats plain = CombatStats.derive(base, 1, TUNING);
        CombatModifiers knight = CombatModifiers.builder().healthPercent(0.15).guard(0.1).defense(20).build();
        CombatStats tank = CombatStats.derive(base, 1, TUNING, knight);
        assertEquals((20.0 + plain.maxHealthBonus()) * 1.15 - 20.0, tank.maxHealthBonus(), EPS);
        assertEquals(0.1, tank.guardBonus(), EPS);
        assertTrue(tank.damageReduction() > plain.damageReduction());

        CombatModifiers hybrid = CombatModifiers.builder().intToPhysical(0.5).physical(0.1).build();
        CombatStats spellblade = CombatStats.derive(base.with(StatType.INT, 105), 1, TUNING, hybrid);
        assertEquals((1.0 + (5 + 105 * 0.5) * 0.02) * 1.1, spellblade.attackMultiplier(), EPS);

        CombatModifiers assassin = CombatModifiers.builder().crit(0.1).critDamage(0.25).dodgeCost(0.8).build();
        CombatStats rogue = CombatStats.derive(base, 1, TUNING, assassin);
        assertEquals(plain.critChance() + 0.1, rogue.critChance(), EPS);
        assertEquals(1.75 + 5 * 0.003, rogue.critMultiplier(), EPS);
        assertEquals(0.8, rogue.dodgeCostFactor(), EPS);
        assertEquals(plain, CombatStats.derive(base, 1, TUNING, CombatModifiers.NONE));
    }

    @Test
    public void combinedModifiersAreClampedNotRejected() {
        CombatModifiers huge = CombatModifiers.builder().guard(1.0).dodgeCost(0.01).moveSpeed(10).crit(1.0).build();
        CombatStats stats = CombatStats.derive(StatBlock.uniform(5), 1, TUNING, huge.plus(huge));
        assertEquals(CombatStats.MAX_GUARD_BONUS, stats.guardBonus(), EPS);
        assertEquals(0.2, stats.dodgeCostFactor(), EPS);
        assertEquals(1.0, stats.moveSpeedBonus(), EPS);
        assertEquals(TUNING.maxCritChance(), stats.critChance(), EPS);
    }

    @Test
    public void extremeStatsAreCapped() {
        CombatStats maxed = CombatStats.derive(StatBlock.uniform(StatBlock.MAX_VALUE), 100, TUNING);
        // what lies beyond a cap turns into something else, which has its own cap
        assertTrue(maxed.damageReduction() > TUNING.maxDamageReduction());
        assertTrue(maxed.damageReduction() <= TUNING.maxDamageReduction() + CombatStats.MAX_OVERFLOW_REDUCTION);
        assertEquals(TUNING.minActionTimeFactor(), maxed.actionSpeedFactor(), EPS);
        assertTrue(maxed.evasion() > 0.0 && maxed.evasion() <= CombatStats.MAX_OVERFLOW_EVASION);
        assertEquals(TUNING.maxCritChance(), maxed.critChance(), EPS);
        assertEquals(10.0, maxed.critMultiplier(), EPS);
        assertEquals(CombatStats.MAX_HEALTH_BONUS, maxed.maxHealthBonus(), EPS);
    }

    /**
     * Owner's request of 2026-11-13: every stat a build specialises in keeps paying off. LUK past the critical chance
     * cap raises critical damage 1 : 1, AGI past the attack speed cap gives evasion, VIT past the max health cap adds
     * damage reduction on top of its cap; below the caps nothing changes.
     */
    @Test
    public void statsBeyondTheirCapsTurnIntoSomethingElse() {
        StatBlock base = StatBlock.uniform(5);
        CombatStats plain = CombatStats.derive(base, 100, TUNING);
        assertEquals(0.0, plain.evasion(), 0.0);
        assertEquals(1.5 + 5 * 0.003, plain.critMultiplier(), EPS);

        CombatStats lucky = CombatStats.derive(base.with(StatType.LUK, 2000), 100, TUNING);
        assertEquals(TUNING.maxCritChance(), lucky.critChance(), EPS);
        double excess = 0.05 + 2000 * 0.002 - TUNING.maxCritChance();
        assertEquals(1.5 + 5 * 0.003 + excess, lucky.critMultiplier(), EPS);
        // just at the cap: no overflow yet
        CombatStats atCap = CombatStats.derive(base.with(StatType.LUK, 350), 100, TUNING);
        assertEquals(1.5 + 5 * 0.003, atCap.critMultiplier(), EPS);

        CombatStats swift = CombatStats.derive(base.with(StatType.AGI, 1000), 100, TUNING);
        assertEquals(TUNING.minActionTimeFactor(), swift.actionSpeedFactor(), EPS);
        double speedExcess = 1000 * 0.003 - (1.0 / TUNING.minActionTimeFactor() - 1.0);
        assertEquals(CombatStats.MAX_OVERFLOW_EVASION * speedExcess / (speedExcess + CombatStats.OVERFLOW_EVASION_HALF),
                swift.evasion(), EPS);
        CombatStats swifter = CombatStats.derive(base.with(StatType.AGI, 4000), 100, TUNING);
        assertTrue(swifter.evasion() > swift.evasion() && swifter.evasion() < CombatStats.MAX_OVERFLOW_EVASION);
        assertEquals(0.0, CombatStats.derive(base.with(StatType.AGI, 200), 100, TUNING).evasion(), 0.0);

        CombatStats sturdy = CombatStats.derive(base.with(StatType.VIT, 5000), 100, TUNING);
        assertEquals(CombatStats.MAX_HEALTH_BONUS, sturdy.maxHealthBonus(), EPS);
        double healthExcess = 5000 * 0.4 + 99 * 0.5 - CombatStats.MAX_HEALTH_BONUS;
        assertEquals(TUNING.maxDamageReduction() + CombatStats.MAX_OVERFLOW_REDUCTION * healthExcess
                / (healthExcess + CombatStats.OVERFLOW_REDUCTION_HALF), sturdy.damageReduction(), EPS);
        CombatStats tough = CombatStats.derive(base.with(StatType.VIT, 2000), 100, TUNING);
        assertEquals(TUNING.maxDamageReduction(), tough.damageReduction(), EPS); // health not capped yet
        assertTrue(sturdy.damageReduction() > tough.damageReduction());
    }

    @Test
    public void invalidTuningIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CombatTuning(Double.NaN, 0.4, 0.5, 0.5, 100, 0.75,
                0.003, 0.6, 0.05, 0.002, 0.75, 1.5, 100, 0.4, 1, 0.005));
        assertThrows(IllegalArgumentException.class, () -> new CombatTuning(0.02, 0.4, 0.5, 0.5, 100, 0.99,
                0.003, 0.6, 0.05, 0.002, 0.75, 1.5, 100, 0.4, 1, 0.005));
        assertThrows(IllegalArgumentException.class, () -> new CombatTuning(0.02, 0.4, 0.5, 0.5, 100, 0.75,
                0.003, 0.6, 0.05, 0.002, 0.75, 0.5, 100, 0.4, 1, 0.005));
        assertThrows(IllegalArgumentException.class, () -> CombatStats.derive(StatBlock.uniform(5), 0, TUNING));
    }
}
