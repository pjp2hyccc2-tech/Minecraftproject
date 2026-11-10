package com.eternalrealm.world.field;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class FieldMapTest {
    @Test
    public void everyFieldOwnsAWideRegion() {
        int[] cells = new int[FieldLayout.FIELD_COUNT + 1];
        for (int x = FieldLayout.MIN_X; x <= FieldLayout.MAX_X; x += 50) {
            for (int z = FieldLayout.MIN_Z; z <= FieldLayout.MAX_Z; z += 50) {
                cells[FieldLayout.areaAt(x, z)]++;
            }
        }
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            // 50 x 50 cells: every field covers at least ~0.2 km² (most cover far more)
            assertTrue("field " + field + " has only " + cells[field] + " cells", cells[field] >= 80);
        }
    }

    @Test
    public void theCapitalIsFlatAndTheLandRampsAwayFromIt() {
        assertEquals(FieldLayout.CAPITAL_GROUND_Y, FieldTerrain.heightAt(0, 0), 0.0);
        assertEquals(FieldLayout.CAPITAL_GROUND_Y, FieldTerrain.heightAt(-150, 150), 0.0);
        assertEquals(FieldLayout.CAPITAL_GROUND_Y, FieldTerrain.heightAt(150, -180), 0.0);
        assertTrue(FieldLayout.inCapital(0, 0));
        assertTrue(!FieldLayout.inCapital(0, 260));
        // just outside the footprint the ground is still close to the capital's
        assertEquals(FieldLayout.CAPITAL_GROUND_Y, FieldTerrain.heightAt(0, 180), 2.0);
    }

    @Test
    public void heightsAreSmoothDeterministicAndInsideTheWorld() {
        for (int x = -1900; x <= 1900; x += 61) {
            for (int z = -1700; z <= 1500; z += 67) {
                double h = FieldTerrain.heightAt(x, z);
                assertEquals(h, FieldTerrain.heightAt(x, z), 0.0);
                assertTrue("height " + h + " at " + x + ", " + z, h >= -50 && h <= FieldTerrain.MAX_HEIGHT);
                double step = Math.abs(FieldTerrain.heightAt(x + 1, z) - h);
                boolean cliff = Math.abs(FieldTerrain.cliffSide(x, z)) < 20.0;
                assertTrue("terrain jumps " + step + " at " + x + ", " + z, cliff || step < 6.0);
            }
        }
    }

    @Test
    public void theLandmarksHaveTheirShapes() {
        // the soaring peaks rise far above the meadow
        assertTrue(FieldTerrain.heightAt(150, -1250) > 160.0);
        assertTrue(FieldTerrain.heightAt(-330, 610) < 90.0);
        // the plateau above the falls and the curtain of water in front of its cliff
        assertTrue(FieldTerrain.heightAt(FieldLayout.LIP_X + 30 * FieldLayout.CLIFF_NX,
                FieldLayout.LIP_Z + 30 * FieldLayout.CLIFF_NZ) > FieldLayout.PLATEAU_Y - 6);
        double curtainX = FieldLayout.LIP_X - 1.5 * FieldLayout.CLIFF_NX;
        double curtainZ = FieldLayout.LIP_Z - 1.5 * FieldLayout.CLIFF_NZ;
        assertTrue(FieldTerrain.isFalls(curtainX, curtainZ));
        int curtainGround = (int) Math.floor(FieldTerrain.heightAt(curtainX, curtainZ));
        assertEquals(FieldLayout.PLATEAU_Y - 1, FieldTerrain.waterTop(curtainX, curtainZ, curtainGround));
        assertTrue(curtainGround < FieldLayout.SEA_LEVEL);
        // the lake and its island
        int lakeGround = (int) Math.floor(FieldTerrain.heightAt(FieldLayout.LAKE_X + 150, FieldLayout.LAKE_Z));
        assertEquals(FieldLayout.SEA_LEVEL, FieldTerrain.waterTop(FieldLayout.LAKE_X + 150, FieldLayout.LAKE_Z,
                lakeGround));
        assertTrue(FieldTerrain.heightAt(FieldLayout.ISLAND_X, FieldLayout.ISLAND_Z) > FieldLayout.SEA_LEVEL);
        // the sea beyond the harbour and beyond the southern bounds; mountains beyond the northern ones
        assertTrue(FieldTerrain.heightAt(1650, 1250) < FieldLayout.SEA_LEVEL - 4);
        assertTrue(FieldTerrain.heightAt(0, FieldLayout.MAX_Z + 150) < FieldLayout.SEA_LEVEL);
        assertTrue(FieldTerrain.heightAt(0, FieldLayout.MIN_Z - 150) > 200);
        // the quarry is a deep terraced pit in the mine's hills
        assertTrue(FieldTerrain.heightAt(FieldTerrain.QUARRY_X, FieldTerrain.QUARRY_Z) < 70.0);
    }

    @Test
    public void waystonesStandOnDryLandInTheirFields() {
        Set<String> ids = new HashSet<>();
        for (FieldLayout.Waystone stone : FieldLayout.waystones()) {
            assertTrue("duplicate " + stone.id(), ids.add(stone.id()));
            assertEquals(stone.id(), stone.field(), FieldLayout.areaAt(stone.x(), stone.z()));
            int ground = (int) Math.floor(FieldTerrain.heightAt(stone.x(), stone.z()));
            assertEquals(stone.id() + " under water", FieldTerrain.NO_WATER,
                    FieldTerrain.waterTop(stone.x(), stone.z(), ground));
        }
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            int f = field;
            assertTrue("field " + field + " has no waystone",
                    FieldLayout.waystones().stream().anyMatch(stone -> stone.field() == f));
        }
    }

    @Test
    public void roadsLeaveTheCapitalAndReachEveryField() {
        Set<Integer> reached = new HashSet<>();
        for (FieldLayout.Road road : FieldLayout.roads()) {
            for (double[] point : road.points()) {
                reached.add(FieldLayout.areaAt(point[0], point[1]));
            }
        }
        for (int field = 1; field <= FieldLayout.FIELD_COUNT; field++) {
            assertTrue("no road into field " + field, reached.contains(field));
        }
        double[] road = FieldLayout.nearestRoad(0, 176);
        assertEquals(0.0, road[0], 1e-9);
    }

    @Test
    public void surfacesFollowTheFields() {
        assertEquals(FieldTerrain.Surface.DIRT_PATH,
                FieldTerrain.surfaceAt(-160, 450, 72, FieldTerrain.NO_WATER, 0.1));
        assertEquals(FieldTerrain.Surface.QUARRY, FieldTerrain.surfaceAt(FieldTerrain.QUARRY_X + 10,
                FieldTerrain.QUARRY_Z, 70, FieldTerrain.NO_WATER, 0.5));
        assertEquals(FieldTerrain.Surface.SNOW, FieldTerrain.surfaceAt(150, -1250, 220, FieldTerrain.NO_WATER, 0.5));
        assertEquals(FieldTerrain.Surface.CLIFF, FieldTerrain.surfaceAt(600, -420, 100, FieldTerrain.NO_WATER, 3.0));
        assertEquals(FieldTerrain.Surface.LAKEBED, FieldTerrain.surfaceAt(FieldLayout.LAKE_X + 150,
                FieldLayout.LAKE_Z, 55, FieldLayout.SEA_LEVEL, 0.2));
        assertNotEquals(FieldTerrain.Surface.SNOW, FieldTerrain.surfaceAt(-330, 610, 72, FieldTerrain.NO_WATER, 0.1));
    }
}
