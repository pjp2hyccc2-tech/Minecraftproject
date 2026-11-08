package com.eternalrealm.monster;

import java.util.Objects;

/**
 * One result of {@link LootRoller#roll}. Either a stack of a concrete item, or a piece of equipment the item module
 * has to generate (it knows the concrete equipment items of each tier). A sealed type instead of a nullable rarity so
 * the tier travels with the rarity and an equipment roll can never be mistaken for an item id.
 */
public sealed interface LootDrop permits LootDrop.ItemDrop, LootDrop.EquipmentDrop {
    /** {@code count} of the item {@code itemId} (namespaced). */
    record ItemDrop(String itemId, int count) implements LootDrop {
        public ItemDrop {
            Check.resourceId("itemId", itemId);
            Check.range("count", count, 1, DropEntry.MAX_COUNT);
        }
    }

    /** One random equipment piece of {@code tier} (1..7) with the given rarity. */
    record EquipmentDrop(int tier, Rarity rarity) implements LootDrop {
        public EquipmentDrop {
            Check.range("tier", tier, 1, EquipmentRoll.MAX_TIER);
            Objects.requireNonNull(rarity, "rarity");
        }
    }
}
