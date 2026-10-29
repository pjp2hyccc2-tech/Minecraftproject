package com.huntcraft.core;

/** The 8 weapon types. */
public enum WeaponClass {
    GREAT_SWORD(DamageKind.CUT),
    LONG_SWORD(DamageKind.CUT),
    SWORD_AND_SHIELD(DamageKind.CUT),
    DUAL_BLADES(DamageKind.CUT),
    HAMMER(DamageKind.BLUNT),
    LANCE(DamageKind.CUT),
    HUNTING_HORN(DamageKind.BLUNT),
    BOW(DamageKind.SHOT);

    public final DamageKind defaultKind;

    WeaponClass(DamageKind kind) {
        this.defaultKind = kind;
    }

    public String key() {
        return "weapon_class.huntcraft." + name().toLowerCase();
    }
}
