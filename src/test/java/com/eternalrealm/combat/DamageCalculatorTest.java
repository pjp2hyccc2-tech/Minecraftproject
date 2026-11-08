package com.eternalrealm.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DamageCalculatorTest {
    private static final double EPS = 1.0e-9;
    private static final CombatStats STATS = new CombatStats(1.1, 0, 0, 1, 0.06, 1.5, 100, 1);
    private static final WeaponProfile SWORD = WeaponProfiles.ONE_HANDED_SWORD;
    private static final HitFrame FIRST = SWORD.combo().get(0).frames().get(0);
    private static final HitFrame FINISHER = SWORD.combo().get(2).frames().get(0);

    @Test
    public void normalHitFollowsTheFormula() {
        DamageRoll roll = DamageCalculator.roll(STATS, SWORD, FIRST, 1.0, 0.5);
        assertFalse(roll.critical());
        assertEquals(6.0 * 1.1 * 1.0, roll.amount(), EPS);
        assertEquals(6.0 * 1.1 * 1.6 * 2.1, DamageCalculator.roll(STATS, SWORD, FINISHER, 2.1, 0.5).amount(), EPS);
    }

    @Test
    public void criticalHitsUseChancePlusWeaponBonus() {
        DamageRoll crit = DamageCalculator.roll(STATS, SWORD, FIRST, 1.0, 0.0599);
        assertTrue(crit.critical());
        assertEquals(6.0 * 1.1 * 1.5, crit.amount(), EPS);
        assertFalse(DamageCalculator.roll(STATS, SWORD, FIRST, 1.0, 0.06).critical());

        WeaponProfile dual = WeaponProfiles.DUAL_BLADES;
        HitFrame dualFrame = dual.combo().get(0).frames().get(0);
        assertTrue("+10% from the dual blades", DamageCalculator.roll(STATS, dual, dualFrame, 1.0, 0.15).critical());
        assertFalse(DamageCalculator.roll(STATS, dual, dualFrame, 1.0, 0.16).critical());
    }

    @Test
    public void criticalChanceIsCapped() {
        CombatStats certain = new CombatStats(1, 0, 0, 1, 1.0, 2, 100, 1);
        assertFalse(DamageCalculator.roll(certain, SWORD, FIRST, 1.0, CombatRules.MAX_CRIT_CHANCE).critical());
    }

    @Test
    public void damageIsCappedAndInputsValidated() {
        CombatStats absurd = new CombatStats(10_000, 0, 0, 1, 0, 10, 100, 1);
        assertEquals(CombatRules.MAX_DAMAGE, DamageCalculator.roll(absurd, SWORD, FINISHER, 1000, 0.5).amount(), EPS);
        assertThrows(IllegalArgumentException.class, () -> DamageCalculator.roll(STATS, SWORD, FIRST, -1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> DamageCalculator.roll(STATS, SWORD, FIRST, 1, 1.5));
    }

    @Test
    public void reductionIsClamped() {
        assertEquals(5.0, DamageCalculator.reduce(10.0, 0.5), EPS);
        assertEquals(10.0 * 0.05, DamageCalculator.reduce(10.0, 2.0), EPS);
        assertEquals(10.0, DamageCalculator.reduce(10.0, -1.0), EPS);
        assertEquals(0.0, DamageCalculator.reduce(-3.0, 0.2), EPS);
    }
}
