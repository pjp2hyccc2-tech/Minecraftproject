package com.eternalrealm.character;

import com.eternalrealm.core.ModInfo;

import java.util.Optional;

/**
 * The six primary attributes.
 *
 * <p>{@link #id()} is the stable persistence key and {@link #networkId()} the stable wire id.
 * Never reorder or reuse either value; append new attributes at the end and bump the
 * network protocol version / data schema version.</p>
 */
public enum StatType {
    /** Physical attack power. */
    STR(0, "str"),
    /** Max HP and defense. */
    VIT(1, "vit"),
    /** Attack speed and evasion. */
    AGI(2, "agi"),
    /** Magic attack power. */
    INT(3, "int"),
    /** Accuracy and ranged attack performance. */
    DEX(4, "dex"),
    /** Critical rate and drop bonus. */
    LUK(5, "luk");

    public static final int COUNT = 6;
    private static final StatType[] BY_NETWORK_ID = new StatType[COUNT];

    static {
        for (StatType type : values()) {
            if (BY_NETWORK_ID[type.networkId] != null) {
                throw new ExceptionInInitializerError("Duplicate stat network id " + type.networkId);
            }
            BY_NETWORK_ID[type.networkId] = type;
        }
    }

    private final int networkId;
    private final String id;

    StatType(int networkId, String id) {
        this.networkId = networkId;
        this.id = id;
    }

    public String id() {
        return id;
    }

    public int networkId() {
        return networkId;
    }

    public String translationKey() {
        return "stat." + ModInfo.MOD_ID + "." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    public static Optional<StatType> byNetworkId(int networkId) {
        if (networkId < 0 || networkId >= BY_NETWORK_ID.length) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NETWORK_ID[networkId]);
    }

    public static Optional<StatType> byId(String id) {
        for (StatType type : values()) {
            if (type.id.equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
