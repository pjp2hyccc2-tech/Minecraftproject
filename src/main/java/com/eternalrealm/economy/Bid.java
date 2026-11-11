package com.eternalrealm.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * A bid at the auction hall. The gold of the highest bid of a lot is held by the hall until the lot ends (to the
 * seller) or the bid is beaten (back to the bidder).
 *
 * @param bidderName display name when bidding (shown in the lot's history)
 */
public record Bid(UUID bidder, String bidderName, long amount, long atMillis) {
    public Bid {
        Objects.requireNonNull(bidder, "bidder");
        Objects.requireNonNull(bidderName, "bidderName");
        if (bidderName.isEmpty() || bidderName.length() > Listing.MAX_SELLER_NAME_LENGTH) {
            throw new IllegalArgumentException("bidderName length out of range");
        }
        if (amount < 1 || amount > Listing.MAX_PRICE) {
            throw new IllegalArgumentException("amount out of range: " + amount);
        }
    }
}
