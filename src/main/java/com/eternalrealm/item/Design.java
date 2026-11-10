package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.Map;
import java.util.Set;

/**
 * A named equipment design (名品): a weapon of {@link Armory#weapons()} or one piece of an {@link ArmorSet}. A design
 * sits on top of a tiered base (its item, rarity, affixes and enhancement work as for every piece) and adds its own
 * name, look, required level, stat profile and where it comes from. {@link #id()} and {@link #model()} are stable:
 * stored in item stacks, never renamed or reused.
 */
public sealed interface Design permits WeaponDesign, ArmorPiece {
    /** First custom model data number of the weapon designs. */
    int WEAPON_MODELS = 3000;
    /** First custom model data number of the armor pieces. */
    int ARMOR_MODELS = 4000;

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
