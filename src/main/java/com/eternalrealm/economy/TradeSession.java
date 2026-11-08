package com.eternalrealm.economy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A direct trade between two players. Server thread only; the integration keeps one session per pair of players.
 *
 * <pre>
 *   state OPEN:     each side edits its offer (items, gold), then LOCKs it, then CONFIRMs once both are locked
 *                   any offer change → both sides back to NEGOTIATING, {@link #revision()} + 1
 *   both CONFIRMED → state SETTLING: {@link #settlement()} describes the exchange
 *   SETTLING → {@link #complete()} → COMPLETED       (integration moved everything)
 *            → {@link #fail(TradeEndReason)} → FAILED (integration could not; nothing may have moved)
 *   OPEN/SETTLING → {@link #cancel(TradeEndReason)} / {@link #playerDisconnected(UUID)} → CANCELLED
 * </pre>
 *
 * <p><b>Anti-scam:</b> lock and confirm carry the offer revision the player's client displayed. If either offer
 * changed in between, the request is rejected with {@link TradeOutcome#OFFER_CHANGED}, so nobody can confirm a deal
 * that was swapped right before their click.</p>
 *
 * <p><b>Settlement contract:</b> in the same server-thread task that saw the second confirmation, the integration
 * re-checks that each giver still has the offered items and gold and that each receiver has room (inventory and
 * {@link TradeSettlement#applyGold}), then moves everything and calls {@link #complete()}; on any problem it moves
 * nothing and calls {@link #fail(TradeEndReason)}. The items themselves are never stored here.</p>
 *
 * @param <T> opaque item handle of the integration; its {@code equals} identifies an offered item
 */
public final class TradeSession<T> {
    public static final int MAX_ITEMS_PER_SIDE = TradeOffer.MAX_ITEMS;

    public enum State {
        OPEN,
        SETTLING,
        COMPLETED,
        CANCELLED,
        FAILED;

        public boolean isEnded() {
            return this == COMPLETED || this == CANCELLED || this == FAILED;
        }
    }

    /** Progress of one side while the trade is open. */
    public enum Stage {
        NEGOTIATING,
        LOCKED,
        CONFIRMED
    }

    private static final class Side<T> {
        final UUID player;
        TradeOffer<T> offer = TradeOffer.empty();
        Stage stage = Stage.NEGOTIATING;

        Side(UUID player) {
            this.player = player;
        }
    }

    private final Side<T> first;
    private final Side<T> second;
    private State state = State.OPEN;
    private long revision;
    private TradeEndReason endReason;

    public TradeSession(UUID first, UUID second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second)) {
            throw new IllegalArgumentException("a player cannot trade with themselves");
        }
        this.first = new Side<>(first);
        this.second = new Side<>(second);
    }

    // ------------------------------------------------------------------------------------------------ offers

    public TradeOutcome addItem(UUID player, T item) {
        Objects.requireNonNull(item, "item");
        Side<T> side = side(player);
        TradeOutcome denied = checkEditable(side);
        if (denied != null) {
            return denied;
        }
        if (side.offer.items().contains(item)) {
            return TradeOutcome.DUPLICATE_ITEM;
        }
        if (side.offer.items().size() >= MAX_ITEMS_PER_SIDE) {
            return TradeOutcome.TOO_MANY_ITEMS;
        }
        changeOffer(side, side.offer.withItem(item));
        return TradeOutcome.OK;
    }

    public TradeOutcome removeItem(UUID player, T item) {
        Objects.requireNonNull(item, "item");
        Side<T> side = side(player);
        TradeOutcome denied = checkEditable(side);
        if (denied != null) {
            return denied;
        }
        if (!side.offer.items().contains(item)) {
            return TradeOutcome.NO_SUCH_ITEM;
        }
        changeOffer(side, side.offer.withoutItem(item));
        return TradeOutcome.OK;
    }

    /** Sets the offered gold. Setting the current amount again changes nothing (locks stay). */
    public TradeOutcome setGold(UUID player, long gold) {
        Side<T> side = side(player);
        TradeOutcome denied = checkEditable(side);
        if (denied != null) {
            return denied;
        }
        if (gold < 0 || gold > Wallet.MAX_BALANCE) {
            return TradeOutcome.INVALID_GOLD;
        }
        if (gold != side.offer.gold()) {
            changeOffer(side, side.offer.withGold(gold));
        }
        return TradeOutcome.OK;
    }

    private void changeOffer(Side<T> side, TradeOffer<T> offer) {
        side.offer = offer;
        revision++;
        first.stage = Stage.NEGOTIATING;
        second.stage = Stage.NEGOTIATING;
    }

    // ------------------------------------------------------------------------------------------------ agreement

    /** Locks the player's side. A side that is already locked or confirmed stays as it is. */
    public TradeOutcome lock(UUID player, long seenRevision) {
        Side<T> side = side(player);
        TradeOutcome denied = checkEditable(side);
        if (denied != null) {
            return denied;
        }
        if (seenRevision != revision) {
            return TradeOutcome.OFFER_CHANGED;
        }
        if (side.stage == Stage.NEGOTIATING) {
            side.stage = Stage.LOCKED;
        }
        return TradeOutcome.OK;
    }

    /** Unlocks the player's side; the partner's confirmation (if any) is withdrawn, their lock stays. */
    public TradeOutcome unlock(UUID player) {
        Side<T> side = side(player);
        TradeOutcome denied = checkEditable(side);
        if (denied != null) {
            return denied;
        }
        side.stage = Stage.NEGOTIATING;
        Side<T> partner = side == first ? second : first;
        if (partner.stage == Stage.CONFIRMED) {
            partner.stage = Stage.LOCKED;
        }
        return TradeOutcome.OK;
    }

    /** Confirms the deal; when both sides have confirmed the trade moves to {@link State#SETTLING}. */
    public TradeOutcome confirm(UUID player, long seenRevision) {
        Side<T> side = side(player);
        TradeOutcome denied = checkEditable(side);
        if (denied != null) {
            return denied;
        }
        if (seenRevision != revision) {
            return TradeOutcome.OFFER_CHANGED;
        }
        if (first.stage == Stage.NEGOTIATING || second.stage == Stage.NEGOTIATING) {
            return TradeOutcome.NOT_ALL_LOCKED;
        }
        side.stage = Stage.CONFIRMED;
        if (first.stage == Stage.CONFIRMED && second.stage == Stage.CONFIRMED) {
            state = State.SETTLING;
        }
        return TradeOutcome.OK;
    }

    // ------------------------------------------------------------------------------------------------ ending

    /** Cancels an open or settling trade. Returns {@link TradeOutcome#NOT_OPEN} if it already ended. */
    public TradeOutcome cancel(TradeEndReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (state.isEnded()) {
            return TradeOutcome.NOT_OPEN;
        }
        state = State.CANCELLED;
        endReason = reason;
        return TradeOutcome.OK;
    }

    /** Cancels with {@link TradeEndReason#DISCONNECTED} if {@code player} takes part. @return whether it was cancelled */
    public boolean playerDisconnected(UUID player) {
        return side(player) != null && cancel(TradeEndReason.DISCONNECTED) == TradeOutcome.OK;
    }

    /** What each side gives and receives; present only in {@link State#SETTLING}. */
    public Optional<TradeSettlement<T>> settlement() {
        if (state != State.SETTLING) {
            return Optional.empty();
        }
        return Optional.of(new TradeSettlement<>(first.player, second.player, first.offer, second.offer));
    }

    /** The integration moved everything described by {@link #settlement()}. */
    public TradeOutcome complete() {
        if (state != State.SETTLING) {
            return TradeOutcome.NOT_SETTLING;
        }
        state = State.COMPLETED;
        return TradeOutcome.OK;
    }

    /** The integration could not perform the settlement and moved nothing. */
    public TradeOutcome fail(TradeEndReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (state != State.SETTLING) {
            return TradeOutcome.NOT_SETTLING;
        }
        state = State.FAILED;
        endReason = reason;
        return TradeOutcome.OK;
    }

    // ------------------------------------------------------------------------------------------------ queries

    public UUID first() {
        return first.player;
    }

    public UUID second() {
        return second.player;
    }

    public boolean isParticipant(UUID player) {
        return side(player) != null;
    }

    public UUID partnerOf(UUID player) {
        return requireSide(player) == first ? second.player : first.player;
    }

    public State state() {
        return state;
    }

    public Stage stage(UUID player) {
        return requireSide(player).stage;
    }

    public TradeOffer<T> offerOf(UUID player) {
        return requireSide(player).offer;
    }

    /** Version of the two offers; incremented by every change. Clients send it back with lock/confirm. */
    public long revision() {
        return revision;
    }

    /** Why the trade was cancelled or failed; empty while running and after completion. */
    public Optional<TradeEndReason> endReason() {
        return Optional.ofNullable(endReason);
    }

    // ------------------------------------------------------------------------------------------------ internals

    private Side<T> side(UUID player) {
        if (first.player.equals(player)) {
            return first;
        }
        return second.player.equals(player) ? second : null;
    }

    private Side<T> requireSide(UUID player) {
        Side<T> side = side(player);
        if (side == null) {
            throw new IllegalArgumentException(player + " is not part of this trade");
        }
        return side;
    }

    private TradeOutcome checkEditable(Side<T> side) {
        if (side == null) {
            return TradeOutcome.NOT_PARTICIPANT;
        }
        return state == State.OPEN ? null : TradeOutcome.NOT_OPEN;
    }
}
