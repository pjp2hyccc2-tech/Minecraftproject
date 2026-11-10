package com.eternalrealm.item.bag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The bag: inventory slots {@value #FIRST_SLOT}..{@value #LAST_SLOT} of the player's main inventory (slot 0 is the
 * equipped weapon, the armor has its own slots). Tabs, the sort order and the capacity; the platform moves the stacks.
 */
public final class Bag {
    public static final int FIRST_SLOT = 1;
    public static final int LAST_SLOT = 35;
    public static final int CAPACITY = LAST_SLOT - FIRST_SLOT + 1;

    /** Equipment first, then consumables, materials, the rest; inside: rarer, higher level, by id, bigger stacks. */
    public static final Comparator<BagEntry> ORDER = Comparator.comparing(BagEntry::category)
            .thenComparing(BagEntry::rarity, Comparator.reverseOrder())
            .thenComparing(BagEntry::level, Comparator.reverseOrder())
            .thenComparing(BagEntry::itemId)
            .thenComparing(BagEntry::count, Comparator.reverseOrder())
            .thenComparing(BagEntry::slot);

    private Bag() {
    }

    public static boolean isBagSlot(int slot) {
        return slot >= FIRST_SLOT && slot <= LAST_SLOT;
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
     * The sort: the slots of the entries in their new order. The stack now in {@code result.get(i)} moves to bag slot
     * {@code FIRST_SLOT + i}; the remaining bag slots end up empty.
     */
    public static List<Integer> sortedSlots(Collection<BagEntry> entries) {
        List<BagEntry> sorted = new ArrayList<>(entries);
        sorted.sort(ORDER);
        List<Integer> slots = new ArrayList<>(sorted.size());
        for (BagEntry entry : sorted) {
            if (!isBagSlot(entry.slot())) {
                throw new IllegalArgumentException("not a bag slot: " + entry.slot());
            }
            slots.add(entry.slot());
        }
        return slots;
    }

    /** "n/35": occupied slots. */
    public static int used(Collection<BagEntry> entries) {
        int used = 0;
        for (BagEntry entry : entries) {
            if (isBagSlot(entry.slot())) {
                used++;
            }
        }
        return used;
    }
}
