package com.eternalrealm.economy;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Optional;

/**
 * Result code of a {@link TradeSession} operation. Only outcomes a player can cause through a correct client carry a
 * message; the others indicate integration bugs or stale client packets and are silently ignored.
 */
public enum TradeOutcome {
    OK(false),
    /** The player is not part of this trade (integration bug). */
    NOT_PARTICIPANT(false),
    /** Both sides confirmed or the trade ended: the offers can no longer change. No arguments. */
    NOT_OPEN(true),
    /** The offer already holds {@link TradeOffer#MAX_ITEMS} items. {@code %s} = maximum. */
    TOO_MANY_ITEMS(true),
    /** The same item handle is already offered (stale client view). */
    DUPLICATE_ITEM(false),
    /** The item handle is not in the offer (stale client view). */
    NO_SUCH_ITEM(false),
    /** Gold outside {@code [0, Wallet.MAX_BALANCE]}. No arguments. */
    INVALID_GOLD(true),
    /** Lock/confirm referred to an older version of the offers; the player must review the new offers. No arguments. */
    OFFER_CHANGED(true),
    /** Confirm requires both sides to be locked. No arguments. */
    NOT_ALL_LOCKED(true),
    /** {@code complete}/{@code fail} outside the settling state (integration bug). */
    NOT_SETTLING(false);

    private final boolean shownToPlayer;

    TradeOutcome(boolean shownToPlayer) {
        this.shownToPlayer = shownToPlayer;
    }

    public boolean isSuccess() {
        return this == OK;
    }

    /** Translation key worth showing to the player, if any. */
    public Optional<String> messageKey() {
        if (!shownToPlayer) {
            return Optional.empty();
        }
        return Optional.of("message." + ModInfo.MOD_ID + ".trade.error." + name().toLowerCase(Locale.ROOT));
    }
}
