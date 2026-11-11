package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;

import java.util.EnumMap;
import java.util.Map;

/**
 * The weapon profiles of the tier weapons (iron .. abyss; the six starter weapons are the iron ones): the weapon
 * type's own moves with the tier's weapon art on the weapon-skill key ({@link GeneratedArts#forTier}). One instance per
 * tier and type, so a profile's identity is stable.
 */
public final class TierProfiles {
    private static final Map<ItemTier, Map<WeaponType, WeaponProfile>> PROFILES = new EnumMap<>(ItemTier.class);

    static {
        for (ItemTier tier : ItemTier.values()) {
            Map<WeaponType, WeaponProfile> byType = new EnumMap<>(WeaponType.class);
            for (WeaponType type : WeaponType.values()) {
                WeaponProfile base = WeaponProfiles.of(type);
                byType.put(type, new WeaponProfile(tier.id() + "_" + type.id(), type, base.baseAttack(),
                        base.critBonus(), base.combo(), base.comboResetTicks(), base.heavy(),
                        GeneratedArts.forTier(tier, type).action(), base.guard()));
            }
            PROFILES.put(tier, byType);
        }
    }

    private TierProfiles() {
    }

    public static WeaponProfile of(ItemTier tier, WeaponType type) {
        return PROFILES.get(tier).get(type);
    }
}
