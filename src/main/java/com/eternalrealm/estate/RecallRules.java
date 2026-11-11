package com.eternalrealm.estate;

import com.eternalrealm.core.ModInfo;

/**
 * Going home to one's room from anywhere (帰還, docs/modules/estates.md): a cast of {@value #CAST_TICKS} ticks that
 * moving more than {@value #MOVE_TOLERANCE} blocks or taking damage breaks; refused in combat, in dungeons and raids,
 * in a boss fight or a PvP match, while down, with no room (or one still being prepared), when already home, and for
 * {@value #COOLDOWN_TICKS} ticks after the last trip home. The room's exit door takes the player back to where the
 * cast began. Pure.
 */
public final class RecallRules {
    /** Length of the cast (3 s). */
    public static final int CAST_TICKS = 60;
    /** After arriving home, the next trip home waits this long (60 s). */
    public static final int COOLDOWN_TICKS = 60 * 20;
    /** How far the player may drift while casting (blocks; looking around and small nudges are fine). */
    public static final double MOVE_TOLERANCE = 0.35;
    /** UI action of the key and the menu entry. */
    public static final String ACTION = "estate.recall";
    /** View data channel of the cast bar: {@code {"state": "cast"|"cancel"|"done", "ticks": n, "reason": key}}. */
    public static final String CHANNEL = "estate.recall";

    /** Whether the trip can start; {@link #messageKey()} says why not. */
    public enum Check {
        OK("ok"),
        NO_ROOM("no_room"),
        NOT_READY("not_ready"),
        ALREADY_HOME("already_home"),
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
     * @param cooldownLeft ticks until the next trip home is allowed (0 = now)
     */
    public record Situation(boolean hasRoom, boolean roomReady, boolean inOwnRoom, boolean inCombat, boolean inDungeon,
                            boolean inBossFight, boolean inPvp, boolean down, boolean casting, long cooldownLeft) {
    }

    private RecallRules() {
    }

    /** The first reason the trip cannot start, in the order the player most needs to hear it. */
    public static Check check(Situation s) {
        if (s.casting()) {
            return Check.CASTING;
        }
        if (!s.hasRoom()) {
            return Check.NO_ROOM;
        }
        if (s.inOwnRoom()) {
            return Check.ALREADY_HOME;
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
        if (!s.roomReady()) {
            return Check.NOT_READY;
        }
        if (s.cooldownLeft() > 0) {
            return Check.COOLDOWN;
        }
        return Check.OK;
    }

    /** Ticks until the next trip home ({@code lastArrival} &lt; 0: never went home). */
    public static long cooldownLeft(long now, long lastArrival) {
        if (lastArrival < 0) {
            return 0L;
        }
        return Math.max(0L, lastArrival + COOLDOWN_TICKS - now);
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
