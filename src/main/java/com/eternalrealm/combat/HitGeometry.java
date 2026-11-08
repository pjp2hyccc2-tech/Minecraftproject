package com.eternalrealm.combat;

import java.util.Objects;

/**
 * Hit tests of {@link HitShape}s against target boxes (docs/COMBAT_SYSTEM.md, section 6). Pure math, no line of sight
 * (the platform layer checks blocks separately).
 */
public final class HitGeometry {
    private static final double LINE_STEP = 0.2;
    private static final double EPSILON = 1.0e-6;

    private HitGeometry() {
    }

    public static boolean contains(HitShape shape, AttackOrigin origin, Box target) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        return switch (shape.kind()) {
            case ARC -> arc(shape, origin, target);
            case LINE -> line(shape, origin, target);
            case CIRCLE -> circle(shape, origin, target);
        };
    }

    /** Squared distance from the attacker's eyes to the target box (used to pick the nearest targets). */
    public static double distanceSquared(AttackOrigin origin, Box target) {
        return target.distanceSquaredTo(origin.x(), origin.eyeY(), origin.z());
    }

    /** Unit look vector {x, y, z}. */
    public static double[] look(double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        return new double[]{-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch};
    }

    /** Angle in degrees between the horizontal facing and the horizontal direction {@code (dx, dz)}; 0..180. */
    public static double horizontalAngle(double yawDegrees, double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < EPSILON) {
            return 0.0;
        }
        double yaw = Math.toRadians(yawDegrees);
        double dot = (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / length;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    private static boolean arc(HitShape shape, AttackOrigin origin, Box target) {
        if (!withinRadius(shape.reach(), origin, target) || !withinHeight(shape, origin, target, true)) {
            return false;
        }
        if (shape.halfAngleDegrees() >= 180.0 || target.horizontalDistanceSquaredTo(origin.x(), origin.z()) == 0.0) {
            return true; // all around, or the attacker stands inside the target's footprint
        }
        double dx = target.centerX() - origin.x();
        double dz = target.centerZ() - origin.z();
        double centerDistance = Math.sqrt(dx * dx + dz * dz);
        if (centerDistance < EPSILON) {
            return true;
        }
        double halfWidth = Math.max(target.maxX() - target.minX(), target.maxZ() - target.minZ()) / 2.0;
        double tolerance = Math.toDegrees(Math.atan2(halfWidth, centerDistance));
        return horizontalAngle(origin.yawDegrees(), dx, dz) <= shape.halfAngleDegrees() + tolerance;
    }

    private static boolean line(HitShape shape, AttackOrigin origin, Box target) {
        if (!withinRadius(shape.reach(), origin, target)) {
            return false; // the width widens the thrust sideways, never beyond its tip
        }
        double[] dir = look(origin.yawDegrees(), origin.pitchDegrees());
        double widthSquared = shape.width() * shape.width();
        double step = Math.min(LINE_STEP, shape.width());
        int samples = (int) Math.ceil(shape.reach() / step);
        for (int i = 0; i <= samples; i++) {
            double t = Math.min(shape.reach(), i * step);
            double px = origin.x() + dir[0] * t;
            double py = origin.eyeY() + dir[1] * t;
            double pz = origin.z() + dir[2] * t;
            if (target.horizontalDistanceSquaredTo(px, pz) > widthSquared) {
                continue;
            }
            double gap = target.verticalGap(py); // > 0: target below the line, < 0: above
            if ((gap >= 0.0 && gap <= shape.verticalDown()) || (gap < 0.0 && -gap <= shape.verticalUp())) {
                return true;
            }
        }
        return false;
    }

    private static boolean circle(HitShape shape, AttackOrigin origin, Box target) {
        if (!withinHeight(shape, origin, target, false)) {
            return false;
        }
        if (shape.offsetForward() <= 0.0) {
            return withinRadius(shape.reach(), origin, target);
        }
        double[] center = circleCenter(shape, origin);
        return target.horizontalDistanceSquaredTo(center[0], center[1]) <= shape.reach() * shape.reach();
    }

    /** Horizontal centre {x, z} of a CIRCLE shape (moved ahead along the facing by its offset). */
    public static double[] circleCenter(HitShape shape, AttackOrigin origin) {
        double yaw = Math.toRadians(origin.yawDegrees());
        return new double[]{origin.x() - Math.sin(yaw) * shape.offsetForward(),
                origin.z() + Math.cos(yaw) * shape.offsetForward()};
    }

    private static boolean withinRadius(double radius, AttackOrigin origin, Box target) {
        return target.horizontalDistanceSquaredTo(origin.x(), origin.z()) <= radius * radius;
    }

    /** Vertical window around the eyes; for ARC it extends towards where the attacker looks (up or down). */
    private static boolean withinHeight(HitShape shape, AttackOrigin origin, Box target, boolean followPitch) {
        double up = shape.verticalUp();
        double down = shape.verticalDown();
        if (followPitch) {
            double sinPitch = Math.sin(Math.toRadians(origin.pitchDegrees()));
            if (sinPitch < 0.0) {
                up += -sinPitch * shape.reach();
            } else {
                down += sinPitch * shape.reach();
            }
        }
        return target.maxY() >= origin.eyeY() - down && target.minY() <= origin.eyeY() + up;
    }
}
