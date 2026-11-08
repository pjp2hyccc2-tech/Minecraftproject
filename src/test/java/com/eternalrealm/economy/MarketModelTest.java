package com.eternalrealm.economy;

import com.eternalrealm.season.SeasonId;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MarketModelTest {
    private static final UUID ID = new UUID(5L, 1L);
    private static final UUID SELLER = new UUID(5L, 2L);
    private static final ItemInfo ITEM = new ItemInfo("minecraft:diamond", 3, "common");
    private static final SeasonId S1 = SeasonId.of("s1");

    private static Listing listing(UUID id) {
        return new Listing(id, SELLER, "Alex", "{diamond}", ITEM, 100, 1_000, 2_000);
    }

    private static MailItem mail(UUID id) {
        return new MailItem(id, "{diamond}", ITEM, MailReason.AUCTION_EXPIRED, 1_000);
    }

    @Test
    public void itemInfoIsValidated() {
        assertTrue(ItemInfo.isValidItemId("eternalrealm:weapons/iron_longsword"));
        for (String invalid : List.of("diamond", "Minecraft:diamond", "minecraft:", ":diamond", "a:b c", "a:b:c")) {
            assertFalse(invalid, ItemInfo.isValidItemId(invalid));
        }
        assertFalse(ItemInfo.isValidItemId("a:" + "b".repeat(ItemInfo.MAX_ITEM_ID_LENGTH)));
        assertFalse(ItemInfo.isValidItemId(null));
        assertThrows(IllegalArgumentException.class, () -> new ItemInfo("minecraft:diamond", 0, "common"));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemInfo("minecraft:diamond", ItemInfo.MAX_COUNT + 1, "common"));
        assertThrows(IllegalArgumentException.class, () -> new ItemInfo("minecraft:diamond", 1, "Rare"));
        assertThrows(IllegalArgumentException.class, () -> new ItemInfo("minecraft:diamond", 1, ""));
        assertThrows(NullPointerException.class, () -> new ItemInfo("minecraft:diamond", 1, null));
    }

    @Test
    public void listingsAreValidated() {
        Listing listing = listing(ID);
        assertFalse(listing.isExpired(1_999));
        assertTrue(listing.isExpired(2_000));
        assertThrows(IllegalArgumentException.class, () -> new Listing(ID, SELLER, "Alex", "{d}", ITEM, 0, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new Listing(ID, SELLER, "Alex", "{d}", ITEM, Listing.MAX_PRICE + 1, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Listing(ID, SELLER, "Alex", "{d}", ITEM, 1, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new Listing(ID, SELLER, "", "{d}", ITEM, 1, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new Listing(ID, SELLER, "x".repeat(Listing.MAX_SELLER_NAME_LENGTH + 1), "{d}", ITEM, 1, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Listing(ID, SELLER, "Alex", "", ITEM, 1, 1, 2));
        assertThrows(NullPointerException.class, () -> new Listing(ID, null, "Alex", "{d}", ITEM, 1, 1, 2));
    }

    @Test
    public void mailboxesAreImmutableValues() {
        Mailbox mailbox = Mailbox.empty(SELLER);
        assertTrue(mailbox.isEmpty());
        Mailbox filled = mailbox.plusGold(10).plusItem(mail(ID));
        assertTrue("the original is unchanged", mailbox.isEmpty());
        assertEquals(10, filled.gold());
        assertEquals(Optional.of(mail(ID)), filled.item(ID));
        assertEquals(Long.MAX_VALUE, filled.plusGold(Long.MAX_VALUE).gold());
        assertTrue(filled.withGold(0).withoutItems(Set.of(ID)).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> filled.items().clear());
        assertThrows(IllegalArgumentException.class, () -> new Mailbox(SELLER, -1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Mailbox(SELLER, 0, List.of(mail(ID), mail(ID))));
        assertThrows(IllegalArgumentException.class,
                () -> new MailItem(ID, "", ITEM, MailReason.SYSTEM, 0));
        assertEquals(Optional.of(MailReason.AUCTION_PURCHASE), MailReason.byId("auction_purchase"));
        assertEquals(Optional.empty(), MailReason.byId("gift"));
    }

    @Test
    public void marketDataRejectsDuplicates() {
        UUID other = new UUID(5L, 9L);
        assertThrows(IllegalArgumentException.class, () -> new MarketData(S1, List.of(listing(ID), listing(ID)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MarketData(S1, List.of(),
                List.of(Mailbox.empty(SELLER), Mailbox.empty(SELLER))));
        assertThrows(IllegalArgumentException.class, () -> new MarketData(S1, List.of(),
                List.of(new Mailbox(SELLER, 0, List.of(mail(ID))), new Mailbox(other, 0, List.of(mail(ID))))));
        MarketData data = new MarketData(S1, List.of(listing(ID)), List.of(new Mailbox(SELLER, 5, List.of(mail(ID)))));
        assertEquals(1, data.listings().size());
        assertTrue(MarketData.empty(S1).listings().isEmpty());
    }

    @Test
    public void queriesAndPagesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new MarketQuery("", MarketQuery.Sort.NEWEST, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new MarketQuery("", MarketQuery.Sort.NEWEST, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketQuery("", MarketQuery.Sort.NEWEST, 0, MarketQuery.MAX_PAGE_SIZE + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketQuery("x".repeat(MarketQuery.MAX_FILTER_LENGTH + 1), MarketQuery.Sort.NEWEST, 0, 10));
        assertThrows(NullPointerException.class, () -> new MarketQuery(null, MarketQuery.Sort.NEWEST, 0, 10));

        Page<String> empty = new Page<>(List.of(), 0, 10, 0);
        assertEquals(0, empty.totalPages());
        assertFalse(empty.hasNext());
        Page<String> full = new Page<>(List.of("a", "b"), 0, 2, 5);
        assertEquals(3, full.totalPages());
        assertTrue(full.hasNext());
        Page<String> huge = new Page<>(List.of(), Integer.MAX_VALUE, 100, Integer.MAX_VALUE);
        assertEquals(21_474_837, huge.totalPages());
        assertFalse("no int overflow", huge.hasNext());
        assertThrows(IllegalArgumentException.class, () -> new Page<>(List.of("a", "b", "c"), 0, 2, 5));
        assertThrows(IllegalArgumentException.class, () -> new Page<>(List.of("a"), 0, 2, 0));
    }

    @Test
    public void auctionRulesAreValidated() {
        AuctionRules rules = AuctionRules.defaults();
        assertEquals(Duration.ofHours(48), rules.listingDuration());
        assertEquals(10, rules.maxListingsPerPlayer());
        assertEquals(50, rules.salesTax(1_000));
        assertEquals(0, rules.salesTax(19));
        assertEquals(499_999_999, rules.salesTax(Listing.MAX_PRICE));
        assertThrows(IllegalArgumentException.class, () -> rules.salesTax(0));
        assertThrows(IllegalArgumentException.class, () -> new AuctionRules(Duration.ofSeconds(59), 200, 1, 500, 10));
        assertThrows(IllegalArgumentException.class, () -> new AuctionRules(Duration.ofDays(31), 200, 1, 500, 10));
        assertThrows(IllegalArgumentException.class, () -> new AuctionRules(Duration.ofHours(1), -1, 1, 500, 10));
        assertThrows(IllegalArgumentException.class, () -> new AuctionRules(Duration.ofHours(1), 200, 1, 10_001, 10));
        assertThrows(IllegalArgumentException.class, () -> new AuctionRules(Duration.ofHours(1), 200, -1, 500, 10));
        assertThrows(IllegalArgumentException.class, () -> new AuctionRules(Duration.ofHours(1), 200, 1, 500, 0));
        assertThrows(IllegalArgumentException.class, () -> new AuctionRules(Duration.ofHours(1), 200, 1, 500, 101));
        assertEquals(Long.MAX_VALUE / 10_000 * 10_000 + Long.MAX_VALUE % 10_000,
                AuctionRules.basisPointsOf(Long.MAX_VALUE, 10_000));
    }

    @Test
    public void resultsAreConsistent() {
        assertThrows(IllegalArgumentException.class,
                () -> new ListingResult(AuctionOutcome.OK, Optional.empty(), 1, Wallet.EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> new ListingResult(AuctionOutcome.NOT_FOUND, Optional.empty(), 1, Wallet.EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> new SaleResult(AuctionOutcome.OK, Optional.of(listing(ID)), 10, 11, Wallet.EMPTY));
        assertThrows(IllegalArgumentException.class,
                () -> new CancelResult(AuctionOutcome.NOT_OWNER, Optional.of(listing(ID))));
        assertThrows(IllegalArgumentException.class, () -> new MailClaim(-1, List.of(), Wallet.EMPTY));
        assertEquals(Optional.of("message.eternalrealm.auction.error.own_listing"),
                AuctionOutcome.OWN_LISTING.messageKey());
        assertEquals(Optional.empty(), AuctionOutcome.OK.messageKey());
    }
}
