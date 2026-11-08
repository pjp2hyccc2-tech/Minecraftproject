package com.eternalrealm.dungeon;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Hands out {@link InstanceSlot}s: the lowest free slot first, so the dimension stays compact and released slots are
 * reused. At most {@link #maxInstances()} run at once (server config; each instance costs loaded chunks and mobs).
 * Server thread only.
 *
 * <p>Release a slot only after its run is closed and its blocks were cleared (see
 * {@link DungeonLayout#occupiedBoxes()}), otherwise the next instance would be built into leftovers.</p>
 */
public final class InstanceAllocator {
    public static final int DEFAULT_MAX_INSTANCES = 16;
    /** Slot indexes stay below this (x below ~4.2 million, well inside the world border). */
    public static final int HARD_LIMIT = 4096;
    /** Shown when every slot is taken; argument 1: the limit. */
    public static final String FULL_MESSAGE_KEY = "message." + ModInfo.MOD_ID + ".dungeon.full";

    private final BitSet used = new BitSet();
    private int maxInstances;

    public InstanceAllocator() {
        this(DEFAULT_MAX_INSTANCES);
    }

    public InstanceAllocator(int maxInstances) {
        setMaxInstances(maxInstances);
    }

    /** The lowest free slot, or empty when {@link #maxInstances()} instances are running. */
    public Optional<InstanceSlot> allocate() {
        if (used.cardinality() >= maxInstances) {
            return Optional.empty();
        }
        int index = used.nextClearBit(0); // never above the number of used slots, so below the limit
        used.set(index);
        return Optional.of(new InstanceSlot(index));
    }

    /** Frees a slot for reuse; returns false if it was not allocated. */
    public boolean release(InstanceSlot slot) {
        Objects.requireNonNull(slot, "slot");
        boolean wasUsed = used.get(slot.index());
        used.clear(slot.index());
        return wasUsed;
    }

    public boolean isInUse(InstanceSlot slot) {
        return used.get(Objects.requireNonNull(slot, "slot").index());
    }

    public int inUse() {
        return used.cardinality();
    }

    public int maxInstances() {
        return maxInstances;
    }

    /** Changes the limit (config reload). Running instances above a lowered limit keep their slots. */
    public void setMaxInstances(int maxInstances) {
        this.maxInstances = Check.range("maxInstances", maxInstances, 1, HARD_LIMIT);
    }

    /** Allocated slots in index order. */
    public List<InstanceSlot> activeSlots() {
        List<InstanceSlot> slots = new ArrayList<>();
        for (int i = used.nextSetBit(0); i >= 0; i = used.nextSetBit(i + 1)) {
            slots.add(new InstanceSlot(i));
        }
        return List.copyOf(slots);
    }
}
