package com.eternalrealm.item;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Validation helpers of the item definitions (code constants and caller input: a failure is a programming error). */
final class Checks {
    /** Namespaced item id ({@code namespace:path}) as Minecraft's resource locations allow it. */
    private static final String ITEM_ID = "[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}";

    private Checks() {
    }

    static double range(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range [" + min + ", " + max + "]: " + value);
        }
        return value;
    }

    static int range(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range [" + min + ", " + max + "]: " + value);
        }
        return value;
    }

    static long range(String name, long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range [" + min + ", " + max + "]: " + value);
        }
        return value;
    }

    static String id(String name, String value) {
        if (value == null || !value.matches("[a-z0-9_]{1,64}")) {
            throw new IllegalArgumentException(name + " must match [a-z0-9_]{1,64}: " + value);
        }
        return value;
    }

    static boolean isItemId(String value) {
        return value != null && value.matches(ITEM_ID);
    }

    static String itemId(String name, String value) {
        if (!isItemId(value)) {
            throw new IllegalArgumentException(name + " must be a namespaced item id (namespace:path): " + value);
        }
        return value;
    }

    /**
     * An immutable, sorted copy of an item count map: namespaced ids, counts in {@code [minCount, maxCount]}. Sorting
     * makes iteration (removal order, tooltips, JSON) deterministic.
     */
    static Map<String, Integer> itemCounts(String name, Map<String, Integer> counts, int minCount, int maxCount) {
        Objects.requireNonNull(counts, name);
        Map<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            itemId(name + " id", entry.getKey());
            Integer count = Objects.requireNonNull(entry.getValue(), name + " count of " + entry.getKey());
            range(name + " count of " + entry.getKey(), count, minCount, maxCount);
            copy.put(entry.getKey(), count);
        }
        return Collections.unmodifiableMap(copy);
    }
}
