package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Optional;

/**
 * What a trinket is (装身具): the four kinds of accessory (アクセサリー) and the four kinds of charm (お守り). The kind
 * decides the slot, the noun of the name and the icon. {@link #id()} is a stable key.
 */
public enum TrinketKind {
    RING(EquipmentSlot.ACCESSORY),
    AMULET(EquipmentSlot.ACCESSORY),
    EARRING(EquipmentSlot.ACCESSORY),
    BRACELET(EquipmentSlot.ACCESSORY),
    /** A paper or wooden ward (護符). */
    TALISMAN(EquipmentSlot.CHARM),
    /** A warding bell (守り鈴). */
    BELL(EquipmentSlot.CHARM),
    /** A carved ward stone (守り石). */
    STONE(EquipmentSlot.CHARM),
    /** A sewn amulet pouch (お守り袋). */
    POUCH(EquipmentSlot.CHARM);

    private final EquipmentSlot slot;

    TrinketKind(EquipmentSlot slot) {
        this.slot = slot;
    }

    public EquipmentSlot slot() {
        return slot;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The noun ("指輪", "Ring"). */
    public String translationKey() {
        return "trinket_kind." + ModInfo.MOD_ID + "." + id();
    }

    public static Optional<TrinketKind> byId(String id) {
        for (TrinketKind kind : values()) {
            if (kind.id().equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
