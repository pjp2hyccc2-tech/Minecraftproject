package com.eternalrealm.economy;

import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AuctionHouseTest {
    private static final SeasonId S1 = SeasonId.of("s1");
    private static final long NOW = 1_794_096_000_000L; // 2026-11-08T00:00:00Z
    private static final long HOUR = 3_600_000L;
    private static final long LISTING_TIME = 48 * HOUR;
    private static final UUID SELLER = new UUID(8L, 1L);
    private static final UUID BUYER = new UUID(8L, 2L);
    private static final UUID OTHER = new UUID(8L, 3L);
    private static final ItemInfo SWORD = new ItemInfo("eternalrealm:iron_longsword", 1, "rare");
    private static final ItemInfo DIAMONDS = new ItemInfo("minecraft:diamond", 16, "common");
    private static final Wallet RICH = Wallet.of(1_000_000);

    private AuctionHouse house = new AuctionHouse(AuctionRules.defaults(), new SplittableRandom(5L), MarketData.empty(S1));

    private Listing list(UUID seller, ItemInfo item, long price, long now) {
        ListingResult result = house.list(seller, "Seller", "{id:\"" + item.itemId() + "\",n:" + now + "}", item, price,
                RICH, now);
        assertEquals(AuctionOutcome.OK, result.outcome());
        return result.listing().orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ listing

    @Test
    public void listingChargesTheFeeAndHoldsTheItem() {
        ListingResult result = house.list(SELLER, "Alex", "{sword}", SWORD, 1_000, Wallet.of(100), NOW);
        assertEquals(AuctionOutcome.OK, result.outcome());
        assertEquals(20, result.fee());
        assertEquals(Wallet.of(80), result.wallet());
        Listing listing = result.listing().orElseThrow();
        assertEquals(SELLER, listing.sellerId());
        assertEquals("Alex", listing.sellerName());
        assertEquals("{sword}", listing.itemData());
        assertEquals(SWORD, listing.item());
        assertEquals(1_000, listing.price());
        assertEquals(NOW, listing.createdAtMillis());
        assertEquals(NOW + LISTING_TIME, listing.expiresAtMillis());
        assertEquals(4, listing.id().version());
        assertEquals(Optional.of(listing), house.listing(listing.id()));
        assertEquals(List.of(listing), house.listingsOf(SELLER));
        assertEquals(1, house.activeListings(SELLER));
        assertEquals(1, house.listingCount());
        assertTrue(house.isDirty());
    }

    @Test
    public void theListingFeeIsTwoPercentButAtLeastOne() {
        assertEquals(1, house.listingFee(1));
        assertEquals(1, house.listingFee(99));
        assertEquals(2, house.listingFee(100));
        assertEquals("2 % up to the cap", 100_000, house.listingFee(5_000_000));
        assertEquals("expensive pieces pay at most the cap", AuctionRules.MAX_LISTING_FEE,
                house.listingFee(Listing.MAX_PRICE));
        assertThrows(IllegalArgumentException.class, () -> house.listingFee(0));
        AuctionRules expensive = new AuctionRules(Duration.ofHours(1), 0, 100, 0, 1);
        assertEquals("the minimum may exceed a low price", 100, expensive.listingFee(10));
    }

    @Test
    public void listingRulesAreCheckedInTheDocumentedOrder() {
        for (int i = 0; i < 10; i++) {
            list(SELLER, DIAMONDS, 100 + i, NOW);
        }
        long revision = house.revision();
        assertEquals(AuctionOutcome.INVALID_PRICE, house.checkListing(SELLER, 0, Wallet.EMPTY));
        assertEquals(AuctionOutcome.INVALID_PRICE, house.checkListing(OTHER, Listing.MAX_PRICE + 1, RICH));
        assertEquals(AuctionOutcome.TOO_MANY_LISTINGS, house.checkListing(SELLER, 100, Wallet.EMPTY));
        assertEquals(AuctionOutcome.INSUFFICIENT_FUNDS, house.checkListing(OTHER, 100, Wallet.EMPTY));
        assertEquals(AuctionOutcome.OK, house.checkListing(OTHER, 100, Wallet.of(2)));

        ListingResult refused = house.list(SELLER, "Seller", "{x}", SWORD, 100, RICH, NOW);
        assertEquals(AuctionOutcome.TOO_MANY_LISTINGS, refused.outcome());
        assertFalse(refused.listing().isPresent());
        assertEquals(0, refused.fee());
        assertEquals(RICH, refused.wallet());
        assertEquals(AuctionOutcome.INSUFFICIENT_FUNDS,
                house.list(OTHER, "Other", "{x}", SWORD, 100, Wallet.of(1), NOW).outcome());
        assertEquals("failures change nothing", revision, house.revision());
        assertEquals(10, house.activeListings(SELLER));

        house.buy(BUYER, house.listingsOf(SELLER).get(0).id(), RICH, NOW);
        assertEquals(9, house.activeListings(SELLER));
        assertEquals(AuctionOutcome.OK, house.checkListing(SELLER, 100, RICH));
    }

    @Test
    public void invalidItemDataIsAProgrammingError() {
        assertThrows(IllegalArgumentException.class, () -> house.list(SELLER, "S", "", SWORD, 10, RICH, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> house.list(SELLER, "S", "x".repeat(Listing.MAX_ITEM_DATA_LENGTH + 1), SWORD, 10, RICH, NOW));
        assertThrows(IllegalArgumentException.class, () -> house.list(SELLER, "", "{x}", SWORD, 10, RICH, NOW));
        assertEquals("nothing was listed", 0, house.listingCount());
    }

    // ------------------------------------------------------------------------------------------------ buying

    @Test
    public void buyingPaysTheSellerThePriceMinusTax() {
        Listing listing = list(SELLER, SWORD, 1_000, NOW);
        SaleResult sale = house.buy(BUYER, listing.id(), Wallet.of(1_500), NOW + HOUR);
        assertEquals(AuctionOutcome.OK, sale.outcome());
        assertEquals(Optional.of(listing), sale.listing());
        assertEquals(1_000, sale.price());
        assertEquals(50, sale.tax());
        assertEquals(950, sale.sellerProceeds());
        assertEquals(Wallet.of(500), sale.wallet());
        assertFalse(house.listing(listing.id()).isPresent());
        assertEquals(0, house.activeListings(SELLER));
        assertEquals(950, house.mailbox(SELLER).gold());
        assertTrue(house.mailbox(BUYER).isEmpty());
        assertEquals("an item is sold once", AuctionOutcome.NOT_FOUND,
                house.buy(OTHER, listing.id(), RICH, NOW + HOUR).outcome());
        assertEquals(950, house.mailbox(SELLER).gold());
    }

    @Test
    public void buyingRulesAreCheckedInTheDocumentedOrder() {
        Listing listing = list(SELLER, SWORD, 1_000, NOW);
        assertEquals(AuctionOutcome.NOT_FOUND, house.buy(BUYER, new UUID(0L, 1L), RICH, NOW).outcome());
        assertEquals(AuctionOutcome.OWN_LISTING, house.buy(SELLER, listing.id(), RICH, NOW + LISTING_TIME).outcome());
        assertEquals(AuctionOutcome.EXPIRED, house.buy(BUYER, listing.id(), Wallet.EMPTY, NOW + LISTING_TIME).outcome());
        SaleResult poor = house.buy(BUYER, listing.id(), Wallet.of(999), NOW);
        assertEquals(AuctionOutcome.INSUFFICIENT_FUNDS, poor.outcome());
        assertEquals(Wallet.of(999), poor.wallet());
        assertEquals(0, poor.price());
        assertTrue("the listing stays", house.listing(listing.id()).isPresent());
        assertEquals(AuctionOutcome.OK, house.buy(BUYER, listing.id(), Wallet.of(1_000), NOW + LISTING_TIME - 1).outcome());
    }

    @Test
    public void cheapSalesPayNoTax() {
        Listing listing = list(SELLER, DIAMONDS, 19, NOW);
        assertEquals(0, house.buy(BUYER, listing.id(), RICH, NOW).tax());
        assertEquals(19, house.mailbox(SELLER).gold());
    }

    // ------------------------------------------------------------------------------------------------ cancel & expiry

    @Test
    public void cancellingReturnsTheItemToTheSellersMailbox() {
        Listing listing = list(SELLER, SWORD, 1_000, NOW);
        assertEquals(AuctionOutcome.NOT_OWNER, house.cancel(OTHER, listing.id(), NOW).outcome());
        assertEquals(AuctionOutcome.NOT_FOUND, house.cancel(SELLER, new UUID(0L, 1L), NOW).outcome());

        CancelResult cancelled = house.cancel(SELLER, listing.id(), NOW + 5);
        assertEquals(Optional.of(listing), cancelled.listing());
        assertFalse(house.listing(listing.id()).isPresent());
        Mailbox mailbox = house.mailbox(SELLER);
        assertEquals("the fee is not refunded", 0, mailbox.gold());
        assertEquals(1, mailbox.items().size());
        MailItem item = mailbox.items().get(0);
        assertEquals(listing.itemData(), item.itemData());
        assertEquals(SWORD, item.item());
        assertEquals(MailReason.AUCTION_CANCELLED, item.reason());
        assertEquals(NOW + 5, item.receivedAtMillis());
        assertEquals(AuctionOutcome.NOT_FOUND, house.cancel(SELLER, listing.id(), NOW).outcome());
    }

    @Test
    public void expiredListingsGoBackToTheirSellers() {
        Listing first = list(SELLER, SWORD, 1_000, NOW);
        Listing second = list(OTHER, DIAMONDS, 300, NOW + HOUR);
        assertTrue(house.expireListings(NOW + LISTING_TIME - 1).isEmpty());
        assertEquals(List.of(first), house.expireListings(NOW + LISTING_TIME));
        assertTrue(house.expireListings(NOW + LISTING_TIME).isEmpty());
        assertEquals(MailReason.AUCTION_EXPIRED, house.mailbox(SELLER).items().get(0).reason());
        assertEquals(0, house.activeListings(SELLER));
        assertEquals(List.of(second), house.expireListings(NOW + 100 * HOUR));
        assertEquals(1, house.mailbox(OTHER).items().size());
        assertEquals(0, house.listingCount());
    }

    // ------------------------------------------------------------------------------------------------ search

    @Test
    public void searchFiltersSortsAndPages() {
        Listing longsword = list(SELLER, SWORD, 1_000, NOW);
        Listing diamonds = list(SELLER, DIAMONDS, 300, NOW + 1);
        Listing diamondSword = list(OTHER, new ItemInfo("minecraft:diamond_sword", 1, "epic"), 300, NOW + 2);
        Listing goldenSword = list(OTHER, new ItemInfo("minecraft:golden_sword", 1, "common"), 5_000, NOW + 3);

        assertEquals(List.of(diamondSword, longsword, goldenSword),
                house.search(new MarketQuery("SWORD", MarketQuery.Sort.PRICE_ASCENDING, 0, 10), NOW + 10).items());
        assertEquals("equal prices: older first", List.of(diamonds, diamondSword, longsword, goldenSword),
                house.search(MarketQuery.all(MarketQuery.Sort.PRICE_ASCENDING, 10), NOW + 10).items());
        assertEquals(List.of(goldenSword, longsword, diamonds, diamondSword),
                house.search(MarketQuery.all(MarketQuery.Sort.PRICE_DESCENDING, 10), NOW + 10).items());
        assertEquals(List.of(goldenSword, diamondSword, diamonds, longsword),
                house.search(MarketQuery.all(MarketQuery.Sort.NEWEST, 10), NOW + 10).items());

        Page<Listing> first = house.search(new MarketQuery("", MarketQuery.Sort.NEWEST, 0, 3), NOW + 10);
        assertEquals(3, first.items().size());
        assertEquals(4, first.totalItems());
        assertEquals(2, first.totalPages());
        assertTrue(first.hasNext());
        Page<Listing> second = house.search(new MarketQuery("", MarketQuery.Sort.NEWEST, 1, 3), NOW + 10);
        assertEquals(List.of(longsword), second.items());
        assertFalse(second.hasNext());
        Page<Listing> beyond = house.search(new MarketQuery("", MarketQuery.Sort.NEWEST, 7, 3), NOW + 10);
        assertTrue(beyond.items().isEmpty());
        assertEquals(4, beyond.totalItems());
        assertTrue(house.search(new MarketQuery("netherite", MarketQuery.Sort.NEWEST, 0, 3), NOW).items().isEmpty());

        assertEquals("expired listings are hidden before they are returned", List.of(goldenSword),
                house.search(MarketQuery.all(MarketQuery.Sort.NEWEST, 10), NOW + LISTING_TIME + 2).items());
        assertEquals(2, house.listingsOf(SELLER).size());
    }

    // ------------------------------------------------------------------------------------------------ mail

    @Test
    public void goldClaimsStopAtTheWalletLimit() {
        house.buy(BUYER, list(SELLER, SWORD, 1_000, NOW).id(), RICH, NOW);
        house.buy(BUYER, list(SELLER, SWORD, 2_000, NOW).id(), RICH, NOW);
        assertEquals(2_850, house.mailbox(SELLER).gold());

        MailClaim partial = house.claimGold(SELLER, Wallet.of(Wallet.MAX_BALANCE - 100));
        assertEquals(100, partial.gold());
        assertEquals(Wallet.MAX_BALANCE, partial.wallet().balance());
        assertTrue(partial.items().isEmpty());
        assertEquals(2_750, house.mailbox(SELLER).gold());

        long revision = house.revision();
        MailClaim nothing = house.claimGold(SELLER, Wallet.of(Wallet.MAX_BALANCE));
        assertTrue(nothing.isEmpty());
        assertEquals("an empty claim changes nothing", revision, house.revision());

        MailClaim rest = house.claimAll(SELLER, Wallet.EMPTY, 5);
        assertEquals(2_750, rest.gold());
        assertEquals(Wallet.of(2_750), rest.wallet());
        assertTrue(house.mailbox(SELLER).isEmpty());
        assertTrue("empty mailboxes are not stored", house.snapshot().mailboxes().isEmpty());
    }

    @Test
    public void itemClaimsTakeOneOrTheOldestFirst() {
        List<Listing> listings = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            listings.add(list(SELLER, DIAMONDS, 100, NOW + i));
        }
        for (Listing listing : listings) {
            house.cancel(SELLER, listing.id(), NOW + 10);
        }
        List<MailItem> mail = house.mailbox(SELLER).items();
        assertEquals(3, mail.size());

        assertFalse(house.claimItem(SELLER, new UUID(0L, 9L)).isPresent());
        assertFalse(house.claimItem(BUYER, mail.get(1).id()).isPresent());
        assertEquals(Optional.of(mail.get(1)), house.claimItem(SELLER, mail.get(1).id()));
        assertFalse("claimed once", house.claimItem(SELLER, mail.get(1).id()).isPresent());

        MailClaim claim = house.claimAll(SELLER, RICH, 1);
        assertEquals(List.of(mail.get(0)), claim.items());
        assertEquals(0, claim.gold());
        assertEquals(RICH, claim.wallet());
        assertEquals(List.of(mail.get(2)), house.mailbox(SELLER).items());
        assertTrue(house.claimAll(SELLER, RICH, 0).isEmpty());
        assertEquals(List.of(mail.get(2)), house.claimAll(SELLER, RICH, 10).items());
        assertTrue(house.mailbox(SELLER).isEmpty());
        assertTrue(house.claimAll(OTHER, RICH, 10).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> house.claimAll(SELLER, RICH, -1));
    }

    @Test
    public void otherSystemsCanMailGoldAndItems() {
        house.sendGold(BUYER, 500, "quest reward");
        MailItem item = house.sendItem(BUYER, "{diamonds}", DIAMONDS, MailReason.AUCTION_PURCHASE, NOW);
        Mailbox mailbox = house.mailbox(BUYER);
        assertEquals(500, mailbox.gold());
        assertEquals(List.of(item), mailbox.items());
        assertEquals(MailReason.AUCTION_PURCHASE, item.reason());
        assertThrows(IllegalArgumentException.class, () -> house.sendGold(BUYER, 0, "nothing"));
        assertThrows(IllegalArgumentException.class, () -> house.sendItem(BUYER, "", DIAMONDS, MailReason.SYSTEM, NOW));
    }

    // ------------------------------------------------------------------------------------------------ persistence

    @Test
    public void aSnapshotRestoresTheSameMarket() {
        Listing kept = list(SELLER, SWORD, 1_000, NOW);
        house.buy(BUYER, list(OTHER, DIAMONDS, 300, NOW).id(), RICH, NOW);
        house.cancel(SELLER, list(SELLER, DIAMONDS, 50, NOW).id(), NOW);
        MarketData snapshot = house.snapshot();
        assertEquals(S1, snapshot.season());
        assertEquals(List.of(kept), snapshot.listings());
        assertEquals(2, snapshot.mailboxes().size());

        AuctionHouse restored = new AuctionHouse(AuctionRules.defaults(), new SplittableRandom(6L), snapshot);
        assertEquals(snapshot, restored.snapshot());
        assertFalse(restored.isDirty());
        assertEquals(1, restored.activeListings(SELLER));
        assertEquals(house.mailbox(OTHER), restored.mailbox(OTHER));
        assertEquals(AuctionOutcome.OK, restored.buy(BUYER, kept.id(), RICH, NOW).outcome());
        MailItem mail = restored.mailbox(SELLER).items().get(0);
        assertEquals(Optional.of(mail), restored.claimItem(SELLER, mail.id()));

        MarketData withEmptyMailbox = new MarketData(S1, List.of(), List.of(Mailbox.empty(BUYER)));
        assertTrue(new AuctionHouse(AuctionRules.defaults(), new SplittableRandom(1L), withEmptyMailbox)
                .snapshot().mailboxes().isEmpty());
    }

    @Test
    public void onlyChangesMakeTheMarketDirty() {
        assertFalse(house.isDirty());
        house.search(MarketQuery.all(MarketQuery.Sort.NEWEST, 10), NOW);
        house.expireListings(NOW);
        house.claimAll(SELLER, RICH, 5);
        assertFalse(house.isDirty());
        list(SELLER, SWORD, 10, NOW);
        assertTrue(house.isDirty());
        long revision = house.revision();
        house.markSaved(revision);
        assertFalse(house.isDirty());
        house.markSaved(revision - 1);
        assertFalse(house.isDirty());
        house.expireListings(NOW + LISTING_TIME);
        assertTrue(house.isDirty());
    }

    @Test
    public void listingIdsComeFromTheInjectedRandomGenerator() {
        AuctionHouse a = new AuctionHouse(AuctionRules.defaults(), new SplittableRandom(9L), MarketData.empty(S1));
        AuctionHouse b = new AuctionHouse(AuctionRules.defaults(), new SplittableRandom(9L), MarketData.empty(S1));
        UUID first = a.list(SELLER, "S", "{x}", SWORD, 10, RICH, NOW).listing().orElseThrow().id();
        assertEquals(first, b.list(SELLER, "S", "{x}", SWORD, 10, RICH, NOW).listing().orElseThrow().id());
        assertNotEquals(first, a.list(SELLER, "S", "{x}", SWORD, 10, RICH, NOW).listing().orElseThrow().id());
    }

    @Test
    public void rulesCanBeReloaded() {
        AuctionRules strict = new AuctionRules(Duration.ofHours(1), 1_000, 5, 1_000, 1);
        house.setRules(strict);
        assertEquals(strict, house.rules());
        ListingResult result = house.list(SELLER, "S", "{x}", SWORD, 1_000, RICH, NOW);
        assertEquals(100, result.fee());
        assertEquals(NOW + HOUR, result.listing().orElseThrow().expiresAtMillis());
        assertEquals(AuctionOutcome.TOO_MANY_LISTINGS, house.checkListing(SELLER, 1_000, RICH));
        assertEquals(100, house.buy(BUYER, result.listing().orElseThrow().id(), RICH, NOW).tax());
    }

    // ------------------------------------------------------------------------------------------------ conservation

    @Test
    public void randomTradingNeverCreatesOrDestroysGoldOrItems() {
        AuctionHouse market = new AuctionHouse(new AuctionRules(Duration.ofMinutes(10), 200, 1, 500, 3),
                new SplittableRandom(11L), MarketData.empty(S1));
        Random rng = new Random(20261108L);
        List<UUID> players = new ArrayList<>();
        Map<UUID, Wallet> wallets = new HashMap<>();
        Map<UUID, List<String>> inventories = new HashMap<>();
        Set<String> allItems = new HashSet<>();
        for (int p = 0; p < 6; p++) {
            UUID player = new UUID(9L, p);
            players.add(player);
            wallets.put(player, Wallet.of(10_000));
            List<String> inventory = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                inventory.add("item-" + p + "-" + i);
            }
            inventories.put(player, inventory);
            allItems.addAll(inventory);
        }
        long totalGold = 60_000;
        long destroyed = 0;
        long now = NOW;
        int sales = 0;
        int claims = 0;
        for (int step = 0; step < 5_000; step++) {
            now += rng.nextInt(60_000);
            UUID player = players.get(rng.nextInt(players.size()));
            Wallet wallet = wallets.get(player);
            List<String> inventory = inventories.get(player);
            switch (rng.nextInt(6)) {
                case 0, 1 -> {
                    if (!inventory.isEmpty()) {
                        String item = inventory.get(rng.nextInt(inventory.size()));
                        ListingResult result = market.list(player, "P", item, DIAMONDS, 1 + rng.nextInt(3_000), wallet, now);
                        if (result.isSuccess()) {
                            inventory.remove(item);
                            wallets.put(player, result.wallet());
                            destroyed += result.fee();
                        }
                    }
                }
                case 2, 3 -> {
                    UUID seller = players.get(rng.nextInt(players.size()));
                    List<Listing> offers = market.listingsOf(seller);
                    if (!offers.isEmpty()) {
                        SaleResult result = market.buy(player, offers.get(rng.nextInt(offers.size())).id(), wallet, now);
                        if (result.isSuccess()) {
                            wallets.put(player, result.wallet());
                            inventory.add(result.listing().orElseThrow().itemData());
                            destroyed += result.tax();
                            sales++;
                        }
                    }
                }
                case 4 -> {
                    List<Listing> own = market.listingsOf(player);
                    if (!own.isEmpty()) {
                        market.cancel(player, own.get(rng.nextInt(own.size())).id(), now);
                    }
                }
                default -> {
                    MailClaim claim = market.claimAll(player, wallet, rng.nextInt(3));
                    wallets.put(player, claim.wallet());
                    for (MailItem item : claim.items()) {
                        inventory.add(item.itemData());
                    }
                    if (!claim.isEmpty()) {
                        claims++;
                    }
                }
            }
            if (step % 10 == 0) {
                market.expireListings(now);
            }
            assertConserved(market, players, wallets, inventories, allItems, totalGold - destroyed);
        }
        assertTrue("the run must actually trade: " + sales + " sales, " + claims + " claims", sales > 100 && claims > 100);
    }

    private static void assertConserved(AuctionHouse market, List<UUID> players, Map<UUID, Wallet> wallets,
                                        Map<UUID, List<String>> inventories, Set<String> allItems, long expectedGold) {
        long gold = 0;
        List<String> items = new ArrayList<>();
        for (UUID player : players) {
            gold += wallets.get(player).balance() + market.mailbox(player).gold();
            items.addAll(inventories.get(player));
            for (MailItem item : market.mailbox(player).items()) {
                items.add(item.itemData());
            }
            for (Listing listing : market.listingsOf(player)) {
                items.add(listing.itemData());
            }
        }
        assertEquals("gold is only destroyed by fees and taxes", expectedGold, gold);
        assertEquals("no item duplicated", items.size(), new HashSet<>(items).size());
        assertEquals("no item lost", allItems, new HashSet<>(items));
    }
}
