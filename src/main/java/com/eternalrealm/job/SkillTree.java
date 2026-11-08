package com.eternalrealm.job;

import com.eternalrealm.combat.Combatant;
import com.eternalrealm.combat.SkillBinding;

import java.util.Map;
import java.util.Objects;

/**
 * Rules for investing skill points. Pure functions over the character's level, job and investments, used by the
 * server (validation) and the client (journal display, predicted skill bindings).
 */
public final class SkillTree {
    public enum InvestResult {
        OK,
        NO_JOB,
        UNKNOWN_SKILL,
        WRONG_JOB,
        LOCKED,
        MAX_LEVEL,
        NO_POINTS;

        public String translationKey() {
            return "message.eternalrealm.skill." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private SkillTree() {
    }

    /** Whether one more point may go into {@code skillId}. */
    public static InvestResult canInvest(JobType job, int characterLevel, Map<String, Integer> investments,
                                         int unspentPoints, String skillId) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(investments, "investments");
        if (!job.isChosen()) {
            return InvestResult.NO_JOB;
        }
        SkillDefinition skill = Jobs.skill(skillId).orElse(null);
        if (skill == null) {
            return InvestResult.UNKNOWN_SKILL;
        }
        if (skill.job() != job) {
            return InvestResult.WRONG_JOB;
        }
        int level = skill.level(characterLevel, investments.getOrDefault(skillId, 0));
        if (level == 0) {
            return InvestResult.LOCKED;
        }
        if (level >= skill.maxLevel()) {
            return InvestResult.MAX_LEVEL;
        }
        return unspentPoints < 1 ? InvestResult.NO_POINTS : InvestResult.OK;
    }

    /**
     * Whether stored investments are still valid for the job (after loading or a rule change): every invested skill
     * belongs to the job, is unlocked and stays within its maximum level.
     */
    public static boolean isValid(JobType job, int characterLevel, Map<String, Integer> investments) {
        for (Map.Entry<String, Integer> entry : investments.entrySet()) {
            SkillDefinition skill = Jobs.skill(entry.getKey()).orElse(null);
            if (skill == null || skill.job() != job || characterLevel < skill.unlockLevel()
                    || 1 + entry.getValue() > skill.maxLevel()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The bindings for the combat state machine: index {@code slot} (1..4) holds the skill of that slot, or
     * {@code null} while it is locked. Index 0 (the weapon skill) is always {@code null}.
     */
    public static SkillBinding[] bindings(JobType job, int characterLevel, Map<String, Integer> investments) {
        SkillBinding[] bindings = new SkillBinding[Combatant.SKILL_SLOTS];
        if (!job.isChosen()) {
            return bindings;
        }
        for (SkillDefinition skill : Jobs.get(job).skills()) {
            int level = skill.level(characterLevel, investments.getOrDefault(skill.id(), 0));
            if (level > 0) {
                bindings[skill.slot()] = skill.binding(level);
            }
        }
        return bindings;
    }

    /** Level of {@code skill} for the character (0 = locked). */
    public static int levelOf(SkillDefinition skill, int characterLevel, Map<String, Integer> investments) {
        return skill.level(characterLevel, investments.getOrDefault(skill.id(), 0));
    }
}
