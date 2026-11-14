package com.eternalrealm.job;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The second classes (二次職; owner, 2026-11-14: 「職業をレベル40でニ次職に進化できるようにして2つ枝分かれをつくって、
 * ナイトなら守護士か聖騎士、みたいなのをそれぞれに作って」): at level {@value #LEVEL} a job grows into one of its two
 * classes, which brings a tree of {@value #SKILLS} skills of its own. A character of a class keeps its job: weapons,
 * outfit, the job's tree and its innate skills stay; it may use the job's skills and its class's, not the other
 * class's. {@link #id()} is stored as the character's job id after the advance (so {@link JobType#fromStored} still
 * names the job): never rename it.
 */
public enum JobClass {
    /** 守護士: the wall that does not fall — shields, provocation, holding the line for the party. */
    GUARDIAN("guardian", JobType.KNIGHT, JobRole.TANK),
    /** 聖騎士: the holy knight — light on the blade, auras, healing the line while leading the charge. */
    PALADIN("paladin", JobType.KNIGHT, JobRole.HYBRID),
    /** 忍者: shadow arts and ninjutsu — clones, smoke, fire, water and lightning techniques, speed. */
    SHINOBI("shinobi", JobType.ASSASSIN, JobRole.MELEE),
    /** 死神: the harvest of souls — marks of death, life drained, executions. */
    REAPER("reaper", JobType.ASSASSIN, JobRole.MELEE),
    /** 賢者: mastery of every element and the arts that keep a party standing. */
    SAGE("sage", JobType.MAGE, JobRole.CASTER),
    /** 冥術士: curses, the drinking of life, the gates of the underworld. */
    NECROMANCER("necromancer", JobType.MAGE, JobRole.CASTER),
    /** 狙撃手: the shot from afar that does not miss — focus, weak points, the long line. */
    SNIPER("sniper", JobType.RANGER, JobRole.RANGED),
    /** 精霊弓士: arrows of the spirits — wind, thunder, ice and the forest answer the bow. */
    SPIRIT_ARCHER("spirit_archer", JobType.RANGER, JobRole.RANGED),
    /** 司教: the great healer — sanctuaries, barriers, the raising of the fallen. */
    BISHOP("bishop", JobType.PRIEST, JobRole.HEALER),
    /** 審問官: the judge of light — sentences, holy fire, unbending faith. */
    INQUISITOR("inquisitor", JobType.PRIEST, JobRole.HYBRID),
    /** 剣聖: the sword itself — flawless technique, speed, cut upon cut. */
    SWORD_SAINT("sword_saint", JobType.SPELLBLADE, JobRole.MELEE),
    /** 呪剣士: the cursed blade — blood paid for power, darkness on the edge. */
    HEXBLADE("hexblade", JobType.SPELLBLADE, JobRole.HYBRID);

    /** The level a job grows into a class at. */
    public static final int LEVEL = 40;
    /** Skills of every class's tree. */
    public static final int SKILLS = 40;

    private final String id;
    private final JobType base;
    private final JobRole role;

    JobClass(String id, JobType base, JobRole role) {
        this.id = id;
        this.base = base;
        this.role = role;
    }

    public String id() {
        return id;
    }

    /** The job the class grows from. */
    public JobType base() {
        return base;
    }

    public JobRole role() {
        return role;
    }

    public String translationKey() {
        return "job." + ModInfo.MOD_ID + "." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    /** Translation key of a branch title of the class's tree ({@code 0..1}). */
    public String branchKey(int branch) {
        Checks.range("branch", branch, 0, SkillDefinition.CLASS_BRANCHES - 1);
        return "skill_branch." + ModInfo.MOD_ID + "." + id + "." + branch;
    }

    /** The two classes of {@code job} (none for {@link JobType#NONE}). */
    public static List<JobClass> of(JobType job) {
        List<JobClass> classes = new ArrayList<>(2);
        for (JobClass value : values()) {
            if (value.base == job) {
                classes.add(value);
            }
        }
        return classes;
    }

    public static Optional<JobClass> byId(String id) {
        for (JobClass value : values()) {
            if (value.id.equals(id)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
