package com.eternalrealm.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TelegraphTest {
    private static final Telegraph.Style S = Telegraph.Style.DANGER;

    @Test
    public void circleAndRing() {
        Telegraph circle = Telegraph.circle(S, 10, 64, 10, 3, 20);
        assertTrue(circle.contains(12, 64, 10));
        assertFalse(circle.contains(13.5, 64, 10));
        assertFalse("too far below", circle.contains(10, 60, 10));
        assertTrue("a jump does not avoid it", circle.contains(10, 66, 10));
        Telegraph ring = Telegraph.ring(S, 0, 0, 0, 4, 9, 20);
        assertFalse("safe centre", ring.contains(1, 0, 1));
        assertTrue(ring.contains(6, 0, 0));
        assertFalse(ring.contains(10, 0, 0));
    }

    @Test
    public void rectFacesItsYaw() {
        // yaw 0 = south (+z)
        Telegraph south = Telegraph.rect(S, 0, 0, 0, 0, 10, 2, 20);
        assertTrue(south.contains(0, 0, 5));
        assertTrue(south.contains(0.9, 0, 9.9));
        assertFalse("behind the origin", south.contains(0, 0, -1));
        assertFalse("beside the strip", south.contains(1.5, 0, 5));
        // yaw 90 = west (-x)
        Telegraph west = Telegraph.rect(S, 0, 0, 0, 90, 10, 2, 20);
        assertTrue(west.contains(-5, 0, 0));
        assertFalse(west.contains(5, 0, 0));
        Telegraph line = Telegraph.line(S, 0, 0, 0, 0, -8, 2, 20); // north
        assertEquals(8.0, line.a(), 1.0E-9);
        assertTrue(line.contains(0, 0, -7));
        assertFalse(line.contains(0, 0, 3));
    }

    @Test
    public void squareCrossAndCone() {
        Telegraph square = Telegraph.square(S, 0, 0, 0, 45, 2, 20);
        assertTrue(square.contains(0, 0, 2.5)); // rotated 45 degrees: the corner points along z
        assertFalse(square.contains(2.2, 0, 2.2));
        Telegraph cross = Telegraph.cross(S, 0, 0, 0, 0, 10, 2, 20);
        assertTrue(cross.contains(0, 0, 9));
        assertTrue(cross.contains(9, 0, 0));
        assertFalse(cross.contains(5, 0, 5));
        Telegraph cone = Telegraph.cone(S, 0, 0, 0, 0, 8, 30, 20);
        assertTrue(cone.contains(0, 0, 7));
        assertTrue(cone.contains(3, 0, 6)); // ~26.6 degrees off the axis
        assertFalse(cone.contains(5, 0, 5)); // 45 degrees
        assertFalse(cone.contains(0, 0, -2));
    }

    @Test
    public void rejectsInvalidShapes() {
        assertThrows(IllegalArgumentException.class, () -> Telegraph.ring(S, 0, 0, 0, 5, 4, 20));
        assertThrows(IllegalArgumentException.class, () -> Telegraph.circle(S, Double.NaN, 0, 0, 3, 20));
        assertThrows(IllegalArgumentException.class, () -> Telegraph.circle(S, 0, 0, 0, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> Telegraph.circle(S, 0, 0, 0, 100, 20));
    }
}
