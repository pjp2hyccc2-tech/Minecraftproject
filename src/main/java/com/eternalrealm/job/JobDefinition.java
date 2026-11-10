package com.eternalrealm.job;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.WeaponType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everything that makes a job different.
 *
 * @param growthPerLevel   stats gained automatically for every level above 1 (on top of the free stat points)
 * @param passive          constant secondary bonuses of the job
 * @param weapons          the weapon types the job can use at all (attacks, skills and the guard; enforced by the
 *                         combat state machine); the job without a choice ({@link JobType#NONE}) uses any weapon
 * @param starterWeapon    weapon handed out when the job is chosen for the first time (one of {@code weapons})
 * @param threatMultiplier how strongly bosses prefer this job as their target (damage × multiplier = threat)
 * @param skills           the job's skill tree: every node, innate skills included (tree order: branch, tier, column)
 */
public record JobDefinition(JobType type, JobRole role, StatBlock growthPerLevel, CombatModifiers passive,
                            Set<WeaponType> weapons, WeaponType starterWeapon, double threatMultiplier,
                            List<SkillDefinition> skills) {
    public JobDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(growthPerLevel, "growthPerLevel");
        Objects.requireNonNull(passive, "passive");
        weapons = weapons.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(weapons));
        Checks.range("threatMultiplier", threatMultiplier, 0.1, 10.0);
        skills = List.copyOf(skills);
        if (type.isChosen()) {
            Objects.requireNonNull(starterWeapon, "starterWeapon");
            if (!weapons.contains(starterWeapon)) {
                throw new IllegalArgumentException(type + ": the starter weapon must be one of the job's weapons");
            }
        }
        Map<String, SkillDefinition> byId = new HashMap<>();
        Set<Integer> cells = new HashSet<>();
        boolean[] innate = new boolean[5];
        for (SkillDefinition skill : skills) {
            if (skill.job() != type || byId.put(skill.id(), skill) != null) {
                throw new IllegalArgumentException(type + ": skill " + skill.id() + " is duplicated or of another job");
            }
            if (!cells.add(skill.gridColumn() * 100 + skill.tier())) {
                throw new IllegalArgumentException(type + ": two skills in the tree cell of " + skill.id());
            }
            if (skill.isInnate()) {
                if (innate[skill.innateSlot()]) {
                    throw new IllegalArgumentException(type + ": two innate skills for slot " + skill.innateSlot());
                }
                innate[skill.innateSlot()] = true;
            }
            if (!weapons.containsAll(skill.weapons())) {
                throw new IllegalArgumentException(skill.id() + ": uses a weapon the job cannot use");
            }
        }
        if (type.isChosen()) {
            for (int slot = 1; slot <= 4; slot++) {
                if (!innate[slot]) {
                    throw new IllegalArgumentException(type + ": no innate skill for slot " + slot);
                }
            }
        }
        for (SkillDefinition skill : skills) {
            for (Prerequisite prerequisite : skill.prerequisites()) {
                SkillDefinition required = byId.get(prerequisite.skillId());
                if (required == null || prerequisite.level() > required.maxLevel()) {
                    throw new IllegalArgumentException(skill.id() + ": prerequisite " + prerequisite
                            + " is not a reachable skill of the job");
                }
            }
        }
        checkAcyclic(type, byId);
    }

    private static void checkAcyclic(JobType type, Map<String, SkillDefinition> byId) {
        Map<String, Integer> state = new HashMap<>();
        for (String id : byId.keySet()) {
            visit(type, id, byId, state);
        }
    }

    /** Depth-first search: 1 = on the current path, 2 = done. */
    private static void visit(JobType type, String id, Map<String, SkillDefinition> byId, Map<String, Integer> state) {
        Integer current = state.get(id);
        if (current != null) {
            if (current == 1) {
                throw new IllegalArgumentException(type + ": prerequisite cycle through " + id);
            }
            return;
        }
        state.put(id, 1);
        for (Prerequisite prerequisite : byId.get(id).prerequisites()) {
            visit(type, prerequisite.skillId(), byId, state);
        }
        state.put(id, 2);
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

    /** Whether the job can use weapons of {@code type} (no job: every weapon). */
    public boolean canUse(WeaponType type) {
        return !this.type.isChosen() || weapons.contains(type);
    }

    /** The innate skill of slot {@code 1..4} (4 = the ultimate). */
    public SkillDefinition innate(int slot) {
        Checks.range("slot", slot, 1, 4);
        for (SkillDefinition skill : skills) {
            if (skill.innateSlot() == slot) {
                return skill;
            }
        }
        throw new IllegalStateException(type + " has no innate skill for slot " + slot);
    }

    public Optional<SkillDefinition> skill(String id) {
        for (SkillDefinition skill : skills) {
            if (skill.id().equals(id)) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    /** The skills that list {@code id} as a prerequisite (what learning it opens up). */
    public List<SkillDefinition> unlockedBy(String id) {
        return skills.stream().filter(skill -> skill.prerequisites().stream()
                .anyMatch(prerequisite -> prerequisite.skillId().equals(id))).toList();
    }

    /** Skills by id, in tree order. */
    public Map<String, SkillDefinition> skillsById() {
        Map<String, SkillDefinition> map = new LinkedHashMap<>();
        for (SkillDefinition skill : skills) {
            map.put(skill.id(), skill);
        }
        return Collections.unmodifiableMap(map);
    }

    /** Translation key of a branch title ({@code 0..2}). */
    public String branchKey(int branch) {
        Checks.range("branch", branch, 0, SkillDefinition.BRANCHES - 1);
        return "skill_branch.eternalrealm." + type.id() + "." + branch;
    }
}
