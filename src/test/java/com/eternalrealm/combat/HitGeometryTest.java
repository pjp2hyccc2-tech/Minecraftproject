package com.eternalrealm.combat;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The attacker stands at (0, 64, 0) with eyes at 65.62. Yaw 0 faces +Z (south). Targets are zombie-sized
 * (0.6 x 1.95) unless stated otherwise.
 */
public class HitGeometryTest {
    private static final double EYE = 65.62;
    private static final AttackOrigin SOUTH = new AttackOrigin(0, EYE, 0, 0, 0);
    private static final HitShape SLASH = HitShape.arc(3.2, 50);
    private static final HitShape THRUST = HitShape.line(4.5, 0.6);
    private static final HitShape SPIN = HitShape.circle(3.8);

    private static Box zombieAt(double x, double z) {
        return Box.ofEntity(x, 64, z, 0.6, 1.95);
    }

    @Test
    public void lookVectorsFollowMinecraftConventions() {
        assertArrayEquals(new double[]{0, 0, 1}, HitGeometry.look(0, 0), 1e-9);
        assertArrayEquals(new double[]{-1, 0, 0}, HitGeometry.look(90, 0), 1e-9);
        assertArrayEquals(new double[]{0, 0, -1}, HitGeometry.look(180, 0), 1e-9);
        assertArrayEquals(new double[]{1, 0, 0}, HitGeometry.look(-90, 0), 1e-9);
        assertArrayEquals(new double[]{0, -1, 0}, HitGeometry.look(0, 90), 1e-9);
        assertEquals(90.0, HitGeometry.horizontalAngle(0, 1, 0), 1e-9);
        assertEquals(180.0, HitGeometry.horizontalAngle(0, 0, -1), 1e-9);
    }

    @Test
    public void arcHitsInFrontWithinReachOnly() {
        assertTrue(HitGeometry.contains(SLASH, SOUTH, zombieAt(0, 2)));
        assertTrue("reach is measured to the hitbox edge", HitGeometry.contains(SLASH, SOUTH, zombieAt(0, 3.45)));
        assertFalse(HitGeometry.contains(SLASH, SOUTH, zombieAt(0, 3.6)));
        assertFalse("behind", HitGeometry.contains(SLASH, SOUTH, zombieAt(0, -2)));
        assertFalse("90 degrees to the side is outside a 50 degree half angle",
                HitGeometry.contains(SLASH, SOUTH, zombieAt(2, 0)));
        assertTrue("45 degrees is inside", HitGeometry.contains(SLASH, SOUTH, zombieAt(1.5, 1.5)));
        assertTrue("facing west hits the zombie to the west",
                HitGeometry.contains(SLASH, new AttackOrigin(0, EYE, 0, 90, 0), zombieAt(-2, 0)));
        assertTrue("standing inside the target's footprint", HitGeometry.contains(SLASH, SOUTH, zombieAt(0.1, -0.1)));
    }

    @Test
    public void arcHeightWindowFollowsThePitch() {
        Box flying = Box.ofEntity(0, 68.5, 2, 0.6, 0.6); // 2.9 blocks above the eyes
        assertFalse(HitGeometry.contains(SLASH, SOUTH, flying));
        assertTrue("looking up extends the window", HitGeometry.contains(SLASH, new AttackOrigin(0, EYE, 0, 0, -60), flying));

        Box small = Box.ofEntity(0, 63.0, 2, 0.4, 0.3); // a block below the feet
        assertFalse(HitGeometry.contains(SLASH, SOUTH, small));
        assertTrue("looking down extends the window", HitGeometry.contains(SLASH, new AttackOrigin(0, EYE, 0, 0, 45), small));
        assertTrue("a spider-sized mob on the ground is hit without aiming down",
                HitGeometry.contains(SLASH, SOUTH, Box.ofEntity(0, 64, 2, 1.4, 0.9)));
    }

    @Test
    public void lineHitsAlongTheThrustAndPierces() {
        assertTrue(HitGeometry.contains(THRUST, SOUTH, zombieAt(0, 2)));
        assertTrue(HitGeometry.contains(THRUST, SOUTH, zombieAt(0, 4.5)));
        assertFalse(HitGeometry.contains(THRUST, SOUTH, zombieAt(0, 5.3)));
        assertFalse("off the line", HitGeometry.contains(THRUST, SOUTH, zombieAt(1.5, 3)));
        assertFalse("behind", HitGeometry.contains(THRUST, SOUTH, zombieAt(0, -2)));
        assertTrue("a short mob below a level thrust", HitGeometry.contains(THRUST, SOUTH, Box.ofEntity(0, 64, 3, 0.7, 0.7)));
        assertFalse("too far below a level thrust", HitGeometry.contains(THRUST, SOUTH, Box.ofEntity(0, 64, 3, 0.4, 0.3)));
        assertTrue("aiming down reaches it", HitGeometry.contains(THRUST, new AttackOrigin(0, EYE, 0, 0, 30),
                Box.ofEntity(0, 64, 3, 0.4, 0.3)));
    }

    @Test
    public void circleHitsAllAroundWithinRadius() {
        assertTrue(HitGeometry.contains(SPIN, SOUTH, zombieAt(0, -3)));
        assertTrue(HitGeometry.contains(SPIN, SOUTH, zombieAt(3, 0)));
        assertTrue(HitGeometry.contains(SPIN, SOUTH, zombieAt(-2.5, 2.5)));
        assertFalse(HitGeometry.contains(SPIN, SOUTH, zombieAt(4.2, 0)));
        assertFalse("too high", HitGeometry.contains(SPIN, SOUTH, Box.ofEntity(0, 68, 2, 0.6, 0.6)));
    }

    @Test
    public void distanceIsMeasuredFromTheEyesToTheHitbox() {
        assertEquals(1.7 * 1.7, HitGeometry.distanceSquared(SOUTH, zombieAt(0, 2)), 1e-9);
        assertEquals(0.0, HitGeometry.distanceSquared(SOUTH, zombieAt(0, 0)), 1e-9);
    }
}
