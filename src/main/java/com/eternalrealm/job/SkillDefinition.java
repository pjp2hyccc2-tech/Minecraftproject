package com.eternalrealm.job;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.SkillBinding;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One node of a job's skill tree (docs/modules/skills.md).
 *
 * <p><b>Learning.</b> A node is learned with skill points: every point raises its level by one up to
 * {@link #maxLevel()}, once the character has reached {@link #requiredLevel()} and every {@link #prerequisites()
 * prerequisite} has its required level. The four <em>innate</em> skills of a job (the original skills, {@link
 * #innateSlot()} 1..4) are learned for free at level 1 as soon as the character reaches their level, so their
 * stored investment means "points above level 1" exactly as before the tree existed.</p>
 *
 * <p><b>Levels.</b> Each level above 1 adds {@value #DAMAGE_PER_LEVEL} to the damage scale, shortens the cooldown by
 * {@value #COOLDOWN_PER_LEVEL} and strengthens the effects by {@value #EFFECT_PER_LEVEL} ({@link #effectScale}).
 * Passive values are multiplied by the level.</p>
 *
 * @param kind              active, passive or ultimate
 * @param theme             look and sound of the skill (platform layer)
 * @param branch            tree column group {@code 0..2} (each job has three branches)
 * @param column            column inside the branch {@code 0..2}
 * @param tier              tree row {@code 1..6}; deeper rows need higher levels
 * @param requiredLevel     character level needed to learn the node
 * @param innateSlot        {@code 1..4}: an innate skill and its default slot; {@code 0}: learned with points
 * @param action            the action of an active/ultimate (its id is the skill id); {@code null} for passives
 * @param baseCooldownTicks cooldown at skill level 1 ({@code 0} for passives)
 * @param weapons           weapon types an active can be used with; empty = every weapon of the job
 * @param prerequisites     nodes that must be learned first (same job)
 * @param passives          per-level bonuses of a passive (empty for actives)
 */
public record SkillDefinition(String id, JobType job, SkillKind kind, SkillTheme theme, int branch, int column,
                              int tier, int requiredLevel, int innateSlot, int maxLevel, ActionDefinition action,
                              int baseCooldownTicks, DamageType damageType, Set<WeaponType> weapons,
                              List<SkillEffect> effects, List<Prerequisite> prerequisites,
                              List<PassiveEffect> passives) {
    public static final double DAMAGE_PER_LEVEL = 0.10;
    public static final double COOLDOWN_PER_LEVEL = 0.05;
    public static final double EFFECT_PER_LEVEL = 0.10;
    /** Upper bound of any node's maximum level (the catalogue uses 3 and 5). */
    public static final int MAX_SKILL_LEVEL = 10;
    public static final int BRANCHES = 3;
    public static final int COLUMNS = 3;
    public static final int TIERS = 6;

    public SkillDefinition {
        Checks.id("id", id);
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(theme, "theme");
        if (!job.isChosen()) {
            throw new IllegalArgumentException(id + ": skills belong to a real job");
        }
        Checks.range("branch", branch, 0, BRANCHES - 1);
        Checks.range("column", column, 0, COLUMNS - 1);
        Checks.range("tier", tier, 1, TIERS);
        Checks.range("requiredLevel", requiredLevel, 1, 100);
        Checks.range("innateSlot", innateSlot, 0, 4);
        Checks.range("maxLevel", maxLevel, 1, MAX_SKILL_LEVEL);
        Objects.requireNonNull(damageType, "damageType");
        weapons = weapons.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(weapons));
        effects = List.copyOf(effects);
        prerequisites = List.copyOf(prerequisites);
        passives = List.copyOf(passives);
        Set<String> required = new HashSet<>();
        for (Prerequisite prerequisite : prerequisites) {
            if (prerequisite.skillId().equals(id) || !required.add(prerequisite.skillId())) {
                throw new IllegalArgumentException(id + ": invalid prerequisite " + prerequisite);
            }
        }
        if (innateSlot > 0 && !prerequisites.isEmpty()) {
            throw new IllegalArgumentException(id + ": innate skills have no prerequisites");
        }
        if ((innateSlot == 4 && kind != SkillKind.ULTIMATE) || (innateSlot > 0 && innateSlot < 4 && kind != SkillKind.ACTIVE)) {
            throw new IllegalArgumentException(id + ": innate slot 4 is the ultimate, 1..3 are actives");
        }
        if (kind == SkillKind.PASSIVE) {
            if (action != null || baseCooldownTicks != 0 || !effects.isEmpty() || passives.isEmpty()
                    || innateSlot != 0 || !weapons.isEmpty()) {
                throw new IllegalArgumentException(id + ": a passive has bonuses and nothing else");
            }
        } else {
            Objects.requireNonNull(action, "action");
            if (action.kind() != ActionKind.SKILL || !action.id().equals(id)) {
                throw new IllegalArgumentException(id + ": the action must be a SKILL with the skill's id");
            }
            Checks.range("baseCooldownTicks", baseCooldownTicks, 1, 20 * 600);
            if (!passives.isEmpty()) {
                throw new IllegalArgumentException(id + ": only passives have passive bonuses");
            }
            for (SkillEffect effect : effects) {
                if (effect.frame() >= action.frames().size()) {
                    throw new IllegalArgumentException(id + ": effect at frame " + effect.frame() + " but the action has "
                            + action.frames().size() + " frame(s)");
                }
                boolean needsHit = effect instanceof SkillEffect.Afflict || effect instanceof SkillEffect.Siphon
                        || effect instanceof SkillEffect.Execute;
                if (needsHit && !action.frames().get(effect.frame()).damaging()) {
                    throw new IllegalArgumentException(id + ": afflictions, siphons and executes need a damaging frame");
                }
            }
        }
    }

    public String translationKey() {
        return "skill." + ModInfo.MOD_ID + "." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    public boolean isInnate() {
        return innateSlot > 0;
    }

    public boolean isPassive() {
        return kind == SkillKind.PASSIVE;
    }

    /** Placed in skill slots (actives and ultimates). */
    public boolean isUsable() {
        return kind.usable();
    }

    /** Column of the whole tree ({@code 0..8}): branches side by side. */
    public int gridColumn() {
        return branch * COLUMNS + column;
    }

    /**
     * Learned level for a character of {@code characterLevel} with {@code investment} points: 0 = not learned. Does
     * not check prerequisites (they were checked when the points were invested, see {@link SkillTree#isValid}).
     */
    public int level(int characterLevel, int investment) {
        if (characterLevel < requiredLevel) {
            return 0;
        }
        int points = Math.max(0, investment);
        return Math.min(maxLevel, isInnate() ? 1 + points : points);
    }

    /** Points a learned level costs ({@code level - 1} for innate skills). */
    public int pointsFor(int level) {
        Checks.range("level", level, 0, maxLevel);
        return isInnate() ? Math.max(0, level - 1) : level;
    }

    public double damageScale(int level) {
        return 1.0 + DAMAGE_PER_LEVEL * (Checks.range("level", level, 1, maxLevel) - 1);
    }

    public int cooldownTicks(int level) {
        double factor = 1.0 - COOLDOWN_PER_LEVEL * (Checks.range("level", level, 1, maxLevel) - 1);
        return Math.max(1, (int) Math.round(baseCooldownTicks * factor));
    }

    /** Multiplier of effect strengths (heals, shields, buffs, afflictions) at {@code level}. */
    public double effectScale(int level) {
        return 1.0 + EFFECT_PER_LEVEL * (Checks.range("level", level, 1, maxLevel) - 1);
    }

    /** What the combat state machine needs for {@code level} (1..maxLevel), without passive bonuses. */
    public SkillBinding binding(int level) {
        return binding(level, PassiveTotals.NONE);
    }

    /** The binding at {@code level} with the character's passive skill power, cooldown and stamina bonuses. */
    public SkillBinding binding(int level, PassiveTotals passive) {
        if (!isUsable()) {
            throw new IllegalStateException(id + " is a passive");
        }
        Objects.requireNonNull(passive, "passive");
        double scale = damageScale(level) * (1.0 + passive.skillPower());
        int cooldown = Math.max(1, (int) Math.round(cooldownTicks(level) * (1.0 - passive.cooldownReduction())));
        double stamina = action.staminaCost() * (1.0 - passive.staminaReduction());
        return new SkillBinding(action, weapons, scale, cooldown, stamina);
    }

    /** Effects happening at {@code frame} ({@link SkillEffect#ON_START} for the start). */
    public List<SkillEffect> effectsAt(int frame) {
        List<SkillEffect> result = new ArrayList<>();
        for (SkillEffect effect : effects) {
            if (effect.frame() == frame) {
                result.add(effect);
            }
        }
        return result;
    }

    /** Number of damaging hits (projectiles counted one by one) of an active; 0 for passives. */
    public int hitCount() {
        if (action == null) {
            return 0;
        }
        int hits = 0;
        for (HitFrame frame : action.frames()) {
            if (frame.damaging()) {
                hits += frame.isProjectile() ? frame.projectile().count() : 1;
            }
        }
        return hits;
    }

    /** Sum of the damage multipliers of every hit (projectiles counted one by one) at level 1. */
    public double totalMultiplier() {
        if (action == null) {
            return 0.0;
        }
        double total = 0.0;
        for (HitFrame frame : action.frames()) {
            if (frame.damaging()) {
                total += frame.multiplier() * (frame.isProjectile() ? frame.projectile().count() : 1);
            }
        }
        return total;
    }
}
