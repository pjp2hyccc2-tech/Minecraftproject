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

        // INT and DEX have no melee effect in Phase 2
        assertEquals(reference, CombatStats.derive(base.with(StatType.INT, 105).with(StatType.DEX, 105), 10, TUNING));
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
