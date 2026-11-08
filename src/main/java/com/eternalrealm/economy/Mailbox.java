package com.eternalrealm.economy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A player's mailbox: gold and items waiting to be claimed (sale proceeds, returned listings, rewards). Immutable;
 * {@link AuctionHouse} keeps the current mailbox of every player and persists them together with the listings.
 *
 * <p>Unlike a {@link Wallet} the pending gold has no {@link Wallet#MAX_BALANCE} cap, so proceeds are never lost to a
 * full wallet; claiming moves only as much as the wallet can take.</p>
 *
 * @param items oldest first, unique ids
 */
public record Mailbox(UUID owner, long gold, List<MailItem> items) {
    public Mailbox {
        Objects.requireNonNull(owner, "owner");
        if (gold < 0) {
            throw new IllegalArgumentException("gold must be >= 0: " + gold);
        }
        items = List.copyOf(items);
        Set<UUID> ids = new HashSet<>();
        for (MailItem item : items) {
            if (!ids.add(item.id())) {
                throw new IllegalArgumentException("duplicate mail item " + item.id());
            }
        }
    }

    public static Mailbox empty(UUID owner) {
        return new Mailbox(owner, 0L, List.of());
    }

    public boolean isEmpty() {
        return gold == 0 && items.isEmpty();
    }

    public Optional<MailItem> item(UUID mailItemId) {
        for (MailItem item : items) {
            if (item.id().equals(mailItemId)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /** Adds gold, saturating at {@link Long#MAX_VALUE} (unreachable in practice: never wraps). */
    Mailbox plusGold(long amount) {
        long sum = gold + amount;
        return new Mailbox(owner, ((gold ^ sum) & (amount ^ sum)) < 0 ? Long.MAX_VALUE : sum, items);
    }

    Mailbox withGold(long newGold) {
        return new Mailbox(owner, newGold, items);
    }

    Mailbox plusItem(MailItem item) {
        List<MailItem> copy = new ArrayList<>(items);
        copy.add(item);
        return new Mailbox(owner, gold, copy);
    }

    Mailbox withoutItems(Set<UUID> mailItemIds) {
        List<MailItem> copy = new ArrayList<>(items);
        copy.removeIf(item -> mailItemIds.contains(item.id()));
        return new Mailbox(owner, gold, copy);
    }
}
