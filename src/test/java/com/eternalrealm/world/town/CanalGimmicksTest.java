package com.eternalrealm.world.town;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Bunting cords, secret door timing and the shop sign swing (the pure maths behind the canal town decorations). */
public class CanalGimmicksTest {
    private static final double EPS = 1e-6;

    @Test
    public void cordRunsFromTheAnchorToTheFarEnd() {
        double[] p = new double[6];
        for (double[] end : new double[][]{{12, 0, 0}, {0, 3, -9}, {-20, -4, 7}, {5, 0, 5}, {31, 2, -31}}) {
            BuntingLine line = BuntingLine.of(end[0], end[1], end[2], 1.4);
            line.at(0.0, p, 0);
            assertEquals(0.0, p[0], EPS);
            assertEquals(0.0, p[1], EPS);
            assertEquals(0.0, p[2], EPS);
            line.at(line.length(), p, 0);
            assertEquals("x", end[0], p[0], 1e-5);
            assertEquals("y", end[1], p[1], 1e-5);
            assertEquals("z", end[2], p[2], 1e-5);
            double chord = Math.sqrt(end[0] * end[0] + end[1] * end[1] + end[2] * end[2]);
            assertTrue("a sagging cord is longer than its chord", line.length() > chord);
        }
    }

    @Test
    public void cordSagsByTheRequestedDepthAtMidSpan() {
        for (double sag : new double[]{0.3, 1.2, 2.5}) {
            for (double rise : new double[]{0.0, 2.0, -3.0}) {
                BuntingLine line = BuntingLine.of(16.0, rise, 0.0, sag);
                assertEquals(sag, line.sag(), 1e-6);
                // find the point at the horizontal middle and measure it against the chord
                double lo = 0.0;
                double hi = line.length();
                double[] p = new double[6];
                for (int i = 0; i < 80; i++) {
                    double mid = 0.5 * (lo + hi);
                    line.at(mid, p, 0);
                    if (p[0] < 8.0) {
                        lo = mid;
                    } else {
                        hi = mid;
                    }
                }
                line.at(lo, p, 0);
                assertEquals("sag " + sag + " rise " + rise, sag, rise / 2.0 - p[1], 1e-4);
                assertEquals(sag, line.depthAt(lo), 1e-4);
            }
        }
    }

    @Test
    public void arcLengthSpacesPointsEvenly() {
        BuntingLine line = BuntingLine.of(-14.0, 3.0, 9.0, 2.0);
        double[] a = new double[6];
        double[] b = new double[6];
        double step = line.length() / 200.0;
        for (int i = 0; i < 200; i++) {
            line.at(i * step, a, 0);
            line.at((i + 1) * step, b, 0);
            double d = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
            assertEquals("chord of a short piece equals its arc length", step, d, step * 1e-3);
            double t = Math.sqrt(sq(a[3]) + sq(a[4]) + sq(a[5]));
            assertEquals("unit tangent", 1.0, t, 1e-9);
            // the tangent points along the curve
            double dot = (b[0] - a[0]) * a[3] + (b[1] - a[1]) * a[4] + (b[2] - a[2]) * a[5];
            assertTrue(dot > 0.99 * d);
        }
    }

    @Test
    public void flatOrUprightCordsAreStraight() {
        BuntingLine upright = BuntingLine.of(0.0, 6.0, 0.0, 1.5);
        assertTrue(upright.straight());
        assertEquals(6.0, upright.length(), EPS);
        BuntingLine taut = BuntingLine.of(8.0, 0.0, 0.0, 0.0);
        assertTrue(taut.straight());
        double[] p = new double[6];
        taut.at(4.0, p, 0);
        assertEquals(4.0, p[0], EPS);
        assertEquals(0.0, p[1], EPS);
        assertEquals(0.0, taut.depthAt(4.0), EPS);
        BuntingLine silly = BuntingLine.of(3.0, 0.0, 0.0, 50.0);
        assertEquals("sag is clamped to the span", 3.0, silly.sag(), 1e-6);
        assertTrue(Double.isFinite(silly.length()));
        BuntingLine nan = BuntingLine.of(5.0, 0.0, 0.0, Double.NaN);
        assertTrue(nan.straight());
        BuntingLine none = BuntingLine.of(0.0, 0.0, 0.0, 1.0);
        assertEquals(0.0, none.length(), EPS);
    }

    @Test
    public void swingTurnsAboutTheChordAndKeepsTheEnds() {
        BuntingLine line = BuntingLine.of(10.0, 2.0, -6.0, 1.5);
        double angle = 0.6;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double[] end = {10.0, 2.0, -6.0};
        line.swing(cos, sin, end, 0);
        assertArrayEquals("the far end lies on the axis", new double[]{10.0, 2.0, -6.0}, end, 1e-9);
        double[] p = new double[6];
        double[] q = new double[6];
        for (double s = 0.0; s < line.length(); s += 0.7) {
            line.at(s, p, 0);
            line.at(s + 0.7, q, 0);
            double before = Math.sqrt(sq(q[0] - p[0]) + sq(q[1] - p[1]) + sq(q[2] - p[2]));
            line.swing(cos, sin, p, 0);
            line.swing(cos, sin, q, 0);
            double after = Math.sqrt(sq(q[0] - p[0]) + sq(q[1] - p[1]) + sq(q[2] - p[2]));
            assertEquals("a rigid turn", before, after, 1e-9);
            assertEquals("the tangent stays a unit vector", 1.0, Math.sqrt(sq(p[3]) + sq(p[4]) + sq(p[5])), 1e-6);
        }
    }

    @Test
    public void windPushesTheCordDownwind() {
        // a cord running north-south across the west wind swings east
        BuntingLine line = BuntingLine.of(0.0, 0.0, 12.0, 1.5);
        double cross = line.crossWind(BuntingSway.WIND_X, BuntingSway.WIND_Z);
        assertEquals(1.0, cross, EPS);
        double angle = BuntingSway.meanSwing(1.0, cross);
        assertTrue(angle > 0.0);
        double[] p = new double[6];
        line.at(line.length() / 2.0, p, 0);
        double restX = p[0];
        line.swing(Math.cos(angle), Math.sin(angle), p, 0);
        assertTrue("the middle moves east", p[0] > restX + 0.3);
        assertTrue("and rises a little", p[1] > -1.5);
        // a cord along the wind does not swing out at all
        BuntingLine along = BuntingLine.of(12.0, 0.0, 0.0, 1.5);
        assertEquals(0.0, along.crossWind(BuntingSway.WIND_X, BuntingSway.WIND_Z), EPS);
        assertEquals(0.0, BuntingSway.meanSwing(1.0, 0.0), EPS);
    }

    @Test
    public void swayStaysGentleAndBounded() {
        double max = 0.0;
        for (int t = 0; t < 4000; t++) {
            double a = BuntingSway.swingAngle(t * 0.05, 77L, 1.4, 1.0, 1.2);
            max = Math.max(max, Math.abs(a));
            double c = BuntingSway.clothAngle(t * 0.3, t * 0.01, 1.4, 1.0, 1.0);
            assertTrue(Math.abs(c) < 1.6);
        }
        assertTrue("a storm swings the cord visibly: " + max, max > 0.5);
        assertTrue("but never over the top: " + max, max < 1.0);
        assertTrue(BuntingSway.pendulumOmega(3.0) < BuntingSway.pendulumOmega(0.5));
        assertTrue(BuntingSway.flutterSpeed(1.4) > BuntingSway.flutterSpeed(0.2));
        assertEquals(0.0, BuntingSway.clampWind(Double.NaN), EPS);
        assertEquals(1.6, BuntingSway.clampWind(9.0), EPS);
    }

    @Test
    public void layoutsFitBetweenTheHooks() {
        for (BuntingStyle style : BuntingStyle.values()) {
            for (double length : new double[]{0.5, 1.0, 3.3, 12.0, 40.0}) {
                BuntingLayout layout = BuntingLayout.of(style, length, 1234L);
                for (int i = 0; i < layout.count(); i++) {
                    double half = layout.width(i) / 2.0;
                    assertTrue(style + " starts after the hook", layout.at(i) - half >= BuntingLayout.END_MARGIN - 1e-6);
                    assertTrue(style + " ends before the far hook",
                            layout.at(i) + half <= length - BuntingLayout.END_MARGIN + 1e-6);
                    assertTrue(layout.height(i) <= style.hang() + 0.2);
                }
                if (length >= 3.0) {
                    assertTrue(style + " has pieces on a " + length + " block cord", layout.count() > 0);
                }
            }
        }
        BuntingLayout pennants = BuntingLayout.of(BuntingStyle.PENNANT, 16.0, 1L);
        for (int i = 1; i < pennants.count(); i++) {
            assertEquals(BuntingStyle.PENNANT.spacing(), pennants.at(i) - pennants.at(i - 1), 1e-9);
            assertEquals("colours go round in turn", i, pennants.colorSlot(i));
        }
        BuntingLayout a = BuntingLayout.of(BuntingStyle.LAUNDRY, 20.0, 99L);
        BuntingLayout b = BuntingLayout.of(BuntingStyle.LAUNDRY, 20.0, 99L);
        assertEquals(a.count(), b.count());
        for (int i = 0; i < a.count(); i++) {
            assertEquals("the same laundry for everyone", a.kind(i), b.kind(i));
            assertEquals(a.at(i), b.at(i), 0.0);
            if (i > 0) {
                assertTrue("laundry does not overlap", a.at(i) - a.width(i) / 2.0 > a.at(i - 1) + a.width(i - 1) / 2.0);
            }
        }
        BuntingLayout garland = BuntingLayout.of(BuntingStyle.GARLAND, 10.0, 5L);
        int flowers = 0;
        for (int i = 0; i < garland.count(); i++) {
            if (garland.kind(i) == BuntingLayout.KIND_FLOWER) {
                assertTrue(garland.colorSlot(i) >= 0);
                flowers++;
            } else {
                assertEquals("leaves are not tinted", -1, garland.colorSlot(i));
            }
        }
        assertTrue(flowers > 5);
    }

    @Test
    public void stylesAndColoursParse() {
        assertEquals(BuntingStyle.LANTERNS, BuntingStyle.of("lanterns"));
        assertEquals(BuntingStyle.LAUNDRY, BuntingStyle.of(" Laundry "));
        assertEquals(BuntingStyle.PENNANT, BuntingStyle.of("nonsense"));
        assertEquals(BuntingStyle.PENNANT, BuntingStyle.of(null));
        for (BuntingStyle style : BuntingStyle.values()) {
            assertEquals(style, BuntingStyle.of(style.id()));
            assertTrue(style.defaultColors().length > 0);
        }
        assertArrayEquals(new int[]{14, 0, 15}, BuntingStyle.cleanColors(new int[]{14, -1, 0, 16, 15}, 8));
        assertArrayEquals(new int[]{1, 2}, BuntingStyle.cleanColors(new int[]{1, 2, 3}, 2));
        assertEquals(0, BuntingStyle.cleanColors(null, 4).length);
        assertEquals(0xD43A30, BuntingStyle.dyeRgb(14));
        assertEquals(BuntingStyle.dyeRgb(0), BuntingStyle.dyeRgb(16));
    }

    @Test
    public void secretDoorSinksThenSlidesAndBack() {
        assertEquals(0.0, SecretDoorMotion.progress(SecretDoorMotion.IDLE, 0L, 50.0, false), EPS);
        assertEquals(1.0, SecretDoorMotion.progress(SecretDoorMotion.IDLE, 0L, 50.0, true), EPS);
        assertEquals(0.0, SecretDoorMotion.progress(SecretDoorMotion.OPENING, 100L, 100.0, true), EPS);
        assertEquals(0.5, SecretDoorMotion.progress(SecretDoorMotion.OPENING, 100L, 106.0, true), EPS);
        assertEquals(1.0, SecretDoorMotion.progress(SecretDoorMotion.OPENING, 100L, 140.0, true), EPS);
        assertEquals(1.0, SecretDoorMotion.progress(SecretDoorMotion.CLOSING, 100L, 99.0, true), EPS);
        assertEquals(0.0, SecretDoorMotion.progress(SecretDoorMotion.CLOSING, 100L, 112.0, true), EPS);
        double lastSlide = 0.0;
        double lastRecess = 0.0;
        for (int i = 0; i <= 100; i++) {
            double p = i / 100.0;
            double slide = SecretDoorMotion.slide(p);
            double recess = SecretDoorMotion.recess(p);
            assertTrue("monotonic", slide >= lastSlide - 1e-12 && recess >= lastRecess - 1e-12);
            if (slide > 0.0) {
                assertEquals("it only slides once it has sunk", SecretDoorMotion.RECESS, recess, 1e-12);
            }
            lastSlide = slide;
            lastRecess = recess;
        }
        assertEquals(0.0, SecretDoorMotion.slide(0.0), EPS);
        assertEquals(0.0, SecretDoorMotion.recess(0.0), EPS);
        assertEquals(SecretDoorMotion.SLIDE, SecretDoorMotion.slide(1.0), EPS);
        assertEquals(12, SecretDoorMotion.MOVE_TICKS);
        assertTrue(SecretDoorMotion.finished(100L, 112L));
        assertFalse(SecretDoorMotion.finished(100L, 111L));
    }

    @Test
    public void aClosingDoorReopensFromWhereItIs() {
        long start = 1000L;
        for (long now = start; now <= start + SecretDoorMotion.MOVE_TICKS; now++) {
            double closing = SecretDoorMotion.progress(SecretDoorMotion.CLOSING, start, now, true);
            long reopened = SecretDoorMotion.reversedStart(start, now);
            double opening = SecretDoorMotion.progress(SecretDoorMotion.OPENING, reopened, now, true);
            assertEquals("no jump at tick " + now, closing, opening, EPS);
        }
        assertEquals(1, SecretDoorMotion.slideSide(true, true));
        assertEquals(1, SecretDoorMotion.slideSide(true, false));
        assertEquals(-1, SecretDoorMotion.slideSide(false, true));
        assertEquals(1, SecretDoorMotion.slideSide(false, false));
    }

    @Test
    public void shopSignsSwingGently() {
        double max = 0.0;
        double sum = 0.0;
        int n = 0;
        for (int t = 0; t < 5000; t++) {
            double a = SignSwing.angle(t * 0.05, 4242L, 1.0, 1.0);
            assertTrue(Math.abs(a) <= SignSwing.maxAngle(1.0) + 1e-9);
            max = Math.max(max, Math.abs(a));
            sum += a;
            n++;
        }
        assertTrue("it moves", max > 0.05);
        assertTrue("gently (under 15 degrees): " + max, max < Math.toRadians(15.0));
        assertTrue("it leans downwind on average", sum / n > 0.03);
        double edgeOn = 0.0;
        for (int t = 0; t < 5000; t++) {
            edgeOn = Math.max(edgeOn, Math.abs(SignSwing.angle(t * 0.05, 4242L, 1.0, 0.0)));
        }
        assertTrue("a board edge-on to the wind barely moves", edgeOn < max * 0.6);
    }

    @Test
    public void floatingLanternsStayInTheirBlockOnTheWater() {
        double[] d = new double[2];
        double[] t = new double[2];
        double minBob = 1.0;
        double maxBob = -1.0;
        for (long seed : new long[]{1L, 77L, -9_123_456_789L}) {
            for (int i = 0; i < 4000; i++) {
                double s = i * 0.05;
                double bob = FloatingLanternMotion.bob(s, seed);
                assertTrue(Math.abs(bob) <= FloatingLanternMotion.MAX_BOB);
                minBob = Math.min(minBob, bob);
                maxBob = Math.max(maxBob, bob);
                FloatingLanternMotion.drift(s, seed, d);
                assertTrue("drifts within its block", Math.hypot(d[0], d[1]) <= FloatingLanternMotion.MAX_DRIFT);
                FloatingLanternMotion.tilt(s, seed, t);
                assertTrue(Math.abs(t[0]) <= FloatingLanternMotion.MAX_TILT
                        && Math.abs(t[1]) <= FloatingLanternMotion.MAX_TILT);
                double f = FloatingLanternMotion.flicker(s, seed);
                assertTrue(f >= 0.8 && f <= 1.0);
                assertTrue(Double.isFinite(FloatingLanternMotion.yaw(s, seed)));
            }
        }
        assertTrue("it bobs", maxBob - minBob > 0.04);
        assertTrue("lanterns move on their own phases",
                Math.abs(FloatingLanternMotion.bob(3.0, 1L) - FloatingLanternMotion.bob(3.0, 2L)) > 1e-6);
    }

    private static double sq(double v) {
        return v * v;
    }
}
