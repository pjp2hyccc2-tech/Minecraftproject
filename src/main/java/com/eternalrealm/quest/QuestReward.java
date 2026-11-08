package com.eternalrealm.quest;

import java.util.List;

/**
 * What {@link QuestEngine#turnIn} grants. The Forge layer applies it in the same server tick: EXP through the character
 * (skip it when 0; EXP beyond the level cap is discarded as usual), gold through the economy, items into the inventory
 * (dropped at the player's feet if it is full).
 */
public record QuestReward(long exp, long gold, List<ItemReward> items) {
    public static final long MAX_AMOUNT = 1_000_000_000L;
    public static final int MAX_ITEMS = 8;

    public QuestReward {
        Check.range("exp", exp, 0L, MAX_AMOUNT);
        Check.range("gold", gold, 0L, MAX_AMOUNT);
        items = List.copyOf(items);
        Check.range("items", items.size(), 0, MAX_ITEMS);
    }
}
