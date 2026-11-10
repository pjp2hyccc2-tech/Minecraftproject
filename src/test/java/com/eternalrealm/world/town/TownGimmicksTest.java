package com.eternalrealm.world.town;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TownGimmicksTest {
    private static final double EPS = 1e-9;

    @Test
    public void clockReadsMinecraftTime() {
        assertEquals(6, TowerClock.hour(0));
        assertEquals(12, TowerClock.hour(6000));
        assertEquals(0, TowerClock.hour(18000));
        assertEquals(5, TowerClock.hour(23999));
        assertEquals(6, TowerClock.hour(24000 * 7L));
        assertEquals(5, TowerClock.hour(-1));
        assertEquals(180.0, TowerClock.hourAngle(0), EPS);        // six o'clock
        assertEquals(0.0, TowerClock.hourAngle(6000), EPS);       // noon
        assertEquals(195.0, TowerClock.hourAngle(500), EPS);      // half past six
        assertEquals(0.0, TowerClock.minuteAngle(6000), EPS);
        assertEquals(180.0, TowerClock.minuteAngle(6500), EPS);
        assertEquals(15.0, TowerClock.minutes(6250), EPS);
    }

    @Test
    public void bellStrikesTwelveHourCount() {
        assertEquals(12, TowerClock.strikes(0));
        assertEquals(12, TowerClock.strikes(12));
        assertEquals(1, TowerClock.strikes(13));
        assertEquals(6, TowerClock.strikes(6));
        assertEquals(11, TowerClock.strikes(23));
    }

    @Test
    public void windStaysInRangeAndStormsAreStronger() {
        double calm = 0.0;
        double storm = 0.0;
        for (int t = 0; t < 2000; t++) {
            double a = TownWind.strength(t * 0.7, 12345L, 0.0, 0.0);
            double b = TownWind.strength(t * 0.7, 12345L, 1.0, 1.0);
            assertTrue(a >= TownWind.MIN && a <= TownWind.MAX);
            assertTrue(b >= TownWind.MIN && b <= TownWind.MAX);
            calm += a;
            storm += b;
        }
        assertTrue(storm > calm * 1.5);
        assertTrue(TownWind.phase(1L) != TownWind.phase(2L));
        assertTrue(TownWind.phase(99L) >= 0.0 && TownWind.phase(99L) < Math.PI * 2.0);
    }

    @Test
    public void flagStaysOnThePoleAndKeepsItsLength() {
        int cols = 18;
        int rows = 8;
        double[] grid = new double[(cols + 1) * (rows + 1) * 3];
        for (double wind : new double[]{0.2, 0.6, 1.4}) {
            for (double phase = 0.0; phase < 20.0; phase += 1.3) {
                FlagCloth.compute(3.0, 2.0, cols, rows, FlagCloth.Shape.RECTANGLE, phase, wind, grid);
                for (int row = 0; row <= rows; row++) {
                    int i = (row * (cols + 1)) * 3;
                    assertEquals("attached at the pole", 0.0, grid[i], EPS);
                    assertEquals(0.0, grid[i + 2], EPS);
                    assertEquals(-2.0 * row / rows, grid[i + 1], EPS);
                    double length = 0.0;
                    for (int col = 1; col <= cols; col++) {
                        int a = (row * (cols + 1) + col - 1) * 3;
                        int b = a + 3;
                        double dx = grid[b] - grid[a];
                        double dy = grid[b + 1] - grid[a + 1];
                        double dz = grid[b + 2] - grid[a + 2];
                        length += Math.sqrt(dx * dx + dy * dy + dz * dz);
                        assertTrue("never folds back over the pole", grid[b] > grid[a]);
                    }
                    assertTrue("cloth length kept roughly: " + length, length > 2.7 && length < 3.6);
                }
            }
        }
    }

    @Test
    public void calmFlagDroopsAndGaleFlagStreams() {
        int cols = 12;
        int rows = 4;
        double[] calm = new double[(cols + 1) * (rows + 1) * 3];
        double[] gale = new double[calm.length];
        FlagCloth.compute(3.0, 2.0, cols, rows, FlagCloth.Shape.RECTANGLE, 0.0, 0.2, calm);
        FlagCloth.compute(3.0, 2.0, cols, rows, FlagCloth.Shape.RECTANGLE, 0.0, 1.2, gale);
        int tip = cols * 3 + 1;                                   // top row, fly end, y
        assertTrue(calm[tip] < gale[tip] - 0.5);
    }

    @Test
    public void pennantNarrowsToAPoint() {
        assertEquals(1.0, FlagCloth.heightAt(FlagCloth.Shape.PENNANT, 1.0, 0.0), EPS);
        assertEquals(0.1, FlagCloth.heightAt(FlagCloth.Shape.PENNANT, 1.0, 1.0), EPS);
        assertEquals(2.0, FlagCloth.heightAt(FlagCloth.Shape.RECTANGLE, 2.0, 1.0), EPS);
    }

    @Test
    public void jetsReachTheirHeights() {
        for (int power = 1; power <= 3; power++) {
            double v = WaterJet.verticalSpeed(power);
            assertEquals(WaterJet.apex(power), v * v / (2.0 * WaterJet.GRAVITY), 1e-9);
            double t = WaterJet.spoutTimeToDrop(power, 0.0);
            double[] landing = WaterJet.spoutPoint(power, t);
            assertEquals(WaterJet.reach(power), landing[0], 1e-6);
            assertEquals(0.0, landing[1], 1e-6);
            assertTrue(WaterJet.spoutTimeToDrop(power, 1.0) > t);
        }
        assertEquals(1, WaterJet.clampPower(-4));
        assertEquals(3, WaterJet.clampPower(9));
        assertTrue(WaterJet.apex(3) > WaterJet.apex(1));
    }
}
