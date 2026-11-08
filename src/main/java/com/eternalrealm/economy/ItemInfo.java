package com.eternalrealm.economy;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Display information of a market item, extracted by the integration so that the market can be searched and shown
 * without deserializing the item.
 *
 * @param itemId registry id such as {@code minecraft:diamond} or {@code eternalrealm:iron_longsword} (searchable)
 * @param count  stack size, {@code 1..MAX_COUNT}
 * @param rarity rarity id of the item system ({@code [a-z0-9_]{1,32}}, e.g. {@code common}); opaque here
 */
public record ItemInfo(String itemId, int count, String rarity) {
    public static final int MAX_ITEM_ID_LENGTH = 128;
    /** Generous bound (vanilla stacks hold at most 64) so that stored data stays valid if stack rules change. */
    public static final int MAX_COUNT = 9_999;
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern RARITY = Pattern.compile("[a-z0-9_]{1,32}");

    public ItemInfo {
        if (!isValidItemId(itemId)) {
            throw new IllegalArgumentException("invalid item id '" + itemId + "'");
        }
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("count out of range [1, " + MAX_COUNT + "]: " + count);
        }
        Objects.requireNonNull(rarity, "rarity");
        if (!RARITY.matcher(rarity).matches()) {
            throw new IllegalArgumentException("invalid rarity '" + rarity + "'");
        }
    }

    /** Whether {@code itemId} is a lower-case {@code namespace:path} resource location. */
    public static boolean isValidItemId(String itemId) {
        return itemId != null && itemId.length() <= MAX_ITEM_ID_LENGTH && ITEM_ID.matcher(itemId).matches();
    }
}
