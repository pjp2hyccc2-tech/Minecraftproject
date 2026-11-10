package com.eternalrealm.combat;

import com.eternalrealm.job.SkillTheme;
import org.junit.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The weapon skills (Z key): every type has its own staging, a visible wind-up, and the staff's skill is a spell. */
public class WeaponSkillLooksTest {

    @Test
    public void everyWeaponTypeHasItsOwnStaging() {
        Set<WeaponSkillLooks.Stage> stages = EnumSet.noneOf(WeaponSkillLooks.Stage.class);
        Set<Integer> colours = new HashSet<>();
        for (WeaponType type : WeaponType.values()) {
            WeaponSkillLooks.Look look = WeaponSkillLooks.of(type);
            assertNotNull(type.name(), look);
            assertTrue("stage used twice: " + look.stage(), stages.add(look.stage()));
            assertTrue("colour used twice: " + type, colours.add(look.rgb()));
            assertNotEquals(look.rgb(), look.accentRgb());
        }
        assertEquals(WeaponType.values().length, stages.size());
    }

    @Test
    public void everyWeaponSkillWindsUpBeforeItsFirstHit() {
        for (WeaponType type : WeaponType.values()) {
            WeaponProfile profile = WeaponProfiles.of(type);
            int windUp = WeaponSkillLooks.windUpTicks(profile);
            // long enough to see and hear the charge, and the first hit comes inside the action
            assertTrue(type + " winds up for " + windUp + " ticks", windUp >= 4);
            assertTrue(windUp < profile.skill().durationTicks());
            int[] hits = WeaponSkillLooks.hitTicks(profile);
            assertEquals(windUp, hits[0]);
            for (int i = 1; i < hits.length; i++) {
                assertTrue(hits[i] >= hits[i - 1]);
            }
        }
    }

    @Test
    public void theStaffSkillIsASpellThatBurstsWhereItLands() {
        ActionDefinition skill = WeaponProfiles.STAFF.skill();
        assertEquals(WeaponProfiles.STAR_SIGIL, skill.id());
        assertFalse(skill.frames().isEmpty());
        for (HitFrame frame : skill.frames()) {
            // no melee volume around the caster: the skill launches a bolt
            assertTrue("the staff skill must not hit in melee", frame.isProjectile());
            assertEquals(WeaponProfiles.STAR_SIGIL, frame.projectile().kind());
            assertTrue("the bolt bursts in an area", frame.projectile().explosionRadius() >= 3.0);
            assertTrue(frame.damaging());
        }
        assertEquals(ActionKind.SKILL, skill.kind());
        assertTrue(skill.cooldownTicks() > 0);
    }

    @Test
    public void theElementOfANamedWeaponColoursTheSkill() {
        WeaponType sword = WeaponType.ONE_HANDED_SWORD;
        int own = WeaponSkillLooks.of(sword).rgb();
        assertEquals(own, WeaponSkillLooks.rgb(sword, null));
        assertEquals(own, WeaponSkillLooks.rgb(sword, SkillTheme.STEEL));
        assertNotEquals(own, WeaponSkillLooks.rgb(sword, SkillTheme.FIRE));
        assertEquals(WeaponSkillLooks.rgb(WeaponType.BOW, SkillTheme.FIRE), WeaponSkillLooks.rgb(sword, SkillTheme.FIRE));
    }
}
