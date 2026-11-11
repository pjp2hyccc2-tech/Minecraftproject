package com.eternalrealm.estate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * The sorting desk's book of everything stored in a room (docs/modules/estates.md): one entry per thing (the same
 * item with the same data), its total and where it lies, by kind; and the search the desk offers. Pure.
 */
public final class Ledger {
    /** One stack lying in the room: in which piece ({@code chest.3}, {@code stand.1}, {@code floor}). */
    public record Stored(String place, String itemKey, int count, StorageLabel kind) {
    }

    /**
     * One line of the ledger.
     *
     * @param places the pieces holding it, sorted
     */
    public record Entry(String itemKey, int count, int stacks, StorageLabel kind, List<String> places) {
        public Entry {
            places = List.copyOf(places);
        }
    }

    private Ledger() {
    }

    /** Totals by thing, sorted by kind (the label order) then by key. */
    public static List<Entry> aggregate(List<Stored> stored) {
        Map<String, int[]> totals = new LinkedHashMap<>();
        Map<String, TreeSet<String>> places = new LinkedHashMap<>();
        Map<String, StorageLabel> kinds = new LinkedHashMap<>();
        for (Stored each : stored) {
            if (each.count() <= 0 || each.itemKey().isEmpty()) {
                continue;
            }
            int[] total = totals.computeIfAbsent(each.itemKey(), key -> new int[2]);
            total[0] += each.count();
            total[1]++;
            places.computeIfAbsent(each.itemKey(), key -> new TreeSet<>()).add(each.place());
            kinds.putIfAbsent(each.itemKey(), each.kind());
        }
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, int[]> total : totals.entrySet()) {
            String key = total.getKey();
            entries.add(new Entry(key, total.getValue()[0], total.getValue()[1], kinds.get(key),
                    new ArrayList<>(places.get(key))));
        }
        entries.sort(Comparator.comparing(Entry::kind).thenComparing(Entry::itemKey));
        return entries;
    }

    /**
     * Whether an entry matches what the player typed: every word of the query appears in the entry's words (its
     * names in each language and its item id), case ignored. An empty query matches everything.
     */
    public static boolean matches(String query, String words) {
        String haystack = words.toLowerCase(Locale.ROOT);
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!word.isEmpty() && !haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
