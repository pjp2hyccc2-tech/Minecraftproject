package com.eternalrealm.economy;

import java.util.UUID;

/**
 * Something the auction hall did that the platform must carry out or announce: gold and items leave the hall only
 * through these events (to the player if online, else to the mailbox).
 */
public sealed interface HallEvent {
    /** Gold back to a player: a beaten bid, or the fee of a queued lot that was withdrawn. */
    record Refund(UUID player, long gold, UUID lotId) implements HallEvent {
    }

    /** The seller's proceeds of a sold lot (the winning bid minus the hall's tax). */
    record Payout(UUID player, long gold, UUID lotId, long tax) implements HallEvent {
    }

    /** An item leaves the hall: to the winner ({@code won}) or back to its seller (unsold, withdrawn). */
    record Deliver(UUID player, String itemData, ItemInfo item, UUID lotId, boolean won) implements HallEvent {
    }

    /** A lot went live (announce it to everyone). */
    record Started(Lot lot) implements HallEvent {
    }

    /** A lot was started while the hall was full and waits ({@code position} 1 = next). */
    record Queued(Lot lot, int position) implements HallEvent {
    }

    /** {@code player}'s bid on the lot was beaten. */
    record Outbid(UUID player, Lot lot) implements HallEvent {
    }

    /** A bid came in ({@code extended}: in the last moments, so the lot runs longer). */
    record BidPlaced(Lot lot, boolean extended) implements HallEvent {
    }

    /** A running lot has less than a minute left (once per lot). */
    record EndingSoon(Lot lot) implements HallEvent {
    }

    /** A lot ended: sold, unsold or withdrawn. */
    record Ended(Lot lot) implements HallEvent {
    }
}
