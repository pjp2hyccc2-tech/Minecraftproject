package com.eternalrealm.world.map;

import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.gen.AstoriaAreas;
import com.eternalrealm.world.gen.AstoriaBiome;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.FieldTerrain;
import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.gen.LandmarkSites;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class MapGeometryTest {
    private static final double EPS = 1.0E-9;

    @Test
    public void markersCoverTheCapitalTheTowerAndEveryDungeon() {
        List<MapMarkers.Marker> markers = MapMarkers.astoria();
        assertEquals(MapMarkers.Kind.CAPITAL, markers.get(0).kind());
        assertEquals(1 + LandmarkSites.all().size(), markers.size());
        Set<String> dungeons = new HashSet<>();
        for (MapMarkers.Marker marker : markers) {
            if (marker.kind() == MapMarkers.Kind.CAPITAL) {
                continue;
            }
            dungeons.add(marker.dungeonId());
            assertEquals(marker.dungeonId(), Areas.dungeon(marker.dungeonId()).orElseThrow().areaId(), marker.areaId());
            assertEquals(marker.dungeonId(), AstoriaAreas.areaAt(marker.x(), marker.z()), marker.areaId());
            assertEquals(Dungeons.byId(marker.dungeonId()).orElseThrow().recommendedLevel(), marker.level());
        }
        assertEquals(Set.of("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8"), dungeons);
        MapMarkers.Marker tower = markers.get(markers.size() - 1);
        assertEquals(MapMarkers.Kind.TOWER, tower.kind());
        assertEquals(AstoriaLayout.TOWER_Z + 0.5, tower.z(), EPS);
    }

    @Test
    public void colourArithmetic() {
        assertEquals(0x806040, MapMath.shade(0xFFC080, 0.5));
        assertEquals(0xFFFFFF, MapMath.shade(0xC0C0C0, 2.0));
        assertEquals(0x808080, MapMath.mix(0x000000, 0xFFFFFF, 0.5021));
        assertEquals(0xFF332211, MapMath.opaque(0x23332211));
        assertEquals(0xFF112233, MapMath.argbToAbgr(0xFF332211));
        assertEquals(MapMath.LIT, MapMath.slope(70, 69), EPS);
        assertEquals(MapMath.FLAT, MapMath.slope(70, 70), EPS);
        assertEquals(MapMath.SHADED, MapMath.slope(69, 70), EPS);
        assertTrue(MapMath.waterDepth(8) < MapMath.waterDepth(1));
        assertTrue("deep water is darker", MapMath.column(0x4060FF, 60, 60, 9) < MapMath.column(0x4060FF, 60, 60, 1));
    }

    @Test
    public void minimapProjection() {
        // north-up: east is right, south is down
        assertArrayEquals(new double[] {10.0, 0.0}, MapMath.toMinimap(5.0, 0.0, 0.0, false, 2.0), EPS);
        assertArrayEquals(new double[] {0.0, 10.0}, MapMath.toMinimap(0.0, 5.0, 123.0, false, 2.0), EPS);
        // rotating: whatever the player faces is up
        for (double yaw : new double[] {0.0, 90.0, 180.0, 270.0, 33.0}) {
            double radians = Math.toRadians(yaw);
            double[] ahead = MapMath.toMinimap(-Math.sin(radians) * 4.0, Math.cos(radians) * 4.0, yaw, true, 1.0);
            assertArrayEquals("yaw " + yaw, new double[] {0.0, -4.0}, ahead, 1.0E-6);
        }
        double[] inside = MapMath.clampToCircle(3.0, 4.0, 10.0);
        assertArrayEquals(new double[] {3.0, 4.0, 0.0}, inside, EPS);
        double[] pinned = MapMath.clampToCircle(30.0, 40.0, 10.0);
        assertArrayEquals(new double[] {6.0, 8.0, 1.0}, pinned, EPS);
        // the player arrow points up when facing north on a north-up map
        assertEquals(0.0, Math.sin(MapMath.arrowAngle(180.0, 0.0)), 1.0E-9);
        assertEquals(Math.PI / 2.0, MapMath.arrowAngle(270.0, 0.0) % (2.0 * Math.PI), 1.0E-9);
    }

    @Test
    public void viewportZoomKeepsThePointUnderTheMouse() {
        MapViewport view = new MapViewport(100.0, -50.0, 4.0);
        assertEquals(100.0, view.worldX(200.0, 200.0), EPS);
        double worldX = view.worldX(260.0, 200.0);
        double worldZ = view.worldZ(90.0, 120.0);
        view.zoom(0.5, 260.0, 90.0, 200.0, 120.0);
        assertEquals(2.0, view.blocksPerPixel(), EPS);
        assertEquals(worldX, view.worldX(260.0, 200.0), 1.0E-6);
        assertEquals(worldZ, view.worldZ(90.0, 120.0), 1.0E-6);
        view.drag(10.0, 0.0);
        assertEquals(worldX - 20.0, view.worldX(260.0, 200.0), 1.0E-6);
        view.zoom(1.0E9, 0, 0, 0, 0);
        assertEquals(MapViewport.MAX_BLOCKS_PER_PIXEL, view.blocksPerPixel(), EPS);
        view.zoom(1.0E-9, 0, 0, 0, 0);
        assertEquals(MapViewport.MIN_BLOCKS_PER_PIXEL, view.blocksPerPixel(), EPS);
        view.centerOn(1.0E9, Double.NaN);
        assertEquals(MapViewport.MAX_PAN, view.centerX(), EPS);
        assertEquals(0.0, view.centerZ(), EPS);
        assertEquals(view.screenX(500.0, 10.0), 10.0 + (500.0 - view.centerX()) * view.pixelsPerBlock(), 1.0E-6);
    }

    @Test
    public void theAtlasIsDeterministicAndFollowsTheLayout() {
        int[] a = AtlasPainter.paint(64, true);
        assertArrayEquals(a, AtlasPainter.paint(64, true));
        for (int pixel : a) {
            assertEquals("opaque", 0xFF, pixel >>> 24);
        }
        assertNotEquals("biomes or rings only", AtlasPainter.paint(64, false)[32 * 64 + 40], a[32 * 64 + 40]);
        // the capital is light, the tower is dark
        int capital = AtlasPainter.color(0.0, 0.0, 4.0, true, 1.0, 70.0);
        int tower = AtlasPainter.color(AstoriaLayout.TOWER_X, AstoriaLayout.TOWER_Z, 4.0, true, 1.0, 78.0);
        assertTrue(brightness(capital) > 150);
        assertTrue(brightness(tower) < 80);
        // every biome has its own colour
        Set<Integer> colours = new HashSet<>();
        for (AstoriaBiome biome : AstoriaBiome.values()) {
            colours.add(AtlasPainter.biomeColor(biome));
        }
        assertEquals(AstoriaBiome.values().length, colours.size());
        // the field borders are inked: find an inked border point on the meadow's edge
        boolean inked = false;
        for (int x = -900; x <= 900 && !inked; x++) {
            double z = 260.0;
            if (FieldLayout.borderDistance(x, z) < 1.0 && Math.floorMod((long) Math.floor((x + z) / 22.0), 3L) != 2L
                    && FieldLayout.borderDistance(x + 14, z) > 6.0) {
                double h = FieldTerrain.heightAt(x, z);
                double hb = FieldTerrain.heightAt(x + 14, z);
                if (FieldTerrain.waterTop(x, z, (int) h) == FieldTerrain.NO_WATER
                        && FieldTerrain.waterTop(x + 14, z, (int) hb) == FieldTerrain.NO_WATER) {
                    int on = AtlasPainter.color(x, z, 4.0, true, 1.0, h);
                    int beside = AtlasPainter.color(x + 14, z, 4.0, true, 1.0, hb);
                    assertTrue(brightness(on) < brightness(beside));
                    inked = true;
                }
            }
        }
        assertTrue("no field border found to check", inked);
        // beyond the bounds the map fades into clouds
        assertTrue(brightness(AtlasPainter.color(1890.0, 1490.0, 4.0, true, 1.0, 40.0)) > 180);
    }

    private static int brightness(int rgb) {
        return (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
    }
}
