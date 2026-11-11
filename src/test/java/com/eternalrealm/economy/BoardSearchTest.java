package com.eternalrealm.economy;

import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The trading board's filters and sorts, and the price history (相場). */
public class BoardSearchTest {
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long NOW = 1_000_000L;

    private static ItemInfo item(String id, int count, String rarity, String category, int level, boolean gold,
                                 String words) {
        return new ItemInfo(id, count, rarity, category, level, gold, "item:" + id + ":" + rarity, words);
    }

    private static AuctionHouse board() {
        AuctionHouse house = new AuctionHouse(AuctionRules.defaults(), new SplittableRandom(1L),
                MarketData.empty(SeasonId.of("s1")));
        list(house, item("eternalrealm:iron_longsword", 1, "common", "weapon", 1, false, "鉄の長剣 iron longsword"), 100);
        list(house, item("eternalrealm:abyss_greatsword", 1, "legendary", "weapon", 55, true,
                "終焉の大剣 abyss greatsword"), 2_000_000);
        list(house, item("eternalrealm:beast_hide", 32, "common", "material", 0, false, "獣の皮 beast hide"), 320);
        list(house, item("eternalrealm:beast_hide", 4, "common", "material", 0, false, "獣の皮 beast hide"), 60);
        list(house, item("eternalrealm:frost_chestplate", 1, "epic", "armor", 30, false, "氷晶の胴 frost chestplate"),
                50_000);
        return house;
    }

    private static void list(AuctionHouse house, ItemInfo item, long price) {
        assertTrue(house.list(SELLER, "seller", "{}", item, price, Wallet.of(10_000_000), NOW).isSuccess());
    }

    private static List<String> ids(Page<Listing> page) {
        return page.items().stream().map(listing -> listing.item().itemId() + "x" + listing.item().count()).toList();
    }

    @Test
    public void filtersByKindTextRarityLevelAndGold() {
        AuctionHouse house = board();
        assertEquals(2, house.search(new MarketQuery("", "weapon", -1, 0, 999, false, MarketQuery.Sort.NEWEST, 0, 10),
                NOW).totalItems());
        assertEquals("Japanese words match", 2, house.search(new MarketQuery("獣の皮", MarketQuery.Sort.NEWEST, 0, 10),
                NOW).totalItems());
        assertEquals("every word must match", 1, house.search(new MarketQuery("大剣 abyss", MarketQuery.Sort.NEWEST,
                0, 10), NOW).totalItems());
        assertEquals("epic or better", 2, house.search(new MarketQuery("", "", 3, 0, 999, false,
                MarketQuery.Sort.NEWEST, 0, 10), NOW).totalItems());
        assertEquals("level 20-40", 1, house.search(new MarketQuery("", "", -1, 20, 40, false,
                MarketQuery.Sort.NEWEST, 0, 10), NOW).totalItems());
        assertEquals("gold frames only", List.of("eternalrealm:abyss_greatswordx1"), ids(house.search(
                new MarketQuery("", "", -1, 0, 999, true, MarketQuery.Sort.NEWEST, 0, 10), NOW)));
    }

    @Test
    public void sortsByUnitPriceAndLevel() {
        AuctionHouse house = board();
        List<String> cheapest = ids(house.search(new MarketQuery("", "material", -1, 0, 999, false,
                MarketQuery.Sort.UNIT_PRICE_ASCENDING, 0, 10), NOW));
        assertEquals("10 G a piece before 15 G a piece",
                List.of("eternalrealm:beast_hidex32", "eternalrealm:beast_hidex4"), cheapest);
        assertEquals("eternalrealm:abyss_greatswordx1", ids(house.search(MarketQuery.all(
                MarketQuery.Sort.LEVEL_DESCENDING, 10), NOW)).get(0));
        assertEquals("the highest price is fine on the board", 2_000_000L, house.search(MarketQuery.all(
                MarketQuery.Sort.PRICE_DESCENDING, 1), NOW).items().get(0).price());
    }

    @Test
    public void priceHistoryKeepsTheLatestSalesPerGroup() {
        PriceHistory history = PriceHistory.empty();
        history.record("item:a", 300, 3, 1, false);
        history.record("item:a", 500, 1, 2, true);
        history.record("item:a", 200, 2, 3, false);
        PriceHistory.Stats stats = history.stats("item:a").orElseThrow();
        assertEquals(3, stats.sales());
        assertEquals(100L, stats.lowest());
        assertEquals(500L, stats.highest());
        assertEquals(100L, stats.median());
        assertEquals(100L, stats.last());
        assertTrue(history.sales("item:a").get(1).auction());
        for (int i = 0; i < 30; i++) {
            history.record("item:a", i, 1, 10 + i, false);
        }
        assertEquals(PriceHistory.SALES_KEPT, history.sales("item:a").size());
        assertFalse(history.stats("item:b").isPresent());
        for (int i = 0; i < PriceHistory.GROUPS_KEPT + 5; i++) {
            history.record("item:g" + i, 10, 1, 100 + i, false);
        }
        Map<String, List<PriceHistory.Sale>> kept = history.snapshot();
        assertEquals(PriceHistory.GROUPS_KEPT, kept.size());
        assertFalse("the oldest groups go first", kept.containsKey("item:a"));
        assertEquals(kept, new PriceHistory(kept).snapshot());
    }
}
