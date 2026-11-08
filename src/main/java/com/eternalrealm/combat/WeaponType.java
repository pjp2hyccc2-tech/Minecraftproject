package com.eternalrealm.combat;

import com.eternalrealm.core.ModInfo;

/**
 * Weapon families. Each family has its own combo, heavy attack, skill and guard (see {@link WeaponProfiles}).
 * Bows and staves (ranged / magic, DEX / INT) are added with the job system in Phase 3.
 */
public enum WeaponType {
    ONE_HANDED_SWORD("one_handed_sword"),
    TWO_HANDED_SWORD("two_handed_sword"),
    DUAL_BLADES("dual_blades"),
    SPEAR("spear");

    private final String id;

    WeaponType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "weapon_type." + ModInfo.MOD_ID + "." + id;
    }
}
