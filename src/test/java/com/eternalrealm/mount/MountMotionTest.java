package com.eternalrealm.mount;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Speeds, jumps and flight of the mounts, tick by tick. */
public class MountMotionTest {
    private static final MountMotion.Input FORWARD = new MountMotion.Input(1.0, 0.0, false, false);

    /** The speed a mount settles at running straight on open ground (or in the open air for a flier). */
    private static double topSpeed(MountKind kind, boolean airborne) {
        MountMotion.Velocity v = MountMotion.Velocity.ZERO;
        MountMotion.Surroundings where = airborne ? new MountMotion.Surroundings(false, false, 50.0)
                : MountMotion.Surroundings.OPEN_GROUND;
        for (int tick = 0; tick < 400; tick++) {
            v = MountMotion.step(kind, FORWARD, 0.0, v, where);
            if (airborne) {
                v = new MountMotion.Velocity(v.x(), 0.0, v.z());
            }
        }
        return v.horizontal();
    }

    @Test
    public void theHorseRunsTwiceAsFastAsSprintingAndTheBikeHalfAgainAsFast() {
        double sprint = MountMotion.SPRINT_SPEED;
        double horse = topSpeed(MountKind.HORSE, false);
        double bike = topSpeed(MountKind.BIKE, false);
        double griffin = topSpeed(MountKind.GRIFFIN, true);
        assertEquals(0.286, sprint, 0.002);
        assertTrue("horse " + horse / sprint + "x sprint", horse / sprint >= 1.8 && horse / sprint <= 2.2);
        assertTrue("bike " + bike / horse + "x horse", bike / horse >= 1.4 && bike / horse <= 1.6);
        assertTrue("griffin flies " + griffin / horse + "x the horse", Math.abs(griffin / horse - 1.0) <= 0.15);
        assertTrue(bike > horse && horse > sprint);
        // the griffin is no racer on foot
        assertTrue(topSpeed(MountKind.GRIFFIN, false) < horse);
    }

    @Test
    public void theMountGoesWhereTheRiderLooks() {
        // yaw 0 faces south (+z), 90 west (-x); strafing left (positive) while facing south goes east (+x)
        MountMotion.Velocity south = run(MountKind.HORSE, FORWARD, 0.0);
        assertTrue(south.z() > 0.5 && Math.abs(south.x()) < 1.0E-6);
        MountMotion.Velocity west = run(MountKind.HORSE, FORWARD, 90.0);
        assertTrue(west.x() < -0.5 && Math.abs(west.z()) < 1.0E-6);
        MountMotion.Velocity left = run(MountKind.HORSE, new MountMotion.Input(0.0, 1.0, false, false), 0.0);
        assertTrue(left.x() > 0.2 && left.x() < 0.4);
        MountMotion.Velocity back = run(MountKind.HORSE, new MountMotion.Input(-1.0, 0.0, false, false), 0.0);
        assertTrue(back.z() < -0.1 && back.horizontal() < 0.25);
        // pressing too hard is pressing fully
        assertEquals(1.0, new MountMotion.Input(5.0, -3.0, false, false).forward(), 0.0);
        assertEquals(-1.0, new MountMotion.Input(5.0, -3.0, false, false).strafe(), 0.0);
    }

    private static MountMotion.Velocity run(MountKind kind, MountMotion.Input input, double yaw) {
        MountMotion.Velocity v = MountMotion.Velocity.ZERO;
        for (int tick = 0; tick < 200; tick++) {
            v = MountMotion.step(kind, input, yaw, v, MountMotion.Surroundings.OPEN_GROUND);
        }
        return v;
    }

    @Test
    public void withoutInputTheMountCoastsToAStop() {
        MountMotion.Velocity v = run(MountKind.BIKE, FORWARD, 0.0);
        double before = v.horizontal();
        for (int tick = 0; tick < 5; tick++) {
            v = MountMotion.step(MountKind.BIKE, MountMotion.Input.NONE, 0.0, v, MountMotion.Surroundings.OPEN_GROUND);
        }
        assertTrue(v.horizontal() < before && v.horizontal() > 0.2);
        for (int tick = 0; tick < 200; tick++) {
            v = MountMotion.step(MountKind.BIKE, MountMotion.Input.NONE, 0.0, v, MountMotion.Surroundings.OPEN_GROUND);
        }
        assertEquals(0.0, v.horizontal(), 1.0E-9);
    }

    @Test
    public void groundMountsJumpAndFall() {
        for (MountKind kind : new MountKind[]{MountKind.HORSE, MountKind.BIKE}) {
            MountMotion.Velocity v = MountMotion.step(kind, new MountMotion.Input(0.0, 0.0, true, false), 0.0,
                    MountMotion.Velocity.ZERO, MountMotion.Surroundings.OPEN_GROUND);
            assertEquals(kind.stats().jump(), v.y(), 1.0E-9);
            double height = 0.0;
            double peak = 0.0;
            for (int tick = 0; tick < 60 && height >= 0.0; tick++) {
                height += v.y();
                peak = Math.max(peak, height);
                v = MountMotion.step(kind, MountMotion.Input.NONE, 0.0, v,
                        new MountMotion.Surroundings(false, false, Double.MAX_VALUE));
            }
            assertTrue(kind + " jumps " + peak, peak >= 1.2 && peak <= 3.6);
        }
        // the horse clears more than the bike
        assertTrue(MountKind.HORSE.stats().jump() > MountKind.BIKE.stats().jump());
        // standing: pressed to the ground
        MountMotion.Velocity rest = MountMotion.step(MountKind.HORSE, MountMotion.Input.NONE, 0.0,
                MountMotion.Velocity.ZERO, MountMotion.Surroundings.OPEN_GROUND);
        assertEquals(-MountMotion.GRAVITY, rest.y(), 1.0E-9);
    }

    @Test
    public void waterSlowsEveryMountAndFloatsItUp() {
        for (MountKind kind : MountKind.values()) {
            MountMotion.Velocity v = MountMotion.Velocity.ZERO;
            MountMotion.Surroundings water = new MountMotion.Surroundings(false, true, 40.0);
            for (int tick = 0; tick < 300; tick++) {
                v = MountMotion.step(kind, FORWARD, 0.0, v, water);
            }
            assertTrue(kind + " wades at " + v.horizontal(), v.horizontal() <= kind.stats().topSpeed() * 0.41);
            assertTrue(kind + " floats", v.y() > 0.0);
        }
    }

    @Test
    public void theGriffinTakesOffClimbsToTheCeilingGlidesAndDives() {
        MountKind griffin = MountKind.GRIFFIN;
        double ground = 70.0;
        double ceiling = ground + MountRules.FLIGHT_HEIGHT;
        double y = ground;
        boolean onGround = true;
        MountMotion.Velocity v = MountMotion.Velocity.ZERO;
        MountMotion.Input climb = new MountMotion.Input(1.0, 0.0, true, false);
        double highest = y;
        for (int tick = 0; tick < 600; tick++) {
            v = MountMotion.step(griffin, climb, 0.0, v, new MountMotion.Surroundings(onGround, false, ceiling - y));
            y += v.y();
            onGround = y <= ground;
            if (onGround) {
                y = ground;
            }
            highest = Math.max(highest, y);
            assertTrue("above the ceiling at tick " + tick + ": " + y, y <= ceiling + 1.0E-9);
        }
        assertTrue("reached " + highest, highest >= ceiling - 0.5);
        // let go: a slow glide down
        for (int tick = 0; tick < 60; tick++) {
            v = MountMotion.step(griffin, MountMotion.Input.NONE, 0.0, v, new MountMotion.Surroundings(false, false,
                    ceiling - y));
            y += v.y();
        }
        assertTrue("glides at " + v.y(), v.y() < 0.0 && v.y() >= -griffin.stats().glide() - 1.0E-3);
        // sneak: a proper dive
        for (int tick = 0; tick < 30; tick++) {
            v = MountMotion.step(griffin, new MountMotion.Input(0.0, 0.0, false, true), 0.0, v,
                    new MountMotion.Surroundings(false, false, ceiling - y));
            y += v.y();
        }
        assertTrue("dives at " + v.y(), v.y() <= -0.4);
        // above the ceiling (the ground fell away beneath): eased down, never up
        MountMotion.Velocity above = MountMotion.step(griffin, climb, 0.0, new MountMotion.Velocity(0.0, 0.2, 0.0),
                new MountMotion.Surroundings(false, false, -5.0));
        assertTrue(above.y() <= -MountMotion.ABOVE_CEILING_SINK);
        // no take-off without room
        MountMotion.Velocity grounded = MountMotion.step(griffin, climb, 0.0, MountMotion.Velocity.ZERO,
                new MountMotion.Surroundings(true, false, 0.0));
        assertTrue(grounded.y() < 0.0);
    }

    @Test
    public void groundMountsNeverFly() {
        MountMotion.Velocity v = MountMotion.Velocity.ZERO;
        MountMotion.Input climb = new MountMotion.Input(1.0, 0.0, true, false);
        double y = 0.0;
        for (int tick = 0; tick < 100; tick++) {
            boolean onGround = y <= 0.0;
            v = MountMotion.step(MountKind.BIKE, climb, 0.0, v, new MountMotion.Surroundings(onGround, false, 100.0));
            y = Math.max(0.0, y + v.y());
            assertTrue(y < 2.5);
        }
    }
}
