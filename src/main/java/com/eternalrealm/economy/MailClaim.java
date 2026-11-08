package com.eternalrealm.economy;

import java.util.List;
import java.util.Objects;

/**
 * What a claim took out of a {@link Mailbox}.
 *
 * @param gold   gold moved into the wallet (less than pending if the wallet would otherwise overflow)
 * @param items  items removed from the mailbox: the integration must now give them to the player
 * @param wallet the wallet after the claim; the caller stores it
 */
public record MailClaim(long gold, List<MailItem> items, Wallet wallet) {
    public MailClaim {
        if (gold < 0) {
            throw new IllegalArgumentException("gold must be >= 0: " + gold);
        }
        items = List.copyOf(items);
        Objects.requireNonNull(wallet, "wallet");
    }

    public boolean isEmpty() {
        return gold == 0 && items.isEmpty();
    }
}
