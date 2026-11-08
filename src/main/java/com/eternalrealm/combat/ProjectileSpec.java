package com.eternalrealm.combat;

/**
 * A hit frame that launches projectiles instead of sweeping a melee shape. The platform layer spawns them from the
 * attacker's eyes along the look direction; each projectile carries the damage rolled for the frame and applies it to
 * what it hits (to everything in {@link #explosionRadius()} around the impact point if that is positive).
 *
 * @param kind            visual/behaviour id understood by the platform layer (e.g. {@code arrow}, {@code fireball})
 * @param speed           blocks per tick
 * @param gravity         whether the projectile falls (arrows) or flies straight (magic)
 * @param lifetimeTicks   the projectile disappears after this long without hitting anything
 * @param pierce          additional targets a projectile passes through before it stops (0 = stops at the first)
 * @param explosionRadius area damage radius at the impact point, 0 = single target
 * @param count           projectiles launched by the frame
 * @param spreadDegrees   horizontal fan of {@code count > 1} projectiles (total opening angle)
 * @param fromAbove       launched straight down from {@link #FROM_ABOVE_HEIGHT} blocks above the point the attacker
 *                        looks at (meteors, arrow rain) instead of from the attacker's eyes
 */
public record ProjectileSpec(String kind, double speed, boolean gravity, int lifetimeTicks, int pierce,
                             double explosionRadius, int count, double spreadDegrees, boolean fromAbove) {
    public static final double FROM_ABOVE_HEIGHT = 14.0;
    /** How far ahead a {@link #fromAbove} projectile may land (the look ray is clipped to this). */
    public static final double FROM_ABOVE_RANGE = 24.0;

    public ProjectileSpec {
        Check.id("kind", kind);
        Check.range("speed", speed, 0.1, 6.0);
        Check.range("lifetimeTicks", lifetimeTicks, 1, 200);
        Check.range("pierce", pierce, 0, 16);
        Check.range("explosionRadius", explosionRadius, 0.0, 12.0);
        Check.range("count", count, 1, 16);
        Check.range("spreadDegrees", spreadDegrees, 0.0, 180.0);
        if (count == 1 && spreadDegrees > 0.0) {
            throw new IllegalArgumentException("a single projectile cannot spread");
        }
    }

    /** One straight, non-falling projectile. */
    public static ProjectileSpec magic(String kind, double speed, int lifetimeTicks) {
        return new ProjectileSpec(kind, speed, false, lifetimeTicks, 0, 0.0, 1, 0.0, false);
    }

    /** One arrow (falls). */
    public static ProjectileSpec arrow(double speed) {
        return new ProjectileSpec("arrow", speed, true, 60, 0, 0.0, 1, 0.0, false);
    }

    public ProjectileSpec withPierce(int value) {
        return new ProjectileSpec(kind, speed, gravity, lifetimeTicks, value, explosionRadius, count, spreadDegrees,
                fromAbove);
    }

    public ProjectileSpec withExplosion(double radius) {
        return new ProjectileSpec(kind, speed, gravity, lifetimeTicks, pierce, radius, count, spreadDegrees, fromAbove);
    }

    public ProjectileSpec withFan(int projectiles, double spread) {
        return new ProjectileSpec(kind, speed, gravity, lifetimeTicks, pierce, explosionRadius, projectiles, spread,
                fromAbove);
    }

    public ProjectileSpec fallingFromAbove() {
        return new ProjectileSpec(kind, speed, gravity, lifetimeTicks, pierce, explosionRadius, count, spreadDegrees,
                true);
    }

    /**
     * Horizontal yaw offsets (degrees) of the {@link #count()} projectiles, symmetric around the look direction.
     */
    public double[] yawOffsets() {
        double[] offsets = new double[count];
        if (count == 1) {
            return offsets;
        }
        double step = spreadDegrees / (count - 1);
        for (int i = 0; i < count; i++) {
            offsets[i] = -spreadDegrees / 2.0 + step * i;
        }
        return offsets;
    }
}
