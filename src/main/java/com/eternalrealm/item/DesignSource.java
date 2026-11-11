package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;

/** Where a named design can be obtained. */
public enum DesignSource {
    /** Forged at the smithy ({@link Recipes#forDesign}). */
    CRAFT,
    /** Dropped by the monsters of its field. */
    FIELD,
    /** Carried visibly by monsters of its field, which may drop it ({@link Signatures#monsterWeapon}). */
    MONSTER,
    /** A reward of its dungeon's boss (origin = the dungeon id). */
    DUNGEON,
    /** The signature drop of a field lord, ヌシ (origin = the lord id). */
    LORD,
    /** The signature drop of a field calamity, 災厄 (origin = the calamity id). */
    CALAMITY,
    /** A boss weapon (origin = the boss id). */
    BOSS,
    /**
     * A relic (金枠の遺宝): about one kill in a thousand of a dungeon boss, a calamity, a lord or a wanderer leaves one
     * of its own ({@link Relics}; origin = the relic source id).
     */
    RELIC;

    public String translationKey() {
        return "design_source." + ModInfo.MOD_ID + "." + name().toLowerCase(Locale.ROOT);
    }
}
