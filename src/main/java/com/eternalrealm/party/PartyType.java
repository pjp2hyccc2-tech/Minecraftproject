package com.eternalrealm.party;

import com.eternalrealm.core.ModInfo;

/**
 * Size class of a party. Capacities come from {@link PartyRules#capacity(PartyType)}.
 *
 * <p>A party starts as {@link #NORMAL}; its leader may convert it into a {@link #RAID} for content that needs more
 * players (the final boss 終焉の王 is designed for 10 to 20). The conversion is one-way: a raid stays a raid until it is
 * disbanded, so a raid can never be pushed back over the normal cap.</p>
 */
public enum PartyType {
    NORMAL("normal"),
    RAID("raid");

    private final String id;

    PartyType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "party_type." + ModInfo.MOD_ID + "." + id;
    }
}
