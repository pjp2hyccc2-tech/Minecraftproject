package com.eternalrealm.estate;

import com.eternalrealm.core.ModInfo;

/**
 * Going home to one's room, or to the royal capital, from anywhere (帰還 and 王都へ転移, docs/modules/estates.md): a
 * cast of {@value #CAST_TICKS} ticks that moving more than {@value #MOVE_TOLERANCE} blocks or taking damage breaks;
 * refused in combat, in dungeons and raids, in a boss fight or a PvP match, while down, when already there, for the
 * room with no room (or one still being prepared), and for {@value #COOLDOWN_TICKS} ticks after the last arrival at
 * the same place. The room's exit door takes the player back to where the cast began. Pure.
 */
public final class RecallRules {
    /** Length of the cast (3 s). */
    public static final int CAST_TICKS = 60;
    /**
     * After arriving, the next trip to the same place waits this long (30 s). The owner saw "600 seconds" once and
     * asked for 40 at most (2026-11-14): that came from a clock left over from an earlier session, see
     * {@link #cooldownLeft}.
     */
    public static final int COOLDOWN_TICKS = 30 * 20;
    /** How far the player may drift while casting (blocks; looking around and small nudges are fine). */
    public static final double MOVE_TOLERANCE = 0.35;
    /** Closer than this to the capital's arrival point (blocks), the player is already there. */
    public static final double CAPITAL_NEAR = 12.0;
    /** UI action of the keys and the menu entries ({@code {"to": "room"|"capital", "cancel": bool}}). */
    public static final String ACTION = "estate.recall";
    /**
     * View data channel of the cast bar:
     * {@code {"state": "cast"|"broken"|"refused"|"done", "to": "room"|"capital", "ticks": n, "reason": key}}.
     */
    public static final String CHANNEL = "estate.recall";

    /** Where the cast goes. */
    public enum Destination {
        /** One's inn room (Home). */
        ROOM("room"),
        /** The plaza of the royal capital (End). */
        CAPITAL("capital");

        private final String id;

        Destination(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** The destination with this id; the room for anything else (older clients send none). */
        public static Destination byId(String id) {
            for (Destination destination : values()) {
                if (destination.id.equals(id)) {
                    return destination;
                }
            }
            return ROOM;
        }

        /** HUD line while casting. */
        public String castingKey() {
            return "hud." + ModInfo.MOD_ID + ".recall.casting" + (this == ROOM ? "" : "_" + id);
        }

        /** HUD line on arrival. */
        public String doneKey() {
            return "hud." + ModInfo.MOD_ID + ".recall.done" + (this == ROOM ? "" : "_" + id);
        }
    }

    /** Whether the trip can start; {@link #messageKey()} says why not. */
    public enum Check {
        OK("ok"),
        NO_ROOM("no_room"),
        NOT_READY("not_ready"),
        ALREADY_HOME("already_home"),
        ALREADY_IN_CAPITAL("already_capital"),
        IN_COMBAT("in_combat"),
        IN_DUNGEON("in_dungeon"),
        IN_BOSS_FIGHT("in_boss_fight"),
        IN_PVP("in_pvp"),
        DOWN("down"),
        CASTING("casting"),
        COOLDOWN("cooldown");

        private final String id;

        Check(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public boolean ok() {
            return this == OK;
        }

        public String messageKey() {
            return "message." + ModInfo.MOD_ID + ".recall." + id;
        }
    }

    /** Why a cast stopped. */
    public enum Interrupt {
        MOVED("moved"),
        HURT("hurt"),
        COMBAT("combat"),
        /** The player pressed the key (or used the menu) again. */
        CANCELLED("cancelled"),
        /** The situation changed: the room went away, the player went down or into a dungeon... */
        REFUSED("refused");

        private final String id;

        Interrupt(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String messageKey() {
            return "message." + ModInfo.MOD_ID + ".recall.broken." + id;
        }
    }

    /**
     * Where the player stands.
     *
     * @param to           where the cast would go
     * @param hasRoom      the player has a room (only the way home needs one)
     * @param roomReady    the room is built
     * @param there        already at the destination (in one's own room; on the capital's plaza)
     * @param cooldownLeft ticks until the next trip there is allowed (0 = now)
     */
    public record Situation(Destination to, boolean hasRoom, boolean roomReady, boolean there, boolean inCombat,
                            boolean inDungeon, boolean inBossFight, boolean inPvp, boolean down, boolean casting,
                            long cooldownLeft) {
        /** The way home to one's room. */
        public Situation(boolean hasRoom, boolean roomReady, boolean inOwnRoom, boolean inCombat, boolean inDungeon,
                         boolean inBossFight, boolean inPvp, boolean down, boolean casting, long cooldownLeft) {
            this(Destination.ROOM, hasRoom, roomReady, inOwnRoom, inCombat, inDungeon, inBossFight, inPvp, down,
                    casting, cooldownLeft);
        }
    }

    private RecallRules() {
    }

    /** The first reason the trip cannot start, in the order the player most needs to hear it. */
    public static Check check(Situation s) {
        boolean room = s.to() == Destination.ROOM;
        if (s.casting()) {
            return Check.CASTING;
        }
        if (room && !s.hasRoom()) {
            return Check.NO_ROOM;
        }
        if (s.there()) {
            return room ? Check.ALREADY_HOME : Check.ALREADY_IN_CAPITAL;
        }
        if (s.down()) {
            return Check.DOWN;
        }
        if (s.inDungeon()) {
            return Check.IN_DUNGEON;
        }
        if (s.inBossFight()) {
            return Check.IN_BOSS_FIGHT;
        }
        if (s.inPvp()) {
            return Check.IN_PVP;
        }
        if (s.inCombat()) {
            return Check.IN_COMBAT;
        }
        if (room && !s.roomReady()) {
            return Check.NOT_READY;
        }
        if (s.cooldownLeft() > 0) {
            return Check.COOLDOWN;
        }
        return Check.OK;
    }

    /**
     * Ticks until the next trip ({@code lastArrival} &lt; 0: never went), never more than {@link #COOLDOWN_TICKS}.
     * An arrival "later than now" was counted by an earlier server run (the tick counter starts again at zero when a
     * world is opened again in the same game) and does not count.
     */
    public static long cooldownLeft(long now, long lastArrival) {
        if (lastArrival < 0 || lastArrival > now) {
            return 0L;
        }
        return Math.min(COOLDOWN_TICKS, Math.max(0L, lastArrival + COOLDOWN_TICKS - now));
    }

    /** Whether the player drifted too far from where the cast began. */
    public static boolean moved(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz > MOVE_TOLERANCE * MOVE_TOLERANCE;
    }

    /** The cast's progress 0..1. */
    public static double progress(long started, long now) {
        return Math.max(0.0, Math.min(1.0, (now - started) / (double) CAST_TICKS));
    }

    /** Whether the cast that began at {@code started} is complete. */
    public static boolean complete(long started, long now) {
        return now - started >= CAST_TICKS;
    }
}
