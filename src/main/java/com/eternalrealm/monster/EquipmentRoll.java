package com.eternalrealm.monster;

import java.util.Objects;

/**
 * The equipment part of a drop table: with probability {@code chance} one piece of equipment of {@code tier} drops,
 * its rarity rolled from {@code weights} (shifted by luck). The item module turns the result into a concrete item.
 *
 * @param tier equipment tier 1..7; tier N matches area N
 */
public record EquipmentRoll(int tier, double chance, RarityWeights weights) {
    public static final int MAX_TIER = 7;

    public EquipmentRoll {
        Check.range("tier", tier, 1, MAX_TIER);
        Check.chance("chance", chance);
        Objects.requireNonNull(weights, "weights");
    }
}
