package com.eternalrealm.boss;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Shapes are anchored at y = 64 unless stated otherwise. Yaw 0 faces +Z (south), 90 faces -X (west). */
public class BossShapeTest {
    private static final Point ORIGIN = new Point(0, 64, 0);

    @Test
    public void circleUsesTheHorizontalDistanceAndAVerticalTolerance() {
        BossShape circle = new BossShape.Circle(new Point(10, 64, 10), 3);
        assertTrue(circle.contains(10, 64, 10));
        assertTrue("edge is inside", circle.contains(13, 64, 10));
        assertFalse(circle.contains(13.01, 64, 10));
        assertTrue("diagonal", circle.contains(12, 64, 12));
        assertFalse(circle.contains(12.2, 64, 12.2));
        assertTrue("jumping does not avoid a ground attack", circle.contains(10, 65.25, 10));
        assertTrue(circle.contains(10, 64 + BossRules.VERTICAL_TOLERANCE, 10));
        assertFalse("far above (a ledge) is outside", circle.contains(10, 64 + BossRules.VERTICAL_TOLERANCE + 0.01, 10));
        assertFalse("far below is outside", circle.contains(10, 60.9, 10));
        assertEquals(new Point(10, 64, 10), circle.anchor());
    }

    @Test
    public void coneFollowsMinecraftYaw() {
        BossShape south = new BossShape.Cone(ORIGIN, 0, 8, 30);
        assertTrue(south.contains(0, 64, 5));
        assertTrue(south.contains(2, 64, 5)); // about 22 degrees off axis
        assertFalse("40 degrees off axis", south.contains(4.2, 64, 5));
        assertFalse("behind", south.contains(0, 64, -5));
        assertFalse("beyond range", south.contains(0, 64, 8.1));
        assertTrue("standing on the origin", south.contains(0, 64, 0));

        BossShape west = new BossShape.Cone(ORIGIN, 90, 8, 30);
        assertTrue(west.contains(-5, 64, 0));
        assertFalse(west.contains(5, 64, 0));
        BossShape north = new BossShape.Cone(ORIGIN, 180, 8, 30);
        assertTrue(north.contains(0, 64, -5));
        BossShape east = new BossShape.Cone(ORIGIN, -90, 8, 30);
        assertTrue(east.contains(5, 64, 0));
        assertTrue("yaw 270 equals -90", new BossShape.Cone(ORIGIN, 270, 8, 30).contains(5, 64, 0));

        BossShape allAround = new BossShape.Cone(ORIGIN, 0, 8, 180);
        assertTrue(allAround.contains(0, 64, -7));
        assertFalse(south.contains(0, 70, 5));
    }

    @Test
    public void lineIsAStripWithHalfTheWidthOnEachSide() {
        BossShape line = new BossShape.Line(ORIGIN, new Point(0, 64, 20), 4);
        assertTrue(line.contains(0, 64, 10));
        assertTrue(line.contains(2, 64, 10));
        assertFalse(line.contains(2.1, 64, 10));
        assertTrue(line.contains(-2, 64, 0));
        assertFalse("before the start (rounded cap only within half width)", line.contains(0, 64, -2.1));
        assertTrue(line.contains(0, 64, 21.9));
        assertFalse(line.contains(0, 64, 22.1));
        assertFalse(line.contains(0, 68, 10));
        assertEquals(20.0, ((BossShape.Line) line).length(), 1e-9);

        BossShape diagonal = new BossShape.Line(ORIGIN, new Point(10, 64, 10), 2);
        assertTrue(diagonal.contains(5, 64, 5));
        assertTrue(diagonal.contains(5.5, 64, 4.5)); // 0.71 from the axis
        assertFalse(diagonal.contains(6, 64, 4)); // 1.41 from the axis

        BossShape point = new BossShape.Line(ORIGIN, ORIGIN, 2);
        assertTrue("degenerate line is a small disc", point.contains(0.9, 64, 0));
        assertFalse(point.contains(1.1, 64, 0));
    }

    @Test
    public void ringIsSafeNearTheCenterAndFarAway() {
        BossShape ring = new BossShape.Ring(ORIGIN, 4, 10);
        assertFalse("safe spot at the boss", ring.contains(0, 64, 0));
        assertFalse(ring.contains(3.9, 64, 0));
        assertTrue(ring.contains(4, 64, 0));
        assertTrue(ring.contains(0, 64, -7));
        assertTrue(ring.contains(10, 64, 0));
        assertFalse("safe beyond the outer radius", ring.contains(10.1, 64, 0));
        assertFalse(ring.contains(7, 70, 0));
    }

    @Test
    public void arenaContainsEveryone() {
        BossShape arena = new BossShape.Arena(ORIGIN);
        assertTrue(arena.contains(0, 64, 0));
        assertTrue(arena.contains(500, 0, -500));
        assertEquals(ORIGIN, arena.anchor());
    }

    @Test
    public void invalidShapesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BossShape.Circle(ORIGIN, 0));
        assertThrows(IllegalArgumentException.class, () -> new BossShape.Circle(ORIGIN, Double.NaN));
        assertThrows(NullPointerException.class, () -> new BossShape.Circle(null, 2));
        assertThrows(IllegalArgumentException.class, () -> new BossShape.Cone(ORIGIN, 0, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new BossShape.Cone(ORIGIN, Double.POSITIVE_INFINITY, 5, 30));
        assertThrows(IllegalArgumentException.class, () -> new BossShape.Ring(ORIGIN, 5, 4));
        assertThrows(IllegalArgumentException.class, () -> new BossShape.Line(ORIGIN, ORIGIN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Point(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BossPosition(0, 0, 0, Double.NaN));
        assertThrows(NullPointerException.class, () -> new TargetSnapshot(null, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TargetSnapshot(java.util.UUID.randomUUID(), 0, 0, 0, Double.NaN));
    }
}
