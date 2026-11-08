package com.eternalrealm.economy;

import com.eternalrealm.core.ModInfo;

/**
 * Translation keys for economy notifications (texts in docs/i18n/party.json). Failure messages come from the
 * outcome enums ({@link WalletResult.Status}, {@link TradeOutcome}, {@link TradeEndReason}, {@link AuctionOutcome},
 * {@link ShopOutcome}). Item names are the item's own translation. Gold amounts are passed as plain numbers formatted
 * with digit grouping; the message texts already contain the unit ("G"). {@link #GOLD_AMOUNT} formats a standalone
 * amount (HUD, screens).
 */
public final class EconomyMessages {
    private static final String PREFIX = "message." + ModInfo.MOD_ID + ".";

    /** Gold amount as shown everywhere. {@code %s} = amount (formatted with digit grouping). */
    public static final String GOLD_AMOUNT = "economy." + ModInfo.MOD_ID + ".gold";
    /** Trade completed, to both players. No arguments. */
    public static final String TRADE_COMPLETED = PREFIX + "trade.completed";
    /** Listing created, to the seller. {@code %1$s} = item, {@code %2$s} = price, {@code %3$s} = listing fee. */
    public static final String AUCTION_LISTED = PREFIX + "auction.listed";
    /** Purchase, to the buyer. {@code %1$s} = item, {@code %2$s} = price. */
    public static final String AUCTION_BOUGHT = PREFIX + "auction.bought";
    /** Purchase, to the seller if online. {@code %1$s} = item, {@code %2$s} = price, {@code %3$s} = proceeds. */
    public static final String AUCTION_SOLD = PREFIX + "auction.sold";
    /** Purchase delivered to the mailbox because the inventory was full, to the buyer. {@code %s} = item. */
    public static final String AUCTION_BOUGHT_TO_MAILBOX = PREFIX + "auction.bought_to_mailbox";
    /** Listing cancelled, to the seller. {@code %s} = item. */
    public static final String AUCTION_CANCELLED = PREFIX + "auction.cancelled";
    /** Listing expired, to the seller if online. {@code %s} = item. */
    public static final String AUCTION_EXPIRED = PREFIX + "auction.expired";
    /** Mailbox not empty (at login, when opening the market). {@code %1$s} = gold, {@code %2$s} = item count. */
    public static final String MAIL_WAITING = PREFIX + "mail.waiting";
    /** After a claim. {@code %1$s} = gold, {@code %2$s} = item count. */
    public static final String MAIL_CLAIMED = PREFIX + "mail.claimed";
    /** A claim left gold behind because the wallet is full. {@code %s} = gold still in the mailbox. */
    public static final String MAIL_GOLD_REMAINING = PREFIX + "mail.gold_remaining";
    /** Shop purchase. {@code %1$s} = quantity, {@code %2$s} = item, {@code %3$s} = cost. */
    public static final String SHOP_PURCHASED = PREFIX + "shop.purchased";

    private EconomyMessages() {
    }
}
