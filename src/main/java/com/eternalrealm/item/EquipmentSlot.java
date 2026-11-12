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
 *
 * <p>The trinkets (装身具, owner's request of 2026-11-11) have slots of their own that vanilla does not know: four
 * accessories (アクセサリー: rings, amulets, earrings, bracelets) and two charms (お守り) since the owner's request of
 * 2026-11-13. They carry no defense; their worth is in their stat percentages ({@link TrinketDesign}).</p>
 */
public enum EquipmentSlot {
    WEAPON("weapon", 0.0),
    HEAD("head", 0.20),
    CHEST("chest", 0.35),
    LEGS("legs", 0.27),
    FEET("feet", 0.18),
    ACCESSORY("accessory", 0.0),
    CHARM("charm", 0.0);

    private static final List<EquipmentSlot> ARMOR = List.of(HEAD, CHEST, LEGS, FEET);
    private static final List<EquipmentSlot> TRINKETS = List.of(ACCESSORY, CHARM);

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

    /** Head, chest, legs or feet (worn in a vanilla armor slot). */
    public boolean isArmor() {
        return this == HEAD || this == CHEST || this == LEGS || this == FEET;
    }

    /** An accessory or the charm (their own slots). */
    public boolean isTrinket() {
        return this == ACCESSORY || this == CHARM;
    }

    /** How many pieces of this slot can be worn at once: four accessories, two charms, one of everything else. */
    public int capacity() {
        return switch (this) {
            case ACCESSORY -> 4;
            case CHARM -> 2;
            default -> 1;
        };
    }

    /** Fraction of a full armor set's defense and max health this piece carries; 0 for the weapon. */
    public double armorShare() {
        return armorShare;
    }

    /** The four armor slots in display order (head to feet). */
    public static List<EquipmentSlot> armor() {
        return ARMOR;
    }

    /** The trinket slots (accessory, charm). */
    public static List<EquipmentSlot> trinkets() {
        return TRINKETS;
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
