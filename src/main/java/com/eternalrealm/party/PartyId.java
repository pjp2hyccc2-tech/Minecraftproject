package com.eternalrealm.party;

/**
 * Identifier of a party, unique within one server run.
 *
 * <p>A sequence number is enough because parties are never persisted: they all end when the server stops, and a new
 * run starts again at 1. A stale id from before a restart can never join anyone, because joining always requires a
 * pending invite of the new party with that id.</p>
 */
public record PartyId(long value) {
    public PartyId {
        if (value < 1) {
            throw new IllegalArgumentException("party id must be positive: " + value);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
