package com.eternalrealm.monster;

import com.eternalrealm.core.ModInfo;

import java.util.Optional;

/**
 * Equipment rarity. {@link #id()} is the stable persistence key (item data); never rename or reorder the constants,
 * because the order is the "tier" used by the luck formula ({@link RarityWeights#shifted}).
 */
public enum Rarity {
    COMMON("common", 60, 0xFFFFFF, 1.00),
    UNCOMMON("uncommon", 25, 0x55FF55, 1.10),
    RARE("rare", 10, 0x4FA3FF, 1.25),
    EPIC("epic", 4, 0xB04DFF, 1.45),
    LEGENDARY("legendary", 1, 0xFFAA00, 1.70);

    private final String id;
    private final int baseWeight;
    private final int color;
    private final double statMultiplier;

    Rarity(String id, int baseWeight, int color, double statMultiplier) {
        this.id = id;
        this.baseWeight = baseWeight;
        this.color = color;
        this.statMultiplier = statMultiplier;
    }

    public String id() {
        return id;
    }

    /** Relative drop weight without luck (60/25/10/4/1, i.e. percent). */
    public int baseWeight() {
        return baseWeight;
    }

    /** RGB colour of the item name. */
    public int color() {
        return color;
    }

    /** Multiplier the item module applies to the base stats of a piece of this rarity. */
    public double statMultiplier() {
        return statMultiplier;
    }

    public String translationKey() {
        return "rarity." + ModInfo.MOD_ID + "." + id;
    }

    public static Optional<Rarity> byId(String id) {
        for (Rarity rarity : values()) {
            if (rarity.id.equals(id)) {
                return Optional.of(rarity);
            }
        }
        return Optional.empty();
    }
}
