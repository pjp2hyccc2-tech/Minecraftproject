package com.eternalrealm.economy;

import java.util.Comparator;
import java.util.Objects;

/**
 * A board search (掲示板): free text over names and ids, the item's kind, the lowest rarity, a level range, gold
 * frames only, a sort and a page.
 *
 * @param text      every space-separated word must appear in the item's id or keywords ({@code ""}: any)
 * @param category  one of {@link ItemInfo#CATEGORIES}, or {@code ""} for any
 * @param minRarity rarity rank the item must reach ({@link ItemInfo#rarityRank}; -1: any)
 * @param minLevel  lowest required level shown (0: any)
 * @param maxLevel  highest required level shown ({@link ItemInfo#MAX_LEVEL}: any)
 * @param goldOnly  only gold-frame pieces
 */
public record MarketQuery(String text, String category, int minRarity, int minLevel, int maxLevel, boolean goldOnly,
                          Sort sort, int page, int pageSize) {
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_FILTER_LENGTH = 128;

    /** Ties are broken by listing time (older first) and id, so paging is stable. */
    public enum Sort {
        PRICE_ASCENDING(Comparator.comparingLong(Listing::price)),
        PRICE_DESCENDING(Comparator.comparingLong(Listing::price).reversed()),
        NEWEST(Comparator.comparingLong(Listing::createdAtMillis).reversed()),
        /** Cheapest per piece first (stacks of materials). */
        UNIT_PRICE_ASCENDING(Comparator.comparingDouble(listing -> listing.price() / (double) listing.item().count())),
        /** Closest to its end first. */
        ENDING_SOON(Comparator.comparingLong(Listing::expiresAtMillis)),
        /** Highest required level first. */
        LEVEL_DESCENDING(Comparator.comparingInt((Listing listing) -> listing.item().level()).reversed());

        private final Comparator<Listing> comparator;

        Sort(Comparator<Listing> primary) {
            this.comparator = primary.thenComparingLong(Listing::createdAtMillis).thenComparing(Listing::id);
        }

        public Comparator<Listing> comparator() {
            return comparator;
        }
    }

    public MarketQuery {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(sort, "sort");
        if (text.length() > MAX_FILTER_LENGTH) {
            throw new IllegalArgumentException("filter longer than " + MAX_FILTER_LENGTH + " characters");
        }
        if (!category.isEmpty() && !ItemInfo.CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("unknown category " + category);
        }
        if (minRarity < -1 || minRarity > 4 || minLevel < 0 || maxLevel < minLevel || maxLevel > ItemInfo.MAX_LEVEL) {
            throw new IllegalArgumentException("bad rarity or level range");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0: " + page);
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize out of range [1, " + MAX_PAGE_SIZE + "]: " + pageSize);
        }
    }

    /** A plain text search (no other filter). */
    public MarketQuery(String itemIdContains, Sort sort, int page, int pageSize) {
        this(itemIdContains, "", -1, 0, ItemInfo.MAX_LEVEL, false, sort, page, pageSize);
    }

    /** Every listing, first page. */
    public static MarketQuery all(Sort sort, int pageSize) {
        return new MarketQuery("", sort, 0, pageSize);
    }

    /** The older name of {@link #text()}. */
    public String itemIdContains() {
        return text;
    }

    /** Whether {@code item} passes the filters. */
    public boolean accepts(ItemInfo item) {
        return item.matches(text) && (category.isEmpty() || category.equals(item.category()))
                && (minRarity < 0 || item.rarityRank() >= minRarity || item.gold())
                && item.level() >= minLevel && item.level() <= maxLevel && (!goldOnly || item.gold());
    }
}
