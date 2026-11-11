package com.eternalrealm.economy;

import java.util.Optional;

/** Where a lot of the auction hall stands. {@link #id()} is the stable persistence key. */
public enum LotState {
    /** Registered at the hall, not started yet (the seller can still change their mind). */
    DRAFT("draft"),
    /** Started while the hall was full: goes live, in order, as soon as a place frees up. */
    QUEUED("queued"),
    /** Live: anyone, anywhere, can bid. */
    RUNNING("running"),
    /** Ended with a winning bid. */
    SOLD("sold"),
    /** Ended without a bid. */
    UNSOLD("unsold"),
    /** Withdrawn by its seller (or an old draft returned). */
    CANCELLED("cancelled");

    private final String id;

    LotState(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<LotState> byId(String id) {
        for (LotState state : values()) {
            if (state.id.equals(id)) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }
}
