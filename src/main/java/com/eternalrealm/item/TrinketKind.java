package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Optional;

/**
 * What a trinket is (装身具): the eight kinds of accessory (アクセサリー) and the eight kinds of charm (お守り); the
 * second four of each since 2026-11-13. The kind decides the slot, the noun of the name and the icon. {@link #id()} is
 * a stable key.
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
    POUCH(EquipmentSlot.CHARM),
    // the kinds of the named trinkets (owner's request of 2026-11-13), appended: ids are stable
    /** A brooch (ブローチ). */
    BROOCH(EquipmentSlot.ACCESSORY),
    /** A circlet worn on the brow (額冠). */
    CIRCLET(EquipmentSlot.ACCESSORY),
    /** An anklet (足環). */
    ANKLET(EquipmentSlot.ACCESSORY),
    /** A medal of honour (勲章). */
    MEDAL(EquipmentSlot.ACCESSORY),
    /** A small warding mirror (護り鏡). */
    MIRROR(EquipmentSlot.CHARM),
    /** A warding feather (守り羽). */
    FEATHER(EquipmentSlot.CHARM),
    /** Prayer beads (数珠). */
    BEADS(EquipmentSlot.CHARM),
    /** A ward doll that takes the blow instead (身代わり人形). */
    DOLL(EquipmentSlot.CHARM);

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
