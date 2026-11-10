package com.eternalrealm.world;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DiscoveryAndLevelRangeTest {
    @Test
    public void discoveryGrowsImmutablyAndIsSorted() {
        Discovery none = Discovery.NONE;
        assertEquals(0, none.count());
        Discovery some = none.with(3).with(1);
        assertEquals(0, none.count());
        assertEquals(List.of(1, 3), some.sortedIds());
        assertTrue(some.contains(3));
        assertFalse(some.contains(2));
        assertSame("discovering a known area returns the same instance", some, some.with(1));
        assertEquals(Discovery.of(3, 1), some);
        assertEquals(Discovery.of(List.of(1, 3)), some);
        assertThrows(UnsupportedOperationException.class, () -> some.areaIds().add(5));
        assertFalse(some.isComplete());
        assertFalse(Discovery.of(1, 2, 3, 4, 5, 6, 7).isComplete());
        assertTrue(Discovery.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).isComplete());
    }

    @Test
    public void discoveryRejectsUnknownAreas() {
        assertThrows(IllegalArgumentException.class, () -> Discovery.NONE.with(0));
        assertThrows(IllegalArgumentException.class, () -> Discovery.NONE.with(11));
        assertThrows(IllegalArgumentException.class, () -> Discovery.of(1, 12));
        Set<Integer> withNull = new HashSet<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> new Discovery(withNull));
        assertThrows(NullPointerException.class, () -> new Discovery(null));
    }

    @Test
    public void levelRangesClampAndRollInsideTheirBounds() {
        LevelRange range = LevelRange.of(8, 15);
        assertTrue(range.contains(8) && range.contains(15));
        assertFalse(range.contains(7) || range.contains(16));
        assertEquals(8, range.clamp(-3));
        assertEquals(15, range.clamp(99));
        assertEquals(10, range.clamp(10));
        assertEquals("Lv8-15", range.toString());

        Random random = new Random(42);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            int level = range.random(random);
            assertTrue(range.contains(level));
            seen.add(level);
        }
        assertEquals("every level of the range is reachable", 8, seen.size());
        assertEquals(5, LevelRange.of(5, 5).random(random));
    }

    @Test
    public void invalidLevelRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LevelRange.of(0, 5));
        assertThrows(IllegalArgumentException.class, () -> LevelRange.of(6, 5));
        assertThrows(IllegalArgumentException.class, () -> LevelRange.of(1, 101));
        assertEquals(100, LevelRange.of(1, 100).max());
    }
}
