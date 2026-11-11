package com.eternalrealm.guild.house;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What changed in a storage piece between two looks at it, per kind of item: the storage log is written from these
 * changes when a member closes a chest (deposits and withdrawals of the same session net out per kind). A "kind" is
 * whatever key the caller uses to tell items apart (the item and its data). Pure.
 */
public final class StorageDiff {
    private StorageDiff() {
    }

    /** {@code delta > 0}: that many were put in; {@code delta < 0}: taken out. */
    public record Change<K>(K key, int delta) {
        public Change {
            Objects.requireNonNull(key, "key");
            if (delta == 0) {
                throw new IllegalArgumentException("no change");
            }
        }

        public boolean deposit() {
            return delta > 0;
        }

        public int count() {
            return Math.abs(delta);
        }
    }

    /** Counts per kind of a list of (kind, count) stacks. */
    public static <K> Map<K, Integer> count(List<Map.Entry<K, Integer>> stacks) {
        Map<K, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<K, Integer> stack : stacks) {
            if (stack.getValue() > 0) {
                counts.merge(stack.getKey(), stack.getValue(), Integer::sum);
            }
        }
        return counts;
    }

    /** The changes from {@code before} to {@code after}: withdrawals first, then deposits, each in key order seen. */
    public static <K> List<Change<K>> between(Map<K, Integer> before, Map<K, Integer> after) {
        List<Change<K>> out = new ArrayList<>();
        for (Map.Entry<K, Integer> entry : before.entrySet()) {
            int delta = after.getOrDefault(entry.getKey(), 0) - entry.getValue();
            if (delta < 0) {
                out.add(new Change<>(entry.getKey(), delta));
            }
        }
        for (Map.Entry<K, Integer> entry : after.entrySet()) {
            int delta = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (delta > 0) {
                out.add(new Change<>(entry.getKey(), delta));
            }
        }
        return out;
    }

    /** Applies {@code changes} to a snapshot (used to keep other viewers' snapshots in step). */
    public static <K> Map<K, Integer> apply(Map<K, Integer> snapshot, List<Change<K>> changes) {
        Map<K, Integer> out = new LinkedHashMap<>(snapshot);
        for (Change<K> change : changes) {
            int value = out.getOrDefault(change.key(), 0) + change.delta();
            if (value > 0) {
                out.put(change.key(), value);
            } else {
                out.remove(change.key());
            }
        }
        return out;
    }
}
