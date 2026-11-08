package com.eternalrealm.economy;

import com.eternalrealm.core.ModInfo;

/**
 * Why a trade was cancelled ({@link TradeSession#cancel}) or failed ({@link TradeSession#fail}). Each reason has a
 * message (no arguments) for both players.
 */
public enum TradeEndReason {
    /** A player closed the trade window or pressed cancel. */
    CANCELLED_BY_PLAYER("cancelled_by_player"),
    /** A player logged out ({@link TradeSession#playerDisconnected}). */
    DISCONNECTED("disconnected"),
    /** The players moved too far apart, changed dimension or one of them died. */
    TOO_FAR("too_far"),
    /** The trade stayed open for too long. */
    TIMEOUT("timeout"),
    /** Settlement: a player has no room for the received items. */
    INVENTORY_FULL("inventory_full"),
    /** Settlement: an offered item is no longer in the giver's inventory. */
    ITEM_UNAVAILABLE("item_unavailable"),
    /** Settlement: a player no longer has the offered gold. */
    INSUFFICIENT_FUNDS("insufficient_funds"),
    /** Settlement: the received gold would exceed {@link Wallet#MAX_BALANCE}. */
    WALLET_FULL("wallet_full"),
    /** Anything else (server stopping, unexpected error). */
    ERROR("error");

    private final String id;

    TradeEndReason(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String messageKey() {
        return "message." + ModInfo.MOD_ID + ".trade.end." + id;
    }
}
