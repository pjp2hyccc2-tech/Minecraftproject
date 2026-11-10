package com.eternalrealm.combat;

import java.util.Objects;

/**
 * An area that is about to be hit, drawn on the ground as a red (or themed) marker while it charges: bosses, dungeon
 * traps and the big moves of strong monsters all use it, so players learn one visual language. Coordinates are world
 * coordinates; {@code y} is the ground height (feet level) of the marker. Yaw follows Minecraft (0 = +Z/south,
 * 90 = −X/west), like {@link HitGeometry}.
 *
 * <p>Parameters by kind: {@link Kind#CIRCLE} a = radius; {@link Kind#RING} a = inner, b = outer radius (safe inside);
 * {@link Kind#CONE} a = range, b = half angle in degrees; {@link Kind#RECT} a = length forward from the origin,
 * b = full width; {@link Kind#SQUARE} a = half size (centred, turned by yaw); {@link Kind#CROSS} a = arm length from the
 * centre, b = arm width.</p>
 *
 * @param durationTicks how long it charges before it hits (the inner fill grows over this time)
 */
public record Telegraph(Kind kind, Style style, double x, double y, double z, double yaw, double a, double b,
                        int durationTicks) {
    /** Feet within this height of the marker are inside it (jumping does not avoid a ground attack). */
    public static final double VERTICAL_TOLERANCE = 2.5;
    public static final int MAX_TICKS = 20 * 30;
    public static final double MAX_SIZE = 64.0;

    public enum Kind {
        CIRCLE, RING, CONE, RECT, SQUARE, CROSS
    }

    /**
     * Colour theme; {@link #SAFE} marks where to stand, {@link #ALLY} where a player's own skill or weapon art will
     * land (a friendly marker: standing in it is no danger).
     */
    public enum Style {
        DANGER, MAGIC, FIRE, FROST, HOLY, DARK, SAFE, ALLY;

        /** Whether standing in a charging marker of this style warns of danger. */
        public boolean warns() {
            return this != SAFE && this != ALLY;
        }
    }

    public Telegraph {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(style, "style");
        finite("x", x);
        finite("y", y);
        finite("z", z);
        finite("yaw", yaw);
        Check.range("a", finite("a", a), 0.0, MAX_SIZE);
        Check.range("b", finite("b", b), 0.0, kind == Kind.CONE ? 180.0 : MAX_SIZE);
        Check.range("durationTicks", durationTicks, 1, MAX_TICKS);
        if (kind == Kind.RING && b < a) {
            throw new IllegalArgumentException("ring outer radius " + b + " < inner " + a);
        }
    }

    private static double finite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
        return value;
    }

    public static Telegraph circle(Style style, double x, double y, double z, double radius, int ticks) {
        return new Telegraph(Kind.CIRCLE, style, x, y, z, 0.0, radius, 0.0, ticks);
    }

    public static Telegraph ring(Style style, double x, double y, double z, double inner, double outer, int ticks) {
        return new Telegraph(Kind.RING, style, x, y, z, 0.0, inner, outer, ticks);
    }

    public static Telegraph cone(Style style, double x, double y, double z, double yaw, double range,
                                 double halfAngleDegrees, int ticks) {
        return new Telegraph(Kind.CONE, style, x, y, z, yaw, range, halfAngleDegrees, ticks);
    }

    /** A strip from the origin forward ({@code yaw}), e.g. a charge, a beam or a breath. */
    public static Telegraph rect(Style style, double x, double y, double z, double yaw, double length, double width,
                                 int ticks) {
        return new Telegraph(Kind.RECT, style, x, y, z, yaw, length, width, ticks);
    }

    /** A strip between two points. */
    public static Telegraph line(Style style, double x0, double y0, double z0, double x1, double z1, double width,
                                 int ticks) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        return rect(style, x0, y0, z0, yaw, Math.min(MAX_SIZE, Math.sqrt(dx * dx + dz * dz)), width, ticks);
    }

    public static Telegraph square(Style style, double x, double y, double z, double yaw, double halfSize, int ticks) {
        return new Telegraph(Kind.SQUARE, style, x, y, z, yaw, halfSize, 0.0, ticks);
    }

    public static Telegraph cross(Style style, double x, double y, double z, double yaw, double armLength,
                                  double armWidth, int ticks) {
        return new Telegraph(Kind.CROSS, style, x, y, z, yaw, armLength, armWidth, ticks);
    }

    /** Whether feet at {@code (px, py, pz)} are inside (what the hit test uses). */
    public boolean contains(double px, double py, double pz) {
        if (Math.abs(py - y) > VERTICAL_TOLERANCE) {
            return false;
        }
        double dx = px - x;
        double dz = pz - z;
        double radians = Math.toRadians(yaw);
        // forward = direction the yaw faces, side = to its left
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        double along = dx * forwardX + dz * forwardZ;
        double across = dx * forwardZ - dz * forwardX;
        double distanceSquared = dx * dx + dz * dz;
        return switch (kind) {
            case CIRCLE -> distanceSquared <= a * a;
            case RING -> distanceSquared >= a * a && distanceSquared <= b * b;
            case CONE -> distanceSquared <= a * a
                    && (distanceSquared < 1.0E-9 || HitGeometry.horizontalAngle(yaw, dx, dz) <= b);
            case RECT -> along >= 0.0 && along <= a && Math.abs(across) <= b / 2.0;
            case SQUARE -> Math.abs(along) <= a && Math.abs(across) <= a;
            case CROSS -> (Math.abs(along) <= a && Math.abs(across) <= b / 2.0)
                    || (Math.abs(across) <= a && Math.abs(along) <= b / 2.0);
        };
    }

    /** Radius of a circle around (x, z) that contains the whole marker (for culling and audiences). */
    public double boundingRadius() {
        return switch (kind) {
            case CIRCLE, CONE -> a;
            case RING -> b;
            case RECT -> Math.sqrt(a * a + b * b / 4.0);
            case SQUARE -> a * Math.sqrt(2.0);
            case CROSS -> Math.sqrt(a * a + b * b / 4.0);
        };
    }
}
