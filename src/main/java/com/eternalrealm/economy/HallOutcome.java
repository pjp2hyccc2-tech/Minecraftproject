package com.eternalrealm.economy;

import com.eternalrealm.core.ModInfo;

import java.util.Optional;

/** Result of an action at the auction hall; everything but {@link #OK} is a refusal with a message. */
public enum HallOutcome {
    OK(null),
    INVALID_PRICE("invalid_price"),
    INVALID_BUYOUT("invalid_buyout"),
    INVALID_DURATION("invalid_duration"),
    TOO_MANY_LOTS("too_many_lots"),
    QUEUE_FULL("queue_full"),
    NOT_FOUND("not_found"),
    NOT_OWNER("not_owner"),
    NOT_DRAFT("not_draft"),
    NOT_RUNNING("not_running"),
    ENDED("ended"),
    HAS_BIDS("has_bids"),
    OWN_LOT("own_lot"),
    ALREADY_HIGHEST("already_highest"),
    BID_TOO_LOW("bid_too_low"),
    NO_BUYOUT("no_buyout"),
    INSUFFICIENT_FUNDS("insufficient_funds");

    private final String key;

    HallOutcome(String key) {
        this.key = key;
    }

    /** {@code message.eternalrealm.hall.error.<key>}, empty for {@link #OK}. */
    public Optional<String> messageKey() {
        return key == null ? Optional.empty()
                : Optional.of("message." + ModInfo.MOD_ID + ".hall.error." + key);
    }
}
