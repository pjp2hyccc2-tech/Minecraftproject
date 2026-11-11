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
 * <p>The trinkets (accessories and charms) have bases too, one per tier ({@code abyss_accessory}, {@code iron_charm}):
 * they exist only as the items the named trinkets ({@link TrinketDesign}) are made on, so they are not part of
 * {@link #all()} (no plain trinket drops or recipes); {@link #trinkets()} and {@link #everything()} list them.</p>
 *
 * @param weaponType required for {@link EquipmentSlot#WEAPON}, {@code null} for armor and trinkets
 */
public record EquipmentBase(EquipmentSlot slot, WeaponType weaponType, ItemTier tier) {
    private static final List<EquipmentBase> ALL;
    private static final List<EquipmentBase> TRINKETS;
    private static final List<EquipmentBase> EVERYTHING;

    static {
        List<EquipmentBase> all = new ArrayList<>();
        List<EquipmentBase> trinkets = new ArrayList<>();
        for (ItemTier tier : ItemTier.values()) {
            for (WeaponType type : WeaponType.values()) {
                all.add(weapon(type, tier));
            }
            for (EquipmentSlot slot : EquipmentSlot.armor()) {
                all.add(armor(slot, tier));
            }
            for (EquipmentSlot slot : EquipmentSlot.trinkets()) {
                trinkets.add(trinket(slot, tier));
            }
        }
        ALL = Collections.unmodifiableList(all);
        TRINKETS = Collections.unmodifiableList(trinkets);
        List<EquipmentBase> everything = new ArrayList<>(all);
        everything.addAll(trinkets);
        EVERYTHING = Collections.unmodifiableList(everything);
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

    /** The base of an accessory or a charm of {@code tier}. */
    public static EquipmentBase trinket(EquipmentSlot slot, ItemTier tier) {
        if (!Objects.requireNonNull(slot, "slot").isTrinket()) {
            throw new IllegalArgumentException("Not a trinket slot: " + slot);
        }
        return new EquipmentBase(slot, null, tier);
    }

    /** Every base of weapons and armor: 7 tiers × (6 weapon types + 4 armor slots), tier by tier. */
    public static List<EquipmentBase> all() {
        return ALL;
    }

    /** The trinket bases: 7 tiers × (accessory, charm). */
    public static List<EquipmentBase> trinkets() {
        return TRINKETS;
    }

    /** Every base, the trinkets included (item registration). */
    public static List<EquipmentBase> everything() {
        return EVERYTHING;
    }

    public static Optional<EquipmentBase> byItemPath(String path) {
        for (EquipmentBase base : EVERYTHING) {
            if (base.itemPath().equals(path)) {
                return Optional.of(base);
            }
        }
        return Optional.empty();
    }

    public boolean isWeapon() {
        return slot == EquipmentSlot.WEAPON;
    }

    public boolean isTrinket() {
        return slot.isTrinket();
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
            case ACCESSORY -> "accessory";
            case CHARM -> "charm";
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
