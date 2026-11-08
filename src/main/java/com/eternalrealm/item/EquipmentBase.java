package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The fixed part of an equipment piece: what it is, without rarity, affixes or enhancement. Crafting recipes produce a
 * base; drops and recipes roll the rest ({@link EquipmentGenerator}).
 *
 * <p>{@link #itemPath()} names the piece for item registration ({@code <tier>_<type>}, e.g. {@code abyss_longsword});
 * the tier-1 weapons match the Phase 2 items ({@code iron_longsword}, {@code iron_greatsword},
 * {@code iron_twin_blades}, {@code iron_spear}) so they can stay registered under the same names.</p>
 *
 * @param weaponType required for {@link EquipmentSlot#WEAPON}, {@code null} for armor
 */
public record EquipmentBase(EquipmentSlot slot, WeaponType weaponType, ItemTier tier) {
    private static final List<EquipmentBase> ALL;

    static {
        List<EquipmentBase> all = new ArrayList<>();
        for (ItemTier tier : ItemTier.values()) {
            for (WeaponType type : WeaponType.values()) {
                all.add(weapon(type, tier));
            }
            for (EquipmentSlot slot : EquipmentSlot.armor()) {
                all.add(armor(slot, tier));
            }
        }
        ALL = Collections.unmodifiableList(all);
    }

    public EquipmentBase {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(tier, "tier");
        if ((slot == EquipmentSlot.WEAPON) != (weaponType != null)) {
            throw new IllegalArgumentException("A weapon type is required for weapons and forbidden for armor: "
                    + slot + " / " + weaponType);
        }
    }

    public static EquipmentBase weapon(WeaponType type, ItemTier tier) {
        return new EquipmentBase(EquipmentSlot.WEAPON, Objects.requireNonNull(type, "type"), tier);
    }

    public static EquipmentBase armor(EquipmentSlot slot, ItemTier tier) {
        if (!Objects.requireNonNull(slot, "slot").isArmor()) {
            throw new IllegalArgumentException("Not an armor slot: " + slot);
        }
        return new EquipmentBase(slot, null, tier);
    }

    /** Every base: 7 tiers × (6 weapon types + 4 armor slots), tier by tier. */
    public static List<EquipmentBase> all() {
        return ALL;
    }

    public static Optional<EquipmentBase> byItemPath(String path) {
        for (EquipmentBase base : ALL) {
            if (base.itemPath().equals(path)) {
                return Optional.of(base);
            }
        }
        return Optional.empty();
    }

    public boolean isWeapon() {
        return slot == EquipmentSlot.WEAPON;
    }

    /** Stable noun id of the piece: longsword, greatsword, twin_blades, spear, staff, bow, helmet, chestplate, ... */
    public String typeId() {
        if (weaponType != null) {
            return switch (weaponType) {
                case ONE_HANDED_SWORD -> "longsword";
                case TWO_HANDED_SWORD -> "greatsword";
                case DUAL_BLADES -> "twin_blades";
                case SPEAR -> "spear";
                case STAFF -> "staff";
                case BOW -> "bow";
            };
        }
        return switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            case WEAPON -> throw new IllegalStateException("unreachable");
        };
    }

    /** Registry path of the item, e.g. {@code frost_spear} or {@code iron_boots}. */
    public String itemPath() {
        return tier.id() + "_" + typeId();
    }

    /** Namespaced item id, e.g. {@code eternalrealm:frost_spear}. */
    public String itemId() {
        return ModInfo.MOD_ID + ":" + itemPath();
    }

    /** The noun of the piece ("片手剣", "兜", ...), composed with the tier prefix into the display name. */
    public String nounKey() {
        return "equipment_type." + ModInfo.MOD_ID + "." + typeId();
    }

    public int requiredLevel() {
        return tier.requiredLevel();
    }
}
