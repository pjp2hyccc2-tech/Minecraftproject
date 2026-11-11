package com.eternalrealm.character;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExperienceCurveAndRulesTest {
    @Test
    public void powerCurveMatchesFormula() {
        PowerExperienceCurve curve = new PowerExperienceCurve(100, 1.5);
        assertEquals(100, curve.experienceToNext(1));
        assertEquals(283, curve.experienceToNext(2)); // round(100 * 2^1.5) = round(282.84)
        assertEquals(520, curve.experienceToNext(3)); // round(519.62)
        assertEquals(800, curve.experienceToNext(4));
        assertEquals(2700, curve.experienceToNext(9));
    }

    @Test
    public void powerCurveIsStrictlyIncreasing() {
        PowerExperienceCurve curve = new PowerExperienceCurve(100, 1.5);
        for (int level = 2; level < CharacterRules.MAX_LEVEL; level++) {
            assertTrue("level " + level, curve.experienceToNext(level) > curve.experienceToNext(level - 1));
        }
    }

    @Test
    public void powerCurveRejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new PowerExperienceCurve(0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new PowerExperienceCurve(100, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new PowerExperienceCurve(100, Double.NaN));
        PowerExperienceCurve curve = new PowerExperienceCurve(100, 1.5);
        assertThrows(IllegalArgumentException.class, () -> curve.experienceToNext(0));
        assertThrows(IllegalArgumentException.class, () -> curve.experienceToNext(CharacterRules.MAX_LEVEL));
    }

    @Test
    public void extremeButValidCurveStaysWithinBounds() {
        PowerExperienceCurve curve = new PowerExperienceCurve(PowerExperienceCurve.MAX_BASE, PowerExperienceCurve.MAX_EXPONENT);
        assertTrue(curve.experienceToNext(99) <= PowerExperienceCurve.MAX_EXPERIENCE_PER_LEVEL);
    }

    @Test
    public void rulesDeriveStatPointsAndCap() {
        CharacterRules rules = CharacterRules.defaults();
        assertEquals(100, rules.levelCap());
        assertEquals(CharacterRules.MAX_LEVEL, rules.levelCap());
        assertEquals(0, rules.earnedStatPoints(1));
        assertEquals(3 * 9, rules.earnedStatPoints(10));
        assertFalse(rules.isAtCap(50));
        assertFalse(rules.isAtCap(99));
        assertTrue(rules.isAtCap(100));
        assertEquals(0, rules.experienceToNext(100));
        assertTrue(rules.experienceToNext(99) > rules.experienceToNext(50));
        CharacterRules lowered = new CharacterRules(50, 3, 5, 10, new PowerExperienceCurve(100, 1.5));
        assertTrue(lowered.isAtCap(50));
        assertTrue("levels above a lowered cap count as capped", lowered.isAtCap(60));
        assertEquals(0, lowered.experienceToNext(50));
        assertEquals(100, rules.experienceToNext(1));
        assertEquals(StatBlock.uniform(5), rules.baseStats());
    }

    @Test
    public void rulesValidateRanges() {
        PowerExperienceCurve curve = new PowerExperienceCurve(100, 1.5);
        assertThrows(IllegalArgumentException.class, () -> new CharacterRules(0, 3, 5, 10, curve));
        assertThrows(IllegalArgumentException.class, () -> new CharacterRules(101, 3, 5, 10, curve));
        assertThrows(IllegalArgumentException.class, () -> new CharacterRules(50, -1, 5, 10, curve));
        assertThrows(IllegalArgumentException.class, () -> new CharacterRules(50, 3, 101, 10, curve));
        assertThrows(IllegalArgumentException.class, () -> new CharacterRules(50, 3, 5, 0, curve));
        assertThrows(NullPointerException.class, () -> new CharacterRules(50, 3, 5, 10, null));
    }
}
