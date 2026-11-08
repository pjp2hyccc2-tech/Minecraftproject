package com.eternalrealm.ranking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Scores of one ranking category, highest first.
 *
 * <p><b>Order</b> (a strict total order, so every entry has its own rank): higher score first; on equal scores the
 * entry that reached the score <em>earlier</em> first; then by id ({@code UUID.compareTo} / {@code String.compareTo}).
 * An {@link #update} that does not change the score keeps the original timestamp, so refreshing a player's stats never
 * costs them a tie they had won.</p>
 *
 * <p><b>Cost:</b> entries are kept in a sorted array plus a hash index. {@link #rankOf} and {@link #entryOf} are
 * O(log n), {@link #top} O(k), {@link #update} and {@link #remove} O(log n) plus an array shift (a memory move that is a
 * few microseconds even for tens of thousands of entries). This favours rank lookups, which every player's HUD/UI may
 * request, over a tree whose rank lookup would be O(n).</p>
 *
 * <p>Not thread-safe: use from the server thread only.</p>
 *
 * @param <K> id type: {@code UUID} for players, {@code String} for guilds
 */
public final class Leaderboard<K extends Comparable<K>> {
    private record Slot<K>(K id, String name, long score, long updatedAtMillis) {
    }

    private final Comparator<Slot<K>> order = (a, b) -> {
        int byScore = Long.compare(b.score, a.score);
        if (byScore != 0) {
            return byScore;
        }
        int byTime = Long.compare(a.updatedAtMillis, b.updatedAtMillis);
        return byTime != 0 ? byTime : a.id.compareTo(b.id);
    };
    private final List<Slot<K>> ordered = new ArrayList<>();
    private final Map<K, Slot<K>> byId = new HashMap<>();

    /**
     * Sets the score of {@code id}.
     *
     * @param displayName     shown in the ranking; 1 to 64 characters
     * @param score           {@code >= 0}
     * @param updatedAtMillis when the score was reached (server wall clock); ignored if the score is unchanged
     * @return {@code true} if the score or the name changed
     */
    public boolean update(K id, String displayName, long score, long updatedAtMillis) {
        Objects.requireNonNull(id, "id");
        Check.displayName("displayName", displayName);
        Check.nonNegative("score", score);
        Check.nonNegative("updatedAtMillis", updatedAtMillis);
        Slot<K> current = byId.get(id);
        if (current != null) {
            if (current.score == score) {
                if (current.name.equals(displayName)) {
                    return false;
                }
                Slot<K> renamed = new Slot<>(id, displayName, score, current.updatedAtMillis);
                ordered.set(indexOf(current), renamed);
                byId.put(id, renamed);
                return true;
            }
            ordered.remove(indexOf(current));
        }
        Slot<K> slot = new Slot<>(id, displayName, score, updatedAtMillis);
        int search = Collections.binarySearch(ordered, slot, order);
        if (search >= 0) {
            throw new IllegalStateException("Duplicate leaderboard slot for " + id);
        }
        ordered.add(-search - 1, slot);
        byId.put(id, slot);
        return true;
    }

    /** @return {@code true} if the id was ranked */
    public boolean remove(K id) {
        Slot<K> current = byId.remove(Objects.requireNonNull(id, "id"));
        if (current == null) {
            return false;
        }
        ordered.remove(indexOf(current));
        return true;
    }

    /** The first {@code n} entries (fewer if the board is smaller), ranked from 1. */
    public List<RankingEntry<K>> top(int n) {
        Check.range("n", n, 0, Integer.MAX_VALUE);
        int count = Math.min(n, ordered.size());
        List<RankingEntry<K>> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(entryAt(i));
        }
        return Collections.unmodifiableList(entries);
    }

    /** 1-based rank, or empty if the id is not ranked. */
    public OptionalInt rankOf(K id) {
        Slot<K> current = byId.get(Objects.requireNonNull(id, "id"));
        return current == null ? OptionalInt.empty() : OptionalInt.of(indexOf(current) + 1);
    }

    public Optional<RankingEntry<K>> entryOf(K id) {
        Slot<K> current = byId.get(Objects.requireNonNull(id, "id"));
        return current == null ? Optional.empty() : Optional.of(entryAt(indexOf(current)));
    }

    /** When the current score of {@code id} was reached, or empty if the id is not ranked. */
    public OptionalLong updatedAtOf(K id) {
        Slot<K> current = byId.get(Objects.requireNonNull(id, "id"));
        return current == null ? OptionalLong.empty() : OptionalLong.of(current.updatedAtMillis);
    }

    public int size() {
        return ordered.size();
    }

    public void clear() {
        ordered.clear();
        byId.clear();
    }

    private RankingEntry<K> entryAt(int index) {
        Slot<K> slot = ordered.get(index);
        return new RankingEntry<>(index + 1, slot.id, slot.name, slot.score);
    }

    private int indexOf(Slot<K> slot) {
        int index = Collections.binarySearch(ordered, slot, order);
        if (index < 0) {
            throw new IllegalStateException("Leaderboard index is inconsistent for " + slot.id);
        }
        return index;
    }
}
