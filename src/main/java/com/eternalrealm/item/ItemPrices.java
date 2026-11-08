package com.eternalrealm.item;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * NPC prices in gold. Every NPC buys for less than any NPC sells, so no buy-and-sell loop creates gold, and the
 * gold-converting actions (crafting, enhancement) always cost more than the NPC value they add.
 *
 * <pre>
 * equipment sell value = tier base (8, 20, 40, 70, 110, 160, 220) × rarity price multiplier (1, 1.5, 2.5, 4, 7)
 *                        × (1 + 0.05 × enhancement), rounded; affixes do not matter (rerolling cannot raise it)
 * area material        = 2, 4, 6, 9, 13, 18, 25 per item (area 1..7); not sold by NPCs
 * enhancement stone    = shop 30, sell 6          reroll crystal = shop 150, sell 30
 * consumables          = shop price ({@link Consumable#shopPrice()}), sell a quarter of it (rounded down)
 * </pre>
 *
 * Bound pieces may be sold to NPCs (only player-to-player transfers are forbidden).
 */
public final class ItemPrices {
    public static final double ENHANCEMENT_VALUE_PER_LEVEL = 0.05;
    public static final long ENHANCEMENT_STONE_SHOP_PRICE = 30;
    public static final long ENHANCEMENT_STONE_SELL_PRICE = 6;
    public static final long REROLL_CRYSTAL_SHOP_PRICE = 150;
    public static final long REROLL_CRYSTAL_SELL_PRICE = 30;
    /** Consumables sell for {@code shop price / CONSUMABLE_SELL_DIVISOR}. */
    public static final int CONSUMABLE_SELL_DIVISOR = 4;

    /** Index = tier number − 1: sell value of a COMMON +0 piece. */
    private static final long[] EQUIPMENT_BASE = {8, 20, 40, 70, 110, 160, 220};
    /** Index = area − 1. */
    private static final long[] MATERIAL_SELL = {2, 4, 6, 9, 13, 18, 25};

    private ItemPrices() {
    }

    /** What an NPC pays for a piece. */
    public static long equipmentSellValue(ItemTier tier, ItemRarity rarity, int enhancement) {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(rarity, "rarity");
        Checks.range("enhancement", enhancement, 0, EquipmentData.MAX_ENHANCEMENT);
        return Math.round(EQUIPMENT_BASE[tier.number() - 1] * rarity.priceMultiplier()
                * (1.0 + ENHANCEMENT_VALUE_PER_LEVEL * enhancement));
    }

    public static long equipmentSellValue(EquipmentData data) {
        Objects.requireNonNull(data, "data");
        return equipmentSellValue(data.tier(), data.rarity(), data.enhancement());
    }

    /** What an NPC pays for one unit of a material; empty for ids that are not {@link Materials}. */
    public static OptionalLong materialSellValue(String itemId) {
        if (Materials.ENHANCEMENT_STONE.equals(itemId)) {
            return OptionalLong.of(ENHANCEMENT_STONE_SELL_PRICE);
        }
        if (Materials.REROLL_CRYSTAL.equals(itemId)) {
            return OptionalLong.of(REROLL_CRYSTAL_SELL_PRICE);
        }
        OptionalInt area = Materials.areaOf(itemId);
        return area.isPresent() ? OptionalLong.of(MATERIAL_SELL[area.getAsInt() - 1]) : OptionalLong.empty();
    }

    /** Shop price of a material; only the enhancement stone and the reroll crystal are sold by NPCs. */
    public static OptionalLong materialShopPrice(String itemId) {
        if (Materials.ENHANCEMENT_STONE.equals(itemId)) {
            return OptionalLong.of(ENHANCEMENT_STONE_SHOP_PRICE);
        }
        if (Materials.REROLL_CRYSTAL.equals(itemId)) {
            return OptionalLong.of(REROLL_CRYSTAL_SHOP_PRICE);
        }
        return OptionalLong.empty();
    }

    public static long consumableShopPrice(Consumable consumable) {
        return Objects.requireNonNull(consumable, "consumable").shopPrice();
    }

    public static long consumableSellPrice(Consumable consumable) {
        return consumableShopPrice(consumable) / CONSUMABLE_SELL_DIVISOR;
    }

    /** NPC value of a whole {@link Cost} (gold plus the items' sell values; unknown items count 0). */
    public static long sellValue(Cost cost) {
        Objects.requireNonNull(cost, "cost");
        long total = cost.gold();
        for (var entry : cost.items().entrySet()) {
            total += materialSellValue(entry.getKey()).orElse(0L) * entry.getValue();
        }
        return total;
    }
}
