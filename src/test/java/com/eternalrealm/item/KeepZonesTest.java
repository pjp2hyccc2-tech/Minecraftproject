package com.eternalrealm.item;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** What spills from a container a player breaks is theirs; elsewhere, and later, nothing is covered. */
public class KeepZonesTest {
    private static final String ASTORIA = "eternalrealm:astoria";

    @Test
    public void spillsInTheSameTickAreCovered() {
        KeepZones zones = new KeepZones();
        zones.open(ASTORIA, 10.5, 64.5, 10.5, 100);
        assertTrue(zones.covers(ASTORIA, 10.9, 64.2, 10.1, 100));
        assertTrue("the next tick too", zones.covers(ASTORIA, 11.0, 64.0, 11.0, 101));
        assertFalse("not two ticks later", zones.covers(ASTORIA, 10.5, 64.5, 10.5, 102));
    }

    @Test
    public void onlyNearTheBlockInTheSameDimension() {
        KeepZones zones = new KeepZones();
        zones.open(ASTORIA, 0.5, 70.5, 0.5, 5);
        assertFalse(zones.covers(ASTORIA, 3.0, 70.5, 0.5, 5));
        assertFalse(zones.covers("eternalrealm:dungeons", 0.5, 70.5, 0.5, 5));
        assertFalse("not before it opened", zones.covers(ASTORIA, 0.5, 70.5, 0.5, 4));
    }

    @Test
    public void staysSmall() {
        KeepZones zones = new KeepZones();
        for (int i = 0; i < KeepZones.MAX * 3; i++) {
            zones.open(ASTORIA, i * 10.0, 64.0, 0.0, 50);
        }
        assertEquals(KeepZones.MAX, zones.size());
        assertTrue("the newest is kept", zones.covers(ASTORIA, (KeepZones.MAX * 3 - 1) * 10.0, 64.0, 0.0, 50));
        zones.covers(ASTORIA, 0.0, 0.0, 0.0, 60);
        assertEquals("old zones go", 0, zones.size());
    }
}
