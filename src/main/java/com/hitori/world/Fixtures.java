package com.hitori.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Everything the game (ritual, director, the doll) does to the apartment (docs/DESIGN.md §2/§3). Owner: world — compiling
 * stubs in the base commit; the world implements them. Safe to call any time on the apartment level; no-ops elsewhere.
 * Rooms are the Layout room keys ("tatami", "bath", ...).
 */
public final class Fixtures {
    private Fixtures() {}

    /** Makes sure the building exists and its block entities are created (call when a game starts). */
    public static void ensureBuilt(ServerLevel level) {}

    /** Restores room 203 to the evening's start state (lights on in the western room, TV off, bath empty, the plush on
     *  its shelf, the knife on its rack, every door shut, items back in place). */
    public static void resetAll(ServerLevel level) {}

    // --- lights ---------------------------------------------------------------------------------------------------
    public static void setLights(ServerLevel level, String room, boolean on) {}

    public static boolean lightsOn(ServerLevel level, String room) { return false; }

    /** The breaker in the genkan: everything electric off/on (the TV included). */
    public static void setBreaker(ServerLevel level, boolean on) {}

    public static boolean breakerOn(ServerLevel level) { return true; }

    /** A light flickers for a while, even if switched off (霊障). */
    public static void flicker(ServerLevel level, String room, int ticks) {}

    /** The hanging lamp in the tatami room swings for a while. */
    public static void swingLamp(ServerLevel level, int ticks) {}

    // --- the TV ---------------------------------------------------------------------------------------------------
    public static void setTv(ServerLevel level, boolean on) {}

    public static boolean tvOn(ServerLevel level) { return false; }

    /** 0 static, 1 a dark room, 2 a face. */
    public static void setTvChannel(ServerLevel level, int channel) {}

    // --- water ----------------------------------------------------------------------------------------------------
    /** The bath tap (fills the tub over time while running). */
    public static void setBathTap(ServerLevel level, boolean running) {}

    /** 0 empty, 1 half, 2 full. */
    public static int bathWater(ServerLevel level) { return 0; }

    public static void setBathWater(ServerLevel level, int water) {}

    public static void setTap(ServerLevel level, String which, boolean running) {} // "kitchen", "washbasin"

    // --- doors ----------------------------------------------------------------------------------------------------
    /** The closet's fusuma: 0 shut, 1 crack, 2 open. */
    public static void setClosetFusuma(ServerLevel level, int open) {}

    public static int closetFusuma(ServerLevel level) { return 0; }

    /** Interior doors / fusuma by marker-free key: "toilet", "washroom", "bath", "western", "tatami_dk", "tatami_hall". */
    public static void setDoor(ServerLevel level, String door, boolean open) {}

    public static boolean doorOpen(ServerLevel level, String door) { return false; }

    // --- devices and the ritual's world items -----------------------------------------------------------------------
    /** "intercom" (ringing), "washing_machine", "fridge" (door open), "laptop" (screen on), "desk_lamp". */
    public static void setDevice(ServerLevel level, String key, boolean on) {}

    public static boolean deviceOn(ServerLevel level, String key) { return false; }

    /** World items that leave their place when taken: "knife" (rack), "plush" (shelf), "lighter" (drawer). */
    public static void setPresent(ServerLevel level, String key, boolean present) {}

    public static boolean present(ServerLevel level, String key) { return true; }

    /** Something falls (a dish breaks, a thing drops) near pos. */
    public static void dropSomething(ServerLevel level, BlockPos pos) {}

    /** Wet marks where the doll went (fade). */
    public static void wetMark(ServerLevel level, BlockPos pos) {}

    /** Light fixtures of a room (for the director and the shader). */
    public static List<BlockPos> lightsIn(ServerLevel level, String room) { return List.of(); }
}
