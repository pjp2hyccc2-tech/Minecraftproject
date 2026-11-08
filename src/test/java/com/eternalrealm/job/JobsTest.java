package com.eternalrealm.job;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.SkillBinding;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JobsTest {
    @Test
    public void sixJobsWithFourSkillsEach() {
        assertEquals(6, Jobs.selectable().size());
        assertEquals(24, Jobs.allSkills().size());
        Set<String> actionIds = new HashSet<>();
        for (JobDefinition job : Jobs.selectable()) {
            assertEquals(4, job.skills().size());
            assertNotNull(job.starterWeapon());
            for (int slot = 1; slot <= 4; slot++) {
                SkillDefinition skill = job.skill(slot);
                assertEquals(Jobs.UNLOCK_LEVELS[slot - 1], skill.unlockLevel());
                assertTrue(skill.id().startsWith(job.type().id() + "_"));
                assertTrue(actionIds.add(skill.action().id()));
                assertSame(skill, Jobs.skillOfAction(skill.action().id()).orElseThrow());
            }
            assertFalse(job.preferredWeapons().isEmpty());
            assertTrue(job.preferredWeapons().contains(job.starterWeapon()));
        }
        assertTrue(Jobs.get(JobType.NONE).skills().isEmpty());
    }

    @Test
    public void skillIdsNeverCollideWithWeaponActions() {
        for (var weapon : WeaponProfiles.all()) {
            assertFalse(Jobs.skill(weapon.skill().id()).isPresent());
        }
    }

    @Test
    public void everySkillIsUsableWithItsJobsStarterWeaponOrAnyWeapon() {
        for (JobDefinition job : Jobs.selectable()) {
            for (SkillDefinition skill : job.skills()) {
                assertTrue(skill.id(), skill.weapons().isEmpty() || skill.weapons().contains(job.starterWeapon()));
            }
        }
    }

    @Test
    public void totalDamageOfEverySkillIsSane() {
        for (SkillDefinition skill : Jobs.allSkills().values()) {
            double total = 0;
            boolean damaging = false;
            for (HitFrame frame : skill.action().frames()) {
                if (frame.damaging()) {
                    damaging = true;
                    int projectiles = frame.isProjectile() ? frame.projectile().count() : 1;
                    total += frame.multiplier() * projectiles;
                }
            }
            if (damaging) {
                assertTrue(skill.id() + " total " + total, total >= 1.0 && total <= 10.0);
            } else {
                assertFalse(skill.id() + " needs an effect", skill.effects().isEmpty());
            }
            assertTrue(skill.id(), skill.action().staminaCost() <= 40.0);
        }
    }

    @Test
    public void ultimatesHaveTheLongestCooldowns() {
        for (JobDefinition job : Jobs.selectable()) {
            int ultimate = job.skill(4).baseCooldownTicks();
            for (int slot = 1; slot <= 3; slot++) {
                assertTrue(job.type() + " slot " + slot, job.skill(slot).baseCooldownTicks() < ultimate);
            }
        }
    }

    @Test
    public void skillLevelsScaleDamageAndCooldown() {
        SkillDefinition fireball = Jobs.skill("mage_fireball").orElseThrow();
        assertEquals(1, fireball.level(1, 0));
        assertEquals(5, fireball.level(50, 99));
        assertEquals(1.0, fireball.damageScale(1), 1e-9);
        assertEquals(1.4, fireball.damageScale(5), 1e-9);
        assertEquals(100, fireball.cooldownTicks(1));
        assertEquals(80, fireball.cooldownTicks(5));
        SkillBinding binding = fireball.binding(3);
        assertEquals(1.2, binding.damageScale(), 1e-9);
        assertEquals(90, binding.cooldownTicks());
        assertTrue(binding.allows(WeaponProfiles.STAFF));
        assertFalse(binding.allows(WeaponProfiles.BOW));
        assertThrows(IllegalArgumentException.class, () -> fireball.damageScale(6));
        SkillDefinition meteor = Jobs.skill("mage_meteor").orElseThrow();
        assertEquals(0, meteor.level(29, 3));
        assertEquals(1, meteor.level(30, 0));
    }

    @Test
    public void investingFollowsTheRules() {
        Map<String, Integer> none = Map.of();
        assertEquals(SkillTree.InvestResult.NO_JOB, SkillTree.canInvest(JobType.NONE, 10, none, 3, "mage_fireball"));
        assertEquals(SkillTree.InvestResult.UNKNOWN_SKILL, SkillTree.canInvest(JobType.MAGE, 10, none, 3, "nope"));
        assertEquals(SkillTree.InvestResult.WRONG_JOB, SkillTree.canInvest(JobType.MAGE, 10, none, 3, "priest_heal"));
        assertEquals(SkillTree.InvestResult.LOCKED, SkillTree.canInvest(JobType.MAGE, 7, none, 3, "mage_ice_lance"));
        assertEquals(SkillTree.InvestResult.NO_POINTS, SkillTree.canInvest(JobType.MAGE, 10, none, 0, "mage_fireball"));
        assertEquals(SkillTree.InvestResult.OK, SkillTree.canInvest(JobType.MAGE, 10, none, 1, "mage_fireball"));
        assertEquals(SkillTree.InvestResult.MAX_LEVEL,
                SkillTree.canInvest(JobType.MAGE, 50, Map.of("mage_fireball", 4), 9, "mage_fireball"));
    }

    @Test
    public void storedInvestmentsAreValidatedAgainstTheJob() {
        assertTrue(SkillTree.isValid(JobType.MAGE, 20, Map.of("mage_fireball", 4, "mage_thunderstorm", 1)));
        assertFalse(SkillTree.isValid(JobType.MAGE, 20, Map.of("priest_heal", 1)));
        assertFalse(SkillTree.isValid(JobType.MAGE, 17, Map.of("mage_thunderstorm", 1)));
        assertFalse(SkillTree.isValid(JobType.MAGE, 50, Map.of("mage_fireball", 5)));
        assertFalse(SkillTree.isValid(JobType.MAGE, 50, Map.of("gone_skill", 1)));
        assertTrue(SkillTree.isValid(JobType.NONE, 50, Map.of()));
    }

    @Test
    public void bindingsFollowUnlockLevels() {
        SkillBinding[] early = SkillTree.bindings(JobType.RANGER, 8, Map.of("ranger_rapid_shot", 2));
        assertEquals(Combatant.SKILL_SLOTS, early.length);
        assertNull(early[0]);
        assertEquals(1.2, early[1].damageScale(), 1e-9);
        assertNotNull(early[2]);
        assertNull(early[3]);
        assertNull(early[4]);
        SkillBinding[] none = SkillTree.bindings(JobType.NONE, 50, Map.of());
        for (SkillBinding binding : none) {
            assertNull(binding);
        }
        assertEquals(4, java.util.Arrays.stream(SkillTree.bindings(JobType.PRIEST, 30, Map.of()))
                .filter(java.util.Objects::nonNull).count());
    }

    @Test
    public void jobGrowthAndPassivesCompose() {
        StatBlock own = StatBlock.uniform(5).with(StatType.INT, 25);
        ComposedStats mage = ComposedStats.compose(own, 11, JobType.MAGE, StatBlock.ZERO.with(StatType.INT, 7),
                CombatModifiers.builder().magic(0.05).build());
        assertEquals(25 + 20 + 7, mage.total().get(StatType.INT));
        assertEquals(20, mage.jobBonus().get(StatType.INT));
        assertEquals(7, mage.gearBonus().get(StatType.INT));
        assertEquals(27, mage.bonus().get(StatType.INT));
        assertEquals(0.25, mage.modifiers().magicBonus(), 1e-9);
        ComposedStats nobody = ComposedStats.compose(own, 50, JobType.NONE, StatBlock.ZERO, CombatModifiers.NONE);
        assertEquals(own, nobody.total());
        assertEquals(CombatModifiers.NONE, nobody.modifiers());
        assertEquals(2 * 99, Jobs.get(JobType.MAGE).bonusStats(100).get(StatType.INT));
        assertEquals(StatBlock.ZERO, Jobs.get(JobType.MAGE).bonusStats(1));
        assertThrows(IllegalArgumentException.class, () -> Jobs.get(JobType.MAGE).bonusStats(0));
    }

    @Test
    public void jobChangeCost() {
        JobRules rules = JobRules.defaults();
        assertEquals(0, rules.changeCost(JobType.NONE, 40));
        assertEquals(0, rules.changeCost(JobType.KNIGHT, 9));
        assertEquals(1000, rules.changeCost(JobType.KNIGHT, 10));
        assertEquals(5000, rules.changeCost(JobType.MAGE, 50));
    }

    @Test
    public void storedJobIdsAreTolerant() {
        assertEquals(JobType.PRIEST, JobType.fromStored("priest"));
        assertEquals(JobType.NONE, JobType.fromStored("necromancer"));
        for (JobType type : JobType.values()) {
            assertEquals(type, JobType.byId(type.id()).orElseThrow());
        }
    }

    @Test
    public void skillsOfAJobWorkWithTheirWeaponsInTheCombatant() {
        for (JobDefinition job : Jobs.selectable()) {
            Combatant fighter = new Combatant(new com.eternalrealm.combat.CombatStats(1, 0, 0, 1, 0, 1.5, 100, 1));
            SkillBinding[] bindings = SkillTree.bindings(job.type(), 50, new HashMap<>());
            for (int slot = 1; slot <= 4; slot++) {
                fighter.setJobSkill(slot, bindings[slot]);
            }
            long now = 1000;
            fighter.tick(now);
            var weapon = WeaponProfiles.of(job.starterWeapon());
            assertEquals(job.type().toString(), com.eternalrealm.combat.RequestOutcome.ACCEPTED,
                    fighter.submit(com.eternalrealm.combat.CombatRequest.JOB_SKILL_1, now, weapon, false, false));
            assertEquals(job.skill(1).action(), fighter.currentAction().orElseThrow());
            assertTrue(fighter.skillCooldownRemaining(1, now) > 0);
            assertTrue(WeaponType.byId(job.starterWeapon().id()).isPresent());
        }
    }
}
