package com.eternalrealm.world.field;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The turns of a fight with a ヌシ (field lord), at fixed marks of its health: it calls monsters of its field to its
 * side at 70%, flies into a rage at 50% (faster and harder hitting for the rest of the fight) and calls again at 30%.
 * Every turn happens once per appearance, in order, also when one heavy blow crosses several marks. Its special moves
 * (all three of its body, {@code SpecialMoves.LORD_MOVES}) come on top.
 */
public final class LordTurns {
    /** Monsters that answer one call. */
    public static final int HELPERS = 2;
    /** Damage multiplier once enraged. */
    public static final double RAGE_DAMAGE = 1.2;
    /** Players this close are thrown back by the roar. */
    public static final double ROAR_RADIUS = 5.0;

    public enum Turn {
        CALL(0.7),
        RAGE(0.5),
        CALL_AGAIN(0.3);

        private final double mark;

        Turn(double mark) {
            this.mark = mark;
        }

        /** Health fraction at or below which the turn comes. */
        public double mark() {
            return mark;
        }

        public boolean calls() {
            return this != RAGE;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Announcement to the players nearby ({@code %s} = the lord's name). */
        public String messageKey() {
            return "threat." + ModInfo.MOD_ID + (calls() ? ".lord_calls" : ".lord_rages");
        }
    }

    private LordTurns() {
    }

    /**
     * The turns due now, in order: those whose mark the health fraction {@code health} has reached and that are not in
     * {@code done} yet.
     */
    public static List<Turn> due(double health, Set<Turn> done) {
        List<Turn> due = new ArrayList<>();
        for (Turn turn : Turn.values()) {
            if (health <= turn.mark() && !done.contains(turn)) {
                due.add(turn);
            }
        }
        return due;
    }

    /** A fresh record of the turns taken (a lord that comes back starts over). */
    public static EnumSet<Turn> none() {
        return EnumSet.noneOf(Turn.class);
    }
}
