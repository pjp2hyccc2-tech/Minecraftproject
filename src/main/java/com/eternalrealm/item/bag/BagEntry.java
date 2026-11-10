package com.eternalrealm.item.bag;

import com.eternalrealm.item.ItemRarity;

import java.util.Objects;

/**
 * One occupied slot of the bag as the bag screen and the sort see it.
 *
 * @param slot     inventory slot ({@link Bag#FIRST_SLOT} .. {@link Bag#LAST_SLOT})
 * @param itemId   registry id of the item
 * @param count    stack size
 * @param category tab and sort group
 * @param rarity   rarity (plain items are {@link ItemRarity#COMMON})
 * @param level    required level of equipment, 0 for everything else
 */
public record BagEntry(int slot, String itemId, int count, BagCategory category, ItemRarity rarity, int level) {
    public BagEntry {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(rarity, "rarity");
        if (count <= 0) {
            throw new IllegalArgumentException("count " + count);
        }
    }
}
