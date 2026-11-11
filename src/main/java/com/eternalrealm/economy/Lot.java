package com.eternalrealm.economy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One item put up at the auction hall (オークション会場): registered as a draft, started (live at once when fewer than
 * {@link HallRules#maxRunning()} lots run, else queued), and finished sold, unsold or withdrawn. Immutable; also the
 * persistence form. While a lot exists the item exists only here; its highest bid is gold held by the hall (escrow).
 *
 * @param startPrice      the lowest first bid
 * @param buyoutPrice     a bid of this much ends the lot at once ({@code 0}: no buyout)
 * @param durationMinutes how long the lot runs once live (before extensions)
 * @param queuedAtMillis  when it was started (queued or live; {@code 0} while a draft)
 * @param startedAtMillis when it went live ({@code 0} before)
 * @param endsAtMillis    when it ends (moves later when a bid comes in its last moments); {@code 0} before it runs
 * @param latestEndMillis it never ends later than this ({@code 0} before it runs)
 * @param bids            the latest bids, oldest first (at most {@link HallRules#bidsKept()}); the last is the highest
 * @param bidCount        how many bids it has had in all
 * @param feePaid         the hall's fee paid when it was started
 * @param finishedAtMillis when it was sold, ended unsold or withdrawn ({@code 0} while active)
 */
public record Lot(UUID id, UUID seller, String sellerName, String itemData, ItemInfo item, long startPrice,
                  long buyoutPrice, int durationMinutes, LotState state, long createdAtMillis, long queuedAtMillis,
                  long startedAtMillis, long endsAtMillis, long latestEndMillis, List<Bid> bids, int bidCount,
                  long feePaid, long finishedAtMillis) {
    public Lot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(sellerName, "sellerName");
        if (sellerName.isEmpty() || sellerName.length() > Listing.MAX_SELLER_NAME_LENGTH) {
            throw new IllegalArgumentException("sellerName length out of range");
        }
        Listing.checkItemData(itemData);
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(state, "state");
        if (startPrice < 1 || startPrice > Listing.MAX_PRICE) {
            throw new IllegalArgumentException("startPrice out of range: " + startPrice);
        }
        if (buyoutPrice != 0 && (buyoutPrice <= startPrice || buyoutPrice > Listing.MAX_PRICE)) {
            throw new IllegalArgumentException("buyoutPrice must be 0 or above the start price: " + buyoutPrice);
        }
        if (durationMinutes < 1 || durationMinutes > HallRules.MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("durationMinutes out of range: " + durationMinutes);
        }
        bids = List.copyOf(bids);
        if (bidCount < bids.size() || feePaid < 0) {
            throw new IllegalArgumentException("inconsistent lot " + id);
        }
        long previous = 0;
        for (Bid bid : bids) {
            if (bid.amount() <= previous) {
                throw new IllegalArgumentException("bids of lot " + id + " must rise");
            }
            previous = bid.amount();
        }
        boolean live = state == LotState.RUNNING;
        if (live && (startedAtMillis <= 0 || endsAtMillis <= startedAtMillis || latestEndMillis < endsAtMillis)) {
            throw new IllegalArgumentException("a running lot needs its times: " + id);
        }
        if (!bids.isEmpty() && state != LotState.RUNNING && state != LotState.SOLD) {
            throw new IllegalArgumentException("only running or sold lots have bids: " + id);
        }
        if (state == LotState.SOLD && bids.isEmpty()) {
            throw new IllegalArgumentException("a sold lot needs its winning bid: " + id);
        }
    }

    /** A new draft. */
    static Lot draft(UUID id, UUID seller, String sellerName, String itemData, ItemInfo item, long startPrice,
                     long buyoutPrice, int durationMinutes, long nowMillis) {
        return new Lot(id, seller, sellerName, itemData, item, startPrice, buyoutPrice, durationMinutes,
                LotState.DRAFT, nowMillis, 0L, 0L, 0L, 0L, List.of(), 0, 0L, 0L);
    }

    public Optional<Bid> highest() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.get(bids.size() - 1));
    }

    /** The price now: the highest bid, or the start price before any bid. */
    public long currentPrice() {
        return highest().map(Bid::amount).orElse(startPrice);
    }

    public boolean isActive() {
        return state == LotState.DRAFT || state == LotState.QUEUED || state == LotState.RUNNING;
    }

    /** Milliseconds left while running (0 otherwise or once due). */
    public long remainingMillis(long nowMillis) {
        return state == LotState.RUNNING ? Math.max(0L, endsAtMillis - nowMillis) : 0L;
    }

    Lot with(LotState newState) {
        return new Lot(id, seller, sellerName, itemData, item, startPrice, buyoutPrice, durationMinutes, newState,
                createdAtMillis, queuedAtMillis, startedAtMillis, endsAtMillis, latestEndMillis, bids, bidCount,
                feePaid, finishedAtMillis);
    }

    Lot queued(long nowMillis, long fee) {
        return new Lot(id, seller, sellerName, itemData, item, startPrice, buyoutPrice, durationMinutes,
                LotState.QUEUED, createdAtMillis, nowMillis, 0L, 0L, 0L, bids, bidCount, fee, 0L);
    }

    Lot running(long nowMillis, long maxExtensionMillis) {
        long ends = nowMillis + durationMinutes * 60_000L;
        return new Lot(id, seller, sellerName, itemData, item, startPrice, buyoutPrice, durationMinutes,
                LotState.RUNNING, createdAtMillis, queuedAtMillis == 0L ? nowMillis : queuedAtMillis, nowMillis, ends,
                ends + maxExtensionMillis, bids, bidCount, feePaid, 0L);
    }

    Lot withBid(Bid bid, long newEndsAtMillis, int keep) {
        List<Bid> next = new java.util.ArrayList<>(bids);
        next.add(bid);
        while (next.size() > keep) {
            next.remove(0);
        }
        return new Lot(id, seller, sellerName, itemData, item, startPrice, buyoutPrice, durationMinutes, state,
                createdAtMillis, queuedAtMillis, startedAtMillis, newEndsAtMillis, latestEndMillis, next,
                bidCount + 1, feePaid, finishedAtMillis);
    }

    Lot finished(LotState end, long nowMillis) {
        return new Lot(id, seller, sellerName, itemData, item, startPrice, buyoutPrice, durationMinutes, end,
                createdAtMillis, queuedAtMillis, startedAtMillis, endsAtMillis, latestEndMillis,
                end == LotState.SOLD ? bids : List.of(), bidCount, feePaid, nowMillis);
    }

    /** The lot's times moved by {@code millis} (a running lot does not lose the time the server was off). */
    Lot shifted(long millis) {
        if (state != LotState.RUNNING || millis <= 0) {
            return this;
        }
        return new Lot(id, seller, sellerName, itemData, item, startPrice, buyoutPrice, durationMinutes, state,
                createdAtMillis, queuedAtMillis, startedAtMillis, endsAtMillis + millis, latestEndMillis + millis, bids,
                bidCount, feePaid, finishedAtMillis);
    }
}
