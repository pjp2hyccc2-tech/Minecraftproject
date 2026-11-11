package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.Map;
import java.util.Set;

/**
 * A named equipment design (名品): a weapon of {@link Armory#weapons()}, one piece of an {@link ArmorSet}, or a relic
 * (金枠: a weapon, an armor piece or a trinket of {@link Relics}). A design
 * sits on top of a tiered base (its item, rarity, affixes and enhancement work as for every piece) and adds its own
 * name, look, required level, stat profile and where it comes from. {@link #id()} and {@link #model()} are stable:
 * stored in item stacks, never renamed or reused.
 */
public sealed interface Design permits WeaponDesign, ArmorPiece, TrinketDesign {
    /** First custom model data number of the weapon designs. */
    int WEAPON_MODELS = 3000;
    /** First custom model data number of the relic weapons (金枠). */
    int RELIC_WEAPON_MODELS = 3500;
    /** First custom model data number of the armor pieces. */
    int ARMOR_MODELS = 4000;
    /** First custom model data number of the relic armor pieces. */
    int RELIC_ARMOR_MODELS = 4500;
    /** First custom model data number of the trinkets (accessories and charms). */
    int TRINKET_MODELS = 5000;
    /** Highest level a design may ask for (the level cap). */
    int MAX_LEVEL = 100;

    String id();

    /** {@code CustomModelData} of the stack, which picks the design's model on the base item. */
    int model();

    /** The tiered base the design is made on (the stack's item). */
    EquipmentBase base();

    /** Field (area id 1..10) the design belongs to. */
    int field();

    /** Character level needed to equip it (never below the base tier's level). */
    int level();

    Set<DesignSource> sources();

    /** Lord, calamity, boss or dungeon id the design comes from, or {@code ""}. */
    String origin();

    /** Extra weights of the affix pool when a piece of this design is rolled (only kinds the pool allows). */
    Map<AffixKind, Integer> bias();

    /** A relic (金枠): the gold frame, its own level, always legendary. */
    default boolean isRelic() {
        return sources().contains(DesignSource.RELIC);
    }

    default String translationKey() {
        return "design." + ModInfo.MOD_ID + "." + id();
    }

    default String loreKey() {
        return translationKey() + ".lore";
    }

    default EquipmentSlot slot() {
        return base().slot();
    }
}
