package com.eternalrealm.estate;

import com.eternalrealm.core.ModInfo;

import java.util.Optional;

/**
 * What a labelled chest is for: its brass plate shows the label's picture, and the sorting desk puts things away by it
 * (docs/modules/estates.md). Players change a chest's label by tapping its plate (sneak and use). {@link #id()} is a
 * block state value: never rename it. The categories of things are the labels other than {@link #NONE}.
 */
public enum StorageLabel {
    NONE("none"),
    /** Weapons, armour, accessories and charms. */
    GEAR("gear"),
    /** Potions and other things used up in a fight. */
    POTION("potion"),
    /** Materials from fields, dungeons and bosses. */
    MATERIAL("material"),
    /** Food. */
    FOOD("food"),
    /** Books, maps, deeds and letters. */
    BOOK("book"),
    /** Rare and precious things: gold-frame relics, gems, metals, epic or better gear. */
    TREASURE("treasure"),
    /** Everything else. */
    MISC("misc");

    private final String id;

    StorageLabel(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "estate." + ModInfo.MOD_ID + ".label." + id;
    }

    /** The next label when the plate is tapped (wraps round). */
    public StorageLabel next() {
        StorageLabel[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static Optional<StorageLabel> byId(String id) {
        for (StorageLabel label : values()) {
            if (label.id.equals(id)) {
                return Optional.of(label);
            }
        }
        return Optional.empty();
    }
}
