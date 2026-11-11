package com.eternalrealm.economy;

import java.time.Duration;
import java.util.Objects;

/**
 * Server-configured auction house rules. Immutable; replaced as a whole when the config is reloaded. Fees and taxes
 * are in basis points (1/100 of a percent: 200 = 2%) so that gold amounts stay exact integers.
 *
 * <pre>
 *   listing fee = max(minimumListingFee, floor(price × listingFeeBasisPoints / 10000)), at most MAX_LISTING_FEE
 *                                                                                       paid when listing, not refunded
 *   sales tax   = floor(price × salesTaxBasisPoints / 10000)                            seller receives price − tax
 * </pre>
 * Fee and tax are gold sinks: they leave the economy.
 */
public record AuctionRules(Duration listingDuration, int listingFeeBasisPoints, long minimumListingFee,
                           int salesTaxBasisPoints, int maxListingsPerPlayer) {
    public static final int BASIS_POINTS = 10_000;
    public static final Duration MIN_LISTING_DURATION = Duration.ofMinutes(1);
    public static final Duration MAX_LISTING_DURATION = Duration.ofDays(30);
    public static final long MAX_MINIMUM_FEE = 1_000_000L;
    public static final int MAX_LISTINGS_PER_PLAYER = 100;

    public AuctionRules {
        Objects.requireNonNull(listingDuration, "listingDuration");
        if (listingDuration.compareTo(MIN_LISTING_DURATION) < 0 || listingDuration.compareTo(MAX_LISTING_DURATION) > 0) {
            throw new IllegalArgumentException("listingDuration out of range [" + MIN_LISTING_DURATION + ", "
                    + MAX_LISTING_DURATION + "]: " + listingDuration);
        }
        checkBasisPoints("listingFeeBasisPoints", listingFeeBasisPoints);
        checkBasisPoints("salesTaxBasisPoints", salesTaxBasisPoints);
        if (minimumListingFee < 0 || minimumListingFee > MAX_MINIMUM_FEE) {
            throw new IllegalArgumentException("minimumListingFee out of range [0, " + MAX_MINIMUM_FEE + "]: "
                    + minimumListingFee);
        }
        if (maxListingsPerPlayer < 1 || maxListingsPerPlayer > MAX_LISTINGS_PER_PLAYER) {
            throw new IllegalArgumentException("maxListingsPerPlayer out of range [1, " + MAX_LISTINGS_PER_PLAYER
                    + "]: " + maxListingsPerPlayer);
        }
    }

    /** 48 hours, 2% listing fee (at least 1 G), 5% sales tax, 10 active listings per player. */
    public static AuctionRules defaults() {
        return new AuctionRules(Duration.ofHours(48), 200, 1L, 500, 10);
    }

    /**
     * At most this much to list one item, however expensive: the board is also for rare pieces at high prices, and a
     * fee lost on an unsold item should not keep them off it.
     */
    public static final long MAX_LISTING_FEE = 100_000L;

    public long listingFee(long price) {
        checkPrice(price);
        return Math.min(Math.max(minimumListingFee, MAX_LISTING_FEE),
                Math.max(minimumListingFee, basisPointsOf(price, listingFeeBasisPoints)));
    }

    public long salesTax(long price) {
        checkPrice(price);
        return basisPointsOf(price, salesTaxBasisPoints);
    }

    /** {@code floor(amount × basisPoints / 10000)} without overflow for {@code 0 <= basisPoints <= 10000}. */
    static long basisPointsOf(long amount, int basisPoints) {
        return amount / BASIS_POINTS * basisPoints + amount % BASIS_POINTS * basisPoints / BASIS_POINTS;
    }

    private static void checkPrice(long price) {
        if (price < 1 || price > Listing.MAX_PRICE) {
            throw new IllegalArgumentException("price out of range [1, " + Listing.MAX_PRICE + "]: " + price);
        }
    }

    private static void checkBasisPoints(String name, int value) {
        if (value < 0 || value > BASIS_POINTS) {
            throw new IllegalArgumentException(name + " out of range [0, " + BASIS_POINTS + "]: " + value);
        }
    }
}
