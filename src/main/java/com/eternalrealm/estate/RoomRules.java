package com.eternalrealm.estate;

import com.eternalrealm.core.ModInfo;

import java.util.Optional;

/**
 * Renting a room at the inn (docs/modules/estates.md): one room per player; the price is paid once. A player may move
 * to another room at any time: a dearer one costs the difference, a cheaper one gives back half of it; everything stored
 * moves along. Giving the room up gives back half of its price, and only when nothing is stored in it any more (no
 * belongings are ever thrown away). Nothing can be bought or changed while the room is still being prepared. Pure.
 */
public final class RoomRules {
    /** Of a price (or a price difference) handed back, in percent. */
    public static final int REFUND_PERCENT = 50;

    /** The answer to a request; {@link #messageKey()} is what the player is told. */
    public enum Outcome {
        OK("ok"),
        /** Renting while a room is already rented. */
        HAS_ROOM("has_room"),
        /** Changing or giving up without a room. */
        NO_ROOM("no_room"),
        NOT_ENOUGH_GOLD("not_enough_gold"),
        /** Changing to the room one already has. */
        SAME_ROOM("same_room"),
        /** The room is still being prepared or moved. */
        BUSY("busy"),
        /** Giving up a room with belongings still inside. */
        NOT_EMPTY("not_empty");

        private final String id;

        Outcome(String id) {
            this.id = id;
        }

        public boolean ok() {
            return this == OK;
        }

        public String messageKey() {
            return "message." + ModInfo.MOD_ID + ".inn." + id;
        }
    }

    private RoomRules() {
    }

    /** Renting {@code wanted} with {@code gold}. */
    public static Outcome rent(Optional<RoomDesign> current, RoomDesign wanted, long gold) {
        if (current.isPresent()) {
            return Outcome.HAS_ROOM;
        }
        return gold >= wanted.price() ? Outcome.OK : Outcome.NOT_ENOUGH_GOLD;
    }

    /**
     * What moving from {@code from} to {@code to} costs: positive = to pay, negative = handed back (half the
     * difference).
     */
    public static long changeCost(RoomDesign from, RoomDesign to) {
        long difference = to.price() - from.price();
        return difference >= 0 ? difference : -(-difference * REFUND_PERCENT / 100);
    }

    /** Moving to {@code wanted}; {@code busy} while the room is still being prepared or moved. */
    public static Outcome change(Optional<RoomDesign> current, RoomDesign wanted, long gold, boolean busy) {
        if (current.isEmpty()) {
            return Outcome.NO_ROOM;
        }
        if (current.get() == wanted) {
            return Outcome.SAME_ROOM;
        }
        if (busy) {
            return Outcome.BUSY;
        }
        return gold >= Math.max(0L, changeCost(current.get(), wanted)) ? Outcome.OK : Outcome.NOT_ENOUGH_GOLD;
    }

    /** Gold handed back when {@code design} is given up. */
    public static long refund(RoomDesign design) {
        return design.price() * REFUND_PERCENT / 100;
    }

    /** Giving the room up: only when nothing is stored inside. */
    public static Outcome giveUp(Optional<RoomDesign> current, boolean busy, boolean empty) {
        if (current.isEmpty()) {
            return Outcome.NO_ROOM;
        }
        if (busy) {
            return Outcome.BUSY;
        }
        return empty ? Outcome.OK : Outcome.NOT_EMPTY;
    }
}
