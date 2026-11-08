package com.eternalrealm.economy;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of {@link ShopCatalog#purchase}. On success the integration stores {@link #wallet()}, gives {@code quantity}
 * units of the entry's item and adds {@code quantity} to the player's daily counter for the entry.
 *
 * @param entry  the ware, when it exists
 * @param cost   gold charged ({@code price × quantity}), 0 on failure
 * @param wallet the wallet after paying on success; unchanged on failure
 */
public record ShopPurchase(ShopOutcome outcome, Optional<ShopEntry> entry, int quantity, long cost, Wallet wallet) {
    public ShopPurchase {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(wallet, "wallet");
        if (outcome.isSuccess() && (entry.isEmpty() || quantity < 1)) {
            throw new IllegalArgumentException("a successful purchase needs an entry and a quantity");
        }
        if (cost < 0 || (!outcome.isSuccess() && cost != 0)) {
            throw new IllegalArgumentException("inconsistent cost " + cost + " for " + outcome);
        }
    }

    static ShopPurchase failure(ShopOutcome outcome, ShopEntry entry, int quantity, Wallet wallet) {
        return new ShopPurchase(outcome, Optional.ofNullable(entry), quantity, 0L, wallet);
    }

    public boolean isSuccess() {
        return outcome.isSuccess();
    }
}
