package com.eternalrealm.job;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.WeaponType;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything that makes a job different.
 *
 * @param growthPerLevel   stats gained automatically for every level above 1 (on top of the free stat points)
 * @param passive          constant secondary bonuses of the job
 * @param starterWeapon    weapon handed out when the job is chosen for the first time
 * @param threatMultiplier how strongly bosses prefer this job as their target (damage × multiplier = threat)
 * @param skills           the job's skills, one per slot 1..4 in slot order
 */
public record JobDefinition(JobType type, JobRole role, StatBlock growthPerLevel, CombatModifiers passive,
                            Set<WeaponType> preferredWeapons, WeaponType starterWeapon, double threatMultiplier,
                            List<SkillDefinition> skills) {
    public JobDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(growthPerLevel, "growthPerLevel");
        Objects.requireNonNull(passive, "passive");
        preferredWeapons = preferredWeapons.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(preferredWeapons));
        Checks.range("threatMultiplier", threatMultiplier, 0.1, 10.0);
        skills = List.copyOf(skills);
        if (type.isChosen()) {
            Objects.requireNonNull(starterWeapon, "starterWeapon");
            if (skills.size() != 4) {
                throw new IllegalArgumentException(type + ": a job has exactly 4 skills");
            }
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < skills.size(); i++) {
            SkillDefinition skill = skills.get(i);
            if (skill.job() != type || skill.slot() != i + 1 || !ids.add(skill.id())) {
                throw new IllegalArgumentException(type + ": skill " + skill.id() + " is in the wrong job/slot");
            }
        }
    }

    /** Stats from the job at {@code level}: {@code growthPerLevel × (level − 1)}, saturating. */
    public StatBlock bonusStats(int level) {
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1: " + level);
        }
        StatBlock result = StatBlock.ZERO;
        for (StatType stat : StatType.values()) {
            long value = (long) growthPerLevel.get(stat) * (level - 1);
            result = result.with(stat, (int) Math.min(StatBlock.MAX_VALUE, value));
        }
        return result;
    }

    public SkillDefinition skill(int slot) {
        return skills.get(Checks.range("slot", slot, 1, skills.size()) - 1);
    }
}
