package com.eternalrealm.economy;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ShopCatalogTest {
    private static final ShopEntry BREAD = ShopEntry.unlimited("bread", "minecraft:bread", 5);
    private static final ShopEntry POTION = ShopEntry.limited("potion", "eternalrealm:healing_potion", 120, 3);
    private static final ShopEntry STARTER = ShopEntry.limited("starter_kit", "eternalrealm:starter_kit", 0, 1);
    private static final ShopCatalog GENERAL_STORE = new ShopCatalog("arcelia_general_store", List.of(BREAD, POTION, STARTER));

    @Test
    public void purchaseChargesPriceTimesQuantity() {
        ShopPurchase purchase = GENERAL_STORE.purchase("bread", 10, Wallet.of(100), 0);
        assertEquals(ShopOutcome.OK, purchase.outcome());
        assertEquals(Optional.of(BREAD), purchase.entry());
        assertEquals(10, purchase.quantity());
        assertEquals(50, purchase.cost());
        assertEquals(Wallet.of(50), purchase.wallet());
        assertEquals("unlimited wares ignore the counter", ShopOutcome.OK,
                GENERAL_STORE.purchase("bread", 1, Wallet.of(5), 1_000_000).outcome());
    }

    @Test
    public void dailyStockIsPerPlayerAndDay() {
        assertEquals(ShopOutcome.OK, GENERAL_STORE.purchase("potion", 3, Wallet.of(1_000), 0).outcome());
        assertEquals(ShopOutcome.OK, GENERAL_STORE.purchase("potion", 1, Wallet.of(1_000), 2).outcome());
        ShopPurchase tooMany = GENERAL_STORE.purchase("potion", 2, Wallet.of(1_000), 2);
        assertEquals(ShopOutcome.DAILY_LIMIT_REACHED, tooMany.outcome());
        assertEquals(0, tooMany.cost());
        assertEquals(Wallet.of(1_000), tooMany.wallet());
        assertEquals(ShopOutcome.DAILY_LIMIT_REACHED, GENERAL_STORE.purchase("potion", 1, Wallet.of(1_000), 3).outcome());
        assertEquals(OptionalInt.of(1), POTION.remainingToday(2));
        assertEquals(OptionalInt.of(0), POTION.remainingToday(7));
        assertEquals(OptionalInt.empty(), BREAD.remainingToday(7));
        assertThrows(IllegalArgumentException.class, () -> POTION.remainingToday(-1));
    }

    @Test
    public void freeWaresCostNothing() {
        ShopPurchase kit = GENERAL_STORE.purchase("starter_kit", 1, Wallet.EMPTY, 0);
        assertEquals(ShopOutcome.OK, kit.outcome());
        assertEquals(0, kit.cost());
        assertEquals(ShopOutcome.DAILY_LIMIT_REACHED, GENERAL_STORE.purchase("starter_kit", 1, Wallet.EMPTY, 1).outcome());
    }

    @Test
    public void failuresAreCheckedInTheDocumentedOrder() {
        assertEquals(ShopOutcome.UNKNOWN_ENTRY, GENERAL_STORE.purchase("sword", 0, Wallet.EMPTY, 0).outcome());
        assertFalse(GENERAL_STORE.purchase("sword", 1, Wallet.EMPTY, 0).entry().isPresent());
        assertEquals(ShopOutcome.INVALID_QUANTITY, GENERAL_STORE.purchase("potion", 0, Wallet.EMPTY, 99).outcome());
        assertEquals(ShopOutcome.INVALID_QUANTITY,
                GENERAL_STORE.purchase("bread", ShopCatalog.MAX_QUANTITY + 1, Wallet.EMPTY, 0).outcome());
        assertEquals(ShopOutcome.DAILY_LIMIT_REACHED, GENERAL_STORE.purchase("potion", 4, Wallet.EMPTY, 0).outcome());
        ShopPurchase poor = GENERAL_STORE.purchase("potion", 3, Wallet.of(359), 0);
        assertEquals(ShopOutcome.INSUFFICIENT_FUNDS, poor.outcome());
        assertEquals(Wallet.of(359), poor.wallet());
        assertEquals(ShopOutcome.OK, GENERAL_STORE.purchase("potion", 3, Wallet.of(360), 0).outcome());
        assertThrows(IllegalArgumentException.class, () -> GENERAL_STORE.purchase("bread", 1, Wallet.EMPTY, -1));
        assertThrows(NullPointerException.class, () -> GENERAL_STORE.purchase(null, 1, Wallet.EMPTY, 0));
    }

    @Test
    public void theLargestPurchaseCannotOverflow() {
        ShopCatalog luxury = new ShopCatalog("luxury", List.of(ShopEntry.unlimited("crown", "minecraft:gold_block",
                Wallet.MAX_BALANCE)));
        ShopPurchase purchase = luxury.purchase("crown", ShopCatalog.MAX_QUANTITY, Wallet.of(Wallet.MAX_BALANCE), 0);
        assertEquals(ShopOutcome.INSUFFICIENT_FUNDS, purchase.outcome());
        assertEquals(ShopOutcome.OK, luxury.purchase("crown", 1, Wallet.of(Wallet.MAX_BALANCE), 0).outcome());
    }

    @Test
    public void catalogAndEntriesAreValidated() {
        assertEquals("arcelia_general_store", GENERAL_STORE.id());
        assertEquals(List.of(BREAD, POTION, STARTER), GENERAL_STORE.entries());
        assertEquals(Optional.of(POTION), GENERAL_STORE.entry("potion"));
        assertTrue(new ShopCatalog("empty", List.of()).entries().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ShopCatalog("store", List.of(BREAD, BREAD)));
        assertThrows(IllegalArgumentException.class, () -> new ShopCatalog("Store", List.of(BREAD)));
        assertThrows(IllegalArgumentException.class, () -> ShopEntry.unlimited("Bread", "minecraft:bread", 5));
        assertThrows(IllegalArgumentException.class, () -> ShopEntry.unlimited("bread", "bread", 5));
        assertThrows(IllegalArgumentException.class, () -> ShopEntry.unlimited("bread", "minecraft:Bread", 5));
        assertThrows(IllegalArgumentException.class, () -> ShopEntry.unlimited("bread", "minecraft:bread", -1));
        assertThrows(IllegalArgumentException.class,
                () -> ShopEntry.unlimited("bread", "minecraft:bread", Wallet.MAX_BALANCE + 1));
        assertThrows(IllegalArgumentException.class, () -> ShopEntry.limited("bread", "minecraft:bread", 5, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ShopEntry.limited("bread", "minecraft:bread", 5, ShopEntry.MAX_DAILY_STOCK + 1));
        assertThrows(IllegalArgumentException.class, () -> new ShopPurchase(ShopOutcome.OK, Optional.empty(), 1, 0,
                Wallet.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new ShopPurchase(ShopOutcome.INSUFFICIENT_FUNDS,
                Optional.of(BREAD), 1, 5, Wallet.EMPTY));
        assertEquals(Optional.of("message.eternalrealm.shop.error.daily_limit_reached"),
                ShopOutcome.DAILY_LIMIT_REACHED.messageKey());
    }
}
