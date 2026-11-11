package com.eternalrealm.economy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of an action at the auction hall.
 *
 * @param lot    the lot after the action (when it found one)
 * @param wallet the acting player's wallet after the action (unchanged when refused or when no gold moved)
 * @param events what the platform must carry out (refunds, payouts, deliveries) and announce
 */
public record HallResult(HallOutcome outcome, Optional<Lot> lot, Wallet wallet, List<HallEvent> events) {
    public HallResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(lot, "lot");
        Objects.requireNonNull(wallet, "wallet");
        events = List.copyOf(events);
    }

    static HallResult refused(HallOutcome outcome, Wallet wallet) {
        return new HallResult(outcome, Optional.empty(), wallet, List.of());
    }

    public boolean isSuccess() {
        return outcome == HallOutcome.OK;
    }
}
