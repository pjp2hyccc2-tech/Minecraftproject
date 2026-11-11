package com.eternalrealm.economy;

import java.util.List;
import java.util.Objects;

/**
 * Server-configured rules of the auction hall (オークション会場). Immutable. Fees and taxes are in basis points
 * (1/100 of a percent) so gold stays exact; both leave the economy (gold sinks).
 *
 * <pre>
 *   fee to start a lot   = max(minimumFee, startPrice × feeBasisPoints / 10000)      refunded only if it never went live
 *   next lowest bid      = startPrice before any bid, else highest + max(minIncrementGold, highest × minIncrementBasisPoints / 10000)
 *   last-moment bids     : a bid with less than snipeWindow left moves the end to now + snipeExtension (never past
 *                          the planned end + maxExtension)
 *   seller receives      = winning bid − winning bid × taxBasisPoints / 10000
 * </pre>
 *
 * @param maxRunning       lots live at once on the whole server (the owner asked for 5)
 * @param maxQueued        lots that may wait for a place
 * @param maxLotsPerPlayer a player's drafts, queued and running lots together
 * @param durationsMinutes the running times a seller can choose
 * @param bidsKept         bids kept per lot for its history (the escrowed highest is always the last)
 * @param historyKept      finished lots kept for the results list
 * @param draftLifetimeMillis a draft never started goes back to its seller's mailbox after this
 * @param endingSoonMillis bidders are told when a lot has this little left
 */
public record HallRules(int maxRunning, int maxQueued, int maxLotsPerPlayer, List<Integer> durationsMinutes,
                        int minIncrementBasisPoints, long minIncrementGold, long snipeWindowMillis,
                        long snipeExtensionMillis, long maxExtensionMillis, int feeBasisPoints, long minimumFee,
                        int taxBasisPoints, int bidsKept, int historyKept, long draftLifetimeMillis,
                        long endingSoonMillis) {
    public static final int MAX_DURATION_MINUTES = 24 * 60;

    public HallRules {
        range("maxRunning", maxRunning, 1, 20);
        range("maxQueued", maxQueued, 0, 200);
        range("maxLotsPerPlayer", maxLotsPerPlayer, 1, 20);
        durationsMinutes = List.copyOf(Objects.requireNonNull(durationsMinutes, "durationsMinutes"));
        if (durationsMinutes.isEmpty()) {
            throw new IllegalArgumentException("at least one duration");
        }
        for (int minutes : durationsMinutes) {
            range("duration", minutes, 1, MAX_DURATION_MINUTES);
        }
        range("minIncrementBasisPoints", minIncrementBasisPoints, 0, AuctionRules.BASIS_POINTS);
        range("minIncrementGold", minIncrementGold, 1, 1_000_000_000L);
        range("snipeWindowMillis", snipeWindowMillis, 0, 3_600_000L);
        range("snipeExtensionMillis", snipeExtensionMillis, 0, 3_600_000L);
        range("maxExtensionMillis", maxExtensionMillis, 0, 24 * 3_600_000L);
        range("feeBasisPoints", feeBasisPoints, 0, AuctionRules.BASIS_POINTS);
        range("minimumFee", minimumFee, 0, 1_000_000L);
        range("taxBasisPoints", taxBasisPoints, 0, AuctionRules.BASIS_POINTS);
        range("bidsKept", bidsKept, 1, 200);
        range("historyKept", historyKept, 0, 1000);
        range("draftLifetimeMillis", draftLifetimeMillis, 60_000L, 90L * 24 * 3_600_000L);
        range("endingSoonMillis", endingSoonMillis, 0, 3_600_000L);
    }

    /**
     * Five lots at once, thirty waiting, three per player; 10, 30, 60 or 180 minutes; bids rise by at least 5 % (10 G);
     * a bid in the last minute adds a minute (at most 15 in all); 1 % to start (at least 10 G), 5 % tax.
     */
    public static HallRules defaults() {
        return new HallRules(5, 30, 3, List.of(10, 30, 60, 180), 500, 10L, 60_000L, 60_000L, 15 * 60_000L, 100, 10L,
                500, 20, 50, 7L * 24 * 3_600_000L, 60_000L);
    }

    /** The fee to start a lot with {@code startPrice}. */
    public long fee(long startPrice) {
        return Math.max(minimumFee, startPrice / AuctionRules.BASIS_POINTS * feeBasisPoints
                + startPrice % AuctionRules.BASIS_POINTS * feeBasisPoints / AuctionRules.BASIS_POINTS);
    }

    /** The hall's tax on a winning bid. */
    public long tax(long amount) {
        return amount / AuctionRules.BASIS_POINTS * taxBasisPoints
                + amount % AuctionRules.BASIS_POINTS * taxBasisPoints / AuctionRules.BASIS_POINTS;
    }

    /** The lowest bid that beats {@code highest} (overflow-safe within {@link Listing#MAX_PRICE}). */
    public long nextBid(long highest) {
        long step = Math.max(minIncrementGold, highest / AuctionRules.BASIS_POINTS * minIncrementBasisPoints
                + highest % AuctionRules.BASIS_POINTS * minIncrementBasisPoints / AuctionRules.BASIS_POINTS);
        return Math.min(Listing.MAX_PRICE, highest + step);
    }

    private static void range(String name, long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " out of range [" + min + ", " + max + "]: " + value);
        }
    }
}
