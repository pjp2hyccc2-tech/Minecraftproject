package com.eternalrealm.mount;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void waterSlowsEveryMountButTheWaveRunnerAndFloatsItUp() {
        for (MountKind kind : MountKind.values()) {
            MountMotion.Velocity v = MountMotion.Velocity.ZERO;
            MountMotion.Surroundings water = new MountMotion.Surroundings(false, true, 40.0);
            for (int tick = 0; tick < 300; tick++) {
                v = MountMotion.step(kind, FORWARD, 0.0, v, water);
            }
            if (kind.talent() == MountTalent.WAVE_RUNNER) {
                assertEquals(kind + " swims up at full speed", kind.stats().topSpeed(), v.horizontal(), 1.0E-3);
            } else {
                assertTrue(kind + " wades at " + v.horizontal(), v.horizontal() <= kind.stats().topSpeed() * 0.41);
            }
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

    /** The highest point of a jump from the ground (no input after the take-off). */
    private static double leap(MountKind kind) {
        MountMotion.Velocity v = MountMotion.step(kind, new MountMotion.Input(0.0, 0.0, true, false), 0.0,
                MountMotion.Velocity.ZERO, MountMotion.Surroundings.OPEN_GROUND);
        double height = 0.0;
        double peak = 0.0;
        for (int tick = 0; tick < 80 && height >= 0.0; tick++) {
            height += v.y();
            peak = Math.max(peak, height);
            v = MountMotion.step(kind, MountMotion.Input.NONE, 0.0, v,
                    new MountMotion.Surroundings(false, false, Double.MAX_VALUE));
        }
        return peak;
    }

    @Test
    public void everyHorseBreedRunsAboutTwiceASprintAndJumpsButTheLeaperClearsFourBlocks() {
        for (MountKind kind : MountKind.breeds(MountFamily.HORSE)) {
            double speed = topSpeed(kind, false) / MountMotion.SPRINT_SPEED;
            assertTrue(kind + " runs " + speed + "x a sprint", speed >= 1.85 && speed <= 2.3);
            double peak = leap(kind);
            if (kind.talent() == MountTalent.GREAT_LEAP) {
                assertTrue(kind + " leaps " + peak, peak >= 3.6 && peak <= 4.3);
            } else {
                assertTrue(kind + " jumps " + peak, peak >= 1.8 && peak <= 3.0);
            }
        }
        assertTrue(leap(MountKind.HORSE_BLOOM) > leap(MountKind.HORSE) + 1.0);
    }

    @Test
    public void everyGriffinBreedFliesAboutTwiceASprintAndTheLionRunsOnTheGround() {
        for (MountKind kind : MountKind.breeds(MountFamily.GRIFFIN)) {
            double air = topSpeed(kind, true) / MountMotion.SPRINT_SPEED;
            assertTrue(kind + " flies " + air + "x a sprint", air >= 1.9 && air <= 2.45);
            double ground = topSpeed(kind, false);
            if (kind.talent() == MountTalent.LAND_STRIDER) {
                assertTrue(kind + " runs at " + ground, ground >= topSpeed(kind, true) * 0.85
                        && ground / MountMotion.SPRINT_SPEED >= 1.75);
            } else {
                assertTrue(kind + " is no racer on foot: " + ground, ground < topSpeed(MountKind.HORSE, false) * 0.7);
            }
        }
    }

    @Test
    public void aWaveRunnerGallopsOnStillWaterAtFullSpeedAndJumpsFromIt() {
        MountKind tide = MountKind.HORSE_TIDE;
        MountMotion.Surroundings surface = new MountMotion.Surroundings(true, true, Double.MAX_VALUE);
        MountMotion.Velocity v = MountMotion.Velocity.ZERO;
        for (int tick = 0; tick < 200; tick++) {
            v = MountMotion.step(tide, FORWARD, 0.0, v, surface);
            assertEquals("stands on the water", -MountMotion.GRAVITY, v.y(), 1.0E-9);
        }
        assertEquals(tide.stats().topSpeed(), v.horizontal(), 1.0E-3);
        MountMotion.Velocity jump = MountMotion.step(tide, new MountMotion.Input(1.0, 0.0, true, false), 0.0, v,
                surface);
        assertEquals(tide.stats().jump(), jump.y(), 1.0E-9);
        // another horse on the same water (it sinks in: never on ground there) wades slowly
        MountMotion.Velocity horse = MountMotion.Velocity.ZERO;
        for (int tick = 0; tick < 200; tick++) {
            horse = MountMotion.step(MountKind.HORSE, FORWARD, 0.0, horse,
                    new MountMotion.Surroundings(false, true, Double.MAX_VALUE));
        }
        assertTrue(horse.horizontal() < v.horizontal() * 0.45);
    }

    @Test
    public void aGliderHoldingJumpSinksSlowlyAndKeepsItsSpeed() {
        MountKind gale = MountKind.HORSE_GALE;
        MountMotion.Surroundings air = new MountMotion.Surroundings(false, false, Double.MAX_VALUE);
        MountMotion.Input glide = new MountMotion.Input(1.0, 0.0, true, false);
        MountMotion.Velocity v = new MountMotion.Velocity(0.0, -0.02, gale.stats().topSpeed());
        MountMotion.Velocity fall = v;
        for (int tick = 0; tick < 40; tick++) {
            v = MountMotion.step(gale, glide, 0.0, v, air);
            fall = MountMotion.step(gale, FORWARD, 0.0, fall, air);
            assertTrue("glides at " + v.y(), v.y() >= -MountTalent.GLIDE_SINK - 1.0E-9 && v.y() < 0.0);
        }
        assertEquals(-MountTalent.GLIDE_SINK, v.y(), 1.0E-9);
        assertEquals(gale.stats().topSpeed(), v.horizontal(), 1.0E-3);
        assertTrue("letting go falls: " + fall.y(), fall.y() < -1.0);
        // without input the glide coasts on like a flier
        MountMotion.Velocity coast = MountMotion.step(gale, new MountMotion.Input(0.0, 0.0, true, false), 0.0, v,
                air);
        assertEquals(v.horizontal() * MountMotion.AIR_COAST, coast.horizontal(), 1.0E-9);
        // rising, the jump is not cut short; other horses do not glide
        MountMotion.Velocity rising = MountMotion.step(gale, glide, 0.0, new MountMotion.Velocity(0.0, 0.4, 0.0), air);
        assertEquals((0.4 - MountMotion.GRAVITY) * MountMotion.DRAG, rising.y(), 1.0E-9);
        MountMotion.Velocity horse = MountMotion.step(MountKind.HORSE, glide, 0.0,
                new MountMotion.Velocity(0.0, -0.5, 0.0), air);
        assertTrue(horse.y() < -0.5);
    }

    @Test
    public void aLongGallopMakesTheBlazeSurgeUntilItSlows() {
        MountKind blaze = MountKind.HORSE_BLAZE;
        MountKind.MountStats stats = blaze.stats();
        MountMotion.Ride ride = MountMotion.Ride.START;
        MountMotion.Velocity v = MountMotion.Velocity.ZERO;
        int surgedAt = -1;
        for (int tick = 0; tick < 200; tick++) {
            MountMotion.Step step = MountMotion.ride(blaze, stats, FORWARD, 0.0, v,
                    MountMotion.Surroundings.OPEN_GROUND, ride);
            if (step.event() == MountMotion.Event.SURGE) {
                assertEquals("surges once", -1, surgedAt);
                surgedAt = tick;
            }
            v = step.velocity();
            ride = step.ride();
        }
        assertTrue("surged at tick " + surgedAt, surgedAt > MountTalent.SURGE_TICKS && surgedAt < 80);
        assertTrue(ride.surging());
        assertEquals(stats.topSpeed() * MountTalent.SURGE_BOOST, v.horizontal(), 1.0E-3);
        // letting go ends it
        MountMotion.Step stop = MountMotion.ride(blaze, stats, MountMotion.Input.NONE, 0.0, v,
                MountMotion.Surroundings.OPEN_GROUND, ride);
        assertFalse(stop.ride().surging());
        // without a ride's memory (and for other breeds) there is no surge
        assertEquals(stats.topSpeed(), topSpeed(blaze, false), 1.0E-3);
        MountMotion.Ride horseRide = MountMotion.Ride.START;
        MountMotion.Velocity horse = MountMotion.Velocity.ZERO;
        for (int tick = 0; tick < 200; tick++) {
            MountMotion.Step step = MountMotion.ride(MountKind.HORSE, MountKind.HORSE.stats(), FORWARD, 0.0, horse,
                    MountMotion.Surroundings.OPEN_GROUND, horseRide);
            assertEquals(MountMotion.Event.NONE, step.event());
            horse = step.velocity();
            horseRide = step.ride();
        }
        assertEquals(MountKind.HORSE.stats().topSpeed(), horse.horizontal(), 1.0E-3);
    }

    @Test
    public void theHalohornStepsOnTheAirOncePerJumpOnAFreshPress() {
        MountKind halo = MountKind.HORSE_HALO;
        MountKind.MountStats stats = halo.stats();
        MountMotion.Surroundings air = new MountMotion.Surroundings(false, false, Double.MAX_VALUE);
        MountMotion.Input press = new MountMotion.Input(0.0, 0.0, true, false);
        // the jump from the ground, still held: no second jump yet
        MountMotion.Step step = MountMotion.ride(halo, stats, press, 0.0, MountMotion.Velocity.ZERO,
                MountMotion.Surroundings.OPEN_GROUND, MountMotion.Ride.START);
        assertEquals(stats.jump(), step.velocity().y(), 1.0E-9);
        step = MountMotion.ride(halo, stats, press, 0.0, step.velocity(), air, step.ride());
        assertEquals(MountMotion.Event.NONE, step.event());
        // let go, then press again: the second jump
        step = MountMotion.ride(halo, stats, MountMotion.Input.NONE, 0.0, step.velocity(), air, step.ride());
        step = MountMotion.ride(halo, stats, press, 0.0, step.velocity(), air, step.ride());
        assertEquals(MountMotion.Event.SKY_STEP, step.event());
        assertEquals(stats.jump() * MountTalent.SKY_STEP_FACTOR, step.velocity().y(), 1.0E-9);
        // only once until it lands
        step = MountMotion.ride(halo, stats, MountMotion.Input.NONE, 0.0, step.velocity(), air, step.ride());
        step = MountMotion.ride(halo, stats, press, 0.0, step.velocity(), air, step.ride());
        assertEquals(MountMotion.Event.NONE, step.event());
        assertTrue(step.velocity().y() < stats.jump() * MountTalent.SKY_STEP_FACTOR);
        step = MountMotion.ride(halo, stats, MountMotion.Input.NONE, 0.0, step.velocity(),
                MountMotion.Surroundings.OPEN_GROUND, step.ride());
        assertTrue("landing gives the step back", step.ride().airJump());
        // two jumps reach higher than one, but not into flight
        double one = leap(halo);
        assertTrue(one >= 2.0 && one <= 3.0);
        MountMotion.Ride ride = MountMotion.Ride.START;
        MountMotion.Velocity v = MountMotion.Velocity.ZERO;
        double height = 0.0;
        double peak = 0.0;
        for (int tick = 0; tick < 80 && height >= 0.0; tick++) {
            boolean ground = tick == 0;
            MountMotion.Input input = tick == 0 || tick == 9 ? press : MountMotion.Input.NONE;
            step = MountMotion.ride(halo, stats, input, 0.0, v, ground ? MountMotion.Surroundings.OPEN_GROUND : air,
                    ride);
            v = step.velocity();
            ride = step.ride();
            height += v.y();
            peak = Math.max(peak, height);
        }
        assertTrue("two jumps reach " + peak, peak > one + 1.0 && peak < 5.0);
        // the stateless step never steps on the air; other horses never do
        MountMotion.Velocity none = MountMotion.step(halo, press, 0.0, new MountMotion.Velocity(0.0, -0.3, 0.0), air);
        assertTrue(none.y() < -0.3);
        MountMotion.Step horse = MountMotion.ride(MountKind.HORSE, MountKind.HORSE.stats(), press, 0.0,
                new MountMotion.Velocity(0.0, -0.3, 0.0), air, MountMotion.Ride.START);
        assertEquals(MountMotion.Event.NONE, horse.event());
    }

    @Test
    public void theOwlHangsInTheAirTheStormDivesAndTheVoidClimbsFast() {
        MountMotion.Surroundings air = new MountMotion.Surroundings(false, false, 40.0);
        // without input the owl stays level; the classic griffin sinks
        MountMotion.Velocity owl = new MountMotion.Velocity(0.0, -0.2, 0.0);
        MountMotion.Velocity griffin = owl;
        for (int tick = 0; tick < 60; tick++) {
            owl = MountMotion.step(MountKind.GRIFFIN_OWL, MountMotion.Input.NONE, 0.0, owl, air);
            griffin = MountMotion.step(MountKind.GRIFFIN, MountMotion.Input.NONE, 0.0, griffin, air);
        }
        assertEquals(0.0, owl.y(), 1.0E-6);
        assertTrue(griffin.y() < -0.03);
        // the storm's dive: steep and faster
        MountMotion.Input dive = new MountMotion.Input(1.0, 0.0, false, true);
        MountMotion.Velocity storm = new MountMotion.Velocity(0.0, 0.0, MountKind.GRIFFIN_STORM.stats().topSpeed());
        MountMotion.Velocity plain = new MountMotion.Velocity(0.0, 0.0, MountKind.GRIFFIN.stats().topSpeed());
        for (int tick = 0; tick < 60; tick++) {
            storm = MountMotion.step(MountKind.GRIFFIN_STORM, dive, 0.0, storm, air);
            plain = MountMotion.step(MountKind.GRIFFIN, dive, 0.0, plain, air);
        }
        assertTrue("the storm dives at " + storm.y(), storm.y() <= -0.85);
        assertTrue(plain.y() > -0.5);
        assertEquals(MountKind.GRIFFIN_STORM.stats().topSpeed() * MountTalent.DIVE_BOOST, storm.horizontal(), 1.0E-3);
        assertEquals(MountKind.GRIFFIN.stats().topSpeed(), plain.horizontal(), 1.0E-3);
        // the void's climb and take-off outdo the classic griffin's by half again
        assertTrue(MountKind.GRIFFIN_VOID.stats().climb() >= MountKind.GRIFFIN.stats().climb() * 1.5);
        assertTrue(MountKind.GRIFFIN_VOID.stats().jump() >= MountKind.GRIFFIN.stats().jump() * 1.4);
        MountMotion.Velocity up = MountMotion.Velocity.ZERO;
        for (int tick = 0; tick < 30; tick++) {
            up = MountMotion.step(MountKind.GRIFFIN_VOID, CLIMB_KEY, 0.0, up, air);
        }
        assertEquals(MountKind.GRIFFIN_VOID.stats().climb(), up.y(), 1.0E-2);
    }

    private static final MountMotion.Input CLIMB_KEY = new MountMotion.Input(0.0, 0.0, true, false);

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
