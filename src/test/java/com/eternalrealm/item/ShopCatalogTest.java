package com.eternalrealm.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The item shop's counter (2026-11-14: 「アイテムショップのHUDをファンタジー風にオシャレにして」). */
public class ShopCatalogTest {
    private static final Path LANG = Paths.get("src", "main", "resources", "assets", "eternalrealm", "lang");

    @Test
    public void theShelvesHoldEveryConsumableAndTheSmithsStonesInOrder() {
        Set<String> ids = new HashSet<>();
        for (ShopCatalog.Ware ware : ShopCatalog.wares()) {
            assertTrue("one ware per item: " + ware.itemId(), ids.add(ware.itemId()));
            assertTrue(ware.itemId() + " has a price", ware.price() > 0);
        }
        for (Consumable consumable : Consumable.values()) {
            ShopCatalog.Ware ware = ShopCatalog.ware(consumable.itemId()).orElseThrow();
            assertEquals(ItemPrices.consumableShopPrice(consumable), ware.price());
            assertEquals(consumable.requiredLevel(), ware.requiredLevel());
        }
        assertEquals(ItemPrices.ENHANCEMENT_STONE_SHOP_PRICE,
                ShopCatalog.ware(Materials.ENHANCEMENT_STONE).orElseThrow().price());
        assertEquals(ShopCatalog.Section.SMITHING, ShopCatalog.ware(Materials.REROLL_CRYSTAL).orElseThrow().section());
        assertEquals(ShopCatalog.Section.POTIONS,
                ShopCatalog.ware(Consumable.HEALTH_POTION_LARGE.itemId()).orElseThrow().section());
        // shelf by shelf, and inside a shelf in the consumables' own order (small, medium, large)
        List<ShopCatalog.Ware> wares = ShopCatalog.wares();
        for (int i = 1; i < wares.size(); i++) {
            assertTrue(wares.get(i - 1).section().ordinal() <= wares.get(i).section().ordinal());
        }
        assertEquals(Consumable.HEALTH_POTION_SMALL.itemId(), wares.get(0).itemId());
        assertEquals(Consumable.HEALTH_POTION_MEDIUM.itemId(), wares.get(1).itemId());
        assertTrue(ShopCatalog.ware("eternalrealm:moon_cheese").isEmpty());
    }

    @Test
    public void theCounterSaysNoWhenItShould() {
        String potion = Consumable.HEALTH_POTION_SMALL.itemId(); // 25 G
        assertEquals(ShopCatalog.BuyCheck.OK, ShopCatalog.canBuy(potion, 4, 100, 64));
        assertEquals(ShopCatalog.BuyCheck.NOT_ENOUGH_GOLD, ShopCatalog.canBuy(potion, 5, 100, 64));
        assertEquals(ShopCatalog.BuyCheck.BAG_FULL, ShopCatalog.canBuy(potion, 4, 100, 3));
        assertEquals(ShopCatalog.BuyCheck.BAD_COUNT, ShopCatalog.canBuy(potion, 0, 100, 64));
        assertEquals(ShopCatalog.BuyCheck.BAD_COUNT, ShopCatalog.canBuy(potion, ShopCatalog.MAX_PER_PURCHASE + 1,
                Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(ShopCatalog.BuyCheck.UNKNOWN, ShopCatalog.canBuy("minecraft:diamond", 1, 100, 64));
        // gold is checked before room: the player hears first what they can do something about
        assertEquals(ShopCatalog.BuyCheck.NOT_ENOUGH_GOLD, ShopCatalog.canBuy(potion, 10, 100, 0));
    }

    @Test
    public void amountsTotalsAndWhatOneCanAfford() {
        assertEquals(250L, ShopCatalog.total(25, 10));
        assertEquals(0L, ShopCatalog.total(25, 0));
        assertEquals(Long.MAX_VALUE, ShopCatalog.total(Long.MAX_VALUE / 2, 3));
        assertEquals(4, ShopCatalog.affordable(25, 100));
        assertEquals(0, ShopCatalog.affordable(25, 24));
        assertEquals(ShopCatalog.MAX_PER_PURCHASE, ShopCatalog.affordable(1, 1_000_000));
        // the counter stays between 1 and what may be bought or sold
        assertEquals(2, ShopCatalog.amount(1, 1, 10));
        assertEquals(1, ShopCatalog.amount(1, -1, 10));
        assertEquals(10, ShopCatalog.amount(5, 10, 10));
        assertEquals(1, ShopCatalog.amount(5, 10, 0));
        assertEquals(ShopCatalog.MAX_PER_PURCHASE, ShopCatalog.amount(98, 50, 1000));
        // selling: never more than the stack
        assertEquals(30L, ShopCatalog.sellTotal(6, 5, 5));
        assertEquals(30L, ShopCatalog.sellTotal(6, 9, 5));
        assertEquals(0L, ShopCatalog.sellTotal(-6, 5, 5));
        assertEquals(ShopCatalog.SellKind.EQUIPMENT, ShopCatalog.SellKind.byId("equipment").orElseThrow());
        assertFalse(ShopCatalog.SellKind.byId("relic").isPresent());
    }

    @Test
    public void everyTextOfTheShopIsTranslated() throws IOException {
        for (String file : List.of("ja_jp.json", "en_us.json")) {
            JsonObject lang = JsonParser.parseString(Files.readString(LANG.resolve(file), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (ShopCatalog.Section section : ShopCatalog.Section.values()) {
                assertTrue(file + " " + section, lang.has(section.translationKey()));
            }
            for (ShopCatalog.BuyCheck check : ShopCatalog.BuyCheck.values()) {
                assertTrue(file + " " + check, lang.has(check.translationKey()));
            }
            for (ShopCatalog.SellKind kind : ShopCatalog.SellKind.values()) {
                assertTrue(file + " " + kind, lang.has("screen.eternalrealm.shop.kind." + kind.id()));
            }
            for (String key : List.of("sign", "keeper", "greet.1", "greet.2", "greet.3", "greet.4", "thanks.1",
                    "thanks.2", "thanks.3", "bought_back.1", "bought_back.2", "bought_back.3", "purse", "tab.buy",
                    "tab.sell", "wares", "sellables", "from_level", "owned", "in_back", "each_buy", "each_sell",
                    "total_buy", "total_sell", "buy", "sell", "sell_confirm", "sell_confirm_hint", "pending", "choose",
                    "short", "max", "all", "empty", "nothing_to_sell", "hint", "buy_tip", "sell_tip", "title")) {
                assertTrue(file + " screen.eternalrealm.shop." + key, lang.has("screen.eternalrealm.shop." + key));
            }
            for (String key : List.of("bought", "sold", "cannot_sell", "sell.moved")) {
                assertTrue(file + " message.eternalrealm.shop." + key, lang.has("message.eternalrealm.shop." + key));
            }
        }
    }
}
