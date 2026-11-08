package com.eternalrealm.economy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * What one side of a {@link TradeSession} offers: up to {@link #MAX_ITEMS} item handles and an amount of gold.
 *
 * @param items opaque item handles supplied by the integration, in the order they were added; no duplicates
 *              (by {@code equals}), so the same inventory slot cannot be offered twice
 * @param gold  {@code 0..Wallet.MAX_BALANCE}
 * @param <T>   the integration's item handle (e.g. an inventory slot reference with a count)
 */
public record TradeOffer<T>(List<T> items, long gold) {
    public static final int MAX_ITEMS = 9;

    public TradeOffer {
        items = List.copyOf(items);
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("at most " + MAX_ITEMS + " items per offer: " + items.size());
        }
        if (new HashSet<>(items).size() != items.size()) {
            throw new IllegalArgumentException("duplicate item in offer " + items);
        }
        if (gold < 0 || gold > Wallet.MAX_BALANCE) {
            throw new IllegalArgumentException("gold out of range [0, " + Wallet.MAX_BALANCE + "]: " + gold);
        }
    }

    public static <T> TradeOffer<T> empty() {
        return new TradeOffer<>(List.of(), 0L);
    }

    public boolean isEmpty() {
        return items.isEmpty() && gold == 0;
    }

    TradeOffer<T> withItem(T item) {
        List<T> copy = new ArrayList<>(items);
        copy.add(item);
        return new TradeOffer<>(copy, gold);
    }

    TradeOffer<T> withoutItem(T item) {
        List<T> copy = new ArrayList<>(items);
        copy.remove(item);
        return new TradeOffer<>(copy, gold);
    }

    TradeOffer<T> withGold(long newGold) {
        return new TradeOffer<>(items, newGold);
    }
}
