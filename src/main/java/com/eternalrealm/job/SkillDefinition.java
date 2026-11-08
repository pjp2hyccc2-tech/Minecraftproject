package com.eternalrealm.job;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.SkillBinding;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One job skill. A skill is unlocked (level 1) when the character reaches {@link #unlockLevel()}; every invested skill
 * point raises its level by one up to {@link #maxLevel()}. Each level above 1 adds
 * {@value #DAMAGE_PER_LEVEL} to the damage scale and shortens the cooldown by {@value #COOLDOWN_PER_LEVEL}.
 *
 * @param slot              job skill slot 1..4 (4 = the ultimate); the action's id equals the skill id
 * @param baseCooldownTicks cooldown at skill level 1
 * @param weapons           weapon types the skill can be used with; empty = any weapon
 */
public record SkillDefinition(String id, JobType job, int slot, int unlockLevel, int maxLevel, ActionDefinition action,
                              int baseCooldownTicks, DamageType damageType, Set<WeaponType> weapons,
                              List<SkillEffect> effects) {
    public static final double DAMAGE_PER_LEVEL = 0.10;
    public static final double COOLDOWN_PER_LEVEL = 0.05;
    public static final int MAX_SKILL_LEVEL = 10;

    public SkillDefinition {
        Checks.id("id", id);
        Objects.requireNonNull(job, "job");
        if (!job.isChosen()) {
            throw new IllegalArgumentException(id + ": skills belong to a real job");
        }
        Checks.range("slot", slot, 1, 4);
        Checks.range("unlockLevel", unlockLevel, 1, 100);
        Checks.range("maxLevel", maxLevel, 1, MAX_SKILL_LEVEL);
        Objects.requireNonNull(action, "action");
        if (action.kind() != ActionKind.SKILL || !action.id().equals(id)) {
            throw new IllegalArgumentException(id + ": the action must be a SKILL with the skill's id");
        }
        Checks.range("baseCooldownTicks", baseCooldownTicks, 1, 20 * 600);
        Objects.requireNonNull(damageType, "damageType");
        weapons = weapons.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(weapons));
        effects = List.copyOf(effects);
        for (SkillEffect effect : effects) {
            if (effect.frame() >= action.frames().size()) {
                throw new IllegalArgumentException(id + ": effect at frame " + effect.frame() + " but the action has "
                        + action.frames().size() + " frame(s)");
            }
            if (effect instanceof SkillEffect.Afflict && !action.frames().get(effect.frame()).damaging()) {
                throw new IllegalArgumentException(id + ": afflictions need a damaging frame");
            }
        }
    }

    public String translationKey() {
        return "skill." + ModInfo.MOD_ID + "." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    /** Skill level for a character of {@code characterLevel} with {@code investment} points: 0 = locked. */
    public int level(int characterLevel, int investment) {
        if (characterLevel < unlockLevel) {
            return 0;
        }
        return Math.min(maxLevel, 1 + Math.max(0, investment));
    }

    public double damageScale(int level) {
        return 1.0 + DAMAGE_PER_LEVEL * (Checks.range("level", level, 1, maxLevel) - 1);
    }

    public int cooldownTicks(int level) {
        double factor = 1.0 - COOLDOWN_PER_LEVEL * (Checks.range("level", level, 1, maxLevel) - 1);
        return Math.max(1, (int) Math.round(baseCooldownTicks * factor));
    }

    /** What the combat state machine needs for {@code level} (1..maxLevel). */
    public SkillBinding binding(int level) {
        return new SkillBinding(action, weapons, damageScale(level), cooldownTicks(level));
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
}
