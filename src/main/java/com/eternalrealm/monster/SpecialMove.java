package com.eternalrealm.monster;

import com.eternalrealm.combat.Telegraph;

import java.util.Locale;

/**
 * A telegraphed big move of a strong monster ({@link SpecialMoves}): its ground marker appears when the wind-up starts
 * and the hit lands {@link #windupTicks()} later on whoever is inside it. Shapes are relative to the monster (the
 * caster) and the target it chose when the move started; the marker never follows anyone afterwards.
 *
 * @param shape       how the marker is laid out
 * @param a           first size (radius, range, length or arm length; ring: inner radius)
 * @param b           second size (cone: half angle in degrees; line/cross: width; ring: outer radius; front circle:
 *                    distance of the centre in front of the caster)
 * @param windupTicks marker time before the hit (at least {@link #MIN_WINDUP_TICKS})
 * @param minRange    the move is only used against a target at least this far away (horizontal blocks)
 * @param maxRange    ... and at most this far
 * @param damage      multiplier of the monster's normal hit
 * @param swing       the attack animation played (slowed down) during the wind-up
 */
public enum SpecialMove {
    /** A wide swing in front (melee humanoids). */
    CLEAVE(Shape.CONE, 5.0, 65.0, 22, 0.0, 4.5, 1.5, Swing.ATTACK1),
    /** An overhead smash onto a circle just in front. */
    SLAM(Shape.FRONT_CIRCLE, 2.8, 2.4, 26, 0.0, 5.0, 1.8, Swing.ATTACK2),
    /** A stomp whose shockwave rolls out in a ring (safe right next to the body). */
    STOMP(Shape.SELF_RING, 2.0, 6.5, 30, 0.0, 6.0, 1.5, Swing.ATTACK2),
    /** A rush along a straight line through the target. */
    CHARGE(Shape.LINE, 10.0, 2.2, 24, 3.0, 9.0, 1.6, Swing.ATTACK1),
    /** A cone of breath (fire, frost or void by the monster's element). */
    BREATH(Shape.CONE, 7.0, 32.0, 26, 0.0, 6.5, 1.4, Swing.ATTACK2),
    /** A leap onto the spot where the target stood. */
    POUNCE(Shape.TARGET_CIRCLE, 2.2, 0.0, 22, 3.0, 8.0, 1.6, Swing.ATTACK1),
    /** A sweep all around the body (chains, tail, wings). */
    SWEEP(Shape.SELF_CIRCLE, 4.2, 0.0, 24, 0.0, 4.0, 1.5, Swing.ATTACK2),
    /** A magic circle under the target. */
    CIRCLE(Shape.TARGET_CIRCLE, 3.0, 0.0, 30, 3.0, 16.0, 1.7, Swing.CAST),
    /** A burst around the caster that punishes standing next to it. */
    NOVA(Shape.SELF_CIRCLE, 4.5, 0.0, 26, 0.0, 4.0, 1.4, Swing.CAST),
    /** A cross of lightning centred on the target. */
    STORM_CROSS(Shape.TARGET_CROSS, 6.0, 2.2, 30, 3.0, 14.0, 1.6, Swing.CAST),
    /** A beam (or a wave of darkness) from the body towards the target. */
    BEAM(Shape.LINE, 14.0, 2.0, 30, 3.0, 13.0, 1.6, Swing.CAST),
    /** A rain of arrows on a circle around the target. */
    VOLLEY(Shape.TARGET_CIRCLE, 3.2, 0.0, 32, 4.0, 18.0, 1.5, Swing.CAST),
    /** A wave of force along the ground towards the target (melee knights). */
    WAVE(Shape.LINE, 9.0, 2.6, 28, 2.0, 8.0, 1.7, Swing.ATTACK2);

    /** Layout of a move's marker. */
    public enum Shape {
        /** Cone from the caster towards the target. */
        CONE,
        /** Circle centred {@code b} blocks in front of the caster. */
        FRONT_CIRCLE,
        /** Circle around the caster. */
        SELF_CIRCLE,
        /** Ring around the caster. */
        SELF_RING,
        /** Strip from the caster towards the target. */
        LINE,
        /** Circle centred on the target. */
        TARGET_CIRCLE,
        /** Cross centred on the target, one arm pointing at the caster. */
        TARGET_CROSS
    }

    /** Animation slot played during the wind-up (the entity layer maps it to its rig's animation). */
    public enum Swing {
        ATTACK1, ATTACK2, CAST
    }

    /** Shortest wind-up of any move: the marker must be readable before it hits. */
    public static final int MIN_WINDUP_TICKS = 20;

    private final Shape shape;
    private final double a;
    private final double b;
    private final int windupTicks;
    private final double minRange;
    private final double maxRange;
    private final double damage;
    private final Swing swing;

    SpecialMove(Shape shape, double a, double b, int windupTicks, double minRange, double maxRange, double damage,
                Swing swing) {
        this.shape = shape;
        this.a = a;
        this.b = b;
        this.windupTicks = windupTicks;
        this.minRange = minRange;
        this.maxRange = maxRange;
        this.damage = damage;
        this.swing = swing;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Shape shape() {
        return shape;
    }

    public double a() {
        return a;
    }

    public double b() {
        return b;
    }

    public int windupTicks() {
        return windupTicks;
    }

    public double minRange() {
        return minRange;
    }

    public double maxRange() {
        return maxRange;
    }

    public double damageMultiplier() {
        return damage;
    }

    public Swing swing() {
        return swing;
    }

    /** Whether the move may be used against a target this far away (horizontal distance). */
    public boolean inRange(double distance) {
        return distance >= minRange && distance <= maxRange;
    }

    /** The move dashes the caster along its marker when it hits (charges and pounces). */
    public boolean dashes() {
        return this == CHARGE || this == POUNCE;
    }

    /**
     * The marker of this move.
     *
     * @param casterX       the caster's position
     * @param casterGroundY ground height (feet level) under the caster (fliers hover above it)
     * @param targetX       the target's position when the move starts
     * @param targetGroundY ground height under the target
     */
    public Telegraph telegraph(Telegraph.Style style, double casterX, double casterGroundY, double casterZ,
                               double targetX, double targetGroundY, double targetZ) {
        double dx = targetX - casterX;
        double dz = targetZ - casterZ;
        double yaw = dx * dx + dz * dz < 1.0E-6 ? 0.0 : Math.toDegrees(Math.atan2(-dx, dz));
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        return switch (shape) {
            case CONE -> Telegraph.cone(style, casterX, casterGroundY, casterZ, yaw, a, b, windupTicks);
            case FRONT_CIRCLE -> Telegraph.circle(style, casterX + forwardX * b, casterGroundY, casterZ + forwardZ * b, a,
                    windupTicks);
            case SELF_CIRCLE -> Telegraph.circle(style, casterX, casterGroundY, casterZ, a, windupTicks);
            case SELF_RING -> Telegraph.ring(style, casterX, casterGroundY, casterZ, a, b, windupTicks);
            case LINE -> Telegraph.rect(style, casterX, casterGroundY, casterZ, yaw, a, b, windupTicks);
            case TARGET_CIRCLE -> Telegraph.circle(style, targetX, targetGroundY, targetZ, a, windupTicks);
            case TARGET_CROSS -> Telegraph.cross(style, targetX, targetGroundY, targetZ, yaw, a, b, windupTicks);
        };
    }
}
