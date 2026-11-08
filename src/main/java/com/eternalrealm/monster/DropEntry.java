package com.eternalrealm.monster;

/**
 * An item a drop table may yield: with probability {@code chance}, a uniformly distributed count in
 * {@code [minCount, maxCount]}. Entries are rolled independently of each other.
 *
 * @param itemId namespaced item id, e.g. {@code eternalrealm:slime_gel} (see {@link Materials})
 */
public record DropEntry(String itemId, int minCount, int maxCount, double chance) {
    public static final int MAX_COUNT = 64;

    public DropEntry {
        Check.resourceId("itemId", itemId);
        Check.range("minCount", minCount, 1, MAX_COUNT);
        Check.range("maxCount", maxCount, minCount, MAX_COUNT);
        Check.chance("chance", chance);
    }
}
