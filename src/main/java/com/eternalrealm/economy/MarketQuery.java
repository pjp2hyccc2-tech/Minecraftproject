package com.eternalrealm.economy;

import java.util.Comparator;
import java.util.Objects;

/**
 * A search on the auction house.
 *
 * @param itemIdContains case-insensitive substring of the item id ({@code ""} matches everything), e.g. {@code sword}
 * @param page           0-based page index
 * @param pageSize       listings per page, {@code 1..MAX_PAGE_SIZE}
 */
public record MarketQuery(String itemIdContains, Sort sort, int page, int pageSize) {
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_FILTER_LENGTH = 128;

    /** Ties are broken by listing time (older first) and id, so paging is stable. */
    public enum Sort {
        PRICE_ASCENDING(Comparator.comparingLong(Listing::price)),
        PRICE_DESCENDING(Comparator.comparingLong(Listing::price).reversed()),
        NEWEST(Comparator.comparingLong(Listing::createdAtMillis).reversed());

        private final Comparator<Listing> comparator;

        Sort(Comparator<Listing> primary) {
            this.comparator = primary.thenComparingLong(Listing::createdAtMillis).thenComparing(Listing::id);
        }

        public Comparator<Listing> comparator() {
            return comparator;
        }
    }

    public MarketQuery {
        Objects.requireNonNull(itemIdContains, "itemIdContains");
        Objects.requireNonNull(sort, "sort");
        if (itemIdContains.length() > MAX_FILTER_LENGTH) {
            throw new IllegalArgumentException("filter longer than " + MAX_FILTER_LENGTH + " characters");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0: " + page);
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize out of range [1, " + MAX_PAGE_SIZE + "]: " + pageSize);
        }
    }

    /** Every listing, first page. */
    public static MarketQuery all(Sort sort, int pageSize) {
        return new MarketQuery("", sort, 0, pageSize);
    }
}
