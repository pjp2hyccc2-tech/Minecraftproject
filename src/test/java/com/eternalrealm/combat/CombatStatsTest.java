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
        assertEquals(1.5, stats.critMultiplier(), EPS);
        assertEquals(102.0, stats.maxStamina(), EPS);
        assertEquals(1.025, stats.staminaRegenPerTick(), EPS);
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
        assertEquals(1.75, rogue.critMultiplier(), EPS);
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
        assertEquals(TUNING.maxDamageReduction(), maxed.damageReduction(), EPS);
        assertEquals(TUNING.minActionTimeFactor(), maxed.actionSpeedFactor(), EPS);
        assertEquals(TUNING.maxCritChance(), maxed.critChance(), EPS);
        assertEquals(CombatStats.MAX_HEALTH_BONUS, maxed.maxHealthBonus(), EPS);
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
