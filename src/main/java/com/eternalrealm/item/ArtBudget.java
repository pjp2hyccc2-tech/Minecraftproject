package com.eternalrealm.item;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.SkillEffect;

import java.util.List;

/**
 * The damage budget of a weapon skill: its burst (the damage multipliers of one use: every damaging frame, every
 * projectile of a fan, every pulse of a damaging ground area) and its burst per second of cooldown. Weapon arts are
 * measured against the weapon type's own skill: tier arts a little above it, the arts of named weapons above those,
 * the boss arts the strongest. Only damage counts; afflictions, heals and buffs are the art's flavour.
 */
public final class ArtBudget {
    private ArtBudget() {
    }

    public static double burst(ActionDefinition action, List<SkillEffect> effects) {
        double total = 0.0;
        for (HitFrame frame : action.frames()) {
            if (frame.damaging()) {
                int count = frame.isProjectile() ? Math.max(1, frame.projectile().count()) : 1;
                total += frame.multiplier() * count;
            }
        }
        for (SkillEffect effect : effects) {
            if (effect instanceof SkillEffect.Zone zone) {
                total += zone.damageMultiplier() * zone.pulses();
            }
        }
        return total;
    }

    public static double burst(WeaponArt art) {
        return burst(art.action(), art.effects());
    }

    /** Burst per second of cooldown. */
    public static double perSecond(WeaponArt art) {
        return burst(art) / (art.action().cooldownTicks() / 20.0);
    }

    /** The burst of the weapon type's own skill: the reference. */
    public static double typeBurst(WeaponType type) {
        return burst(WeaponProfiles.of(type).skill(), List.of());
    }

    public static double typePerSecond(WeaponType type) {
        ActionDefinition skill = WeaponProfiles.of(type).skill();
        return typeBurst(type) / (skill.cooldownTicks() / 20.0);
    }
}
