package com.eternalrealm.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * An item offered on the auction house (取引所). Immutable; also the persistence form.
 *
 * <p>While listed, the item exists <em>only</em> here: the integration removes it from the seller's inventory when
 * listing, and it leaves the market exactly once, by purchase (to the buyer), cancellation or expiry (to the seller's
 * {@link Mailbox}).</p>
 *
 * @param sellerName display name at listing time (the seller may be offline when the item is shown)
 * @param itemData   opaque serialized item (e.g. SNBT), restored by the integration on delivery
 * @param price      total price for the stack in gold, {@code 1..MAX_PRICE}
 */
public record Listing(UUID id, UUID sellerId, String sellerName, String itemData, ItemInfo item, long price,
                      long createdAtMillis, long expiresAtMillis) {
    public static final int MAX_SELLER_NAME_LENGTH = 64;
    /** Bounds the size of the market file; large containers (e.g. full shulker boxes) should not be listable. */
    public static final int MAX_ITEM_DATA_LENGTH = 65_536;
    /** Nobody can pay more than the largest possible balance. */
    public static final long MAX_PRICE = Wallet.MAX_BALANCE;

    public Listing {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(sellerName, "sellerName");
        if (sellerName.isEmpty() || sellerName.length() > MAX_SELLER_NAME_LENGTH) {
            throw new IllegalArgumentException("sellerName length out of range [1, " + MAX_SELLER_NAME_LENGTH + "]");
        }
        checkItemData(itemData);
        if (price < 1 || price > MAX_PRICE) {
            throw new IllegalArgumentException("price out of range [1, " + MAX_PRICE + "]: " + price);
        }
        if (expiresAtMillis <= createdAtMillis) {
            throw new IllegalArgumentException("listing must expire after it was created");
        }
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    static void checkItemData(String itemData) {
        Objects.requireNonNull(itemData, "itemData");
        if (itemData.isEmpty() || itemData.length() > MAX_ITEM_DATA_LENGTH) {
            throw new IllegalArgumentException("itemData length out of range [1, " + MAX_ITEM_DATA_LENGTH + "]: "
                    + itemData.length());
        }
    }
}
