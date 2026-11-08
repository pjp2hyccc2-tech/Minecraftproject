package com.eternalrealm.world.gen;

import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.Dungeons;
import org.junit.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AstoriaLayoutTest {
    @Test
    public void ringsMatchTheAreaTable() {
        assertEquals(1, AstoriaAreas.areaAt(0, 0));
        assertEquals(1, AstoriaAreas.areaAtDistance(299.99));
        assertEquals(2, AstoriaAreas.areaAtDistance(300));
        assertEquals(3, AstoriaAreas.areaAtDistance(600));
        assertEquals(4, AstoriaAreas.areaAtDistance(900));
        assertEquals(5, AstoriaAreas.areaAtDistance(1200));
        assertEquals(6, AstoriaAreas.areaAtDistance(1500));
        assertEquals(6, AstoriaAreas.areaAtDistance(1799.9));
        assertEquals(7, AstoriaAreas.areaAtDistance(1800));
        assertEquals(7, AstoriaAreas.areaAtDistance(1.0e9));
        assertEquals(1, AstoriaAreas.areaAtDistance(Double.NaN));
        assertEquals(2, AstoriaAreas.areaAt(-212.2, 212.2));
        for (int area = 1; area <= AstoriaAreas.COUNT; area++) {
            assertEquals(area, AstoriaAreas.areaAtDistance(AstoriaAreas.innerRadius(area)));
            assertTrue(AstoriaAreas.titleKey(area).startsWith("area.eternalrealm."));
        }
    }

    /** The biome rings must match the gameplay areas of the world module exactly. */
    @Test
    public void ringsAndKeysMatchTheWorldModule() {
        com.eternalrealm.world.AreaMap map = com.eternalrealm.world.AreaMap.standard();
        assertEquals(AstoriaAreas.COUNT, map.areas().size());
        for (double distance = 0.0; distance < 2600.0; distance += 0.5) {
            assertEquals("area at " + distance, map.areaAtDistance(distance).id(), AstoriaAreas.areaAtDistance(distance));
        }
        for (com.eternalrealm.world.AreaDefinition area : map.areas()) {
            assertEquals(area.translationKey(), AstoriaAreas.titleKey(area.id()));
            assertEquals(area.descriptionKey(), AstoriaAreas.subtitleKey(area.id()));
            assertEquals(area.innerRadius(), AstoriaAreas.innerRadius(area.id()));
        }
    }

    @Test
    public void borderJitterIsSmallAndSmooth() {
        double max = 0.0;
        for (int x = -2500; x <= 2500; x += 7) {
            for (int z = -2500; z <= 2500; z += 13) {
                double jitter = AstoriaLayout.borderJitter(x, z);
                max = Math.max(max, Math.abs(jitter));
                double step = Math.abs(jitter - AstoriaLayout.borderJitter(x + 1, z));
                assertTrue("jitter must change smoothly, jumped " + step, step < 1.0);
            }
        }
        assertTrue("jitter " + max, max <= AstoriaLayout.MAX_BORDER_JITTER);
        assertTrue("borders should not be perfect circles", max > 2.0);
    }

    @Test
    public void biomesFollowTheRingsAwayFromTheBorders() {
        for (int x = -2600; x <= 2600; x += 9) {
            for (int z = -2600; z <= 2600; z += 11) {
                double distance = Math.sqrt((double) x * x + (double) z * z);
                double toBorder = distanceToBorder(distance);
                AstoriaBiome biome = AstoriaLayout.biomeAt(x, z);
                if (toBorder > AstoriaLayout.MAX_BORDER_JITTER) {
                    assertEquals("biome " + biome + " at " + x + ", " + z, AstoriaAreas.areaAtDistance(distance),
                            biome.area());
                }
                assertEquals(AstoriaLayout.biomeAreaAt(x, z), biome.area());
            }
        }
    }

    @Test
    public void everySubBiomeAppearsInItsArea() {
        Map<AstoriaBiome, Integer> counts = new EnumMap<>(AstoriaBiome.class);
        for (int x = -3000; x <= 3000; x += 12) {
            for (int z = -3000; z <= 3000; z += 12) {
                counts.merge(AstoriaLayout.biomeAt(x, z), 1, Integer::sum);
            }
        }
        for (AstoriaBiome biome : AstoriaBiome.values()) {
            assertTrue(biome + " never generated", counts.getOrDefault(biome, 0) > 20);
        }
        assertEquals(AstoriaBiome.ROYAL_CAPITAL, AstoriaLayout.biomeAt(0, 0));
        assertEquals(AstoriaBiome.ABYSS_SPIRE, AstoriaLayout.biomeAt(AstoriaLayout.TOWER_X, AstoriaLayout.TOWER_Z));
        for (int area = 1; area <= AstoriaAreas.COUNT; area++) {
            assertTrue("area " + area + " needs at least 3 biomes", AstoriaBiome.inArea(area).size() >= 3);
        }
    }

    @Test
    public void everyDungeonHasOneEntranceInsideItsArea() {
        Set<String> ids = new HashSet<>();
        for (LandmarkSite site : LandmarkSites.all()) {
            assertTrue("duplicate " + site.dungeonId(), ids.add(site.dungeonId()));
            DungeonDefinition dungeon = Dungeons.byId(site.dungeonId()).orElseThrow();
            assertEquals(site.dungeonId() + " area", dungeon.areaId(), site.area());
            assertEquals(dungeon.theme(), site.theme());
            double distance = Math.sqrt((double) site.x() * site.x() + (double) site.z() * site.z());
            double margin = site.padRadius() + AstoriaLayout.MAX_BORDER_JITTER + 10;
            assertTrue(site.dungeonId() + " too close to an area border", distanceToBorder(distance) > margin);
            assertTrue(site.dungeonId() + " too close to the capital", distance > AstoriaLayout.CAPITAL_RADIUS + margin);
            assertEquals(site.area(), AstoriaLayout.biomeAt(site.x(), site.z()).area());
            for (LandmarkSite.Local local : new LandmarkSite.Local[]{site.entrance(), site.npc(), site.waypoint()}) {
                int dx = site.worldX(local.dx(), local.dz()) - site.x();
                int dz = site.worldZ(local.dx(), local.dz()) - site.z();
                assertTrue(site.dungeonId() + " spot outside the pad", dx * dx + dz * dz < site.padRadius() * site.padRadius());
            }
            // the front (+z) faces the capital: the waypoint is closer to (0, 0) than the gate
            double waypointDistance = Math.hypot(site.worldX(0, site.waypoint().dz()), site.worldZ(0, site.waypoint().dz()));
            assertTrue(site.dungeonId() + " does not face the capital", waypointDistance < distance);
        }
        assertEquals(Dungeons.all().size(), ids.size());
        assertEquals(LandmarkSites.D8, LandmarkSites.byId("d8").orElseThrow());
    }

    @Test
    public void rotationMatchesMinecraftBlockPosRotate() {
        // Rotation.CLOCKWISE_90 maps SOUTH (0, 1) to WEST (-1, 0) and EAST (1, 0) to SOUTH (0, 1)
        assertEquals(-1, LandmarkSite.rotateX(0, 1, 1));
        assertEquals(0, LandmarkSite.rotateZ(0, 1, 1));
        assertEquals(0, LandmarkSite.rotateX(1, 0, 1));
        assertEquals(1, LandmarkSite.rotateZ(1, 0, 1));
        for (int turns = 0; turns < 4; turns++) {
            int x = LandmarkSite.rotateX(3, 5, turns);
            int z = LandmarkSite.rotateZ(3, 5, turns);
            assertEquals(3, LandmarkSite.rotateX(x, z, 4 - turns));
            assertEquals(5, LandmarkSite.rotateZ(x, z, 4 - turns));
        }
        assertEquals(90.0F, LandmarkSite.normalizeYaw(-270.0F), 0.0F);
    }

    @Test
    public void hubSpotsAreDistinctAndInsideTheWalls() {
        Set<Long> used = new HashSet<>();
        EnumSet<HubSpot> seen = EnumSet.noneOf(HubSpot.class);
        for (HubSpot spot : HubSpot.values()) {
            assertTrue(spot + " outside the walls", Math.abs(spot.dx()) <= 37 && Math.abs(spot.dz()) <= 37);
            assertTrue(spot + " shares a block", used.add(((long) spot.dx() << 32) ^ (spot.dz() & 0xFFFFFFFFL)));
            assertEquals(1, AstoriaAreas.areaAt(spot.dx(), spot.dz()));
            assertTrue(spot.yaw() >= 0.0F && spot.yaw() < 360.0F);
            seen.add(spot);
        }
        assertEquals(13, seen.size());
    }

    private static double distanceToBorder(double distance) {
        double best = Double.MAX_VALUE;
        for (int area = 2; area <= AstoriaAreas.COUNT; area++) {
            best = Math.min(best, Math.abs(distance - AstoriaAreas.innerRadius(area)));
        }
        return best;
    }
}
