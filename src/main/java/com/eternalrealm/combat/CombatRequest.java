package com.eternalrealm.combat;

import java.util.Optional;

/**
 * Combat intents a client can send. {@link #networkId()} is the stable wire id: never reorder or reuse it.
 */
public enum CombatRequest {
    LIGHT(0),
    HEAVY_START(1),
    HEAVY_RELEASE(2),
    DODGE(3),
    SKILL(4);

    private static final CombatRequest[] BY_NETWORK_ID = new CombatRequest[values().length];

    static {
        for (CombatRequest request : values()) {
            if (BY_NETWORK_ID[request.networkId] != null) {
                throw new ExceptionInInitializerError("Duplicate combat request id " + request.networkId);
            }
            BY_NETWORK_ID[request.networkId] = request;
        }
    }

    private final int networkId;

    CombatRequest(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static Optional<CombatRequest> byNetworkId(int networkId) {
        if (networkId < 0 || networkId >= BY_NETWORK_ID.length) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NETWORK_ID[networkId]);
    }
}
