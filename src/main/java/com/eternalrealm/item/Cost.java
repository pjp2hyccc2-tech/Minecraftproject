package com.eternalrealm.item;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Gold and items an action consumes (crafting, enhancement, reroll). The platform layer checks it with
 * {@link #missing} / {@link #isAffordable}, removes exactly {@link #items()} and {@link #gold()} in the same server
 * tick, and only then applies the action, so a failed removal can never leave a free result behind.
 *
 * @param gold  gold to remove, {@code >= 0}
 * @param items namespaced item id → count ({@code >= 1}), sorted by id
 */
public record Cost(long gold, Map<String, Integer> items) {
    public static final int MAX_ITEM_COUNT = 10_000;

    public Cost {
        Checks.range("gold", gold, 0L, Long.MAX_VALUE / 4);
        items = Checks.itemCounts("items", items, 1, MAX_ITEM_COUNT);
    }

    public static Cost of(long gold, String itemId, int count) {
        return new Cost(gold, Map.of(itemId, count));
    }

    /** How many of each item the inventory lacks (empty if it has everything); gold is not part of this. */
    public Map<String, Integer> missing(Map<String, Integer> inventory) {
        Objects.requireNonNull(inventory, "inventory");
        Map<String, Integer> missing = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            int have = count(inventory, entry.getKey());
            if (have < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - have);
            }
        }
        return Collections.unmodifiableMap(missing);
    }

    public boolean isAffordable(Map<String, Integer> inventory, long availableGold) {
        checkGold(availableGold);
        return missing(inventory).isEmpty() && availableGold >= gold;
    }

    /** Count of {@code itemId} in an inventory map; absent ids count 0, negative counts are rejected. */
    static int count(Map<String, Integer> inventory, String itemId) {
        Integer have = inventory.get(itemId);
        if (have == null) {
            return 0;
        }
        if (have < 0) {
            throw new IllegalArgumentException("Negative inventory count of " + itemId + ": " + have);
        }
        return have;
    }

    static long checkGold(long availableGold) {
        if (availableGold < 0) {
            throw new IllegalArgumentException("Negative gold: " + availableGold);
        }
        return availableGold;
    }
}
