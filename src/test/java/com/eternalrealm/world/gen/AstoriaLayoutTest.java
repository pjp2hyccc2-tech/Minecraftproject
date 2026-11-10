package com.eternalrealm.world.gen;

import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.world.field.FieldLayout;
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
    public void fieldsMatchTheAreaTable() {
        assertEquals(1, AstoriaAreas.areaAt(0, 0));
        assertEquals(AstoriaBiome.ABYSS_SPIRE, AstoriaLayout.biomeAt(AstoriaLayout.TOWER_X, AstoriaLayout.TOWER_Z));
        for (int area = 1; area <= AstoriaAreas.COUNT; area++) {
            assertTrue("field " + area + " needs at least 2 biomes", AstoriaBiome.inArea(area).size() >= 2);
            assertTrue(AstoriaAreas.titleKey(area).startsWith("area.eternalrealm."));
        }
    }

    /** The worldgen fields must be the gameplay fields of the world module. */
    @Test
    public void fieldsAndKeysMatchTheWorldModule() {
        com.eternalrealm.world.AreaMap map = com.eternalrealm.world.AreaMap.standard();
        assertEquals(AstoriaAreas.COUNT, map.areas().size());
        for (int x = -1800; x <= 1800; x += 37) {
            for (int z = -1600; z <= 1400; z += 41) {
                assertEquals("field at " + x + ", " + z, map.areaAt(x, z).id(), AstoriaAreas.areaAt(x, z));
            }
        }
        for (com.eternalrealm.world.AreaDefinition area : map.areas()) {
            assertEquals(area.translationKey(), AstoriaAreas.titleKey(area.id()));
            assertEquals(area.descriptionKey(), AstoriaAreas.subtitleKey(area.id()));
        }
    }

    @Test
    public void biomesFollowTheFieldsAwayFromTheBorders() {
        for (int x = FieldLayout.MIN_X; x <= FieldLayout.MAX_X; x += 23) {
            for (int z = FieldLayout.MIN_Z; z <= FieldLayout.MAX_Z; z += 29) {
                AstoriaBiome biome = AstoriaLayout.biomeAt(x, z);
                if (FieldLayout.capitalDistance(x, z) <= AstoriaLayout.CAPITAL_MARGIN) {
                    assertEquals(AstoriaBiome.ROYAL_CAPITAL, biome);
                    continue;
                }
                assertEquals("biome " + biome + " at " + x + ", " + z, FieldLayout.areaAt(x, z), biome.area());
                assertEquals(AstoriaLayout.biomeAreaAt(x, z), biome.area());
            }
        }
    }

    @Test
    public void everyPlacedBiomeAppearsInItsField() {
        Map<AstoriaBiome, Integer> counts = new EnumMap<>(AstoriaBiome.class);
        for (int x = -1900; x <= 1900; x += 12) {
            for (int z = -1700; z <= 1500; z += 12) {
                counts.merge(AstoriaLayout.biomeAt(x, z), 1, Integer::sum);
            }
        }
        for (AstoriaBiome biome : AstoriaBiome.values()) {
            if (biome.area() == 0) {
                assertEquals(biome + " is retired but still placed", 0, (int) counts.getOrDefault(biome, 0));
            } else {
                assertTrue(biome + " never generated", counts.getOrDefault(biome, 0) > 20);
            }
        }
        assertEquals(AstoriaBiome.ROYAL_CAPITAL, AstoriaLayout.biomeAt(0, 0));
        assertEquals(AstoriaBiome.ABYSS_SPIRE, AstoriaLayout.biomeAt(AstoriaLayout.TOWER_X, AstoriaLayout.TOWER_Z));
    }

    @Test
    public void everyDungeonHasOneEntranceInsideItsArea() {
        Set<String> ids = new HashSet<>();
        for (LandmarkSite site : LandmarkSites.all()) {
            assertTrue("duplicate " + site.dungeonId(), ids.add(site.dungeonId()));
            DungeonDefinition dungeon = Dungeons.byId(site.dungeonId()).orElseThrow();
            assertEquals(site.dungeonId() + " area", dungeon.areaId(), site.area());
            assertEquals(dungeon.theme(), site.theme());
            assertEquals(site.dungeonId() + " field", site.area(), FieldLayout.areaAt(site.x(), site.z()));
            double margin = site.padRadius() + 10;
            assertTrue(site.dungeonId() + " too close to a field border",
                    FieldLayout.borderDistance(site.x(), site.z()) > margin);
            assertTrue(site.dungeonId() + " too close to the capital",
                    FieldLayout.capitalDistance(site.x(), site.z()) > 100 + margin);
            assertEquals(site.area(), AstoriaLayout.biomeAt(site.x(), site.z()).area());
            for (LandmarkSite.Local local : new LandmarkSite.Local[]{site.entrance(), site.npc(), site.waypoint()}) {
                int dx = site.worldX(local.dx(), local.dz()) - site.x();
                int dz = site.worldZ(local.dx(), local.dz()) - site.z();
                assertTrue(site.dungeonId() + " spot outside the pad", dx * dx + dz * dz < site.padRadius() * site.padRadius());
            }
            // the front (+z) faces the capital: the waypoint is closer to (0, 0) than the gate
            double distance = Math.hypot(site.x(), site.z());
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
}
