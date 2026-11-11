package com.eternalrealm.estate.forge;

import com.eternalrealm.estate.StorageLabel;
import net.minecraft.util.StringRepresentable;

/** {@link StorageLabel} as the block state property of a {@link StorageChestBlock} (same ids, same order). */
public enum ChestLabel implements StringRepresentable {
    NONE(StorageLabel.NONE),
    GEAR(StorageLabel.GEAR),
    POTION(StorageLabel.POTION),
    MATERIAL(StorageLabel.MATERIAL),
    FOOD(StorageLabel.FOOD),
    BOOK(StorageLabel.BOOK),
    TREASURE(StorageLabel.TREASURE),
    MISC(StorageLabel.MISC);

    private final StorageLabel label;

    ChestLabel(StorageLabel label) {
        this.label = label;
    }

    public StorageLabel label() {
        return label;
    }

    public static ChestLabel of(StorageLabel label) {
        return values()[label.ordinal()];
    }

    @Override
    public String getSerializedName() {
        return label.id();
    }
}
