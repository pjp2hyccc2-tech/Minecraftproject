package com.eternalrealm.revival;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;

/**
 * The revival rules (復活システム): a player whose health runs out does not die at once but
 * falls (戦闘不能): lying on the ground, out of the fight, untouchable, for a while. From there they
 * <ul>
 *     <li>revive on the spot with a 永久花の花びら (half health),</li>
 *     <li>are revived by an ally (a 永久花の花びら used on them, or a priest's resurrection), or</li>
 *     <li>return to their save point (セーブポイント) at full health: the nearest waystone they recorded in the field,
 *         the capital's plaza, or the entrance of the dungeon they are in. After {@link #autoReturnTicks} this
 *         happens on its own.</li>
 * </ul>
 * Falling in a field costs a little EXP (the server's death penalty), in the capital and in dungeons it does not.
 */
public final class RevivalRules {
    /** The "return" button needs this long, so a click meant for the fight does not send anyone home. */
    public static final int RETURN_UNLOCK_TICKS = 40;
    /** Health after reviving oneself with a 永久花の花びら. */
    public static final double PETAL_SELF_HEALTH = 0.5;
    /** Health after an ally used a 永久花の花びら on the fallen player. */
    public static final double PETAL_ALLY_HEALTH = 0.5;
    /** Health after returning to the save point. */
    public static final double RETURN_HEALTH = 1.0;
    /** Untouchable for this long after any revival (time to get one's bearings). */
    public static final int GRACE_TICKS = 60;
    /** How far an ally may stand from a fallen player to revive them with a petal. */
    public static final double ALLY_REACH = 4.5;
    /** Body anchor: a fallen body drifting further than this (pushed, server lag) is put back. */
    public static final double ANCHOR_SLACK = 0.6;
    /** Beyond this distance a fallen body was moved on purpose (dungeon exit, teleports): that ends the fall. */
    public static final double MOVED_AWAY = 16.0;

    /** Where a player fell; decides the save point, the countdown and the penalty. */
    public enum Place {
        /** Inside the royal capital: back to its plaza after a short wait. */
        CAPITAL(10 * 20),
        /** Out in the fields: back to the nearest recorded waystone of the field (the capital if none). */
        FIELD(60 * 20),
        /** Inside a dungeon instance (boss fight included): back to the dungeon's revival point. */
        DUNGEON(30 * 20);

        private final int autoReturnTicks;

        Place(int autoReturnTicks) {
            this.autoReturnTicks = autoReturnTicks;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Place byId(String id) {
            for (Place place : values()) {
                if (place.id().equals(id)) {
                    return place;
                }
            }
            return FIELD;
        }
    }

    /** How a fallen player came back. */
    public enum Way {
        PETAL_SELF,
        PETAL_ALLY,
        RESURRECTION,
        RETURN,
        AUTO_RETURN;

        public String translationKey() {
            return "message." + ModInfo.MOD_ID + ".revival.revived." + name().toLowerCase(Locale.ROOT);
        }

        /** Whether the player stays where they fell. */
        public boolean inPlace() {
            return this == PETAL_SELF || this == PETAL_ALLY || this == RESURRECTION;
        }
    }

    private RevivalRules() {
    }

    /** After this long the fallen player returns to the save point on their own. */
    public static int autoReturnTicks(Place place) {
        return place.autoReturnTicks;
    }

    /** Whether the return button works {@code fallenFor} ticks after the fall. */
    public static boolean canReturn(int fallenFor) {
        return fallenFor >= RETURN_UNLOCK_TICKS;
    }

    /** Whether the countdown is over {@code fallenFor} ticks after the fall. */
    public static boolean autoReturnDue(Place place, int fallenFor) {
        return fallenFor >= autoReturnTicks(place);
    }

    /** Seconds left before the automatic return (rounded up, never negative). */
    public static int secondsLeft(Place place, int fallenFor) {
        int left = autoReturnTicks(place) - Math.max(0, fallenFor);
        return left <= 0 ? 0 : (left + 19) / 20;
    }

    /** Whether falling here costs EXP (the server's death penalty fraction). */
    public static boolean costsExperience(Place place) {
        return place == Place.FIELD;
    }

    /** Health fraction after coming back the given way. */
    public static double healthAfter(Way way, double resurrectionFraction) {
        return switch (way) {
            case PETAL_SELF -> PETAL_SELF_HEALTH;
            case PETAL_ALLY -> PETAL_ALLY_HEALTH;
            case RESURRECTION -> Math.max(0.1, Math.min(1.0, resurrectionFraction));
            case RETURN, AUTO_RETURN -> RETURN_HEALTH;
        };
    }
}
