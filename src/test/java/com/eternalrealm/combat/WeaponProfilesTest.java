package com.eternalrealm.combat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WeaponProfilesTest {
    private static List<ActionDefinition> actionsOf(WeaponProfile profile) {
        List<ActionDefinition> actions = new ArrayList<>(profile.combo());
        actions.add(profile.heavy().strike());
        actions.add(profile.skill());
        return actions;
    }

    @Test
    public void everyWeaponTypeHasExactlyOneProfileWithUniqueIds() {
        Set<String> ids = new HashSet<>();
        Set<String> actionIds = new HashSet<>();
        actionIds.add(CombatRules.DODGE.id());
        Set<WeaponType> types = new HashSet<>();
        for (WeaponProfile profile : WeaponProfiles.all()) {
            assertTrue("duplicate profile id " + profile.id(), ids.add(profile.id()));
            assertTrue("duplicate type " + profile.type(), types.add(profile.type()));
            for (ActionDefinition action : actionsOf(profile)) {
                assertTrue("duplicate action id " + action.id(), actionIds.add(action.id()));
            }
            assertSame(profile, WeaponProfiles.byId(profile.id()).orElseThrow());
        }
        assertEquals(WeaponType.values().length, types.size());
        assertFalse(WeaponProfiles.byId("nope").isPresent());
    }

    @Test
    public void timingInvariantsHoldAtEveryAttackSpeed() {
        for (WeaponProfile profile : WeaponProfiles.all()) {
            for (ActionDefinition action : actionsOf(profile)) {
                for (double factor : new double[]{1.0, 0.9, 0.75, 0.6, 0.2}) {
                    ActionDefinition.Timing timing = action.timing(factor);
                    int[] frames = timing.frameOffsets();
                    assertEquals(action.frames().size(), frames.length);
                    int previous = -1;
                    for (int offset : frames) {
                        assertTrue(action.id() + " frames ordered", offset >= previous);
                        assertTrue(action.id() + " frame before cancel", offset < timing.cancel());
                        previous = offset;
                    }
                    assertTrue(action.id() + " cancel <= duration", timing.cancel() <= timing.duration());
                    assertTrue(action.id() + " cancel >= 1", timing.cancel() >= 1);
                    if (!action.speedScaled()) {
                        assertEquals(action.durationTicks(), timing.duration());
                    }
                }
            }
        }
    }

    @Test
    public void lightAndHeavyScaleWithSpeedButSkillsAndDodgeDoNot() {
        for (WeaponProfile profile : WeaponProfiles.all()) {
            profile.combo().forEach(step -> assertTrue(step.speedScaled()));
            assertTrue(profile.heavy().strike().speedScaled());
            assertFalse(profile.skill().speedScaled());
        }
        assertFalse(CombatRules.DODGE.speedScaled());
    }

    /** Guards the balance table of docs/COMBAT_SYSTEM.md against accidental edits. */
    @Test
    public void valuesMatchTheDesignTable() {
        WeaponProfile sword = WeaponProfiles.ONE_HANDED_SWORD;
        assertEquals(6.0, sword.baseAttack(), 0.0);
        assertEquals(3, sword.combo().size());
        assertEquals(3, sword.combo().get(0).frames().get(0).offsetTicks());
        assertEquals(10, sword.combo().get(0).durationTicks());
        assertEquals(160, sword.skill().cooldownTicks());
        assertEquals(0.60, sword.guard().reduction(), 0.0);
        assertEquals(5, sword.guard().parryWindowTicks());

        WeaponProfile great = WeaponProfiles.TWO_HANDED_SWORD;
        assertEquals(9.0, great.baseAttack(), 0.0);
        assertEquals(2, great.combo().size());
        assertEquals(240, great.skill().cooldownTicks());
        assertEquals(0.75, great.guard().reduction(), 0.0);

        WeaponProfile dual = WeaponProfiles.DUAL_BLADES;
        assertEquals(4, dual.combo().size());
        assertEquals(0.10, dual.critBonus(), 0.0);
        assertEquals(6, dual.skill().frames().size());
        assertEquals(7, dual.guard().parryWindowTicks());

        WeaponProfile spear = WeaponProfiles.SPEAR;
        assertEquals(HitShape.Kind.LINE, spear.combo().get(0).frames().get(0).shape().kind());
        assertEquals(3, spear.skill().frames().size());

        assertEquals(12, CombatRules.DODGE.durationTicks());
        assertEquals(8, CombatRules.DODGE.invulnerableTicks());
        assertEquals(25.0, CombatRules.DODGE.staminaCost(), 0.0);
    }

    @Test
    public void chargeMultiplierInterpolatesAndClamps() {
        HeavyAttack heavy = WeaponProfiles.ONE_HANDED_SWORD.heavy();
        assertEquals(1.8, heavy.chargeMultiplier(0), 1e-9);
        assertEquals(1.8, heavy.chargeMultiplier(8), 1e-9);
        assertEquals(2.4, heavy.chargeMultiplier(19), 1e-9);
        assertEquals(3.0, heavy.chargeMultiplier(30), 1e-9);
        assertEquals(3.0, heavy.chargeMultiplier(1000), 1e-9);
    }
}
