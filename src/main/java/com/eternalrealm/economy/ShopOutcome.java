package com.eternalrealm.economy;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Optional;

/**
 * Result code of {@link ShopCatalog#purchase}. Message arguments ({@code %s}) are listed per constant.
 */
public enum ShopOutcome {
    OK,
    /** The merchant does not sell this (stale client view). No arguments. */
    UNKNOWN_ENTRY,
    /** Quantity outside {@code [1, ShopCatalog.MAX_QUANTITY]}. No arguments. */
    INVALID_QUANTITY,
    /** The player's daily limit for this ware would be exceeded. {@code %s} = units still available today. */
    DAILY_LIMIT_REACHED,
    /** Not enough gold. {@code %s} = cost. */
    INSUFFICIENT_FUNDS;

    public boolean isSuccess() {
        return this == OK;
    }

    /** Translation key of the failure message; empty for {@link #OK}. */
    public Optional<String> messageKey() {
        if (this == OK) {
            return Optional.empty();
        }
        return Optional.of("message." + ModInfo.MOD_ID + ".shop.error." + name().toLowerCase(Locale.ROOT));
    }
}
