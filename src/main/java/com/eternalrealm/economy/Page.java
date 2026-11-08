package com.eternalrealm.economy;

import java.util.List;

/**
 * One page of a search result.
 *
 * @param page       0-based index of this page (may be beyond the last page; then {@code items} is empty)
 * @param totalItems number of matches over all pages
 */
public record Page<T>(List<T> items, int page, int pageSize, int totalItems) {
    public Page {
        items = List.copyOf(items);
        if (page < 0 || pageSize < 1 || totalItems < 0 || items.size() > pageSize || items.size() > totalItems) {
            throw new IllegalArgumentException("inconsistent page " + page + "/" + pageSize + " of " + totalItems
                    + " with " + items.size() + " items");
        }
    }

    public int totalPages() {
        return (int) ((totalItems + (long) pageSize - 1) / pageSize);
    }

    public boolean hasNext() {
        return (page + 1L) * pageSize < totalItems;
    }
}
