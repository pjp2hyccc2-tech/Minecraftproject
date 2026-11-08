package com.eternalrealm.economy;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of {@link AuctionHouse#buy}.
 *
 * @param listing the purchased listing on success: the integration delivers {@link Listing#itemData()} to the buyer
 *                (into the inventory, or with {@link AuctionHouse#sendItem} into the mailbox if it is full)
 * @param price   gold paid by the buyer, 0 on failure
 * @param tax     sales tax kept back from the seller, 0 on failure
 * @param wallet  the buyer's wallet after paying on success; unchanged on failure. The caller stores it.
 */
public record SaleResult(AuctionOutcome outcome, Optional<Listing> listing, long price, long tax, Wallet wallet) {
    public SaleResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(wallet, "wallet");
        if (outcome.isSuccess() != listing.isPresent() || tax < 0 || tax > price
                || (!outcome.isSuccess() && price != 0)) {
            throw new IllegalArgumentException("inconsistent sale result " + outcome + " price=" + price + " tax=" + tax);
        }
    }

    static SaleResult failure(AuctionOutcome outcome, Wallet wallet) {
        return new SaleResult(outcome, Optional.empty(), 0L, 0L, wallet);
    }

    public boolean isSuccess() {
        return outcome.isSuccess();
    }

    /** Gold credited to the seller's mailbox: price minus tax. */
    public long sellerProceeds() {
        return price - tax;
    }
}
