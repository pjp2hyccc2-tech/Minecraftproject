package com.eternalrealm.job;

import java.util.Objects;

/**
 * A skill that must be learned up to {@code level} before a tree node can be learned ("火球 Lv5 → 爆炎").
 *
 * @param skillId a skill of the same job
 * @param level   required level of that skill, {@code 1..5}
 */
public record Prerequisite(String skillId, int level) {
    public Prerequisite {
        Checks.id("skillId", skillId);
        Checks.range("level", level, 1, SkillDefinition.MAX_SKILL_LEVEL);
    }

    @Override
    public String toString() {
        return Objects.toString(skillId) + " Lv" + level;
    }
}
