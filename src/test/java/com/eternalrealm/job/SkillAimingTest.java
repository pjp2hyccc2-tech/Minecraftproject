package com.eternalrealm.job;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.ProjectileSpec;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.item.WeaponArt;
import com.eternalrealm.item.WeaponArts;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkillAimingTest {
    private static ActionDefinition skill(HitFrame... frames) {
        return new ActionDefinition("test_skill", ActionKind.SKILL, 30, 28, Arrays.asList(frames), 10.0, 100, 0, false);
    }

    private static HitFrame melee(HitShape shape) {
        return new HitFrame(5, shape, 1.0, 0.2);
    }

    @Test
    public void areasAroundTheCasterAreNotAimed() {
        assertFalse(SkillAiming.isAimed(skill(melee(HitShape.circle(3.5))), List.of()));
        assertFalse("a heal", SkillAiming.isAimed(skill(HitFrame.effect(4)),
                List.of(new SkillEffect.Heal(0, 6.0, 0.2, 0.0))));
        assertFalse("an area at the caster's feet", SkillAiming.isAimed(skill(HitFrame.effect(4)), List.of(
                new SkillEffect.Zone(0, SkillEffect.Placement.CASTER, 0.0, 5.0, 0, 3, 10, 0.5, 0.0, null, 0, 0.0))));
        assertFalse("a step back", SkillAiming.isAimed(skill(melee(HitShape.circle(3.0))),
                List.of(new SkillEffect.Dash(0, 4.0, true))));
    }

    @Test
    public void directionalAndPlacedSkillsAreAimed() {
        assertTrue("slash", SkillAiming.isAimed(skill(melee(HitShape.arc(3.6, 70))), List.of()));
        assertTrue("thrust", SkillAiming.isAimed(skill(melee(HitShape.line(7.5, 0.9))), List.of()));
        assertTrue("disc ahead", SkillAiming.isAimed(skill(melee(HitShape.circleAhead(3.0, 4.0))), List.of()));
        assertTrue("bolt", SkillAiming.isAimed(skill(HitFrame.launch(6, ProjectileSpec.magic("bolt", 1.5, 30), 1.0,
                0.2)), List.of()));
        assertTrue("area where it looks", SkillAiming.isAimed(skill(HitFrame.effect(4)), List.of(
                new SkillEffect.Zone(0, SkillEffect.Placement.LOOK, 14.0, 4.5, 0, 3, 10, 0.5, 0.0, null, 0, 0.0))));
        assertTrue("charge", SkillAiming.isAimed(skill(melee(HitShape.circle(3.0))),
                List.of(new SkillEffect.Dash(0, 6.0, false))));
    }

    @Test
    public void rangeFollowsTheFarthestHit() {
        assertEquals("a short slash still looks a little ahead", SkillAiming.MIN_RANGE,
                SkillAiming.range(skill(melee(HitShape.arc(3.0, 60))), List.of()), 1.0e-9);
        assertEquals(8.5, SkillAiming.range(skill(melee(HitShape.line(7.5, 0.9))), List.of()), 1.0e-9);
        assertEquals("a long bolt is capped", SkillAiming.MAX_RANGE, SkillAiming.range(skill(
                HitFrame.launch(6, ProjectileSpec.magic("bolt", 1.5, 30), 1.0, 0.2)), List.of()), 1.0e-9);
        assertEquals(19.5, SkillAiming.range(skill(HitFrame.effect(4)), List.of(new SkillEffect.Zone(0,
                SkillEffect.Placement.LOOK, 14.0, 4.5, 0, 3, 10, 0.5, 0.0, null, 0, 0.0))), 1.0e-9);
        assertEquals("a charge adds its distance", 10.0, SkillAiming.range(skill(melee(HitShape.circle(3.0))),
                List.of(new SkillEffect.Dash(0, 6.0, false))), 1.0e-9);
    }

    @Test
    public void everySkillOfTheGameIsClassified() {
        int aimed = 0;
        int areas = 0;
        for (JobDefinition job : Jobs.selectable()) {
            for (SkillDefinition skill : job.skills()) {
                if (skill.action() == null) {
                    assertFalse(skill.id() + " is passive", SkillAiming.isAimed(skill));
                    continue;
                }
                double range = SkillAiming.range(skill);
                assertTrue(skill.id() + " range " + range,
                        range >= SkillAiming.MIN_RANGE && range <= SkillAiming.MAX_RANGE);
                if (SkillAiming.isAimed(skill)) {
                    aimed++;
                } else {
                    areas++;
                }
            }
        }
        assertTrue("some job skills are aimed: " + aimed, aimed > 20);
        assertTrue("area skills stay as they are: " + areas, areas > 20);
        for (WeaponProfile weapon : WeaponProfiles.all()) {
            SkillAiming.isAimed(weapon.skill(), List.of());
        }
        for (WeaponArt art : WeaponArts.all()) {
            SkillAiming.isAimed(art.action(), art.effects());
        }
        assertTrue("the bow's weapon skill shoots", SkillAiming.isAimed(WeaponProfiles.BOW.skill(), List.of()));
    }
}
