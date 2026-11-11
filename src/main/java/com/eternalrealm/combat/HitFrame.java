package com.eternalrealm.combat;

import java.util.Objects;

/**
 * One damage (or effect) instant of an action.
 *
 * @param offsetTicks ticks after the action starts (before attack-speed scaling)
 * @param shape       melee volume tested at this instant (ignored when {@code projectile} is set or not damaging)
 * @param multiplier  damage multiplier of this frame
 * @param knockback   knockback strength applied to targets (vanilla default for a hit is 0.4)
 * @param projectile  launches projectiles instead of a melee sweep, or {@code null}
 * @param damaging    {@code false} for pure effect instants of skills (heals, buffs): nothing is hit
 */
public record HitFrame(int offsetTicks, HitShape shape, double multiplier, double knockback,
                       ProjectileSpec projectile, boolean damaging) {
    /** Projectile and effect frames need no melee volume; this placeholder is never tested. */
    public static final HitShape NO_SHAPE = HitShape.circle(0.5);

    public HitFrame {
        Check.range("offsetTicks", offsetTicks, 0, 200);
        Objects.requireNonNull(shape, "shape");
        Check.range("multiplier", multiplier, 0.01, 20.0);
        Check.range("knockback", knockback, 0.0, 5.0);
        if (!damaging && projectile != null) {
            throw new IllegalArgumentException("an effect frame cannot launch projectiles");
        }
    }

    /** A melee frame. */
    public HitFrame(int offsetTicks, HitShape shape, double multiplier, double knockback) {
        this(offsetTicks, shape, multiplier, knockback, null, true);
    }

    /** A frame launching projectiles. */
    public static HitFrame launch(int offsetTicks, ProjectileSpec projectile, double multiplier, double knockback) {
        return new HitFrame(offsetTicks, NO_SHAPE, multiplier, knockback,
                Objects.requireNonNull(projectile, "projectile"), true);
    }

    /** An instant that hits nothing; the skill's effects for this frame (heal, buff...) happen here. */
    public static HitFrame effect(int offsetTicks) {
        return new HitFrame(offsetTicks, NO_SHAPE, 1.0, 0.0, null, false);
    }

    public boolean isProjectile() {
        return projectile != null;
    }

    /** This frame with another knockback (the same frame if it is unchanged). */
    public HitFrame withKnockback(double value) {
        return value == knockback ? this : new HitFrame(offsetTicks, shape, multiplier, value, projectile, damaging);
    }
}
