package com.eternalrealm.mount;

import com.eternalrealm.core.ModInfo;

/**
 * What a mount is built like (docs/modules/mounts.md): the horses (the Moonmane Charger and its five breeds) gallop
 * and jump, the griffins (the Azurewing Griffin and its five breeds) fly, the arcbike rolls. The family decides the
 * skeleton and the gaits of the model, the sounds and the stable's tab; a {@link MountKind} is one breed of a family.
 */
public enum MountFamily {
    HORSE("horse"),
    GRIFFIN("griffin"),
    BIKE("bike");

    private final String id;

    MountFamily(String id) {
        this.id = id;
    }

    /** Also the first part of the shape texts' keys (the manes and wings are the family's). Never rename it. */
    public String id() {
        return id;
    }

    /** Whether the family flies (jump climbs, sneak descends, it glides down otherwise). */
    public boolean flies() {
        return this == GRIFFIN;
    }

    /** The name of the family (the stable's tab). */
    public String translationKey() {
        return "mount." + ModInfo.MOD_ID + ".family." + id;
    }
}
