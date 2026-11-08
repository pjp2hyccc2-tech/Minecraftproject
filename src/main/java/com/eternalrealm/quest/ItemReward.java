package com.eternalrealm.quest;

/** {@code count} of the item {@code itemId} (namespaced) given on turn-in. */
public record ItemReward(String itemId, int count) {
    public static final int MAX_COUNT = 64;

    public ItemReward {
        Check.itemId("itemId", itemId);
        Check.range("count", count, 1, MAX_COUNT);
    }
}
