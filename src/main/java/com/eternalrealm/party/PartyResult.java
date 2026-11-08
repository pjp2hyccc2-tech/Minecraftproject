package com.eternalrealm.party;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of a {@link PartyService} operation.
 *
 * @param party  on success, the affected party after the operation; empty on failure and when the party no longer
 *               exists (disbanded)
 * @param events what happened, in order. Usually empty on failure, but a failure can still close an invite that was
 *               found to be expired ({@link PartyEvent.InviteExpired})
 */
public record PartyResult(PartyOutcome outcome, Optional<Party> party, List<PartyEvent> events) {
    public PartyResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(party, "party");
        events = List.copyOf(events);
        if (!outcome.isSuccess() && party.isPresent()) {
            throw new IllegalArgumentException("a failed operation carries no party");
        }
    }

    static PartyResult ok(Party party, List<PartyEvent> events) {
        return new PartyResult(PartyOutcome.OK, Optional.ofNullable(party), events);
    }

    static PartyResult failure(PartyOutcome outcome, List<PartyEvent> events) {
        return new PartyResult(outcome, Optional.empty(), events);
    }

    static PartyResult failure(PartyOutcome outcome) {
        return failure(outcome, List.of());
    }

    public boolean isSuccess() {
        return outcome.isSuccess();
    }
}
