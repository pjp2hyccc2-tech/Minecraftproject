package com.eternalrealm.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Recent sale prices per item group ({@link ItemInfo#group()}) from the board and the auction hall, so a seller sees
 * what the same item went for (相場) before pricing it. Keeps the latest {@link #SALES_KEPT} sales of each group and
 * the {@link #GROUPS_KEPT} groups sold most recently. Server thread only; saved with the market.
 */
public final class PriceHistory {
    public static final int SALES_KEPT = 12;
    public static final int GROUPS_KEPT = 600;

    /** One sale: the price of one piece, how many pieces, when, and whether at the auction hall. */
    public record Sale(long unitPrice, int count, long atMillis, boolean auction) {
        public Sale {
            if (unitPrice < 0 || unitPrice > Listing.MAX_PRICE || count < 1 || count > ItemInfo.MAX_COUNT) {
                throw new IllegalArgumentException("bad sale " + unitPrice + " x " + count);
            }
        }
    }

    /** What a group's recent sales say (prices per piece). */
    public record Stats(int sales, long lowest, long highest, long median, long average, long last, long lastAtMillis) {
    }

    /** Groups in the order they last sold (the oldest first, evicted first). */
    private final LinkedHashMap<String, Deque<Sale>> groups = new LinkedHashMap<>();
    private long revision;

    public PriceHistory(Map<String, List<Sale>> stored) {
        stored.forEach((group, sales) -> {
            Deque<Sale> deque = new ArrayDeque<>();
            for (Sale sale : sales) {
                deque.addLast(sale);
                while (deque.size() > SALES_KEPT) {
                    deque.removeFirst();
                }
            }
            if (!deque.isEmpty()) {
                groups.put(group, deque);
            }
        });
    }

    public static PriceHistory empty() {
        return new PriceHistory(Map.of());
    }

    /** Records a sale of {@code count} pieces for {@code totalPrice} in all. */
    public void record(String group, long totalPrice, int count, long atMillis, boolean auction) {
        Objects.requireNonNull(group, "group");
        if (count < 1 || totalPrice < 0) {
            return;
        }
        Deque<Sale> sales = groups.remove(group); // re-inserted: the group moves to the newest end
        if (sales == null) {
            sales = new ArrayDeque<>();
        }
        groups.put(group, sales);
        sales.addLast(new Sale(Math.min(Listing.MAX_PRICE, totalPrice / count), count, atMillis, auction));
        while (sales.size() > SALES_KEPT) {
            sales.removeFirst();
        }
        while (groups.size() > GROUPS_KEPT) {
            String eldest = groups.keySet().iterator().next();
            groups.remove(eldest);
        }
        revision++;
    }

    public Optional<Stats> stats(String group) {
        Deque<Sale> sales = groups.get(group);
        if (sales == null || sales.isEmpty()) {
            return Optional.empty();
        }
        List<Long> prices = new ArrayList<>();
        long sum = 0;
        for (Sale sale : sales) {
            prices.add(sale.unitPrice());
            sum += sale.unitPrice();
        }
        List<Long> sorted = new ArrayList<>(prices);
        sorted.sort(null);
        long median = sorted.size() % 2 == 1 ? sorted.get(sorted.size() / 2)
                : (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2;
        Sale last = sales.peekLast();
        return Optional.of(new Stats(sales.size(), sorted.get(0), sorted.get(sorted.size() - 1), median,
                sum / sales.size(), last.unitPrice(), last.atMillis()));
    }

    /** The latest sales of a group, the newest first. */
    public List<Sale> sales(String group) {
        Deque<Sale> sales = groups.get(group);
        if (sales == null) {
            return List.of();
        }
        List<Sale> newestFirst = new ArrayList<>(sales);
        java.util.Collections.reverse(newestFirst);
        return newestFirst;
    }

    public Map<String, List<Sale>> snapshot() {
        Map<String, List<Sale>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Deque<Sale>> entry : groups.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return copy;
    }

    public long revision() {
        return revision;
    }
}
