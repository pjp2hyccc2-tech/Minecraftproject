package com.eternalrealm.economy;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of {@link AuctionHouse#list}.
 *
 * @param listing the new listing on success
 * @param fee     listing fee that was charged, 0 on failure
 * @param wallet  the seller's wallet after the fee on success; unchanged on failure. The caller stores it.
 */
public record ListingResult(AuctionOutcome outcome, Optional<Listing> listing, long fee, Wallet wallet) {
    public ListingResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(wallet, "wallet");
        if (outcome.isSuccess() != listing.isPresent() || fee < 0 || (!outcome.isSuccess() && fee != 0)) {
            throw new IllegalArgumentException("inconsistent listing result " + outcome + " fee=" + fee);
        }
    }

    static ListingResult failure(AuctionOutcome outcome, Wallet wallet) {
        return new ListingResult(outcome, Optional.empty(), 0L, wallet);
    }

    public boolean isSuccess() {
        return outcome.isSuccess();
    }
}
