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
        /** A player used something: "breaker", "tv", "bath_tap", "kitchen_tap", "washbasin", "rice", "salt", "cup",
         *  "knife_rack", "sewing_box", "nail_clipper", "plush_shelf", "camera_bag", "laptop", "lighter_drawer",
         *  "table", "switch_<room>", "intercom", "peephole", "printout", "closet_fusuma", ... */
        default void interact(ServerPlayer player, String key, BlockPos pos) {}

        /** A noise was made (doors, swing doors, chairs bumped, shards and menus stepped on, the bell...). */
        default void noise(ServerLevel level, Vec3 pos, float radius, String cause, @Nullable Entity source) {}

        /** A player wants to hide (the 押し入れ or the western closet) at pos; return false to refuse. */
        default boolean hide(ServerPlayer player, BlockPos spot) { return true; }

        default void unhide(ServerPlayer player, BlockPos spot) {}

        /** The front door was opened from inside (a rule during the ritual). */
        default void frontDoorOpened(ServerPlayer player) {}

        /** A player read the note with this id (docs §3.11). */
        default void note(ServerPlayer player, String noteId) {}

        /** A light, the breaker or the TV was switched (by a player or the game). key: room key, "breaker", "tv". */
        default void switched(ServerLevel level, String key, boolean on) {}
    }

    public static Listener LISTENER = new Listener() {};
}
