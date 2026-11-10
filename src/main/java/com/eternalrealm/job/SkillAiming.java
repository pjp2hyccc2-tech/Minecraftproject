package com.eternalrealm.job;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.ProjectileSpec;

import java.util.List;

/**
 * Which skills can be aimed at one enemy by holding their key (owner's request of 2026-11-11: hard-to-land skills
 * lock on to the enemy aimed at while the key is held; area attacks stay as they are). A skill is aimed when where it
 * lands depends on where the caster faces: projectiles, slashes and thrusts, discs placed ahead, ground areas placed
 * where the caster looks or ahead, dashes and leaps. Skills that only hit a disc around the caster, buff or heal are
 * not aimed. Pure; the client decides how to send the key, the server turns the caster toward the locked enemy.
 */
public final class SkillAiming {
    /** Nearest and farthest an aimed skill looks for its enemy (blocks). */
    public static final double MIN_RANGE = 6.0;
    public static final double MAX_RANGE = 32.0;

    private SkillAiming() {
    }

    /** Whether holding the key of {@code action} (with the skill's {@code effects}) aims it at an enemy. */
    public static boolean isAimed(ActionDefinition action, List<SkillEffect> effects) {
        for (HitFrame frame : action.frames()) {
            if (frame.isProjectile()) {
                return true;
            }
            if (frame.damaging() && frame.shape() != HitFrame.NO_SHAPE
                    && (frame.shape().kind() != HitShape.Kind.CIRCLE || frame.shape().offsetForward() > 0.0)) {
                return true;
            }
        }
        for (SkillEffect effect : effects) {
            if (effect instanceof SkillEffect.Zone zone && zone.placement() != SkillEffect.Placement.CASTER
                    || effect instanceof SkillEffect.Mark mark && mark.placement() != SkillEffect.Placement.CASTER
                    || effect instanceof SkillEffect.Dash dash && !dash.backwards()
                    || effect instanceof SkillEffect.Leap leap && leap.distance() > 0.0) {
                return true;
            }
        }
        return false;
    }

    /** Whether the job skill is aimed (passive skills, without an action, are not). */
    public static boolean isAimed(SkillDefinition skill) {
        return skill.action() != null && isAimed(skill.action(), skill.effects());
    }

    /**
     * How far away an enemy may be for the skill to reach it: the farthest its hits, projectiles and placed areas go
     * (a dash adds its distance to the reach of the hits), between {@link #MIN_RANGE} and {@link #MAX_RANGE}.
     */
    public static double range(ActionDefinition action, List<SkillEffect> effects) {
        double reach = 0.0;
        for (HitFrame frame : action.frames()) {
            if (frame.isProjectile()) {
                ProjectileSpec spec = frame.projectile();
                reach = Math.max(reach, spec.fromAbove() ? ProjectileSpec.FROM_ABOVE_RANGE
                        : spec.speed() * spec.lifetimeTicks());
            } else if (frame.damaging() && frame.shape() != HitFrame.NO_SHAPE) {
                reach = Math.max(reach, frame.shape().reach() + frame.shape().offsetForward());
            }
        }
        double dash = 0.0;
        for (SkillEffect effect : effects) {
            if (effect instanceof SkillEffect.Zone zone && zone.placement() != SkillEffect.Placement.CASTER) {
                reach = Math.max(reach, zone.distance() + zone.radius());
            } else if (effect instanceof SkillEffect.Mark mark && mark.placement() != SkillEffect.Placement.CASTER) {
                reach = Math.max(reach, mark.distance() + mark.radius());
            } else if (effect instanceof SkillEffect.Dash move && !move.backwards()) {
                dash = Math.max(dash, move.distance());
            } else if (effect instanceof SkillEffect.Leap leap) {
                dash = Math.max(dash, leap.distance());
            }
        }
        return Math.max(MIN_RANGE, Math.min(MAX_RANGE, reach + dash + 1.0));
    }

    public static double range(SkillDefinition skill) {
        return skill.action() == null ? MIN_RANGE : range(skill.action(), skill.effects());
    }
}
