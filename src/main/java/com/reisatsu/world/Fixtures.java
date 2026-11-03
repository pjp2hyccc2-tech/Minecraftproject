package com.reisatsu.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Everything the game (director, spirits, story) does to the restaurant (docs/DESIGN.md §3.12). Owner: world —
 * these are compiling stubs in the base commit; the world implements them (blocks, states, sounds, particles).
 * All methods are safe to call at any time on the restaurant level and do nothing elsewhere.
 * Doors are named "outer" / "inner"; tables are numbered 1..17 (marker table_n).
 */
public final class Fixtures {
    private Fixtures() {}

    // --- setup ------------------------------------------------------------------------------------------------
    /** Makes sure the restaurant is built and its block entities exist (call when a game starts). */
    public static void ensureBuilt(ServerLevel level) {}

    /** Restores the restaurant to its start state (doors shut, taps off, menus in their stands, circuits as at the start...). */
    public static void resetAll(ServerLevel level) {}

    // --- automatic doors --------------------------------------------------------------------------------------
    /** Opens the door pair now (motor + chime) and closes it after holdTicks unless something stands in the sensor. */
    public static void openAutoDoor(ServerLevel level, String door, int holdTicks) {}

    public static boolean isAutoDoorOpen(ServerLevel level, String door) { return false; }

    /** The key switch (and the DOOR circuit): when off the doors stay shut and ignore the sensor. */
    public static void setAutoDoorsEnabled(ServerLevel level, boolean on) {}

    public static boolean autoDoorsEnabled(ServerLevel level) { return false; }

    /** Locked doors stay shut whatever happens (the visitor's fury); unlocking restores the previous mode. */
    public static void setAutoDoorsLocked(ServerLevel level, boolean locked) {}

    // --- water ------------------------------------------------------------------------------------------------
    public static void setTap(ServerLevel level, BlockPos pos, boolean running) {}

    public static boolean isTapRunning(ServerLevel level, BlockPos pos) { return false; }

    // --- tables -----------------------------------------------------------------------------------------------
    /** The call button of table n rings (chime, red LED) and the call display shows n. */
    public static void ringCallButton(ServerLevel level, int table) {}

    /** The menus of table n fly off and scatter (particles, sound, scattered decals around the table). */
    public static void scatterMenus(ServerLevel level, int table) {}

    /** A glass of ice water appears on table n (the waitress serves). */
    public static void placeWaterGlass(ServerLevel level, int table) {}

    /** Removes glasses / scattered menus around table n and puts its menus back. */
    public static void clearTable(ServerLevel level, int table) {}

    /** The chair at table 7's west bench side is pulled out (or put back). */
    public static void pullOutChair(ServerLevel level, int table, boolean out) {}

    /** Slides (fall = false) or knocks over (fall = true) the chair at pos. */
    public static void knockChair(ServerLevel level, BlockPos chair, boolean fall) {}

    public static List<BlockPos> chairsNear(ServerLevel level, BlockPos pos, int radius) { return List.of(); }

    // --- power and lights -------------------------------------------------------------------------------------
    public static void setCircuit(ServerLevel level, Circuit circuit, boolean on) {}

    public static boolean circuitOn(ServerLevel level, Circuit circuit) { return true; }

    /** One light flickers for a while (ticks). */
    public static void flicker(ServerLevel level, BlockPos light, int ticks) {}

    /** Every light of a room (Layout room key) flickers for a while. */
    public static void flickerRoom(ServerLevel level, String roomKey, int ticks) {}

    /** A tube bursts: pop, glass, the light is dead until reset. */
    public static void burstLight(ServerLevel level, BlockPos light) {}

    /** Closing time: every circuit on, every light lit. */
    public static void allLightsOn(ServerLevel level) {}

    /** Lights (ceiling lights, downlights, pendants) of a room, for the director. */
    public static List<BlockPos> lightsIn(ServerLevel level, String roomKey) { return List.of(); }

    // --- devices ----------------------------------------------------------------------------------------------
    /** Devices by key: "tv", "drink_bar", "hand_dryer_w", "hand_dryer_m", "dishwasher", "fryer", "gas_range",
     *  "pass_lamp", "phone" (ringing), "monitors", "store_music" (the store speakers), "register_drawer". */
    public static void setDevice(ServerLevel level, String key, boolean on) {}

    public static boolean deviceOn(ServerLevel level, String key) { return false; }

    // --- misc -------------------------------------------------------------------------------------------------
    /** Call display: number 0 = blank; closed = shows 「閉店」. */
    public static void setCallDisplay(ServerLevel level, int number, boolean closed) {}

    /** A security camera's red LED (it sees you). */
    public static void setCameraSeen(ServerLevel level, BlockPos camera, boolean seen) {}

    /** The office monitors' red REC frame. */
    public static void setMonitorsAlert(ServerLevel level, boolean alert) {}

    public static void crackMirror(ServerLevel level, boolean cracked) {}

    /** Hand prints appear on the mirror for a while. */
    public static void handPrints(ServerLevel level) {}

    /** A wet footprint (fades over ~2 min). */
    public static void wetFootprint(ServerLevel level, Vec3 pos, float yaw) {}

    /** The stuffed bear in the kids' corner turns. */
    public static void turnBear(ServerLevel level, Direction facing) {}

    /** The A-frame board outside: 0 「本日のおすすめ」, 1 「まだ　いますか」. */
    public static void setMenuBoard(ServerLevel level, int message) {}

    /** A plate smashes here (sound, shards decal). */
    public static void smashPlate(ServerLevel level, BlockPos pos) {}

    /** The third stall of the women's restroom: red 使用中 and locked. */
    public static void setStallOccupied(ServerLevel level, boolean occupied) {}

    /** Store PA / speakers: true = the store music plays from the ceiling speakers (the game picks the track). */
    public static List<BlockPos> speakers(ServerLevel level) { return List.of(); }
}
