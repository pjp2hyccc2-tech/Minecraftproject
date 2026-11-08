package com.eternalrealm.boss;

import com.eternalrealm.combat.HitGeometry;

import java.util.Objects;

/**
 * Area of a boss attack in absolute world coordinates, fixed when its telegraph starts. The platform draws the
 * telegraph from it and later tests every player with {@link #contains(double, double, double)}.
 *
 * <p>Tests use the player's feet position: horizontally against the shape, vertically within
 * {@link BossRules#VERTICAL_TOLERANCE} of the shape's height (jumping does not avoid a ground attack). There is no line
 * of sight: telegraphs are the rule players can read, pillars do not protect. Yaw follows Minecraft (0 = +Z/south,
 * 90 = -X/west), like {@link HitGeometry}.</p>
 */
public sealed interface BossShape permits BossShape.Circle, BossShape.Cone, BossShape.Line, BossShape.Ring,
        BossShape.Arena {

    /** Whether a player whose feet are at {@code (x, y, z)} is inside. */
    boolean contains(double x, double y, double z);

    /** Where the shape is anchored (center or origin); its height is the ground height of the shape. */
    Point anchor();

    private static boolean withinHeight(Point anchor, double y) {
        return Math.abs(y - anchor.y()) <= BossRules.VERTICAL_TOLERANCE;
    }

    /** Disc of {@code radius} around {@code center}. */
    record Circle(Point center, double radius) implements BossShape {
        public Circle {
            Objects.requireNonNull(center, "center");
            Check.range("radius", radius, 0.1, 64.0);
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return withinHeight(center, y) && center.horizontalDistanceSquared(x, z) <= radius * radius;
        }

        @Override
        public Point anchor() {
            return center;
        }
    }

    /** Sector from {@code origin} facing {@code yawDegrees}, out to {@code range}, {@code halfAngleDegrees} each side. */
    record Cone(Point origin, double yawDegrees, double range, double halfAngleDegrees) implements BossShape {
        public Cone {
            Objects.requireNonNull(origin, "origin");
            Check.finite("yawDegrees", yawDegrees);
            Check.range("range", range, 0.5, 64.0);
            Check.range("halfAngleDegrees", halfAngleDegrees, 0.1, 180.0);
        }

        @Override
        public boolean contains(double x, double y, double z) {
            if (!withinHeight(origin, y) || origin.horizontalDistanceSquared(x, z) > range * range) {
                return false;
            }
            return HitGeometry.horizontalAngle(yawDegrees, x - origin.x(), z - origin.z()) <= halfAngleDegrees;
        }

        @Override
        public Point anchor() {
            return origin;
        }
    }

    /** Horizontal strip from {@code origin} to {@code end}; {@code width} is the full width (half on each side). */
    record Line(Point origin, Point end, double width) implements BossShape {
        public Line {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(end, "end");
            Check.range("width", width, 0.1, 32.0);
        }

        @Override
        public boolean contains(double x, double y, double z) {
            if (!withinHeight(origin, y)) {
                return false;
            }
            double segmentX = end.x() - origin.x();
            double segmentZ = end.z() - origin.z();
            double lengthSquared = segmentX * segmentX + segmentZ * segmentZ;
            double t = lengthSquared == 0.0 ? 0.0
                    : ((x - origin.x()) * segmentX + (z - origin.z()) * segmentZ) / lengthSquared;
            t = Math.max(0.0, Math.min(1.0, t));
            double closestX = origin.x() + segmentX * t;
            double closestZ = origin.z() + segmentZ * t;
            double dx = x - closestX;
            double dz = z - closestZ;
            double half = width / 2.0;
            return dx * dx + dz * dz <= half * half;
        }

        @Override
        public Point anchor() {
            return origin;
        }

        /** Horizontal length of the strip. */
        public double length() {
            return Math.sqrt(origin.horizontalDistanceSquared(end.x(), end.z()));
        }
    }

    /** Annulus: hit if the horizontal distance from {@code center} is between {@code inner} and {@code outer}. */
    record Ring(Point center, double inner, double outer) implements BossShape {
        public Ring {
            Objects.requireNonNull(center, "center");
            Check.range("inner", inner, 0.0, 64.0);
            Check.range("outer", outer, inner, 64.0);
        }

        @Override
        public boolean contains(double x, double y, double z) {
            double distanceSquared = center.horizontalDistanceSquared(x, z);
            return withinHeight(center, y) && distanceSquared >= inner * inner && distanceSquared <= outer * outer;
        }

        @Override
        public Point anchor() {
            return center;
        }
    }

    /** Everyone in the fight; {@code center} is where the boss stood (for effects). */
    record Arena(Point center) implements BossShape {
        public Arena {
            Objects.requireNonNull(center, "center");
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return true;
        }

        @Override
        public Point anchor() {
            return center;
        }
    }
}
