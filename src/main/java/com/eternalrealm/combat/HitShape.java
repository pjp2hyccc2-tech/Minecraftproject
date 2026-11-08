package com.eternalrealm.combat;

import java.util.Objects;

/**
 * Volume swept by one hit frame, relative to the attacker's eyes and facing.
 *
 * @param reach            radius (ARC, CIRCLE) or length (LINE) in blocks
 * @param halfAngleDegrees ARC only: half of the opening angle; 180 = all around
 * @param width            LINE only: maximum distance of a target's hitbox from the thrust line
 * @param verticalUp       ARC/CIRCLE: blocks above eye level inside the volume; LINE: how far a target may be above
 *                         the thrust line
 * @param verticalDown     ARC/CIRCLE: blocks below eye level inside the volume; LINE: how far a target may be below
 *                         the thrust line (lets level thrusts hit short mobs)
 * @param maxTargets       nearest targets hit at most per frame
 * @param offsetForward    CIRCLE only: the disc's centre is moved this far ahead of the attacker along the horizontal
 *                         facing (area attacks at a distance, e.g. arrow rain); 0 for every other shape
 */
public record HitShape(Kind kind, double reach, double halfAngleDegrees, double width, double verticalUp,
                       double verticalDown, int maxTargets, double offsetForward) {
    public enum Kind {
        /** Horizontal sector in front of the attacker (slashes). */
        ARC,
        /** Segment along the 3D look direction (thrusts). Pierces: every target on the line can be hit. */
        LINE,
        /** Horizontal disc around the attacker (spin attacks, ground slams). */
        CIRCLE
    }

    public static final double MAX_REACH = 16.0;
    public static final double MAX_OFFSET = 24.0;
    private static final double DEFAULT_UP = 1.0;
    private static final double DEFAULT_DOWN = 2.2;

    public HitShape {
        Objects.requireNonNull(kind, "kind");
        Check.range("reach", reach, 0.5, MAX_REACH);
        Check.range("halfAngleDegrees", halfAngleDegrees, 0.0, 180.0);
        Check.range("width", width, 0.0, 4.0);
        Check.range("verticalUp", verticalUp, 0.0, 8.0);
        Check.range("verticalDown", verticalDown, 0.0, 8.0);
        Check.range("maxTargets", maxTargets, 1, 64);
        if (kind == Kind.ARC && halfAngleDegrees <= 0.0) {
            throw new IllegalArgumentException("ARC needs a positive half angle");
        }
        if (kind == Kind.LINE && width <= 0.0) {
            throw new IllegalArgumentException("LINE needs a positive width");
        }
        Check.range("offsetForward", offsetForward, 0.0, MAX_OFFSET);
        if (offsetForward > 0.0 && kind != Kind.CIRCLE) {
            throw new IllegalArgumentException("only CIRCLE shapes can be offset");
        }
    }

    /** A shape centred on the attacker (no forward offset). */
    public HitShape(Kind kind, double reach, double halfAngleDegrees, double width, double verticalUp,
                    double verticalDown, int maxTargets) {
        this(kind, reach, halfAngleDegrees, width, verticalUp, verticalDown, maxTargets, 0.0);
    }

    public static HitShape arc(double reach, double halfAngleDegrees) {
        return new HitShape(Kind.ARC, reach, halfAngleDegrees, 0.0, DEFAULT_UP, DEFAULT_DOWN, 8);
    }

    public static HitShape line(double reach, double width) {
        return new HitShape(Kind.LINE, reach, 0.0, width, 0.6, 1.0, 6);
    }

    public static HitShape circle(double radius) {
        return new HitShape(Kind.CIRCLE, radius, 180.0, 0.0, 1.5, 2.5, 12);
    }

    /** A disc of {@code radius} whose centre is {@code forward} blocks ahead; taller so it also hits on slopes. */
    public static HitShape circleAhead(double radius, double forward) {
        return new HitShape(Kind.CIRCLE, radius, 180.0, 0.0, 3.0, 4.0, 16, forward);
    }
}
