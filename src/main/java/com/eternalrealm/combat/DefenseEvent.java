package com.eternalrealm.combat;

import java.util.Optional;

/**
 * Defensive outcome reported to the defending client (HUD popup), or a guard correction. {@link #networkId()} is the
 * stable wire id.
 */
public enum DefenseEvent {
    NONE(0),
    DODGED(1),
    PARRIED(2),
    GUARDED(3),
    GUARD_BROKEN(4),
    /** The server refused a guard the client may have predicted: the client must drop its guard pose. */
    GUARD_REFUSED(5);

    private static final DefenseEvent[] BY_NETWORK_ID = new DefenseEvent[values().length];

    static {
        for (DefenseEvent event : values()) {
            if (BY_NETWORK_ID[event.networkId] != null) {
                throw new ExceptionInInitializerError("Duplicate defense event id " + event.networkId);
            }
            BY_NETWORK_ID[event.networkId] = event;
        }
    }

    private final int networkId;

    DefenseEvent(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static Optional<DefenseEvent> byNetworkId(int networkId) {
        if (networkId < 0 || networkId >= BY_NETWORK_ID.length) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NETWORK_ID[networkId]);
    }
}
