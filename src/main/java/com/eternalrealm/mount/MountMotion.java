package com.eternalrealm.mount;

/**
 * One tick of a ridden mount's movement (pure, shared by the rider's client, which moves the mount, and the tests).
 * The mount faces where the rider looks; W/S move along that direction, A/D sideways.
 *
 * <ul>
 *     <li><b>Horizontal</b>: the velocity closes {@link MountKind.MountStats#acceleration()} of the gap to the wanted
 *     velocity each tick; without input it coasts down ({@link MountKind.MountStats#coast()} on the ground, a little
 *     more slowly in the air). In water every mount wades at {@value #WATER_FACTOR} of its speed.</li>
 *     <li><b>Ground mounts</b>: gravity like any mob ({@value #GRAVITY} per tick, drag {@value #DRAG}); jump on the
 *     ground leaps with {@link MountKind.MountStats#jump()}; in water they float up gently.</li>
 *     <li><b>Fliers</b>: on the ground they walk at {@link MountKind.MountStats#groundSpeed()} and jump takes off; in
 *     the air jump climbs, sneak descends and otherwise they glide down slowly. They never climb above the ceiling
 *     ({@link MountRules#flightCeiling}) and are eased back down when above it.</li>
 * </ul>
 */
public final class MountMotion {
    /** Mob gravity (blocks per tick²) and vertical drag, as vanilla. */
    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    /** Share of the speed left in water, and the upward drift there. */
    public static final double WATER_FACTOR = 0.4;
    public static final double WATER_LIFT = 0.06;
    /** Horizontal speed kept per tick without input while flying. */
    public static final double AIR_COAST = 0.94;
    /** How fast a flier above the ceiling is eased down (blocks per tick). */
    public static final double ABOVE_CEILING_SINK = 0.12;
    /** A player's sprinting speed on flat ground (movement speed 0.1 × 1.3 at the vanilla ground friction). */
    public static final double SPRINT_SPEED = 0.1 * 1.3 / (1.0 - 0.6 * 0.91);

    private MountMotion() {
    }

    /**
     * The rider's wish this tick.
     *
     * @param forward 1 forward, -1 back (W/S)
     * @param strafe  1 left, -1 right (A/D, as vanilla's {@code xxa})
     * @param jump    jump held
     * @param descend sneak held (fliers descend)
     */
    public record Input(double forward, double strafe, boolean jump, boolean descend) {
        public static final Input NONE = new Input(0.0, 0.0, false, false);

        public Input {
            forward = clamp(forward, -1.0, 1.0);
            strafe = clamp(strafe, -1.0, 1.0);
        }

        public boolean moving() {
            return forward != 0.0 || strafe != 0.0;
        }
    }

    /** Velocity in blocks per tick (Minecraft axes: +x east, +y up, +z south). */
    public record Velocity(double x, double y, double z) {
        public static final Velocity ZERO = new Velocity(0.0, 0.0, 0.0);

        public double horizontal() {
            return Math.sqrt(x * x + z * z);
        }
    }

    /**
     * Where the mount stands this tick.
     *
     * @param onGround   standing on a block
     * @param inWater    touching water
     * @param ceilingGap blocks between the mount and the flight ceiling (negative above it; ignored for ground mounts)
     */
    public record Surroundings(boolean onGround, boolean inWater, double ceilingGap) {
        public static final Surroundings OPEN_GROUND = new Surroundings(true, false, Double.MAX_VALUE);
    }

    /**
     * The velocity for the next tick.
     *
     * @param yawDegrees the direction the mount faces (Minecraft yaw: 0 = south, 90 = west)
     * @param current    the velocity it had (after the last tick's collisions)
     */
    public static Velocity step(MountKind kind, Input input, double yawDegrees, Velocity current, Surroundings where) {
        MountKind.MountStats stats = kind.stats();
        boolean flying = kind.flies() && !where.onGround();
        double top = kind.flies() && where.onGround() ? stats.groundSpeed() : stats.topSpeed();
        if (where.inWater()) {
            top *= WATER_FACTOR;
        }
        // the wanted horizontal velocity in the mount's frame, then turned to the world
        double forward = input.forward() >= 0.0 ? input.forward() : input.forward() * stats.backFactor();
        double strafe = input.strafe() * stats.strafeFactor();
        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length > 1.0) {
            forward /= length;
            strafe /= length;
        }
        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double wantX = (strafe * cos - forward * sin) * top;
        double wantZ = (forward * cos + strafe * sin) * top;
        double vx;
        double vz;
        if (input.moving()) {
            vx = current.x() + (wantX - current.x()) * stats.acceleration();
            vz = current.z() + (wantZ - current.z()) * stats.acceleration();
        } else {
            double keep = flying ? AIR_COAST : stats.coast();
            vx = current.x() * keep;
            vz = current.z() * keep;
        }
        if (Math.abs(vx) < 1.0E-4) {
            vx = 0.0;
        }
        if (Math.abs(vz) < 1.0E-4) {
            vz = 0.0;
        }
        double vy = kind.flies() ? flierVertical(stats, input, current.y(), where)
                : groundVertical(stats, input, current.y(), where);
        return new Velocity(vx, vy, vz);
    }

    private static double groundVertical(MountKind.MountStats stats, Input input, double vy, Surroundings where) {
        if (where.inWater()) {
            return Math.min(vy + WATER_LIFT, WATER_LIFT * 2.0);
        }
        if (where.onGround()) {
            return input.jump() ? stats.jump() : -GRAVITY;
        }
        return (vy - GRAVITY) * DRAG;
    }

    private static double flierVertical(MountKind.MountStats stats, Input input, double vy, Surroundings where) {
        double gap = where.ceilingGap();
        double wanted;
        if (where.onGround()) {
            if (!input.jump() || gap <= 0.0) {
                return -GRAVITY; // walks; the take-off needs room below the ceiling
            }
            return Math.min(stats.jump(), Math.max(0.0, gap));
        }
        if (gap < 0.0) {
            // above the ceiling (the ground fell away beneath, or the ceiling was lowered): eased back down
            return Math.min(-ABOVE_CEILING_SINK, Math.min(vy, 0.0) * 0.5 - ABOVE_CEILING_SINK);
        }
        if (input.jump() && !input.descend()) {
            wanted = stats.climb();
        } else if (input.descend()) {
            wanted = -stats.descend();
        } else {
            wanted = -stats.glide();
        }
        if (where.inWater()) {
            wanted = Math.max(wanted, stats.climb() * 0.5);
        }
        double next = vy + (wanted - vy) * 0.25;
        return Math.min(next, gap);
    }

    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? 0.0 : Math.max(min, Math.min(max, value));
    }
}
