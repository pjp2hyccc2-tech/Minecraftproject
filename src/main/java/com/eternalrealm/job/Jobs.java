package com.eternalrealm.job;

import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.WeaponType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The six jobs and their skill trees (docs/modules/skills.md): 54 skills per job in three branches of six tiers, the
 * four original skills of each job as innate nodes. Defined in code so the client's prediction and the server's
 * validation always agree.
 *
 * <p>Weapons by job (enforced for attacks, skills and the guard): 騎士 片手剣・両手剣・槍, 暗殺者 双剣・片手剣,
 * 魔導士 杖, 狩人 弓・双剣, 神官 杖・片手剣, 魔剣士 片手剣・両手剣. Without a job every weapon works.</p>
 *
 * <p>From level {@value JobClass#LEVEL} a job grows into one of its two second classes ({@link JobClass}), each with a
 * tree of {@value JobClass#SKILLS} more skills ({@link #classSkills}).</p>
 */
public final class Jobs {
    public static final int MAX_SKILL_LEVEL = 5;
    /** Nodes of every job's tree. */
    public static final int SKILLS_PER_JOB = SkillDefinition.BRANCHES * SkillDefinition.COLUMNS * SkillDefinition.TIERS;

    private static final Map<JobType, JobDefinition> BY_TYPE = new EnumMap<>(JobType.class);
    private static final Map<JobClass, List<SkillDefinition>> CLASS_SKILLS = new EnumMap<>(JobClass.class);
    private static final Map<String, SkillDefinition> SKILLS = new LinkedHashMap<>();

    static {
        register(new JobDefinition(JobType.NONE, JobRole.NONE, StatBlock.ZERO, CombatModifiers.NONE, Set.of(), null,
                1.0, List.of()));
        register(new JobDefinition(JobType.KNIGHT, JobRole.TANK, growth(StatType.STR, 1, StatType.VIT, 1),
                CombatModifiers.builder().healthPercent(0.15).guard(0.10).defense(10).build(),
                Set.of(WeaponType.ONE_HANDED_SWORD, WeaponType.TWO_HANDED_SWORD, WeaponType.SPEAR),
                WeaponType.ONE_HANDED_SWORD, 2.0, KnightSkills.tree()));
        register(new JobDefinition(JobType.ASSASSIN, JobRole.MELEE, growth(StatType.AGI, 1, StatType.LUK, 1),
                CombatModifiers.builder().crit(0.08).critDamage(0.25).dodgeCost(0.8).build(),
                Set.of(WeaponType.DUAL_BLADES, WeaponType.ONE_HANDED_SWORD), WeaponType.DUAL_BLADES, 1.0,
                AssassinSkills.tree()));
        register(new JobDefinition(JobType.MAGE, JobRole.CASTER, growth(StatType.INT, 2, null, 0),
                CombatModifiers.builder().magic(0.20).stamina(20).build(),
                Set.of(WeaponType.STAFF), WeaponType.STAFF, 1.0, MageSkills.tree()));
        register(new JobDefinition(JobType.RANGER, JobRole.RANGED, growth(StatType.DEX, 1, StatType.AGI, 1),
                CombatModifiers.builder().ranged(0.15).moveSpeed(0.05).build(),
                Set.of(WeaponType.BOW, WeaponType.DUAL_BLADES), WeaponType.BOW, 1.0, RangerSkills.tree()));
        register(new JobDefinition(JobType.PRIEST, JobRole.HEALER, growth(StatType.INT, 1, StatType.VIT, 1),
                CombatModifiers.builder().healing(0.30).healthPercent(0.05).magic(0.05).build(),
                Set.of(WeaponType.STAFF, WeaponType.ONE_HANDED_SWORD), WeaponType.STAFF, 0.8, PriestSkills.tree()));
        register(new JobDefinition(JobType.SPELLBLADE, JobRole.HYBRID, growth(StatType.STR, 1, StatType.INT, 1),
                CombatModifiers.builder().intToPhysical(0.5).magic(0.10).build(),
                Set.of(WeaponType.ONE_HANDED_SWORD, WeaponType.TWO_HANDED_SWORD), WeaponType.ONE_HANDED_SWORD, 1.2,
                SpellbladeSkills.tree()));
        registerClass(JobClass.GUARDIAN, GuardianSkills.tree());
        registerClass(JobClass.PALADIN, PaladinSkills.tree());
        registerClass(JobClass.SHINOBI, ShinobiSkills.tree());
        registerClass(JobClass.REAPER, ReaperSkills.tree());
        registerClass(JobClass.SAGE, SageSkills.tree());
        registerClass(JobClass.NECROMANCER, NecromancerSkills.tree());
        registerClass(JobClass.SNIPER, SniperSkills.tree());
        registerClass(JobClass.SPIRIT_ARCHER, SpiritArcherSkills.tree());
        registerClass(JobClass.BISHOP, BishopSkills.tree());
        registerClass(JobClass.INQUISITOR, InquisitorSkills.tree());
        registerClass(JobClass.SWORD_SAINT, SwordSaintSkills.tree());
        registerClass(JobClass.HEXBLADE, HexbladeSkills.tree());
    }

    private Jobs() {
    }

    public static JobDefinition get(JobType type) {
        return BY_TYPE.get(type);
    }

    /** The real jobs in display order. */
    public static List<JobDefinition> selectable() {
        List<JobDefinition> result = new ArrayList<>();
        for (JobType type : JobType.values()) {
            if (type.isChosen()) {
                result.add(BY_TYPE.get(type));
            }
        }
        return result;
    }

    public static Optional<SkillDefinition> skill(String id) {
        return Optional.ofNullable(SKILLS.get(id));
    }

    /** The tree of a second class (its {@value JobClass#SKILLS} nodes in tree order). */
    public static List<SkillDefinition> classSkills(JobClass jobClass) {
        return CLASS_SKILLS.getOrDefault(jobClass, List.of());
    }

    /** The skills that list {@code id} as a prerequisite, in its job's tree or its class's. */
    public static List<SkillDefinition> unlockedBy(String id) {
        return SKILLS.values().stream().filter(skill -> skill.prerequisites().stream()
                .anyMatch(prerequisite -> prerequisite.skillId().equals(id))).toList();
    }

    /** Skill whose action has {@code actionId} (skill and action ids are equal); passives have no action. */
    public static Optional<SkillDefinition> skillOfAction(String actionId) {
        return skill(actionId).filter(SkillDefinition::isUsable);
    }

    public static Map<String, SkillDefinition> allSkills() {
        return Collections.unmodifiableMap(SKILLS);
    }

    /** Whether a character of {@code job} may use weapons of {@code type} (no job: any weapon). */
    public static boolean canUse(JobType job, WeaponType type) {
        return get(job).canUse(type);
    }

    /** The jobs that can use {@code type}, in display order (the equipment tooltip's "usable by" line). */
    public static List<JobType> jobsUsing(WeaponType type) {
        List<JobType> jobs = new ArrayList<>();
        for (JobDefinition job : selectable()) {
            if (job.weapons().contains(type)) {
                jobs.add(job.type());
            }
        }
        return jobs;
    }

    /**
     * A second class's tree: {@value JobClass#SKILLS} nodes of the class filling its two branches of four columns and
     * five tiers, prerequisites inside the tree and without cycles.
     */
    private static void registerClass(JobClass jobClass, List<SkillDefinition> skills) {
        Map<String, SkillDefinition> byId = new LinkedHashMap<>();
        Set<Integer> cells = new java.util.HashSet<>();
        JobDefinition job = BY_TYPE.get(jobClass.base());
        for (SkillDefinition skill : skills) {
            if (skill.jobClass() != jobClass || byId.put(skill.id(), skill) != null) {
                throw new ExceptionInInitializerError(jobClass + ": skill " + skill.id() + " is duplicated or not its own");
            }
            if (!cells.add(skill.gridColumn() * 100 + skill.tier())) {
                throw new ExceptionInInitializerError(jobClass + ": two skills in the tree cell of " + skill.id());
            }
            if (!job.weapons().containsAll(skill.weapons())) {
                throw new ExceptionInInitializerError(skill.id() + ": uses a weapon the job cannot use");
            }
        }
        if (skills.size() != JobClass.SKILLS) {
            throw new ExceptionInInitializerError(jobClass + " has " + skills.size() + " skills");
        }
        for (SkillDefinition skill : skills) {
            for (Prerequisite prerequisite : skill.prerequisites()) {
                SkillDefinition required = byId.get(prerequisite.skillId());
                if (required == null || prerequisite.level() > required.maxLevel()
                        || required.tier() >= skill.tier()) {
                    throw new ExceptionInInitializerError(skill.id() + ": prerequisite " + prerequisite
                            + " is not an earlier node of the class");
                }
            }
        }
        CLASS_SKILLS.put(jobClass, List.copyOf(skills));
        for (SkillDefinition skill : skills) {
            if (SKILLS.put(skill.id(), skill) != null) {
                throw new ExceptionInInitializerError("Duplicate skill id " + skill.id());
            }
        }
    }

    private static void register(JobDefinition job) {
        BY_TYPE.put(job.type(), job);
        for (SkillDefinition skill : job.skills()) {
            if (SKILLS.put(skill.id(), skill) != null) {
                throw new ExceptionInInitializerError("Duplicate skill id " + skill.id());
            }
        }
    }

    private static StatBlock growth(StatType first, int firstValue, StatType second, int secondValue) {
        StatBlock block = StatBlock.ZERO.with(first, firstValue);
        return second == null ? block : block.with(second, secondValue);
    }
}
