package com.eternalrealm.mount;

/**
 * One tick of a ridden mount's movement (pure, shared by the rider's client, which moves the mount, and the tests).
 * The mount faces where the rider looks; W/S move along that direction, A/D sideways.
 *
 * <ul>
 *     <li><b>Horizontal</b>: the velocity closes {@link MountKind.MountStats#acceleration()} of the gap to the wanted
 *     velocity each tick; without input it coasts down ({@link MountKind.MountStats#coast()} on the ground, a little
 *     more slowly in the air). In water every mount wades at {@value #WATER_FACTOR} of its speed (not a
 *     {@link MountTalent#WAVE_RUNNER}).</li>
 *     <li><b>Ground mounts</b>: gravity like any mob ({@value #GRAVITY} per tick, drag {@value #DRAG}); jump on the
 *     ground leaps with {@link MountKind.MountStats#jump()}; in water they float up gently.</li>
 *     <li><b>Fliers</b>: on the ground they walk at {@link MountKind.MountStats#groundSpeed()} and jump takes off; in
 *     the air jump climbs, sneak descends and otherwise they glide down slowly. They never climb above the ceiling
 *     ({@link MountRules#flightCeiling}) and are eased back down when above it.</li>
 *     <li><b>Talents</b> ({@link MountTalent}): a wave runner stands on still water (the entity treats the surface
 *     as ground) and runs there at full speed; a glider holding jump in the air sinks at most
 *     {@value MountTalent#GLIDE_SINK} per tick; a surging horse ({@link Ride#surging()}) runs
 *     {@value MountTalent#SURGE_BOOST}x faster; a sky-stepper jumps once more in the air on a fresh press; a diver
 *     gains {@value MountTalent#DIVE_BOOST}x speed while diving.</li>
 * </ul>
 */
public final class MountMotion {
    /** Mob gravity (blocks per tick²) and vertical drag, as vanilla. */
    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    /** Share of the speed left in water, and the upward drift there. */
    public static final double WATER_FACTOR = 0.4;
    public static final double WATER_LIFT = 0.06;
    /** Horizontal speed kept per tick without input while flying (or gliding). */
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
     * @param onGround   standing on a block (a wave runner: or on still water)
     * @param inWater    touching water
     * @param ceilingGap blocks between the mount and the flight ceiling (negative above it; ignored for ground mounts)
     */
    public record Surroundings(boolean onGround, boolean inWater, double ceilingGap) {
        public static final Surroundings OPEN_GROUND = new Surroundings(true, false, Double.MAX_VALUE);
    }

    /**
     * What a ride remembers from tick to tick (for the talents that need it).
     *
     * @param surge    ticks in a row at full gallop ({@link MountTalent#SURGE}: surging from {@value
     *                 MountTalent#SURGE_TICKS})
     * @param airJump  the second jump is still to be used ({@link MountTalent#SKY_STEP}; back on landing)
     * @param jumpHeld jump was held last tick (a second jump needs a fresh press)
     */
    public record Ride(int surge, boolean airJump, boolean jumpHeld) {
        /** A new ride. */
        public static final Ride START = new Ride(0, true, false);
        /** No memory: the talents that need one do nothing ({@link #step}). */
        public static final Ride NONE = new Ride(0, false, true);

        public Ride {
            surge = Math.max(0, Math.min(MountTalent.SURGE_TICKS, surge));
        }

        public boolean surging() {
            return surge >= MountTalent.SURGE_TICKS;
        }
    }

    /** Something a talent did this tick (the client shows it). */
    public enum Event {
        NONE, SURGE, SKY_STEP
    }

    /** The velocity for the next tick, the ride's memory and what happened. */
    public record Step(Velocity velocity, Ride ride, Event event) {
    }

    /**
     * The velocity for the next tick.
     *
     * @param yawDegrees the direction the mount faces (Minecraft yaw: 0 = south, 90 = west)
     * @param current    the velocity it had (after the last tick's collisions)
     */
    public static Velocity step(MountKind kind, Input input, double yawDegrees, Velocity current, Surroundings where) {
        return step(kind, kind.stats(), input, yawDegrees, current, where);
    }

    /**
     * The velocity for the next tick of a mount moving by {@code stats} (an individual's numbers, {@link
     * MountIndividual#stats()}), without a ride's memory: no surge and no second jump.
     */
    public static Velocity step(MountKind kind, MountKind.MountStats stats, Input input, double yawDegrees,
                                Velocity current, Surroundings where) {
        return ride(kind, stats, input, yawDegrees, current, where, Ride.NONE).velocity();
    }

    /** One tick of a ride: the velocity for the next tick, with the talents that need the ride's memory. */
    public static Step ride(MountKind kind, MountKind.MountStats stats, Input input, double yawDegrees,
                            Velocity current, Surroundings where, Ride ride) {
        MountTalent talent = kind.talent();
        boolean flying = kind.flies() && !where.onGround();
        // a wave runner standing on still water is on ground; one under the surface swims up
        boolean wading = where.inWater() && !(talent == MountTalent.WAVE_RUNNER && where.onGround());
        boolean gliding = talent == MountTalent.GLIDE && !where.onGround() && !where.inWater() && input.jump()
                && current.y() < 0.0;
        double top = kind.flies() && where.onGround() ? stats.groundSpeed() : stats.topSpeed();
        if (where.inWater() && talent != MountTalent.WAVE_RUNNER) {
            top *= WATER_FACTOR;
        }
        if (talent == MountTalent.SURGE && ride.surging()) {
            top *= MountTalent.SURGE_BOOST;
        }
        if (talent == MountTalent.DIVE && flying && input.descend() && !input.jump()) {
            top *= MountTalent.DIVE_BOOST;
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
            double keep = flying || gliding ? AIR_COAST : stats.coast();
            vx = current.x() * keep;
            vz = current.z() * keep;
        }
        if (Math.abs(vx) < 1.0E-4) {
            vx = 0.0;
        }
        if (Math.abs(vz) < 1.0E-4) {
            vz = 0.0;
        }
        Event event = Event.NONE;
        boolean airJump = ride.airJump();
        double vy;
        if (kind.flies()) {
            vy = flierVertical(stats, input, current.y(), where);
        } else if (talent == MountTalent.SKY_STEP && !where.onGround() && !where.inWater() && input.jump()
                && !ride.jumpHeld() && airJump) {
            vy = stats.jump() * MountTalent.SKY_STEP_FACTOR;
            airJump = false;
            event = Event.SKY_STEP;
        } else {
            vy = groundVertical(stats, input, current.y(), where.onGround(), wading);
            if (gliding) {
                vy = Math.max(vy, -MountTalent.GLIDE_SINK);
            }
        }
        if (where.onGround() || where.inWater()) {
            airJump = true;
        }
        int surge = 0;
        if (talent == MountTalent.SURGE) {
            boolean galloping = input.forward() > 0.0 && Math.sqrt(vx * vx + vz * vz)
                    >= stats.topSpeed() * MountTalent.SURGE_THRESHOLD * (wading ? WATER_FACTOR : 1.0) && !wading;
            surge = galloping ? ride.surge() + 1 : 0;
            if (galloping && surge == MountTalent.SURGE_TICKS && !ride.surging()) {
                event = Event.SURGE;
            }
        }
        return new Step(new Velocity(vx, vy, vz), new Ride(surge, airJump, input.jump()), event);
    }

    private static double groundVertical(MountKind.MountStats stats, Input input, double vy, boolean onGround,
                                         boolean wading) {
        if (wading) {
            return Math.min(vy + WATER_LIFT, WATER_LIFT * 2.0);
        }
        if (onGround) {
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
