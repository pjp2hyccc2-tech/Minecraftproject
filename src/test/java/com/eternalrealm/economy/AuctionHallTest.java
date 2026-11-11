package com.eternalrealm.economy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuctionHallTest {
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final ItemInfo SWORD = new ItemInfo("eternalrealm:iron_longsword", 1, "legendary");
    private static final long T0 = 1_000_000L;
    private static final long MINUTE = 60_000L;

    private static AuctionHall hall() {
        return new AuctionHall(HallRules.defaults(), new SplittableRandom(7L), List.of(), 0L);
    }

    private static Lot live(AuctionHall hall, UUID seller, long start, long buyout, int minutes) {
        Lot draft = hall.register(seller, "seller", "{id:\"x\"}", SWORD, start, buyout, minutes, T0).lot().orElseThrow();
        HallResult started = hall.start(seller, draft.id(), Wallet.of(1_000_000L), T0);
        assertTrue(started.outcome().toString(), started.isSuccess());
        return started.lot().orElseThrow();
    }

    @Test
    public void registrationChecksPriceBuyoutDurationAndLimit() {
        AuctionHall hall = hall();
        assertEquals(HallOutcome.INVALID_PRICE, hall.checkRegistration(SELLER, 0, 0, 10));
        assertEquals(HallOutcome.INVALID_BUYOUT, hall.checkRegistration(SELLER, 100, 100, 10));
        assertEquals(HallOutcome.INVALID_DURATION, hall.checkRegistration(SELLER, 100, 0, 7));
        for (int i = 0; i < 3; i++) {
            assertTrue(hall.register(SELLER, "s", "{}", SWORD, 100, 0, 10, T0).isSuccess());
        }
        assertEquals(HallOutcome.TOO_MANY_LOTS, hall.register(SELLER, "s", "{}", SWORD, 100, 0, 10, T0).outcome());
    }

    @Test
    public void startingPaysTheFeeAndGoesLiveOrWaits() {
        AuctionHall hall = new AuctionHall(new HallRules(2, 1, 3, List.of(10), 500, 10, 60_000, 60_000, 900_000, 100,
                10, 500, 20, 50, 7L * 24 * 3_600_000, 60_000), new SplittableRandom(3L), List.of(), 0L);
        Lot a = hall.register(SELLER, "s", "{}", SWORD, 50_000, 0, 10, T0).lot().orElseThrow();
        HallResult started = hall.start(SELLER, a.id(), Wallet.of(1_000), T0);
        assertEquals(LotState.RUNNING, started.lot().orElseThrow().state());
        assertEquals("1 % of 50 000", 500L, 1_000 - started.wallet().balance());
        assertTrue(started.events().stream().anyMatch(event -> event instanceof HallEvent.Started));
        live(hall, ALICE, 100, 0, 10);
        Lot waiting = hall.register(BOB, "b", "{}", SWORD, 100, 0, 10, T0).lot().orElseThrow();
        HallResult queued = hall.start(BOB, waiting.id(), Wallet.of(1_000), T0);
        assertEquals(LotState.QUEUED, queued.lot().orElseThrow().state());
        assertEquals("the minimum fee", 990L, queued.wallet().balance());
        assertTrue(queued.events().get(0) instanceof HallEvent.Queued q && q.position() == 1);
        Lot another = hall.register(BOB, "b", "{}", SWORD, 100, 0, 10, T0).lot().orElseThrow();
        assertEquals(HallOutcome.QUEUE_FULL, hall.start(BOB, another.id(), Wallet.of(1_000), T0).outcome());
        AuctionHall poor = hall();
        Lot unpaid = poor.register(SELLER, "s", "{}", SWORD, 100, 0, 10, T0).lot().orElseThrow();
        assertEquals(HallOutcome.INSUFFICIENT_FUNDS, poor.start(SELLER, unpaid.id(), Wallet.of(9), T0).outcome());
        assertEquals(LotState.DRAFT, poor.lot(unpaid.id()).orElseThrow().state());

        // the first lot ends unsold: the waiting one goes live
        List<HallEvent> events = hall.tick(T0 + 10 * MINUTE);
        assertTrue(events.stream().anyMatch(event -> event instanceof HallEvent.Started s
                && s.lot().id().equals(waiting.id())));
        assertEquals(LotState.UNSOLD, hall.lot(a.id()).orElseThrow().state());
        assertTrue(events.stream().anyMatch(event -> event instanceof HallEvent.Deliver d && d.player().equals(SELLER)
                && !d.won()));
    }

    @Test
    public void bidsRiseHoldTheGoldAndGiveTheBeatenBidBack() {
        AuctionHall hall = hall();
        Lot lot = live(hall, SELLER, 1_000, 0, 30);
        assertEquals(1_000L, hall.nextMinimum(lot));
        assertEquals(HallOutcome.BID_TOO_LOW, hall.bid(ALICE, "a", lot.id(), 999, Wallet.of(5_000), T0 + 1).outcome());
        assertEquals(HallOutcome.OWN_LOT, hall.bid(SELLER, "s", lot.id(), 2_000, Wallet.of(5_000), T0 + 1).outcome());
        HallResult first = hall.bid(ALICE, "a", lot.id(), 1_000, Wallet.of(5_000), T0 + 1);
        assertTrue(first.isSuccess());
        assertEquals("the bid is held by the hall", 4_000L, first.wallet().balance());
        Lot after = first.lot().orElseThrow();
        assertEquals("5 % more (at least 10 G)", 1_050L, hall.nextMinimum(after));
        assertEquals(HallOutcome.ALREADY_HIGHEST, hall.bid(ALICE, "a", lot.id(), 2_000, Wallet.of(4_000), T0 + 2)
                .outcome());
        assertEquals(HallOutcome.INSUFFICIENT_FUNDS, hall.bid(BOB, "b", lot.id(), 1_050, Wallet.of(1_000), T0 + 2)
                .outcome());
        HallResult second = hall.bid(BOB, "b", lot.id(), 1_050, Wallet.of(2_000), T0 + 3);
        assertTrue(second.isSuccess());
        assertTrue(second.events().contains(new HallEvent.Refund(ALICE, 1_000, lot.id())));
        assertTrue(second.events().stream().anyMatch(event -> event instanceof HallEvent.Outbid o
                && o.player().equals(ALICE)));
        assertEquals(1_050L, hall.escrow());
        assertEquals(2, second.lot().orElseThrow().bidCount());
    }

    @Test
    public void aLastMomentBidMovesTheEndButNotPastTheLimit() {
        AuctionHall hall = hall();
        Lot lot = live(hall, SELLER, 100, 0, 10);
        long end = lot.endsAtMillis();
        HallResult early = hall.bid(ALICE, "a", lot.id(), 100, Wallet.of(10_000), end - 5 * MINUTE);
        assertEquals("plenty of time left: no change", end, early.lot().orElseThrow().endsAtMillis());
        HallResult late = hall.bid(BOB, "b", lot.id(), 200, Wallet.of(10_000), end - 10_000);
        assertEquals(end + 50_000, late.lot().orElseThrow().endsAtMillis());
        assertTrue(late.events().stream().anyMatch(event -> event instanceof HallEvent.BidPlaced p && p.extended()));
        // keep sniping: the end never passes the planned end + 15 minutes
        long now = end;
        UUID[] bidders = {ALICE, BOB};
        long amount = 200;
        for (int i = 0; i < 40; i++) {
            Lot current = hall.lot(lot.id()).orElseThrow();
            if (now >= current.endsAtMillis()) {
                break;
            }
            amount = hall.nextMinimum(current);
            assertTrue(hall.bid(bidders[i % 2], "x", lot.id(), amount, Wallet.of(1_000_000), now).isSuccess());
            now = hall.lot(lot.id()).orElseThrow().endsAtMillis() - 1_000;
        }
        assertTrue(hall.lot(lot.id()).orElseThrow().endsAtMillis() <= end + 15 * MINUTE);
    }

    @Test
    public void buyoutEndsAtOnceForTheBuyoutPrice() {
        AuctionHall hall = new AuctionHall(new HallRules(1, 5, 3, List.of(10), 500, 10, 60_000, 60_000, 900_000, 100,
                10, 500, 20, 50, 7L * 24 * 3_600_000, 60_000), new SplittableRandom(5L), List.of(), 0L);
        Lot lot = live(hall, SELLER, 1_000, 5_000, 10);
        Lot waiting = hall.register(ALICE, "a", "{}", SWORD, 10, 0, 10, T0).lot().orElseThrow();
        assertEquals(LotState.QUEUED, hall.start(ALICE, waiting.id(), Wallet.of(100), T0).lot().orElseThrow().state());
        assertTrue(hall.bid(ALICE, "a", lot.id(), 1_000, Wallet.of(10_000), T0 + 1).isSuccess());
        HallResult bought = hall.bid(BOB, "b", lot.id(), 9_999, Wallet.of(9_999), T0 + 2);
        assertTrue(bought.isSuccess());
        assertEquals("only the buyout price is taken", 4_999L, bought.wallet().balance());
        assertEquals(LotState.SOLD, bought.lot().orElseThrow().state());
        List<HallEvent> events = bought.events();
        assertTrue(events.contains(new HallEvent.Refund(ALICE, 1_000, lot.id())));
        assertTrue(events.stream().anyMatch(event -> event instanceof HallEvent.Deliver d && d.player().equals(BOB)
                && d.won()));
        assertTrue(events.contains(new HallEvent.Payout(SELLER, 4_750, lot.id(), 250)));
        assertTrue("the waiting lot goes live", events.stream().anyMatch(event -> event instanceof HallEvent.Started s
                && s.lot().id().equals(waiting.id())));
        assertEquals(HallOutcome.NO_BUYOUT, hall.buyout(BOB, "b", waiting.id(), Wallet.of(100), T0 + 3).outcome());
    }

    @Test
    public void aSoldLotPaysTheSellerMinusTaxAndDeliversTheItem() {
        AuctionHall hall = hall();
        Lot lot = live(hall, SELLER, 10_000, 0, 10);
        hall.bid(ALICE, "a", lot.id(), 10_000, Wallet.of(50_000), T0 + 1);
        hall.bid(BOB, "b", lot.id(), 20_000, Wallet.of(50_000), T0 + 2);
        List<HallEvent> soon = hall.tick(lot.endsAtMillis() - 30_000);
        assertTrue(soon.stream().anyMatch(event -> event instanceof HallEvent.EndingSoon));
        assertFalse("told once", hall.tick(lot.endsAtMillis() - 20_000).stream()
                .anyMatch(event -> event instanceof HallEvent.EndingSoon));
        List<HallEvent> end = hall.tick(lot.endsAtMillis());
        assertTrue(end.contains(new HallEvent.Payout(SELLER, 19_000, lot.id(), 1_000)));
        assertTrue(end.stream().anyMatch(event -> event instanceof HallEvent.Deliver d && d.player().equals(BOB)
                && d.won()));
        assertEquals(0L, hall.escrow());
        assertEquals(HallOutcome.ENDED, hall.bid(ALICE, "a", lot.id(), 30_000, Wallet.of(50_000), T0 + 3).outcome());
        assertEquals(lot.id(), hall.recent().get(0).id());
    }

    @Test
    public void withdrawingReturnsTheItemAndOnlyAQueuedFee() {
        AuctionHall hall = new AuctionHall(new HallRules(1, 5, 3, List.of(10), 500, 10, 60_000, 60_000, 900_000, 100,
                10, 500, 20, 50, 7L * 24 * 3_600_000, 60_000), new SplittableRandom(9L), List.of(), 0L);
        Lot draft = hall.register(SELLER, "s", "{}", SWORD, 100, 0, 10, T0).lot().orElseThrow();
        assertEquals(HallOutcome.NOT_OWNER, hall.withdraw(ALICE, draft.id(), T0).outcome());
        HallResult back = hall.withdraw(SELLER, draft.id(), T0);
        assertTrue(back.events().stream().anyMatch(event -> event instanceof HallEvent.Deliver d && !d.won()));
        Lot running = live(hall, ALICE, 100, 0, 10);
        Lot queued = hall.register(SELLER, "s", "{}", SWORD, 100_000, 0, 10, T0).lot().orElseThrow();
        hall.start(SELLER, queued.id(), Wallet.of(10_000), T0);
        HallResult unqueued = hall.withdraw(SELLER, queued.id(), T0 + 1);
        assertTrue(unqueued.events().contains(new HallEvent.Refund(SELLER, 1_000, queued.id())));
        hall.bid(BOB, "b", running.id(), 100, Wallet.of(1_000), T0 + 2);
        assertEquals(HallOutcome.HAS_BIDS, hall.withdraw(ALICE, running.id(), T0 + 3).outcome());
    }

    @Test
    public void runningLotsKeepTheirTimeOverARestartAndOldDraftsGoBack() {
        AuctionHall hall = hall();
        Lot lot = live(hall, SELLER, 100, 0, 10);
        hall.register(ALICE, "a", "{}", SWORD, 100, 0, 10, T0);
        AuctionHall restarted = new AuctionHall(HallRules.defaults(), new SplittableRandom(1L), hall.snapshot(),
                5 * MINUTE);
        assertEquals(lot.endsAtMillis() + 5 * MINUTE, restarted.lot(lot.id()).orElseThrow().endsAtMillis());
        List<HallEvent> events = restarted.tick(T0 + 8L * 24 * 3_600_000);
        assertTrue(events.stream().anyMatch(event -> event instanceof HallEvent.Deliver d && d.player().equals(ALICE)));
    }

    @Test
    public void noGoldIsEverCreated() {
        AuctionHall hall = hall();
        SplittableRandom random = new SplittableRandom(42L);
        UUID[] players = {SELLER, ALICE, BOB, UUID.randomUUID(), UUID.randomUUID()};
        long[] wallets = {1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000};
        long sinks = 0;
        long now = T0;
        List<HallEvent> all = new ArrayList<>();
        for (int step = 0; step < 3_000; step++) {
            now += random.nextInt(20_000);
            int p = random.nextInt(players.length);
            switch (random.nextInt(4)) {
                case 0 -> {
                    HallResult registered = hall.register(players[p], "p", "{}", SWORD, 1 + random.nextInt(5_000),
                            0, 10, now);
                    if (registered.lot().isPresent()) {
                        HallResult started = hall.start(players[p], registered.lot().get().id(), Wallet.of(wallets[p]),
                                now);
                        if (started.isSuccess()) {
                            wallets[p] = started.wallet().balance();
                            all.addAll(started.events());
                        }
                    }
                }
                case 1, 2 -> {
                    List<Lot> running = hall.running();
                    if (!running.isEmpty()) {
                        Lot lot = running.get(random.nextInt(running.size()));
                        long amount = hall.nextMinimum(lot) + random.nextInt(2_000);
                        HallResult bid = hall.bid(players[p], "p", lot.id(), amount, Wallet.of(wallets[p]), now);
                        if (bid.isSuccess()) {
                            wallets[p] = bid.wallet().balance();
                            all.addAll(bid.events());
                        }
                    }
                }
                default -> all.addAll(hall.tick(now));
            }
            for (HallEvent event : all) {
                if (event instanceof HallEvent.Refund refund) {
                    wallets[index(players, refund.player())] += refund.gold();
                } else if (event instanceof HallEvent.Payout payout) {
                    wallets[index(players, payout.player())] += payout.gold();
                    sinks += payout.tax();
                }
            }
            all.clear();
        }
        long fees = 0;
        for (Lot lot : hall.snapshot()) {
            fees += lot.feePaid();
        }
        long total = sinks + hall.escrow();
        for (long wallet : wallets) {
            total += wallet;
        }
        // fees of lots no longer kept in the history are part of the gap; the gap can only be fees (never negative)
        long gap = 5_000_000 - total;
        assertTrue("gold appeared: " + gap, gap >= 0);
        assertTrue("only fees disappear (kept fees " + fees + ", gap " + gap + ")", gap >= fees);
    }

    private static int index(UUID[] players, UUID player) {
        for (int i = 0; i < players.length; i++) {
            if (players[i].equals(player)) {
                return i;
            }
        }
        throw new AssertionError("unknown player " + player);
    }
}
