package com.eternalrealm.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The wares of one NPC merchant. Immutable and stateless: daily purchase counters are kept by the integration (per
 * player, entry and server day), which passes them in, so the catalog can be shared and reloaded freely.
 *
 * <p>NPC merchants create items for gold, which removes gold from the economy (a gold sink). Selling items to NPCs is
 * not part of this catalog.</p>
 */
public final class ShopCatalog {
    /** Upper bound of one purchase; also keeps {@code price × quantity} far below {@code long} overflow. */
    public static final int MAX_QUANTITY = 9_999;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");

    private final String id;
    private final Map<String, ShopEntry> entries;

    /**
     * @param id      catalog id ({@code [a-z0-9_]{1,64}}), e.g. the merchant's id
     * @param entries wares in display order, unique ids
     */
    public ShopCatalog(String id, List<ShopEntry> entries) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("catalog id must match " + ID.pattern() + ": " + id);
        }
        Map<String, ShopEntry> byId = new LinkedHashMap<>();
        for (ShopEntry entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (byId.put(entry.id(), entry) != null) {
                throw new IllegalArgumentException("duplicate entry '" + entry.id() + "' in catalog " + id);
            }
        }
        this.id = id;
        this.entries = Collections.unmodifiableMap(byId);
    }

    public String id() {
        return id;
    }

    /** Wares in display order. */
    public List<ShopEntry> entries() {
        return List.copyOf(entries.values());
    }

    public Optional<ShopEntry> entry(String entryId) {
        return Optional.ofNullable(entries.get(entryId));
    }

    /**
     * Prices a purchase and charges it to {@code wallet}. Nothing else changes. Order: {@code UNKNOWN_ENTRY},
     * {@code INVALID_QUANTITY}, {@code DAILY_LIMIT_REACHED}, {@code INSUFFICIENT_FUNDS}.
     *
     * @param alreadyBoughtToday units of this entry the player bought today ({@code >= 0}), from the integration
     */
    public ShopPurchase purchase(String entryId, int quantity, Wallet wallet, int alreadyBoughtToday) {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(wallet, "wallet");
        if (alreadyBoughtToday < 0) {
            throw new IllegalArgumentException("alreadyBoughtToday must be >= 0: " + alreadyBoughtToday);
        }
        ShopEntry entry = entries.get(entryId);
        if (entry == null) {
            return ShopPurchase.failure(ShopOutcome.UNKNOWN_ENTRY, null, quantity, wallet);
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            return ShopPurchase.failure(ShopOutcome.INVALID_QUANTITY, entry, quantity, wallet);
        }
        if (entry.dailyStockPerPlayer().isPresent()
                && (long) alreadyBoughtToday + quantity > entry.dailyStockPerPlayer().getAsInt()) {
            return ShopPurchase.failure(ShopOutcome.DAILY_LIMIT_REACHED, entry, quantity, wallet);
        }
        long cost = entry.price() * quantity;
        WalletResult paid = wallet.debit(cost);
        if (!paid.isSuccess()) {
            return ShopPurchase.failure(ShopOutcome.INSUFFICIENT_FUNDS, entry, quantity, wallet);
        }
        return new ShopPurchase(ShopOutcome.OK, Optional.of(entry), quantity, cost, paid.wallet());
    }
}
