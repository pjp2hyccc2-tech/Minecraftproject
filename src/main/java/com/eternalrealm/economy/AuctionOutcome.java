package com.eternalrealm.economy;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Optional;

/**
 * Result code of an {@link AuctionHouse} operation. Message arguments ({@code %s}) are listed per constant.
 */
public enum AuctionOutcome {
    OK,
    /** Price outside {@code [1, Listing.MAX_PRICE]}. {@code %s} = maximum price. */
    INVALID_PRICE,
    /** The seller already has the maximum number of active listings. {@code %s} = maximum. */
    TOO_MANY_LISTINGS,
    /** The wallet cannot pay the listing fee or the price. {@code %s} = amount needed. */
    INSUFFICIENT_FUNDS,
    /** The listing no longer exists (sold, cancelled or expired). No arguments. */
    NOT_FOUND,
    /** Buying your own listing is not allowed. No arguments. */
    OWN_LISTING,
    /** The listing has expired and is about to be returned to its seller. No arguments. */
    EXPIRED,
    /** Only the seller can cancel a listing. No arguments. */
    NOT_OWNER;

    public boolean isSuccess() {
        return this == OK;
    }

    /** Translation key of the failure message; empty for {@link #OK}. */
    public Optional<String> messageKey() {
        if (this == OK) {
            return Optional.empty();
        }
        return Optional.of("message." + ModInfo.MOD_ID + ".auction.error." + name().toLowerCase(Locale.ROOT));
    }
}
