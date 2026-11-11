package com.eternalrealm.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * The auction hall (王都のオークション会場, D-AUCTION): live auctions with rising bids. A seller registers an item at
 * the hall (a draft) and starts it; at most {@link HallRules#maxRunning()} lots run at once on the whole server, the
 * others wait in order and go live as places free up. Anyone, anywhere, can bid on a running lot; the hall holds the
 * highest bid's gold until the lot ends (escrow) and gives a beaten bid back at once. A bid in the last moments moves
 * the end a little later (no sniping), a buyout bid ends a lot at once. A sold lot pays its seller (minus the tax) and
 * delivers the item to the winner; an unsold or withdrawn one goes back to its seller. Server thread only; gold and
 * items leave only through the returned {@link HallEvent}s.
 *
 * <p><b>Persistence:</b> {@link #snapshot()} (active lots and the latest finished ones) is saved with the market;
 * every change increments {@link #revision()}. Lots running when the server stopped resume with the time they had
 * left.</p>
 */
public final class AuctionHall {
    private final RandomGenerator random;
    private HallRules rules;
    private final Map<UUID, Lot> lots = new LinkedHashMap<>();
    /** Running lots already told "ending soon" (not persisted: a restart may tell once more). */
    private final Set<UUID> warned = new HashSet<>();
    private long revision;
    private long savedRevision;

    /**
     * @param stored         the saved lots (active and finished)
     * @param downtimeMillis how long the server was off since they were saved: running lots get that time back
     */
    public AuctionHall(HallRules rules, RandomGenerator random, List<Lot> stored, long downtimeMillis) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.random = Objects.requireNonNull(random, "random");
        for (Lot lot : stored) {
            if (lots.put(lot.id(), lot.shifted(downtimeMillis)) != null) {
                throw new IllegalArgumentException("duplicate lot " + lot.id());
            }
        }
    }

    // ------------------------------------------------------------------ the seller

    /**
     * Registers an item as a draft (the platform has taken it from the seller). Free; {@link #start} makes it live.
     *
     * @param buyoutPrice {@code 0} for none, else above the start price
     */
    public HallResult register(UUID seller, String sellerName, String itemData, ItemInfo item, long startPrice,
                               long buyoutPrice, int durationMinutes, long nowMillis) {
        HallOutcome check = checkRegistration(seller, startPrice, buyoutPrice, durationMinutes);
        if (check != HallOutcome.OK) {
            return HallResult.refused(check, Wallet.EMPTY);
        }
        Lot lot = Lot.draft(newId(), seller, sellerName, itemData, item, startPrice, buyoutPrice, durationMinutes,
                nowMillis);
        lots.put(lot.id(), lot);
        changed();
        return new HallResult(HallOutcome.OK, Optional.of(lot), Wallet.EMPTY, List.of());
    }

    /** Whether a registration would be accepted (before the platform takes the item). */
    public HallOutcome checkRegistration(UUID seller, long startPrice, long buyoutPrice, int durationMinutes) {
        if (startPrice < 1 || startPrice > Listing.MAX_PRICE) {
            return HallOutcome.INVALID_PRICE;
        }
        if (buyoutPrice != 0 && (buyoutPrice <= startPrice || buyoutPrice > Listing.MAX_PRICE)) {
            return HallOutcome.INVALID_BUYOUT;
        }
        if (!rules.durationsMinutes().contains(durationMinutes)) {
            return HallOutcome.INVALID_DURATION;
        }
        if (activeLotsOf(seller) >= rules.maxLotsPerPlayer()) {
            return HallOutcome.TOO_MANY_LOTS;
        }
        return HallOutcome.OK;
    }

    /**
     * Starts a draft: the seller pays the fee; it goes live when fewer than {@link HallRules#maxRunning()} lots run,
     * else it waits in the queue (refused when the queue is full).
     */
    public HallResult start(UUID seller, UUID lotId, Wallet sellerWallet, long nowMillis) {
        Lot lot = lots.get(lotId);
        if (lot == null) {
            return HallResult.refused(HallOutcome.NOT_FOUND, sellerWallet);
        }
        if (!lot.seller().equals(seller)) {
            return HallResult.refused(HallOutcome.NOT_OWNER, sellerWallet);
        }
        if (lot.state() != LotState.DRAFT) {
            return HallResult.refused(HallOutcome.NOT_DRAFT, sellerWallet);
        }
        boolean room = running().size() < rules.maxRunning();
        if (!room && queued().size() >= rules.maxQueued()) {
            return HallResult.refused(HallOutcome.QUEUE_FULL, sellerWallet);
        }
        long fee = rules.fee(lot.startPrice());
        WalletResult paid = sellerWallet.debit(fee);
        if (paid.status() != WalletResult.Status.OK) {
            return HallResult.refused(HallOutcome.INSUFFICIENT_FUNDS, sellerWallet);
        }
        List<HallEvent> events = new ArrayList<>();
        Lot next = lot.queued(nowMillis, fee);
        if (room) {
            next = next.running(nowMillis, rules.maxExtensionMillis());
            events.add(new HallEvent.Started(next));
        }
        lots.put(lotId, next);
        if (!room) {
            events.add(new HallEvent.Queued(next, queuePosition(lotId)));
        }
        changed();
        return new HallResult(HallOutcome.OK, Optional.of(next), paid.wallet(), events);
    }

    /**
     * Withdraws a lot: a draft or a queued one at any time (a queued one gets its fee back), a running one only while
     * nobody has bid (the fee is kept). The item goes back to the seller.
     */
    public HallResult withdraw(UUID seller, UUID lotId, long nowMillis) {
        Lot lot = lots.get(lotId);
        if (lot == null || !lot.isActive()) {
            return HallResult.refused(HallOutcome.NOT_FOUND, Wallet.EMPTY);
        }
        if (!lot.seller().equals(seller)) {
            return HallResult.refused(HallOutcome.NOT_OWNER, Wallet.EMPTY);
        }
        if (lot.state() == LotState.RUNNING && !lot.bids().isEmpty()) {
            return HallResult.refused(HallOutcome.HAS_BIDS, Wallet.EMPTY);
        }
        List<HallEvent> events = new ArrayList<>();
        if (lot.state() == LotState.QUEUED && lot.feePaid() > 0) {
            events.add(new HallEvent.Refund(seller, lot.feePaid(), lotId));
        }
        boolean wasRunning = lot.state() == LotState.RUNNING;
        Lot ended = lot.finished(LotState.CANCELLED, nowMillis);
        lots.put(lotId, ended);
        events.add(new HallEvent.Deliver(seller, lot.itemData(), lot.item(), lotId, false));
        events.add(new HallEvent.Ended(ended));
        if (wasRunning) {
            promote(nowMillis, events);
        }
        prune();
        changed();
        return new HallResult(HallOutcome.OK, Optional.of(ended), Wallet.EMPTY, events);
    }

    // ------------------------------------------------------------------ the bidders

    /**
     * Bids {@code amount} on a running lot: the bidder pays it into the hall at once, the bid it beats goes back to
     * its bidder. A bid in the last {@link HallRules#snipeWindowMillis()} moves the end later; a bid reaching the
     * buyout price ends the lot at once (and costs only the buyout price).
     */
    public HallResult bid(UUID bidder, String bidderName, UUID lotId, long amount, Wallet bidderWallet,
                          long nowMillis) {
        Lot lot = lots.get(lotId);
        if (lot == null) {
            return HallResult.refused(HallOutcome.NOT_FOUND, bidderWallet);
        }
        if (lot.state() != LotState.RUNNING) {
            return HallResult.refused(lot.isActive() ? HallOutcome.NOT_RUNNING : HallOutcome.ENDED, bidderWallet);
        }
        if (nowMillis >= lot.endsAtMillis()) {
            return HallResult.refused(HallOutcome.ENDED, bidderWallet);
        }
        if (lot.seller().equals(bidder)) {
            return HallResult.refused(HallOutcome.OWN_LOT, bidderWallet);
        }
        Optional<Bid> highest = lot.highest();
        if (highest.isPresent() && highest.get().bidder().equals(bidder)) {
            return HallResult.refused(HallOutcome.ALREADY_HIGHEST, bidderWallet);
        }
        long price = lot.buyoutPrice() > 0 ? Math.min(amount, lot.buyoutPrice()) : amount;
        if (price < nextMinimum(lot) || price > Listing.MAX_PRICE) {
            return HallResult.refused(HallOutcome.BID_TOO_LOW, bidderWallet);
        }
        WalletResult paid = bidderWallet.debit(price);
        if (paid.status() != WalletResult.Status.OK) {
            return HallResult.refused(HallOutcome.INSUFFICIENT_FUNDS, bidderWallet);
        }
        List<HallEvent> events = new ArrayList<>();
        highest.ifPresent(beaten -> {
            events.add(new HallEvent.Refund(beaten.bidder(), beaten.amount(), lotId));
        });
        boolean buyout = lot.buyoutPrice() > 0 && price >= lot.buyoutPrice();
        long ends = lot.endsAtMillis();
        boolean extended = false;
        if (!buyout && ends - nowMillis < rules.snipeWindowMillis()) {
            long later = Math.min(lot.latestEndMillis(), nowMillis + rules.snipeExtensionMillis());
            if (later > ends) {
                ends = later;
                extended = true;
            }
        }
        Lot next = lot.withBid(new Bid(bidder, bidderName, price, nowMillis), ends, rules.bidsKept());
        lots.put(lotId, next);
        highest.ifPresent(beaten -> events.add(new HallEvent.Outbid(beaten.bidder(), next)));
        events.add(new HallEvent.BidPlaced(next, extended));
        Lot result = next;
        if (buyout) {
            result = finish(next, nowMillis, events);
            promote(nowMillis, events);
            prune();
        }
        changed();
        return new HallResult(HallOutcome.OK, Optional.of(result), paid.wallet(), events);
    }

    /** Ends a running lot at once at its buyout price. */
    public HallResult buyout(UUID bidder, String bidderName, UUID lotId, Wallet bidderWallet, long nowMillis) {
        Lot lot = lots.get(lotId);
        if (lot != null && lot.state() == LotState.RUNNING && lot.buyoutPrice() == 0) {
            return HallResult.refused(HallOutcome.NO_BUYOUT, bidderWallet);
        }
        return bid(bidder, bidderName, lotId, lot == null ? 0L : lot.buyoutPrice(), bidderWallet, nowMillis);
    }

    /** The lowest bid that is accepted now: the start price before any bid, else the highest plus the step. */
    public long nextMinimum(Lot lot) {
        return lot.highest().map(bid -> rules.nextBid(bid.amount())).orElse(lot.startPrice());
    }

    // ------------------------------------------------------------------ time

    /**
     * Once a second: lots that are due end (sold or unsold), waiting lots go live in order, bidders are told about
     * lots ending soon, and drafts never started for {@link HallRules#draftLifetimeMillis()} go back.
     */
    public List<HallEvent> tick(long nowMillis) {
        List<HallEvent> events = new ArrayList<>();
        boolean change = false;
        for (Lot lot : new ArrayList<>(lots.values())) {
            if (lot.state() == LotState.RUNNING && nowMillis >= lot.endsAtMillis()) {
                finish(lot, nowMillis, events);
                change = true;
            } else if (lot.state() == LotState.RUNNING && lot.endsAtMillis() - nowMillis <= rules.endingSoonMillis()
                    && warned.add(lot.id())) {
                events.add(new HallEvent.EndingSoon(lot));
            } else if (lot.state() == LotState.DRAFT && nowMillis - lot.createdAtMillis() >= rules.draftLifetimeMillis()) {
                Lot ended = lot.finished(LotState.CANCELLED, nowMillis);
                lots.put(lot.id(), ended);
                events.add(new HallEvent.Deliver(lot.seller(), lot.itemData(), lot.item(), lot.id(), false));
                events.add(new HallEvent.Ended(ended));
                change = true;
            }
        }
        if (promote(nowMillis, events)) {
            change = true;
        }
        if (change) {
            prune();
            changed();
        }
        return events;
    }

    /** Ends a running lot: the winner gets the item and the seller the gold, or the item goes back unsold. */
    private Lot finish(Lot lot, long nowMillis, List<HallEvent> events) {
        warned.remove(lot.id());
        Optional<Bid> winner = lot.highest();
        Lot ended;
        if (winner.isPresent()) {
            ended = lot.finished(LotState.SOLD, nowMillis);
            long tax = rules.tax(winner.get().amount());
            events.add(new HallEvent.Deliver(winner.get().bidder(), lot.itemData(), lot.item(), lot.id(), true));
            events.add(new HallEvent.Payout(lot.seller(), winner.get().amount() - tax, lot.id(), tax));
        } else {
            ended = lot.finished(LotState.UNSOLD, nowMillis);
            events.add(new HallEvent.Deliver(lot.seller(), lot.itemData(), lot.item(), lot.id(), false));
        }
        lots.put(lot.id(), ended);
        events.add(new HallEvent.Ended(ended));
        return ended;
    }

    /** Waiting lots go live, oldest first, while there is room. @return whether one did */
    private boolean promote(long nowMillis, List<HallEvent> events) {
        boolean any = false;
        List<Lot> waiting = queued();
        int room = rules.maxRunning() - running().size();
        for (int i = 0; i < waiting.size() && room > 0; i++, room--) {
            Lot live = waiting.get(i).running(nowMillis, rules.maxExtensionMillis());
            lots.put(live.id(), live);
            events.add(new HallEvent.Started(live));
            any = true;
        }
        return any;
    }

    /** Keeps the latest {@link HallRules#historyKept()} finished lots. */
    private void prune() {
        List<Lot> finished = new ArrayList<>();
        for (Lot lot : lots.values()) {
            if (!lot.isActive()) {
                finished.add(lot);
            }
        }
        if (finished.size() <= rules.historyKept()) {
            return;
        }
        finished.sort(Comparator.comparingLong(Lot::finishedAtMillis).reversed().thenComparing(Lot::id));
        for (Lot old : finished.subList(rules.historyKept(), finished.size())) {
            lots.remove(old.id());
        }
    }

    // ------------------------------------------------------------------ views

    public Optional<Lot> lot(UUID lotId) {
        return Optional.ofNullable(lots.get(lotId));
    }

    /** Running lots, the one ending first first. */
    public List<Lot> running() {
        return lots.values().stream().filter(lot -> lot.state() == LotState.RUNNING)
                .sorted(Comparator.comparingLong(Lot::endsAtMillis).thenComparing(Lot::id)).toList();
    }

    /** Waiting lots in the order they will go live. */
    public List<Lot> queued() {
        return lots.values().stream().filter(lot -> lot.state() == LotState.QUEUED)
                .sorted(Comparator.comparingLong(Lot::queuedAtMillis).thenComparing(Lot::id)).toList();
    }

    /** 1 for the next lot to go live; 0 when not waiting. */
    public int queuePosition(UUID lotId) {
        List<Lot> waiting = queued();
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).id().equals(lotId)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** A player's lots (active first, newest first). */
    public List<Lot> lotsOf(UUID seller) {
        return lots.values().stream().filter(lot -> lot.seller().equals(seller))
                .sorted(Comparator.comparing((Lot lot) -> !lot.isActive()).thenComparing(
                        Comparator.comparingLong(Lot::createdAtMillis).reversed())).toList();
    }

    /** Finished lots, the latest first. */
    public List<Lot> recent() {
        return lots.values().stream().filter(lot -> !lot.isActive())
                .sorted(Comparator.comparingLong(Lot::finishedAtMillis).reversed().thenComparing(Lot::id)).toList();
    }

    /** Running lots on which {@code bidder} holds the highest bid. */
    public List<Lot> leading(UUID bidder) {
        return running().stream().filter(lot -> lot.highest().map(bid -> bid.bidder().equals(bidder)).orElse(false))
                .toList();
    }

    public int activeLotsOf(UUID seller) {
        int count = 0;
        for (Lot lot : lots.values()) {
            if (lot.seller().equals(seller) && lot.isActive()) {
                count++;
            }
        }
        return count;
    }

    /** Gold the hall holds for the highest bids of running lots. */
    public long escrow() {
        long sum = 0;
        for (Lot lot : running()) {
            sum += lot.highest().map(Bid::amount).orElse(0L);
        }
        return sum;
    }

    // ------------------------------------------------------------------ persistence and rules

    public List<Lot> snapshot() {
        return List.copyOf(lots.values());
    }

    public long revision() {
        return revision;
    }

    public boolean isDirty() {
        return revision != savedRevision;
    }

    public void markSaved(long persistedRevision) {
        savedRevision = Math.max(savedRevision, persistedRevision);
    }

    public HallRules rules() {
        return rules;
    }

    public void setRules(HallRules newRules) {
        rules = Objects.requireNonNull(newRules, "rules");
    }

    private void changed() {
        revision++;
    }

    private UUID newId() {
        UUID id;
        do {
            id = new UUID(random.nextLong(), random.nextLong());
        } while (lots.containsKey(id));
        return id;
    }
}
