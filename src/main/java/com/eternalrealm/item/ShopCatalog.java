package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What the item shop (道具屋) sells and the rules of its counter (owner, 2026-11-14: 「アイテムショップのHUDを
 * ファンタジー風にオシャレにして」): the wares by section in the order of the shelves, how many may be bought at once,
 * what a purchase or a sale is worth and when the counter says no. The prices are {@link ItemPrices}'. Pure.
 */
public final class ShopCatalog {
    /** Most of one ware bought with one click. */
    public static final int MAX_PER_PURCHASE = 99;
    /** The quick amounts beside the − and + of the counter. */
    public static final List<Integer> STEPS = List.of(1, 5, 10);

    /** The shelves, in their order. */
    public enum Section {
        POTIONS, TRAVEL, ELIXIRS, SMITHING;

        public String translationKey() {
            return "screen." + ModInfo.MOD_ID + ".shop.section." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** A ware: its item id, its shelf, its price and the level it is meant for (it may be bought before). */
    public record Ware(String itemId, Section section, long price, int requiredLevel) {
    }

    /** What the counter says to a purchase. */
    public enum BuyCheck {
        OK, UNKNOWN, BAD_COUNT, NOT_ENOUGH_GOLD, BAG_FULL;

        public String translationKey() {
            return "message." + ModInfo.MOD_ID + ".shop.buy." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** What is sold back: a piece of equipment is sold alone and only when the player confirms it. */
    public enum SellKind {
        EQUIPMENT, CONSUMABLE, MATERIAL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<SellKind> byId(String id) {
            for (SellKind kind : values()) {
                if (kind.id().equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    private static final List<Ware> WARES;

    static {
        List<Ware> wares = new ArrayList<>();
        for (Consumable consumable : Consumable.values()) {
            Section section = switch (consumable) {
                case HEALTH_POTION_SMALL, HEALTH_POTION_MEDIUM, HEALTH_POTION_LARGE, STAMINA_TONIC -> Section.POTIONS;
                case RETURN_SCROLL, EVERBLOOM_PETAL -> Section.TRAVEL;
                case OBLIVION_ELIXIR -> Section.ELIXIRS;
            };
            wares.add(new Ware(consumable.itemId(), section, ItemPrices.consumableShopPrice(consumable),
                    consumable.requiredLevel()));
        }
        for (String id : List.of(Materials.ENHANCEMENT_STONE, Materials.REROLL_CRYSTAL)) {
            ItemPrices.materialShopPrice(id).ifPresent(price -> wares.add(new Ware(id, Section.SMITHING, price, 1)));
        }
        wares.sort(java.util.Comparator.comparingInt(ware -> ware.section().ordinal()));
        WARES = List.copyOf(wares);
    }

    private ShopCatalog() {
    }

    /** Every ware, shelf by shelf. */
    public static List<Ware> wares() {
        return WARES;
    }

    public static Optional<Ware> ware(String itemId) {
        return WARES.stream().filter(ware -> ware.itemId().equals(itemId)).findFirst();
    }

    /** The price of {@code count} of a ware ({@link Long#MAX_VALUE} rather than overflowing). */
    public static long total(long price, int count) {
        if (price <= 0 || count <= 0) {
            return 0L;
        }
        return price > Long.MAX_VALUE / count ? Long.MAX_VALUE : price * count;
    }

    /** How many of a ware {@code gold} pays for at once (0..{@link #MAX_PER_PURCHASE}). */
    public static int affordable(long price, long gold) {
        if (price <= 0) {
            return MAX_PER_PURCHASE;
        }
        return (int) Math.max(0L, Math.min(MAX_PER_PURCHASE, gold / price));
    }

    /**
     * Whether {@code count} of the ware {@code itemId} can be bought with {@code gold} when the bag has room for
     * {@code room} more of it.
     */
    public static BuyCheck canBuy(String itemId, int count, long gold, long room) {
        Optional<Ware> ware = ware(itemId);
        if (ware.isEmpty()) {
            return BuyCheck.UNKNOWN;
        }
        if (count < 1 || count > MAX_PER_PURCHASE) {
            return BuyCheck.BAD_COUNT;
        }
        if (total(ware.get().price(), count) > gold) {
            return BuyCheck.NOT_ENOUGH_GOLD;
        }
        if (room < count) {
            return BuyCheck.BAG_FULL;
        }
        return BuyCheck.OK;
    }

    /** The counter's amount after a press of − / + ({@code delta}) or a quick amount, kept within 1..{@code max}. */
    public static int amount(int current, int delta, int max) {
        int top = Math.max(1, Math.min(MAX_PER_PURCHASE, max));
        long next = (long) current + delta;
        return (int) Math.max(1L, Math.min(top, next));
    }

    /** What selling {@code count} of a stack worth {@code each} pays ({@code count} is clamped to the stack). */
    public static long sellTotal(long each, int count, int stackCount) {
        return total(Math.max(0L, each), Math.max(0, Math.min(count, stackCount)));
    }
}
