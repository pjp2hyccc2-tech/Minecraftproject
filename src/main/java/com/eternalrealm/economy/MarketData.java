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
public record MarketData(SeasonId season, List<Listing> listings, List<Mailbox> mailboxes) {
    public MarketData {
        Objects.requireNonNull(season, "season");
        listings = List.copyOf(listings);
        mailboxes = List.copyOf(mailboxes);
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

    public static MarketData empty(SeasonId season) {
        return new MarketData(season, List.of(), List.of());
    }
}
