package com.eternalrealm.job;

import com.eternalrealm.combat.SkillBinding;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The second classes (owner, 2026-11-14: 「職業をレベル40でニ次職に進化できるようにして2つ枝分かれをつくって…枝分かれの
 * 場合、枝分かれで選んだ方のスキルとナイトの本来のスキルしか使えなくする」): every job grows into one of two classes at
 * level 40; a character of a class may learn and use its job's skills and its own class's, never the other class's.
 */
public class SecondClassTest {
    private static final Map<String, Integer> NOTHING = Map.of();

    @Test
    public void everyJobHasTwoClassesOfItsOwn() {
        assertEquals(12, JobClass.values().length);
        assertEquals(40, JobClass.LEVEL);
        for (JobDefinition job : Jobs.selectable()) {
            List<JobClass> classes = JobClass.of(job.type());
            assertEquals(job.type().toString(), 2, classes.size());
            for (JobClass jobClass : classes) {
                assertEquals(job.type(), jobClass.base());
                assertFalse("a class id is never a job id", JobType.byId(jobClass.id()).isPresent());
                assertEquals(jobClass, JobClass.byId(jobClass.id()).orElseThrow());
                assertEquals(job.type(), JobType.fromStored(jobClass.id()));
                assertEquals("job.eternalrealm." + jobClass.id(), jobClass.translationKey());
                assertEquals("skill_branch.eternalrealm." + jobClass.id() + ".1", jobClass.branchKey(1));
            }
        }
        assertTrue(JobClass.of(JobType.NONE).isEmpty());
        assertTrue(JobClass.byId("knight").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> JobClass.GUARDIAN.branchKey(2));
    }

    @Test
    public void theStoredIdNamesTheJobAndTheClass() {
        JobPath knight = JobPath.of("knight");
        assertEquals(JobType.KNIGHT, knight.job());
        assertNull(knight.advanced());
        assertFalse(knight.isAdvanced());
        assertEquals("knight", knight.storedId());
        assertEquals("job.eternalrealm.knight", knight.translationKey());
        JobPath guardian = JobPath.of("guardian");
        assertEquals(JobType.KNIGHT, guardian.job());
        assertEquals(JobClass.GUARDIAN, guardian.advanced());
        assertTrue(guardian.isAdvanced());
        assertEquals("guardian", guardian.storedId());
        assertEquals("job.eternalrealm.guardian", guardian.translationKey());
        assertEquals(JobPath.NONE, JobPath.of(null));
        assertEquals(JobType.NONE, JobPath.of("lich_king").job());
        assertEquals(JobPath.base(JobType.MAGE), JobPath.of("mage"));
        assertThrows(IllegalArgumentException.class, () -> new JobPath(JobType.MAGE, JobClass.GUARDIAN));
    }

    @Test
    public void aClassOwnsItsJobsSkillsAndItsOwnButNeverTheOtherClasses() {
        SkillDefinition jobSkill = Jobs.skill("knight_shield_bash").orElseThrow();
        SkillDefinition guardianSkill = Jobs.skill("guardian_titan_stance").orElseThrow();
        SkillDefinition paladinSkill = Jobs.skill("paladin_radiant_edge").orElseThrow();
        SkillDefinition mageSkill = Jobs.skill("mage_fireball").orElseThrow();
        JobPath guardian = JobPath.of("guardian");
        assertTrue(guardian.owns(jobSkill));
        assertTrue(guardian.owns(guardianSkill));
        assertFalse(guardian.owns(paladinSkill));
        assertFalse(guardian.owns(mageSkill));
        JobPath knight = JobPath.base(JobType.KNIGHT);
        assertTrue(knight.owns(jobSkill));
        assertFalse("before advancing no class's skills", knight.owns(guardianSkill));
        assertFalse(knight.owns(paladinSkill));
        assertFalse(JobPath.NONE.owns(jobSkill));
        assertEquals(Jobs.SKILLS_PER_JOB + JobClass.SKILLS, guardian.skills().size());
        assertTrue(guardian.skills().contains(guardianSkill));
        assertFalse(guardian.skills().contains(paladinSkill));
        assertEquals(Jobs.get(JobType.KNIGHT).skills(), knight.skills());
        assertTrue(JobPath.NONE.skills().isEmpty());
    }

    @Test
    public void aClassTreeOpensAtLevelFortyForItsClassOnly() {
        JobPath guardian = JobPath.of("guardian");
        assertEquals(SkillTree.InvestResult.OK, SkillTree.canInvest(guardian, 40, NOTHING, 1, "guardian_titan_stance"));
        assertEquals(SkillTree.InvestResult.LOCKED,
                SkillTree.canInvest(guardian, 39, NOTHING, 1, "guardian_titan_stance"));
        assertEquals(SkillTree.InvestResult.WRONG_JOB,
                SkillTree.canInvest(guardian, 60, NOTHING, 1, "paladin_radiant_edge"));
        assertEquals(SkillTree.InvestResult.WRONG_JOB,
                SkillTree.canInvest(JobType.KNIGHT, 60, NOTHING, 1, "guardian_titan_stance"));
        assertEquals("the job's own tree stays open", SkillTree.InvestResult.OK,
                SkillTree.canInvest(guardian, 40, NOTHING, 1, "knight_provoke"));
        assertEquals(SkillTree.InvestResult.PREREQUISITE,
                SkillTree.canInvest(guardian, 60, NOTHING, 1, "guardian_bastion"));
        assertEquals(SkillTree.InvestResult.OK,
                SkillTree.canInvest(guardian, 60, Map.of("guardian_titan_stance", 3), 1, "guardian_bastion"));
        assertEquals(SkillTree.InvestResult.LOCKED,
                SkillTree.canInvest(guardian, 43, Map.of("guardian_titan_stance", 3), 1, "guardian_bastion"));
    }

    @Test
    public void storedInvestmentsAreValidForTheirClassOnly() {
        Map<String, Integer> investments = Map.of("knight_provoke", 2, "guardian_titan_stance", 3);
        assertTrue(SkillTree.isValid(JobPath.of("guardian"), 45, investments));
        assertFalse(SkillTree.isValid(JobPath.of("paladin"), 45, investments));
        assertFalse(SkillTree.isValid(JobType.KNIGHT, 45, investments));
        assertFalse("below level 40 a class's nodes are out of reach",
                SkillTree.isValid(JobPath.of("guardian"), 39, investments));
    }

    @Test
    public void changingClassKeepsTheJobsPointsAndRefundsTheOldClasses() {
        Map<String, Integer> investments = new HashMap<>(Map.of("knight_provoke", 2, "guardian_titan_stance", 3,
                "guardian_stone_skin", 1));
        assertEquals(Map.of("knight_provoke", 2), SkillTree.keptInvestments(JobPath.of("paladin"), investments));
        assertEquals(investments, SkillTree.keptInvestments(JobPath.of("guardian"), investments));
        assertEquals(Map.of("knight_provoke", 2), SkillTree.keptInvestments(JobPath.base(JobType.KNIGHT), investments));
        assertTrue(SkillTree.keptInvestments(JobPath.of("mage"), investments).isEmpty());
    }

    @Test
    public void onlyTheJobsAndTheClassesSkillsGoIntoTheSlotsAndIntoCombat() {
        Map<String, Integer> learned = Map.of("knight_provoke", 1, "guardian_titan_stance", 2,
                "paladin_radiant_edge", 1);
        JobPath guardian = JobPath.of("guardian");
        assertEquals(SkillTree.LoadoutResult.OK, SkillTree.canAssign(guardian, 45, learned, 1, "guardian_titan_stance"));
        assertEquals(SkillTree.LoadoutResult.OK, SkillTree.canAssign(guardian, 45, learned, 2, "knight_provoke"));
        assertEquals(SkillTree.LoadoutResult.WRONG_JOB,
                SkillTree.canAssign(guardian, 45, learned, 3, "paladin_radiant_edge"));
        assertEquals(SkillTree.LoadoutResult.WRONG_JOB,
                SkillTree.canAssign(JobType.KNIGHT, 45, learned, 1, "guardian_titan_stance"));
        List<String> stored = List.of("guardian_titan_stance", "knight_provoke", "paladin_radiant_edge", "", "", "");
        List<String> effective = SkillTree.effectiveLoadout(guardian, 45, learned, stored);
        assertEquals(List.of("guardian_titan_stance", "knight_provoke", "", "", "", ""), effective);
        assertEquals(effective, SkillTree.sanitize(guardian, 45, learned, stored));
        SkillBinding[] bindings = SkillTree.bindings(guardian, 45, learned, stored);
        assertNotNull(bindings[1]);
        assertEquals("guardian_titan_stance", bindings[1].action().id());
        assertNotNull(bindings[2]);
        assertNull("the other class's skill never binds", bindings[3]);
        // the knight before advancing: its class's slot is empty
        assertNull(SkillTree.bindings(JobType.KNIGHT, 45, learned, stored)[1]);
    }

    @Test
    public void aClassesPassivesCountOnlyForTheClass() {
        Map<String, Integer> learned = Map.of("guardian_stone_skin", 3, "paladin_devotion", 3);
        PassiveTotals guardian = SkillTree.passives(JobPath.of("guardian"), 45, learned);
        PassiveTotals paladin = SkillTree.passives(JobPath.of("paladin"), 45, learned);
        PassiveTotals knight = SkillTree.passives(JobType.KNIGHT, 45, learned);
        assertTrue(guardian.modifiers().defenseFlat() > 0.0);
        assertEquals(0.0, paladin.modifiers().defenseFlat(), 1e-9);
        assertTrue(paladin.modifiers().physicalBonus() > 0.0);
        assertEquals(0.0, guardian.modifiers().physicalBonus(), 1e-9);
        assertEquals(0.0, knight.modifiers().defenseFlat(), 1e-9);
        assertEquals(0.0, knight.modifiers().physicalBonus(), 1e-9);
    }
}
