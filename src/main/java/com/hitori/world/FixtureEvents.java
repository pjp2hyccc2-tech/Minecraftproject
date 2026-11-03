package com.hitori.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What the apartment tells the game (docs/DESIGN.md §12). The world's blocks call {@link #LISTENER}; the game
 * installs its listener at startup. Every method has a harmless default.
 */
public final class FixtureEvents {
    private FixtureEvents() {}

    public interface Listener {
        /** A player used something (pos = the block, the lower half for doors). Ritual things: "rice" (rice bag),
         *  "salt", "cup", "knife_rack", "lighter_drawer" (the drawer by the stove; other drawers "drawer"),
         *  "sewing_box" (scissors + thread), "nail_clipper" (the washroom's mirror cabinet; other mirrors "mirror"),
         *  "plush_shelf" (only while the plush is there), "camera_bag", "laptop", "printout" (also {@link #note} "howto"),
         *  "table" (the ちゃぶ台), "bathtub" (sneak + empty hand also puts the lid on/off), "tv", "bath_tap",
         *  "kitchen_tap", "washbasin". Switches: "switch_<room>" (the tatami room's is the lamp's pull-cord), "breaker".
         *  Doors: "front_door", "peephole" (sneak + right-click on the closed front door: nothing opens), "door",
         *  "fusuma", "closet_fusuma" (after hide/unhide, or when the closet is used as a plain door), "balcony_door",
         *  "locked_door" (the other units' doors). Others: "intercom" (answering stops the ringing), "washing_machine",
         *  "fridge", "gas_stove", "desk_lamp", "curtain", "toilet", "microwave", "rice_cooker", "altar_shelf",
         *  "calendar", "wall_clock", "mailboxes". */
        default void interact(ServerPlayer player, String key, BlockPos pos) {}

        /** A noise was made (doors, swing doors, chairs bumped, shards and menus stepped on, the bell...). */
        default void noise(ServerLevel level, Vec3 pos, float radius, String cause, @Nullable Entity source) {}

        /** A player right-clicked a closet's sliding door from outside: hide them at spot (the closet cell behind that
         *  panel: one of closet_hide, or the western closet's column; feet position) and return true. The game moves the
         *  player (and plays the slide); once the player is inside, the world shuts the doors. Return false to refuse:
         *  the doors just open or shut like any sliding door (also when the player was not moved in). */
        default boolean hide(ServerPlayer player, BlockPos spot) { return true; }

        /** A hidden player right-clicked the doors from inside: the game lets the player out (and plays the slide); the
         *  world leaves the doors open behind them. If nobody moves the player, the doors just slide open. */
        default void unhide(ServerPlayer player, BlockPos spot) {}

        /** The front door was opened from inside (a rule during the ritual). */
        default void frontDoorOpened(ServerPlayer player) {}

        /** A player read the note with this id ("howto": the printout on the desk). */
        default void note(ServerPlayer player, String noteId) {}

        /** A light, the breaker or the TV was switched (by a player or the game; reported once per change).
         *  key: the room key of a light switch, "breaker", "tv". */
        default void switched(ServerLevel level, String key, boolean on) {}
    }

    public static Listener LISTENER = new Listener() {};
}
