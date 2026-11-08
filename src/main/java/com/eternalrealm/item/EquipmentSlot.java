package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.List;
import java.util.Optional;

/**
 * Where an equipment piece is worn. {@link #id()} is the stable persistence key (item JSON): never rename it.
 *
 * <p>Armor pieces split a tier's defense and max health by {@link #armorShare()} so the bigger pieces matter more
 * (CHEST &gt; LEGS &gt; HEAD &gt; FEET); the four shares add up to exactly one full set. Not to be confused with
 * Minecraft's own {@code EquipmentSlot}: the platform layer maps HEAD/CHEST/LEGS/FEET to the vanilla armor slots and
 * WEAPON to the main hand.</p>
 */
public enum EquipmentSlot {
    WEAPON("weapon", 0.0),
    HEAD("head", 0.20),
    CHEST("chest", 0.35),
    LEGS("legs", 0.27),
    FEET("feet", 0.18);

    private static final List<EquipmentSlot> ARMOR = List.of(HEAD, CHEST, LEGS, FEET);

    private final String id;
    private final double armorShare;

    EquipmentSlot(String id, double armorShare) {
        this.id = id;
        this.armorShare = armorShare;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "equipment_slot." + ModInfo.MOD_ID + "." + id;
    }

    public boolean isArmor() {
        return this != WEAPON;
    }

    /** Fraction of a full armor set's defense and max health this piece carries; 0 for the weapon. */
    public double armorShare() {
        return armorShare;
    }

    /** The four armor slots in display order (head to feet). */
    public static List<EquipmentSlot> armor() {
        return ARMOR;
    }

    public static Optional<EquipmentSlot> byId(String id) {
        for (EquipmentSlot slot : values()) {
            if (slot.id.equals(id)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
