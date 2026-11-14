package com.eternalrealm.job;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.RequestOutcome;
import com.eternalrealm.combat.SkillBinding;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;
import org.junit.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    /** The 24 skills of the first version: stored investments must stay valid, so these ids never change. */
    static final List<String> ORIGINAL_SKILLS = List.of(
            "knight_shield_bash", "knight_war_cry", "knight_iron_wall", "knight_holy_judgment",
            "assassin_shadow_step", "assassin_venom_blade", "assassin_smoke_bomb", "assassin_thousand_blades",
            "mage_fireball", "mage_ice_lance", "mage_thunderstorm", "mage_meteor",
            "ranger_rapid_shot", "ranger_spread_shot", "ranger_leap_shot", "ranger_arrow_rain",
            "priest_heal", "priest_holy_light", "priest_blessing", "priest_sanctuary",
            "spellblade_arcane_edge", "spellblade_flame_blade", "spellblade_mana_wave", "spellblade_dimension_slash");
    private static final int[] ORIGINAL_LEVELS = {1, 8, 18, 30};

    @Test
    public void sixJobsWithFiftyFourSkillsEach() {
        assertEquals(6, Jobs.selectable().size());
        assertEquals(6 * 54 + JobClass.values().length * JobClass.SKILLS, Jobs.allSkills().size());
        Set<String> actionIds = new HashSet<>();
        for (JobDefinition job : Jobs.selectable()) {
            assertEquals(Jobs.SKILLS_PER_JOB, job.skills().size());
            assertNotNull(job.starterWeapon());
            for (SkillDefinition skill : job.skills()) {
                assertTrue(skill.id(), skill.id().startsWith(job.type().id() + "_"));
                if (skill.isUsable()) {
                    assertTrue(actionIds.add(skill.action().id()));
                    assertSame(skill, Jobs.skillOfAction(skill.action().id()).orElseThrow());
                } else {
                    assertFalse(Jobs.skillOfAction(skill.id()).isPresent());
                }
            }
        }
        assertTrue(Jobs.get(JobType.NONE).skills().isEmpty());
    }

    @Test
    public void theOriginalSkillsAreTheInnateNodesWithTheirOldLevelsAndSlots() {
        int i = 0;
        for (JobDefinition job : Jobs.selectable()) {
            for (int slot = 1; slot <= 4; slot++) {
                SkillDefinition skill = job.innate(slot);
                assertEquals(ORIGINAL_SKILLS.get(i++), skill.id());
                assertEquals(ORIGINAL_LEVELS[slot - 1], skill.requiredLevel());
                assertEquals(5, skill.maxLevel());
                assertTrue(skill.prerequisites().isEmpty());
                assertEquals(slot == 4 ? SkillKind.ULTIMATE : SkillKind.ACTIVE, skill.kind());
            }
        }
        for (SkillDefinition skill : Jobs.allSkills().values()) {
            assertEquals(skill.id(), ORIGINAL_SKILLS.contains(skill.id()), skill.isInnate());
        }
    }

    @Test
    public void skillIdsNeverCollideWithWeaponActions() {
        for (var weapon : WeaponProfiles.all()) {
            assertFalse(Jobs.skill(weapon.skill().id()).isPresent());
        }
    }

    @Test
    public void weaponSetsOfTheJobs() {
        assertEquals(EnumSet.of(WeaponType.ONE_HANDED_SWORD, WeaponType.TWO_HANDED_SWORD, WeaponType.SPEAR),
                EnumSet.copyOf(Jobs.get(JobType.KNIGHT).weapons()));
        assertEquals(EnumSet.of(WeaponType.DUAL_BLADES, WeaponType.ONE_HANDED_SWORD),
                EnumSet.copyOf(Jobs.get(JobType.ASSASSIN).weapons()));
        assertEquals(EnumSet.of(WeaponType.STAFF), EnumSet.copyOf(Jobs.get(JobType.MAGE).weapons()));
        assertEquals(EnumSet.of(WeaponType.BOW, WeaponType.DUAL_BLADES), EnumSet.copyOf(Jobs.get(JobType.RANGER).weapons()));
        assertEquals(EnumSet.of(WeaponType.STAFF, WeaponType.ONE_HANDED_SWORD),
                EnumSet.copyOf(Jobs.get(JobType.PRIEST).weapons()));
        assertEquals(EnumSet.of(WeaponType.ONE_HANDED_SWORD, WeaponType.TWO_HANDED_SWORD),
                EnumSet.copyOf(Jobs.get(JobType.SPELLBLADE).weapons()));
        for (WeaponType type : WeaponType.values()) {
            assertTrue(type + " has no job", !Jobs.jobsUsing(type).isEmpty());
            assertTrue(Jobs.canUse(JobType.NONE, type));
        }
        assertEquals(List.of(JobType.KNIGHT, JobType.ASSASSIN, JobType.PRIEST, JobType.SPELLBLADE),
                Jobs.jobsUsing(WeaponType.ONE_HANDED_SWORD));
        assertEquals(List.of(JobType.RANGER), Jobs.jobsUsing(WeaponType.BOW));
        assertFalse(Jobs.canUse(JobType.MAGE, WeaponType.BOW));
    }

    /** Every weapon a job can hold is a real choice: enough of the job's actives work with it. */
    @Test
    public void everyWeaponOfAJobHasEnoughSkills() {
        for (JobDefinition job : Jobs.selectable()) {
            assertTrue(job.weapons().contains(job.starterWeapon()));
            for (WeaponType weapon : job.weapons()) {
                long usable = job.skills().stream().filter(SkillDefinition::isUsable)
                        .filter(skill -> skill.weapons().isEmpty() || skill.weapons().contains(weapon)).count();
                assertTrue(job.type() + " with " + weapon + ": " + usable, usable >= 8);
            }
            for (SkillDefinition skill : job.skills()) {
                assertTrue(skill.id(), job.weapons().containsAll(skill.weapons()));
            }
        }
    }

    @Test
    public void totalDamageOfEverySkillIsSane() {
        for (SkillDefinition skill : Jobs.allSkills().values()) {
            if (!skill.isUsable()) {
                continue;
            }
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
            int shortestUltimate = Integer.MAX_VALUE;
            int longestActive = 0;
            for (SkillDefinition skill : job.skills()) {
                if (skill.kind() == SkillKind.ULTIMATE) {
                    shortestUltimate = Math.min(shortestUltimate, skill.baseCooldownTicks());
                } else if (skill.kind() == SkillKind.ACTIVE) {
                    longestActive = Math.max(longestActive, skill.baseCooldownTicks());
                }
            }
            assertTrue(job.type() + ": " + longestActive + " < " + shortestUltimate, longestActive < shortestUltimate);
        }
    }

    @Test
    public void skillLevelsScaleDamageCooldownAndEffects() {
        SkillDefinition fireball = Jobs.skill("mage_fireball").orElseThrow();
        assertEquals(1, fireball.level(1, 0));
        assertEquals(5, fireball.level(50, 99));
        assertEquals(1.0, fireball.damageScale(1), 1e-9);
        assertEquals(1.4, fireball.damageScale(5), 1e-9);
        assertEquals(1.4, fireball.effectScale(5), 1e-9);
        assertEquals(100, fireball.cooldownTicks(1));
        assertEquals(80, fireball.cooldownTicks(5));
        SkillBinding binding = fireball.binding(3);
        assertEquals(1.2, binding.damageScale(), 1e-9);
        assertEquals(90, binding.cooldownTicks());
        assertEquals(15.0, binding.staminaCost(), 1e-9);
        assertTrue(binding.allows(WeaponProfiles.STAFF));
        assertThrows(IllegalArgumentException.class, () -> fireball.damageScale(6));
        SkillDefinition meteor = Jobs.skill("mage_meteor").orElseThrow();
        assertEquals(0, meteor.level(29, 3));
        assertEquals(1, meteor.level(30, 0));
        // a node learned with points: level = points
        SkillDefinition explosion = Jobs.skill("mage_explosion").orElseThrow();
        assertEquals(0, explosion.level(50, 0));
        assertEquals(2, explosion.level(50, 2));
        assertEquals(0, explosion.level(7, 2));
        assertEquals(5, explosion.pointsFor(5));
        assertEquals(4, fireball.pointsFor(5));
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
        // 火球 Lv5 → 爆炎
        assertEquals(SkillTree.InvestResult.PREREQUISITE,
                SkillTree.canInvest(JobType.MAGE, 10, Map.of("mage_fireball", 3), 9, "mage_explosion"));
        assertEquals(SkillTree.InvestResult.OK,
                SkillTree.canInvest(JobType.MAGE, 10, Map.of("mage_fireball", 4), 9, "mage_explosion"));
        // A Lv3 + B Lv1 → C (two prerequisites)
        Map<String, Integer> half = Map.of("assassin_quick_slash", 3, "assassin_cross_cut", 3);
        assertEquals(SkillTree.InvestResult.PREREQUISITE,
                SkillTree.canInvest(JobType.ASSASSIN, 20, half, 9, "assassin_assassinate"));
        Map<String, Integer> both = new HashMap<>(half);
        both.put("assassin_vital_strike", 1);
        assertEquals(SkillTree.InvestResult.OK, SkillTree.canInvest(JobType.ASSASSIN, 20, both, 9, "assassin_assassinate"));
        assertEquals(SkillTree.InvestResult.LOCKED, SkillTree.canInvest(JobType.ASSASSIN, 17, both, 9, "assassin_assassinate"));
    }

    @Test
    public void storedInvestmentsAreValidatedAgainstTheJob() {
        assertTrue(SkillTree.isValid(JobType.MAGE, 20, Map.of("mage_fireball", 4, "mage_thunderstorm", 1)));
        assertFalse(SkillTree.isValid(JobType.MAGE, 20, Map.of("priest_heal", 1)));
        assertFalse(SkillTree.isValid(JobType.MAGE, 17, Map.of("mage_thunderstorm", 1)));
        assertFalse(SkillTree.isValid(JobType.MAGE, 50, Map.of("mage_fireball", 5)));
        assertFalse(SkillTree.isValid(JobType.MAGE, 50, Map.of("gone_skill", 1)));
        assertTrue(SkillTree.isValid(JobType.NONE, 50, Map.of()));
        // a learned node whose prerequisite is gone is invalid; with it, it is fine (and may hold 5 points)
        assertFalse(SkillTree.isValid(JobType.MAGE, 20, Map.of("mage_explosion", 1)));
        assertTrue(SkillTree.isValid(JobType.MAGE, 20, Map.of("mage_fireball", 4, "mage_explosion", 5)));
        assertFalse(SkillTree.isValid(JobType.MAGE, 20, Map.of("mage_fireball", 4, "mage_explosion", 6)));
    }

    @Test
    public void defaultBindingsFollowUnlockLevels() {
        SkillBinding[] early = SkillTree.bindings(JobType.RANGER, 8, Map.of("ranger_rapid_shot", 2));
        assertEquals(Combatant.SKILL_SLOTS, early.length);
        assertNull(early[0]);
        assertEquals(1.2, early[1].damageScale(), 1e-9);
        assertNotNull(early[2]);
        assertNull(early[3]);
        assertNull(early[4]);
        assertNull(early[5]);
        assertNull(early[6]);
        for (SkillBinding binding : SkillTree.bindings(JobType.NONE, 50, Map.of())) {
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
        ComposedStats withSkills = ComposedStats.compose(own, 11, JobType.MAGE, StatBlock.ZERO, CombatModifiers.NONE,
                SkillTree.passives(JobType.MAGE, 11, Map.of("mage_pyromancy", 3)).modifiers());
        assertEquals(0.20 + 0.09, withSkills.modifiers().magicBonus(), 1e-9);
        ComposedStats nobody = ComposedStats.compose(own, 50, JobType.NONE, StatBlock.ZERO, CombatModifiers.NONE);
        assertEquals(own, nobody.total());
        assertEquals(CombatModifiers.NONE, nobody.modifiers());
        assertEquals(2 * 99, Jobs.get(JobType.MAGE).bonusStats(100).get(StatType.INT));
        assertEquals(StatBlock.ZERO, Jobs.get(JobType.MAGE).bonusStats(1));
        assertThrows(IllegalArgumentException.class, () -> Jobs.get(JobType.MAGE).bonusStats(0));
    }

    @Test
    public void jobChangeAndSkillResetCost() {
        JobRules rules = JobRules.defaults();
        assertEquals(0, rules.changeCost(JobType.NONE, 40));
        assertEquals(0, rules.changeCost(JobType.KNIGHT, 9));
        assertEquals(1000, rules.changeCost(JobType.KNIGHT, 10));
        assertEquals(5000, rules.changeCost(JobType.MAGE, 50));
        assertEquals(0, rules.skillResetCost(9));
        assertEquals(500, rules.skillResetCost(10));
        assertEquals(2500, rules.skillResetCost(50));
        assertEquals(50L, new JobRules(10, 100L).skillResetGoldPerLevel());
        assertThrows(IllegalArgumentException.class, () -> new JobRules(10, 100L, -1L));
    }

    @Test
    public void storedJobIdsAreTolerant() {
        assertEquals(JobType.PRIEST, JobType.fromStored("priest"));
        assertEquals(JobType.NONE, JobType.fromStored("lich_king"));
        assertEquals(JobType.NONE, JobType.fromStored(null));
        // a second class is stored as its own id and still belongs to its job
        assertEquals(JobType.MAGE, JobType.fromStored("necromancer"));
        assertEquals(JobType.KNIGHT, JobType.fromStored("guardian"));
        assertFalse(JobType.byId("guardian").isPresent());
        for (JobType type : JobType.values()) {
            assertEquals(type, JobType.byId(type.id()).orElseThrow());
        }
    }

    @Test
    public void skillsOfAJobWorkWithTheirWeaponsInTheCombatant() {
        for (JobDefinition job : Jobs.selectable()) {
            Combatant fighter = new Combatant(new CombatStats(1, 0, 0, 1, 0, 1.5, 100, 1));
            SkillBinding[] bindings = SkillTree.bindings(job.type(), 50, new HashMap<>());
            for (int slot = 1; slot < Combatant.SKILL_SLOTS; slot++) {
                fighter.setJobSkill(slot, bindings[slot]);
            }
            fighter.setAllowedWeapons(job.weapons());
            long now = 1000;
            fighter.tick(now);
            var weapon = WeaponProfiles.of(job.starterWeapon());
            assertEquals(job.type().toString(), RequestOutcome.ACCEPTED,
                    fighter.submit(CombatRequest.JOB_SKILL_1, now, weapon, false, false));
            assertEquals(job.innate(1).action(), fighter.currentAction().orElseThrow());
            assertTrue(fighter.skillCooldownRemaining(1, now) > 0);
        }
    }
}
