package com.eternalrealm.economy;

import com.eternalrealm.season.SeasonId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence form of one season's market: every active listing and every non-empty mailbox, saved as one document so
 * that a sale (listing removed, proceeds credited) or a cancellation (listing removed, item mailed) is atomic.
 *
 * <p>The market is season data (stored under {@code seasons/<seasonId>/}) because it holds gold and items of that
 * season's economy, like the character's wallet and inventory. See docs/modules/party.md.</p>
 */
public record MarketData(SeasonId season, List<Listing> listings, List<Mailbox> mailboxes, List<Lot> lots,
                         java.util.Map<String, List<PriceHistory.Sale>> prices, long savedAtMillis) {
    public MarketData {
        Objects.requireNonNull(season, "season");
        listings = List.copyOf(listings);
        mailboxes = List.copyOf(mailboxes);
        lots = List.copyOf(lots);
        java.util.Map<String, List<PriceHistory.Sale>> copiedPrices = new java.util.LinkedHashMap<>();
        prices.forEach((group, sales) -> copiedPrices.put(group, List.copyOf(sales)));
        prices = java.util.Collections.unmodifiableMap(copiedPrices);
        Set<UUID> lotIds = new HashSet<>();
        for (Lot lot : lots) {
            if (!lotIds.add(lot.id())) {
                throw new IllegalArgumentException("duplicate lot " + lot.id());
            }
        }
        Set<UUID> listingIds = new HashSet<>();
        for (Listing listing : listings) {
            if (!listingIds.add(listing.id())) {
                throw new IllegalArgumentException("duplicate listing " + listing.id());
            }
        }
        Set<UUID> owners = new HashSet<>();
        Set<UUID> mailIds = new HashSet<>();
        for (Mailbox mailbox : mailboxes) {
            if (!owners.add(mailbox.owner())) {
                throw new IllegalArgumentException("duplicate mailbox of " + mailbox.owner());
            }
            for (MailItem item : mailbox.items()) {
                if (!mailIds.add(item.id())) {
                    throw new IllegalArgumentException("duplicate mail item " + item.id());
                }
            }
        }
    }

    /** The board and the mailboxes only (no auction lots, no price history). */
    public MarketData(SeasonId season, List<Listing> listings, List<Mailbox> mailboxes) {
        this(season, listings, mailboxes, List.of(), java.util.Map.of(), 0L);
    }

    public static MarketData empty(SeasonId season) {
        return new MarketData(season, List.of(), List.of());
    }

    /** This market with the auction hall's lots, the price history and the time of saving. */
    public MarketData with(List<Lot> newLots, java.util.Map<String, List<PriceHistory.Sale>> newPrices,
                           long newSavedAtMillis) {
        return new MarketData(season, listings, mailboxes, newLots, newPrices, newSavedAtMillis);
    }
}
