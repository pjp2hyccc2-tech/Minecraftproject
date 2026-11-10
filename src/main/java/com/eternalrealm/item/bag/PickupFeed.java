package com.eternalrealm.item.bag;

import com.eternalrealm.item.ItemRarity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * The pickup feed: one row per acquisition, newest on top; the same item picked up again within
 * {@link #MERGE_TICKS} adds to its row (×count) instead of a new one. Rows stay for {@link #HOLD_TICKS} and then fade
 * out over {@link #FADE_TICKS}; at most {@link #MAX_ROWS} show. Rare and better items are highlighted.
 *
 * @param <T> what the platform needs to draw a row (the item stack)
 */
public final class PickupFeed<T> {
    public static final int MAX_ROWS = 5;
    public static final long MERGE_TICKS = 60;
    public static final long HOLD_TICKS = 100;
    public static final long FADE_TICKS = 20;
    private static final int MAX_KEPT = 32;

    /** A row: an item (or gold, or a warning such as a full bag). */
    public enum Kind {
        ITEM,
        GOLD,
        WARNING
    }

    /** One row of the feed; {@link #shownAt} restarts when the row is merged into. */
    public static final class Row<T> {
        private final String key;
        private final Kind kind;
        private T payload;
        private ItemRarity rarity;
        private long count;
        private long shownAt;

        private Row(String key, Kind kind, T payload, ItemRarity rarity, long count, long shownAt) {
            this.key = key;
            this.kind = kind;
            this.payload = payload;
            this.rarity = rarity;
            this.count = count;
            this.shownAt = shownAt;
        }

        public String key() {
            return key;
        }

        public Kind kind() {
            return kind;
        }

        public T payload() {
            return payload;
        }

        public ItemRarity rarity() {
            return rarity;
        }

        public long count() {
            return count;
        }

        public long shownAt() {
            return shownAt;
        }

        /** Rare and better items and warnings stand out (a frame and a sound). */
        public boolean highlighted() {
            return kind == Kind.WARNING || rarity.ordinal() >= ItemRarity.RARE.ordinal();
        }

        /** Opacity in [0, 1] at {@code now}. */
        public float alpha(long now) {
            long age = now - shownAt;
            if (age <= HOLD_TICKS) {
                return 1.0F;
            }
            return Math.max(0.0F, 1.0F - (age - HOLD_TICKS) / (float) FADE_TICKS);
        }
    }

    private final LinkedList<Row<T>> rows = new LinkedList<>();

    /**
     * Adds an acquisition. @return the row (new or merged into)
     *
     * @param key     what makes two pickups the same (item id and its name); warnings never merge
     */
    public Row<T> add(String key, Kind kind, T payload, ItemRarity rarity, long count, long now) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(rarity, "rarity");
        if (kind != Kind.WARNING) {
            for (Iterator<Row<T>> it = rows.iterator(); it.hasNext(); ) {
                Row<T> row = it.next();
                if (row.key.equals(key) && row.kind == kind && now - row.shownAt <= MERGE_TICKS) {
                    it.remove();
                    row.count += count;
                    row.shownAt = now;
                    row.payload = payload;
                    if (rarity.ordinal() > row.rarity.ordinal()) {
                        row.rarity = rarity;
                    }
                    rows.addFirst(row);
                    return row;
                }
            }
        }
        Row<T> row = new Row<>(key, kind, payload, rarity, count, now);
        rows.addFirst(row);
        while (rows.size() > MAX_KEPT) {
            rows.removeLast();
        }
        return row;
    }

    /** The rows to draw at {@code now}, newest first (expired rows are dropped). */
    public List<Row<T>> visible(long now) {
        rows.removeIf(row -> row.alpha(now) <= 0.0F);
        List<Row<T>> out = new ArrayList<>(Math.min(MAX_ROWS, rows.size()));
        for (Row<T> row : rows) {
            if (out.size() == MAX_ROWS) {
                break;
            }
            out.add(row);
        }
        return out;
    }

    public void clear() {
        rows.clear();
    }
}
