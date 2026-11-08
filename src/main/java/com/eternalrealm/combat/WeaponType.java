package com.eternalrealm.combat;

import com.eternalrealm.core.ModInfo;

/**
 * Weapon families. Each family has its own combo, heavy attack, skill and guard (see {@link WeaponProfiles}).
 * {@link #id()} is the stable persistence/network key: never rename it.
 */
public enum WeaponType {
    ONE_HANDED_SWORD("one_handed_sword", DamageType.PHYSICAL),
    TWO_HANDED_SWORD("two_handed_sword", DamageType.PHYSICAL),
    DUAL_BLADES("dual_blades", DamageType.PHYSICAL),
    SPEAR("spear", DamageType.PHYSICAL),
    /** Magic bolts (INT). */
    STAFF("staff", DamageType.MAGIC),
    /** Arrows (DEX). */
    BOW("bow", DamageType.RANGED);

    private final String id;
    private final DamageType damageType;

    WeaponType(String id, DamageType damageType) {
        this.id = id;
        this.damageType = damageType;
    }

    public String id() {
        return id;
    }

    /** Damage type of the weapon's own attacks (combo, heavy, weapon skill). */
    public DamageType damageType() {
        return damageType;
    }

    public String translationKey() {
        return "weapon_type." + ModInfo.MOD_ID + "." + id;
    }

    public static java.util.Optional<WeaponType> byId(String id) {
        for (WeaponType type : values()) {
            if (type.id.equals(id)) {
                return java.util.Optional.of(type);
            }
        }
        return java.util.Optional.empty();
    }
}
