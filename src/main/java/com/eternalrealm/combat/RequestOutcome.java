package com.eternalrealm.combat;

import java.util.Optional;

/**
 * Result of a combat request. Rejections are normal gameplay (latency, spamming, empty stamina) and never protocol
 * violations. {@link #networkId()} is the stable wire id.
 */
public enum RequestOutcome {
    /** Started immediately. */
    ACCEPTED(0, null),
    /** Queued; starts at the current action's cancel point. */
    BUFFERED(1, null),
    /** Nothing to do (e.g. a charge release without a charge). */
    IGNORED(2, null),
    REJECTED_BUSY(3, null),
    REJECTED_STAMINA(4, "message.eternalrealm.combat.no_stamina"),
    REJECTED_COOLDOWN(5, "message.eternalrealm.combat.cooldown"),
    REJECTED_STAGGERED(6, null),
    REJECTED_GUARDING(7, null),
    REJECTED_NO_WEAPON(8, null),
    REJECTED_NOT_ALLOWED(9, null),
    /** The job skill slot is empty (skill not learned yet). */
    REJECTED_NOT_LEARNED(10, "message.eternalrealm.combat.not_learned"),
    /** The job skill cannot be used with the weapon in hand. */
    REJECTED_WRONG_WEAPON(11, "message.eternalrealm.combat.wrong_weapon");

    private static final RequestOutcome[] BY_NETWORK_ID = new RequestOutcome[values().length];

    static {
        for (RequestOutcome outcome : values()) {
            if (BY_NETWORK_ID[outcome.networkId] != null) {
                throw new ExceptionInInitializerError("Duplicate outcome id " + outcome.networkId);
            }
            BY_NETWORK_ID[outcome.networkId] = outcome;
        }
    }

    private final int networkId;
    private final String messageKey;

    RequestOutcome(int networkId, String messageKey) {
        this.networkId = networkId;
        this.messageKey = messageKey;
    }

    public int networkId() {
        return networkId;
    }

    public boolean isRejected() {
        return networkId >= REJECTED_BUSY.networkId;
    }

    /** Translation key worth showing to the player, if any (most rejections are self-explanatory or silent). */
    public Optional<String> messageKey() {
        return Optional.ofNullable(messageKey);
    }

    public static Optional<RequestOutcome> byNetworkId(int networkId) {
        if (networkId < 0 || networkId >= BY_NETWORK_ID.length) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NETWORK_ID[networkId]);
    }
}
