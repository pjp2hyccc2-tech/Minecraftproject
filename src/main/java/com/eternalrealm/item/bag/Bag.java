package com.eternalrealm.item.bag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The bag: its front (手前) is inventory slots {@value #FIRST_SLOT}..{@value #LAST_SLOT} of the player's main inventory
 * (slot 0 is the equipped weapon, the armor has its own slots), its back (カバンの奥) {@value #BACK_SIZE} more slots the
 * server keeps, named {@value #BACK_FIRST}..{@value #BACK_LAST} on screens and in UI actions (owner, 2026-11-14:
 * 「バッグパックに入る荷物の量を1.8倍ほどにして」: 35 + 28 = 63 = 1.8 × 35). Tabs, the sort order and the capacity; the
 * platform moves the stacks.
 */
public final class Bag {
    public static final int FIRST_SLOT = 1;
    public static final int LAST_SLOT = 35;
    /** Slots of the front: the vanilla inventory's. */
    public static final int FRONT_CAPACITY = LAST_SLOT - FIRST_SLOT + 1;
    /** Slots of the back. */
    public static final int BACK_SIZE = 28;
    /** Name of the first back slot (far from every vanilla inventory index). */
    public static final int BACK_FIRST = 100;
    public static final int BACK_LAST = BACK_FIRST + BACK_SIZE - 1;
    /** Front and back. */
    public static final int CAPACITY = FRONT_CAPACITY + BACK_SIZE;

    /** Equipment first, then consumables, materials, the rest; inside: rarer, higher level, by id, bigger stacks. */
    public static final Comparator<BagEntry> ORDER = Comparator.comparing(BagEntry::category)
            .thenComparing(BagEntry::rarity, Comparator.reverseOrder())
            .thenComparing(BagEntry::level, Comparator.reverseOrder())
            .thenComparing(BagEntry::itemId)
            .thenComparing(BagEntry::count, Comparator.reverseOrder())
            .thenComparing(BagEntry::slot);

    private Bag() {
    }

    /** A slot of the front: an index into the vanilla inventory. */
    public static boolean isBagSlot(int slot) {
        return slot >= FIRST_SLOT && slot <= LAST_SLOT;
    }

    /** A slot of the back. */
    public static boolean isBackSlot(int slot) {
        return slot >= BACK_FIRST && slot <= BACK_LAST;
    }

    /** A slot of the front or the back. */
    public static boolean inBag(int slot) {
        return isBagSlot(slot) || isBackSlot(slot);
    }

    /** Index into the back's storage of a back slot. */
    public static int backIndex(int slot) {
        if (!isBackSlot(slot)) {
            throw new IllegalArgumentException("not a back slot: " + slot);
        }
        return slot - BACK_FIRST;
    }

    /** The slot of the {@code position}-th place of the whole bag: the front's first, then the back's. */
    public static int slotAt(int position) {
        if (position < 0 || position >= CAPACITY) {
            throw new IllegalArgumentException("not a place in the bag: " + position);
        }
        return position < FRONT_CAPACITY ? FIRST_SLOT + position : BACK_FIRST + position - FRONT_CAPACITY;
    }

    /** The entries a tab shows, in slot order. */
    public static List<BagEntry> view(Collection<BagEntry> entries, BagTab tab) {
        List<BagEntry> out = new ArrayList<>();
        for (BagEntry entry : entries) {
            if (tab.accepts(entry.category())) {
                out.add(entry);
            }
        }
        out.sort(Comparator.comparingInt(BagEntry::slot));
        return out;
    }

    /**
     * The sort: the slots of the entries in their new order. The stack now in {@code result.get(i)} moves to
     * {@link #slotAt slotAt(i)} (the front first, then the back); the remaining slots end up empty. So the front holds
     * equipment and consumables first, and what waits in the back is mostly materials.
     */
    public static List<Integer> sortedSlots(Collection<BagEntry> entries) {
        List<BagEntry> sorted = new ArrayList<>(entries);
        sorted.sort(ORDER);
        List<Integer> slots = new ArrayList<>(sorted.size());
        for (BagEntry entry : sorted) {
            if (!inBag(entry.slot())) {
                throw new IllegalArgumentException("not a bag slot: " + entry.slot());
            }
            slots.add(entry.slot());
        }
        return slots;
    }

    /** "n/63": occupied slots of the front and the back. */
    public static int used(Collection<BagEntry> entries) {
        int used = 0;
        for (BagEntry entry : entries) {
            if (inBag(entry.slot())) {
                used++;
            }
        }
        return used;
    }
}
