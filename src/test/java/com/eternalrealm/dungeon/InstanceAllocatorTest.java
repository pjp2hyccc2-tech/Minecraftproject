package com.eternalrealm.dungeon;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class InstanceAllocatorTest {
    @Test
    public void slotsAreSpacedAndLimited() {
        InstanceAllocator allocator = new InstanceAllocator();
        assertEquals(16, allocator.maxInstances());
        for (int i = 0; i < 16; i++) {
            InstanceSlot slot = allocator.allocate().orElseThrow();
            assertEquals(i, slot.index());
            assertEquals(i * 1024, slot.originX());
            assertEquals(0, slot.originZ());
        }
        assertEquals(Optional.empty(), allocator.allocate());
        assertEquals(16, allocator.inUse());
        assertTrue("slot spacing leaves room for the footprint", InstanceSlot.SPACING > DungeonLayout.FOOTPRINT);
    }

    @Test
    public void releasedSlotsAreReusedLowestFirst() {
        InstanceAllocator allocator = new InstanceAllocator(4);
        for (int i = 0; i < 4; i++) {
            allocator.allocate();
        }
        assertTrue(allocator.release(new InstanceSlot(2)));
        assertTrue(allocator.release(new InstanceSlot(1)));
        assertFalse("double release", allocator.release(new InstanceSlot(1)));
        assertFalse(allocator.isInUse(new InstanceSlot(1)));
        assertEquals(List.of(new InstanceSlot(0), new InstanceSlot(3)), allocator.activeSlots());
        assertEquals(1, allocator.allocate().orElseThrow().index());
        assertEquals(2, allocator.allocate().orElseThrow().index());
        assertEquals(Optional.empty(), allocator.allocate());
    }

    @Test
    public void loweringTheLimitKeepsRunningInstances() {
        InstanceAllocator allocator = new InstanceAllocator(8);
        for (int i = 0; i < 6; i++) {
            allocator.allocate();
        }
        allocator.setMaxInstances(3);
        assertEquals(6, allocator.inUse());
        assertEquals(Optional.empty(), allocator.allocate());
        for (int i = 0; i < 4; i++) {
            allocator.release(new InstanceSlot(i));
        }
        assertEquals(0, allocator.allocate().orElseThrow().index());
        assertEquals(Optional.empty(), allocator.allocate());
        allocator.setMaxInstances(10);
        assertEquals(1, allocator.allocate().orElseThrow().index());
    }

    @Test
    public void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InstanceAllocator(0));
        assertThrows(IllegalArgumentException.class, () -> new InstanceAllocator(InstanceAllocator.HARD_LIMIT + 1));
        assertThrows(IllegalArgumentException.class, () -> new InstanceSlot(-1));
        assertThrows(NullPointerException.class, () -> new InstanceAllocator().release(null));
    }
}
