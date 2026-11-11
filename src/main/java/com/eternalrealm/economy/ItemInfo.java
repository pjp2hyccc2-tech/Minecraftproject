package com.eternalrealm.economy;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Display information of a market item, extracted by the integration so that the market can be searched, filtered
 * and shown without deserializing the item.
 *
 * @param itemId   registry id such as {@code minecraft:diamond} or {@code eternalrealm:iron_longsword} (searchable)
 * @param count    stack size, {@code 1..MAX_COUNT}
 * @param rarity   rarity id of the item system ({@code [a-z0-9_]{1,32}}, e.g. {@code common})
 * @param category what it is, for the board's filters ({@link #CATEGORIES}; {@code other} when unknown)
 * @param level    character level needed to use it ({@code 0}: none)
 * @param gold     a gold-frame piece (金枠)
 * @param group    what counts as "the same item" for price history (e.g. a design and its rarity); never empty
 * @param keywords lower-case search text (names in every language, ids); at most {@link #MAX_KEYWORDS} characters
 */
public record ItemInfo(String itemId, int count, String rarity, String category, int level, boolean gold,
                       String group, String keywords) {
    public static final int MAX_ITEM_ID_LENGTH = 128;
    /** Generous bound (vanilla stacks hold at most 64) so that stored data stays valid if stack rules change. */
    public static final int MAX_COUNT = 9_999;
    public static final int MAX_LEVEL = 999;
    public static final int MAX_GROUP_LENGTH = 160;
    public static final int MAX_KEYWORDS = 256;
    /** The board's item kinds, in its filter order. */
    public static final java.util.List<String> CATEGORIES = java.util.List.of("weapon", "armor", "accessory",
            "material", "consumable", "mount", "other");
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern RARITY = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern GROUP = Pattern.compile("[a-z0-9_.:/-]{1," + MAX_GROUP_LENGTH + "}");

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
        if (!CATEGORIES.contains(Objects.requireNonNull(category, "category"))) {
            throw new IllegalArgumentException("invalid category '" + category + "'");
        }
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("level out of range [0, " + MAX_LEVEL + "]: " + level);
        }
        if (!GROUP.matcher(Objects.requireNonNull(group, "group")).matches()) {
            throw new IllegalArgumentException("invalid group '" + group + "'");
        }
        Objects.requireNonNull(keywords, "keywords");
        if (keywords.length() > MAX_KEYWORDS) {
            keywords = keywords.substring(0, MAX_KEYWORDS);
        }
    }

    /** An item known only by id, count and rarity (older market data): kind "other", no level, grouped by its id. */
    public ItemInfo(String itemId, int count, String rarity) {
        this(itemId, count, rarity, "other", 0, false, groupOf(itemId), itemId == null ? "" : itemId);
    }

    /** The same item with another count (a part of a stack). */
    public ItemInfo withCount(int newCount) {
        return new ItemInfo(itemId, newCount, rarity, category, level, gold, group, keywords);
    }

    /** Whether every space-separated word of {@code text} (lower case) appears in the id or the keywords. */
    public boolean matches(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String haystack = (itemId + " " + keywords).toLowerCase(java.util.Locale.ROOT);
        for (String word : text.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+")) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Rank of the rarity for "at least" filters (common 0 .. legendary 4, unknown -1). */
    public int rarityRank() {
        return rarityRank(rarity);
    }

    public static int rarityRank(String rarity) {
        return switch (rarity) {
            case "common" -> 0;
            case "uncommon" -> 1;
            case "rare" -> 2;
            case "epic" -> 3;
            case "legendary" -> 4;
            default -> -1;
        };
    }

    private static String groupOf(String itemId) {
        String group = "item:" + (itemId == null ? "unknown" : itemId);
        return GROUP.matcher(group).matches() ? group : "item:unknown";
    }

    /** Whether {@code itemId} is a lower-case {@code namespace:path} resource location. */
    public static boolean isValidItemId(String itemId) {
        return itemId != null && itemId.length() <= MAX_ITEM_ID_LENGTH && ITEM_ID.matcher(itemId).matches();
    }
}
