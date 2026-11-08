package com.eternalrealm.economy;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * One ware of an NPC merchant.
 *
 * @param id                  stable id within the catalog ({@code [a-z0-9_]{1,64}}), used in purchase requests and in
 *                            the integration's daily purchase counters
 * @param itemId              registry id of the item sold
 * @param price               gold per unit, {@code 0..Wallet.MAX_BALANCE} (0 = free, e.g. a starter item with a daily
 *                            limit)
 * @param dailyStockPerPlayer units one player may buy per day, if limited ({@code 1..MAX_DAILY_STOCK})
 */
public record ShopEntry(String id, String itemId, long price, OptionalInt dailyStockPerPlayer) {
    public static final int MAX_DAILY_STOCK = 9_999;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");

    public ShopEntry {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("entry id must match " + ID.pattern() + ": " + id);
        }
        if (!ItemInfo.isValidItemId(itemId)) {
            throw new IllegalArgumentException("invalid item id '" + itemId + "'");
        }
        if (price < 0 || price > Wallet.MAX_BALANCE) {
            throw new IllegalArgumentException("price out of range [0, " + Wallet.MAX_BALANCE + "]: " + price);
        }
        Objects.requireNonNull(dailyStockPerPlayer, "dailyStockPerPlayer");
        if (dailyStockPerPlayer.isPresent()
                && (dailyStockPerPlayer.getAsInt() < 1 || dailyStockPerPlayer.getAsInt() > MAX_DAILY_STOCK)) {
            throw new IllegalArgumentException("dailyStockPerPlayer out of range [1, " + MAX_DAILY_STOCK + "]: "
                    + dailyStockPerPlayer.getAsInt());
        }
    }

    public static ShopEntry unlimited(String id, String itemId, long price) {
        return new ShopEntry(id, itemId, price, OptionalInt.empty());
    }

    public static ShopEntry limited(String id, String itemId, long price, int dailyStockPerPlayer) {
        return new ShopEntry(id, itemId, price, OptionalInt.of(dailyStockPerPlayer));
    }

    /** Units the player may still buy today; empty when unlimited. */
    public OptionalInt remainingToday(int alreadyBoughtToday) {
        if (alreadyBoughtToday < 0) {
            throw new IllegalArgumentException("alreadyBoughtToday must be >= 0: " + alreadyBoughtToday);
        }
        if (dailyStockPerPlayer.isEmpty()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.max(0, dailyStockPerPlayer.getAsInt() - alreadyBoughtToday));
    }
}
