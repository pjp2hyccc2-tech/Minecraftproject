package com.eternalrealm.combat;

import com.eternalrealm.core.ModInfo;

/**
 * What a hit scales with. {@link #HOLY} is magic that the platform layer doubles against undead targets.
 */
public enum DamageType {
    /** Melee weapons (STR). */
    PHYSICAL("physical"),
    /** Bows (DEX). */
    RANGED("ranged"),
    /** Staves and spells (INT). */
    MAGIC("magic"),
    /** Priest light (INT), extra effective against undead. */
    HOLY("holy");

    private final String id;

    DamageType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "damage_type." + ModInfo.MOD_ID + "." + id;
    }

    /** The attack multiplier of {@code stats} that applies to this damage type. */
    public double multiplier(CombatStats stats) {
        return switch (this) {
            case PHYSICAL -> stats.attackMultiplier();
            case RANGED -> stats.rangedMultiplier();
            case MAGIC, HOLY -> stats.magicMultiplier();
        };
    }
}
