package com.eternalrealm.economy;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of {@link AuctionHouse#cancel}.
 *
 * @param listing the cancelled listing on success; its item is now in the seller's {@link Mailbox}
 */
public record CancelResult(AuctionOutcome outcome, Optional<Listing> listing) {
    public CancelResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(listing, "listing");
        if (outcome.isSuccess() != listing.isPresent()) {
            throw new IllegalArgumentException("inconsistent cancel result " + outcome);
        }
    }

    public boolean isSuccess() {
        return outcome.isSuccess();
    }
}
