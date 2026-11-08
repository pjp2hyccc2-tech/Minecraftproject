package com.eternalrealm.boss;

import com.eternalrealm.combat.Telegraph;

/**
 * Ground markers ({@link Telegraph}) for the areas of boss attacks: one marker per {@link BossShape}, in the colour of
 * the ability's {@link AbilityLook}. Attacks on the whole arena and the hit outside safe zones are drawn as a circle of
 * the arena's size (the green safe circles of {@link BossCommand.SafeSpot} are drawn on top).
 */
public final class BossMarkers {
    /** Radius drawn for attacks that hit the whole arena when the arena's size is unknown. */
    public static final double DEFAULT_ARENA_RADIUS = 22.0;

    private BossMarkers() {
    }

    /**
     * The marker of one area.
     *
     * @param arenaRadius radius that covers the arena (for arena-wide hits)
     */
    public static Telegraph marker(Telegraph.Style style, BossShape shape, int durationTicks, double arenaRadius) {
        int ticks = Math.max(1, Math.min(Telegraph.MAX_TICKS, durationTicks));
        if (shape instanceof BossShape.Circle circle) {
            return Telegraph.circle(style, circle.center().x(), circle.center().y(), circle.center().z(),
                    size(circle.radius()), ticks);
        }
        if (shape instanceof BossShape.Ring ring) {
            return Telegraph.ring(style, ring.center().x(), ring.center().y(), ring.center().z(), size(ring.inner()),
                    size(ring.outer()), ticks);
        }
        if (shape instanceof BossShape.Cone cone) {
            return Telegraph.cone(style, cone.origin().x(), cone.origin().y(), cone.origin().z(), cone.yawDegrees(),
                    size(cone.range()), Math.min(180.0, cone.halfAngleDegrees()), ticks);
        }
        if (shape instanceof BossShape.Line line) {
            return Telegraph.line(style, line.origin().x(), line.origin().y(), line.origin().z(), line.end().x(),
                    line.end().z(), size(line.width()), ticks);
        }
        if (shape instanceof BossShape.Square square) {
            return Telegraph.square(style, square.center().x(), square.center().y(), square.center().z(),
                    square.yawDegrees(), size(square.halfSize()), ticks);
        }
        if (shape instanceof BossShape.Cross cross) {
            return Telegraph.cross(style, cross.center().x(), cross.center().y(), cross.center().z(),
                    cross.yawDegrees(), size(cross.armLength()), size(cross.armWidth()), ticks);
        }
        Point center = shape.anchor(); // Arena, Outside
        return Telegraph.circle(style, center.x(), center.y(), center.z(), size(arenaRadius), ticks);
    }

    /** The green marker of a safe spot. */
    public static Telegraph safe(BossShape shape, int durationTicks) {
        return marker(Telegraph.Style.SAFE, shape, durationTicks, DEFAULT_ARENA_RADIUS);
    }

    private static double size(double value) {
        return Math.max(0.0, Math.min(Telegraph.MAX_SIZE, value));
    }
}
