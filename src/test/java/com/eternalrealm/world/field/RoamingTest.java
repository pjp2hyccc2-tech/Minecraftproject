package com.eternalrealm.world.field;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Roaming monsters fill the parts of the fields no habitat reaches (2026-11-14: about half of the fields never had a
 * monster). Every open place in a field gets monsters near it, from a habitat or roaming.
 */
public class RoamingTest {
    @Test
    public void insideAHabitatNoneRoam() {
        Habitats.Habitat habitat = Habitats.all().get(0);
        assertEquals(0, Habitats.roamersWanted(habitat.x(), habitat.z()));
        assertFalse(Habitats.roamable(habitat.x(), habitat.z()));
    }

    @Test
    public void theCapitalAndTheWaystonesStaySafe() {
        assertEquals(0, Habitats.roamersWanted(0, 0));
        assertFalse(Habitats.roamable(0, 0));
        FieldLayout.Waystone stone = FieldLayout.waystones().get(0);
        assertFalse(Habitats.roamable(stone.x() + 3, stone.z()));
    }

    @Test
    public void everyOpenPlaceInTheFieldsHasMonstersNear() {
        int open = 0;
        int empty = 0;
        for (int x = FieldLayout.MIN_X; x <= FieldLayout.MAX_X; x += 24) {
            for (int z = FieldLayout.MIN_Z; z <= FieldLayout.MAX_Z; z += 24) {
                if (FieldLayout.areaAt(x, z) < 1 || FieldLayout.capitalDistance(x, z) < 80) {
                    continue;
                }
                open++;
                boolean habitatNear = Habitats.nearestSpawnEdge(x, z) <= Habitats.ROAM_GAP;
                boolean roam = Habitats.roamersWanted(x, z) > 0;
                if (!habitatNear && !roam) {
                    empty++;
                }
            }
        }
        assertTrue("sampled the fields: " + open, open > 1000);
        assertEquals("open places with no monsters near", 0, empty);
    }

    @Test
    public void theOpenFieldGetsTheFullNumberAndTheFringeFewer() {
        int full = 0;
        int fringe = 0;
        for (int x = FieldLayout.MIN_X; x <= FieldLayout.MAX_X; x += 16) {
            for (int z = FieldLayout.MIN_Z; z <= FieldLayout.MAX_Z; z += 16) {
                int wanted = Habitats.roamersWanted(x, z);
                if (wanted == Habitats.ROAMERS) {
                    full++;
                } else if (wanted > 0) {
                    fringe++;
                    assertTrue(wanted >= 4 && wanted < Habitats.ROAMERS);
                }
            }
        }
        assertTrue("open field: " + full, full > 0);
        assertTrue("fringe: " + fringe, fringe > 0);
    }
}
