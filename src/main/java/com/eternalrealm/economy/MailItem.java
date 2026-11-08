package com.eternalrealm.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * An item waiting in a {@link Mailbox} until its owner claims it.
 *
 * @param itemData opaque serialized item, as in {@link Listing#itemData()}
 */
public record MailItem(UUID id, String itemData, ItemInfo item, MailReason reason, long receivedAtMillis) {
    public MailItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(reason, "reason");
        Listing.checkItemData(itemData);
    }
}
