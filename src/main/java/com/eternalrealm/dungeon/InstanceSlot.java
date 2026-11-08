package com.eternalrealm.dungeon;

/**
 * A region of the dedicated instance dimension. Slot {@code i} starts at block x = {@code i × }{@link #SPACING},
 * z = 0; the platform adds a layout's relative coordinates (and its chosen base height) to this origin. The spacing
 * leaves far more than the {@value DungeonLayout#FOOTPRINT}-block footprint between neighbours, so their chunks and
 * mobs never interact.
 */
public record InstanceSlot(int index) {
    public static final int SPACING = 1024;

    public InstanceSlot {
        Check.range("index", index, 0, InstanceAllocator.HARD_LIMIT - 1);
    }

    public int originX() {
        return index * SPACING;
    }

    public int originZ() {
        return 0;
    }
}
