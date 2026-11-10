package com.eternalrealm.world.field;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WaystonesTest {
    private static final Optional<FieldLayout.Waystone> AT_MEADOW = Waystones.byId("meadow");

    @Test
    public void everyFieldHasAWaystoneInsideItAndOnDryLand() {
        Set<Integer> fields = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (FieldLayout.Waystone waystone : Waystones.all()) {
            assertTrue("duplicate id " + waystone.id(), ids.add(waystone.id()));
            assertTrue(waystone.id(), waystone.id().matches("[a-z_]{1,32}"));
            assertEquals(waystone.id() + " lies in its own field", waystone.field(),
                    FieldLayout.areaAt(waystone.x(), waystone.z()));
            fields.add(waystone.field());
            int top = (int) Math.floor(FieldTerrain.heightAt(waystone.x(), waystone.z()));
            assertEquals(waystone.id() + " stands on dry land", FieldTerrain.NO_WATER,
                    FieldTerrain.waterTop(waystone.x(), waystone.z(), top));
            assertTrue(waystone.id() + " is outside the capital", !FieldLayout.inCapital(waystone.x(), waystone.z()));
        }
        assertEquals(FieldLayout.FIELD_COUNT, fields.size());
    }

    @Test
    public void waystonesAreFarApartSoEveryBlockBelongsToOne() {
        List<FieldLayout.Waystone> all = Waystones.all();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                double dx = all.get(i).x() - all.get(j).x();
                double dz = all.get(i).z() - all.get(j).z();
                assertTrue(all.get(i).id() + " / " + all.get(j).id(),
                        Math.sqrt(dx * dx + dz * dz) > 4 * Waystones.IDENTIFY_RADIUS);
            }
        }
    }

    @Test
    public void nearestFindsTheWaystoneOnlyWithinTheRadius() {
        assertEquals("meadow", Waystones.nearest(-358, 575, Waystones.USE_RADIUS).orElseThrow().id());
        assertTrue(Waystones.nearest(-340, 572, Waystones.USE_RADIUS).isEmpty());
        assertEquals("capital", Waystones.nearest(2, 199, Waystones.IDENTIFY_RADIUS).orElseThrow().id());
        assertTrue(Waystones.byId("nowhere").isEmpty());
        assertTrue(Waystones.byId(null).isEmpty());
    }

    @Test
    public void travelIsCheckedOnTheServer() {
        Set<String> known = Set.of("meadow", "capital", "harbour");
        long now = 1_000_000L;
        assertEquals(Waystones.Result.OK, Waystones.check(AT_MEADOW, "capital", known, f -> true, now, 0L, false));
        assertEquals(Waystones.Result.UNKNOWN, Waystones.check(AT_MEADOW, "atlantis", known, f -> true, now, 0L, false));
        assertEquals(Waystones.Result.NOT_AT_WAYSTONE,
                Waystones.check(Optional.empty(), "capital", known, f -> true, now, 0L, false));
        assertEquals(Waystones.Result.SAME, Waystones.check(AT_MEADOW, "meadow", known, f -> true, now, 0L, false));
        assertEquals(Waystones.Result.NOT_DISCOVERED,
                Waystones.check(AT_MEADOW, "farm", known, f -> true, now, 0L, false));
        assertEquals(Waystones.Result.SEALED, Waystones.check(AT_MEADOW, "harbour", known,
                f -> f != FieldLayout.HARBOUR, now, 0L, false));
        assertEquals(Waystones.Result.IN_COMBAT, Waystones.check(AT_MEADOW, "capital", known, f -> true, now, 0L, true));
        long justNow = now - 2_000L;
        assertEquals(Waystones.Result.COOLDOWN,
                Waystones.check(AT_MEADOW, "capital", known, f -> true, now, justNow, false));
        assertEquals(Waystones.COOLDOWN_SECONDS * 1000L - 2_000L, Waystones.cooldownLeft(now, justNow));
        assertEquals(0L, Waystones.cooldownLeft(now, now - Waystones.COOLDOWN_SECONDS * 1000L));
        assertEquals("waystone.eternalrealm.result.not_discovered", Waystones.Result.NOT_DISCOVERED.translationKey());
    }

    @Test
    public void theRecordedSetRoundTripsAndToleratesJunk() {
        String json = Waystones.toJson(List.of("meadow", "capital"));
        assertEquals("{\"ids\":[\"capital\",\"meadow\"]}", json);
        assertEquals(Set.of("capital", "meadow"), Waystones.fromJson(json));
        assertEquals(Set.of("farm"), Waystones.fromJson("{\"ids\":[\"farm\",\"nowhere\",3,{}]}"));
        assertTrue(Waystones.fromJson("not json").isEmpty());
        assertTrue(Waystones.fromJson("[1,2]").isEmpty());
        assertTrue(Waystones.fromJson(null).isEmpty());
        assertFalse(Waystones.fromJson("{\"ids\":\"meadow\"}").contains("meadow"));
    }

    @Test
    public void regionBoundsContainTheFieldsLabelAndWaystone() {
        for (FieldLayout.Waystone waystone : Waystones.all()) {
            int[] b = FieldLayout.regionBounds(waystone.field());
            double[] label = FieldLayout.labelPoint(waystone.field());
            assertTrue(waystone.id(), b[0] <= waystone.x() + 16 && waystone.x() - 16 <= b[2]
                    && b[1] <= waystone.z() + 16 && waystone.z() - 16 <= b[3]);
            assertTrue("label of " + waystone.field(), b[0] <= label[0] + 16 && label[0] - 16 <= b[2]
                    && b[1] <= label[1] + 16 && label[1] - 16 <= b[3]);
            assertTrue("a field is hundreds of blocks wide", b[2] - b[0] >= 400 && b[3] - b[1] >= 300);
        }
    }
}
