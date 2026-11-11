package com.eternalrealm.combat;

import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.job.SkillDefinition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** D-RANGEDKB: magic and bow attacks push nothing; melee keeps the action's knockback. */
public class KnockbackTest {
    private static final double EPS = 1.0e-12;

    @Test
    public void magicAndBowsPushNothing() {
        assertEquals(0.0, Knockback.of(0.8, DamageType.MAGIC, false), EPS);
        assertEquals(0.0, Knockback.of(1.2, DamageType.HOLY, false), EPS);
        assertEquals(0.0, Knockback.of(2.0, DamageType.RANGED, false), EPS);
        assertTrue(Knockback.isMagicOrBow(DamageType.MAGIC));
        assertTrue(Knockback.isMagicOrBow(DamageType.HOLY));
        assertTrue(Knockback.isMagicOrBow(DamageType.RANGED));
        assertFalse(Knockback.isMagicOrBow(DamageType.PHYSICAL));
    }

    @Test
    public void meleeAndTheSpellbladesSwordBlowsKeepTheirPush() {
        assertEquals(0.6, Knockback.of(0.6, DamageType.PHYSICAL, false), EPS);
        assertEquals(1.0, Knockback.of(1.0, DamageType.MAGIC, true), EPS);
        assertEquals(0.4, Knockback.of(0.4, DamageType.PHYSICAL, true), EPS);
    }

    @Test
    public void everyStaffAndBowActionAndEveryRangedJobSkillEndsWithoutPush() {
        for (WeaponType type : new WeaponType[]{WeaponType.STAFF, WeaponType.BOW}) {
            WeaponProfile weapon = WeaponProfiles.of(type);
            java.util.List<ActionDefinition> actions = new java.util.ArrayList<>(weapon.combo());
            actions.add(weapon.heavy().strike());
            actions.add(weapon.skill());
            for (ActionDefinition action : actions) {
                for (HitFrame frame : action.frames()) {
                    assertEquals(type + " " + action.id(), 0.0,
                            Knockback.of(frame.knockback(), type.damageType(), false), EPS);
                }
            }
        }
        int checked = 0;
        int melee = 0;
        for (SkillDefinition skill : Jobs.allSkills().values()) {
            if (!skill.isUsable() || skill.action() == null || !(skill.job() == JobType.MAGE
                    || skill.job() == JobType.PRIEST || skill.job() == JobType.RANGER)) {
                continue;
            }
            for (HitFrame frame : skill.action().frames()) {
                double knockback = Knockback.of(frame.knockback(), skill.damageType(), false);
                if (skill.damageType() == DamageType.PHYSICAL) {
                    // the ranger's dual-blade skills are melee: they keep their push
                    assertEquals(skill.id(), frame.knockback(), knockback, EPS);
                    melee++;
                } else {
                    assertEquals(skill.id(), 0.0, knockback, EPS);
                    checked++;
                }
            }
        }
        assertTrue("the mage, priest and ranger have magic and bow hit frames", checked > 50);
        assertTrue("the ranger's dual-blade skills are there", melee > 0);
    }

    @Test
    public void aFrameWithTheSameKnockbackIsTheSameFrame() {
        HitFrame frame = new HitFrame(3, HitShape.circle(3.0), 1.0, 0.5);
        assertSame(frame, frame.withKnockback(0.5));
        HitFrame still = frame.withKnockback(0.0);
        assertEquals(0.0, still.knockback(), EPS);
        assertEquals(frame.shape(), still.shape());
        assertEquals(frame.multiplier(), still.multiplier(), EPS);
        assertEquals(frame.offsetTicks(), still.offsetTicks());
    }
}
