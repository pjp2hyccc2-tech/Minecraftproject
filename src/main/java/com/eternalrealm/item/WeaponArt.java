package com.eternalrealm.item;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 武技: the weapon skill of a boss weapon. It replaces the weapon type's own skill on the weapon-skill key while the
 * weapon is held ({@link DesignProfiles}); its hit frames deal the weapon's damage type and its effects run like the
 * effects of a job skill (ground areas, afflictions, buffs, visuals), at effect strength 1.
 *
 * @param action  the action (a SKILL with a cooldown; its id is the art's id)
 * @param theme   colours of its effects
 * @param motion  how the wielder moves while it plays (the client's arm animation)
 * @param effects what happens at the start ({@link SkillEffect#ON_START}) and at the hit frames
 */
public record WeaponArt(String id, WeaponType type, ActionDefinition action, SkillTheme theme, Motion motion,
                        List<SkillEffect> effects) {
    /** The arm motion of the art. */
    public enum Motion {
        OVERHEAD, THRUST, SPIN, CAST, SHOOT
    }

    public WeaponArt {
        Checks.id("id", id);
        if (!id.startsWith("art_")) {
            throw new IllegalArgumentException(id + ": art ids start with art_");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(action, "action");
        if (action.kind() != ActionKind.SKILL || action.cooldownTicks() <= 0 || !action.id().equals(id)) {
            throw new IllegalArgumentException(id + ": the action must be a SKILL with a cooldown and the art's id");
        }
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(motion, "motion");
        effects = List.copyOf(effects);
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
            if (effect instanceof SkillEffect.Revive || effect instanceof SkillEffect.CooldownCut) {
                throw new IllegalArgumentException(id + ": weapon arts do not revive or cut cooldowns");
            }
        }
    }

    /** Name of the art (shared with the action: {@code action.eternalrealm.<id>}). */
    public String translationKey() {
        return action.translationKey();
    }

    /** What the art does, for tooltips. */
    public String descriptionKey() {
        return "art." + ModInfo.MOD_ID + "." + id + ".desc";
    }

    /** The art's own sound event id ({@code eternalrealm:art.<name without art_>}). */
    public String soundId() {
        return ModInfo.MOD_ID + ":art." + id.substring("art_".length());
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

    /** Total damage multiplier of the hit frames (zones not included). */
    public double frameMultiplier() {
        return action.frames().stream().filter(frame -> frame.damaging())
                .mapToDouble(frame -> frame.multiplier() * (frame.isProjectile() ? frame.projectile().count() : 1))
                .sum();
    }
}
