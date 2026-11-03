package com.reisatsu.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What the restaurant tells the game (docs/DESIGN.md §12). The world's blocks call {@link #LISTENER}; the game
 * installs its listener at startup. Every method has a harmless default.
 */
public final class FixtureEvents {
    private FixtureEvents() {}

    public interface Listener {
        /** A player used something: "breaker", "register_drawer", "service_bell", "phone", "door_switch",
         *  "call_button_<n>", "time_recorder", "tv", "menu_board", "pass_bell", "fridge_door", ... */
        default void interact(ServerPlayer player, String key, BlockPos pos) {}

        /** A noise was made (doors, swing doors, chairs bumped, shards and menus stepped on, the bell...). */
        default void noise(ServerLevel level, Vec3 pos, float radius, String cause, @Nullable Entity source) {}

        /** A player wants to hide in the locker at pos; return false to refuse. */
        default boolean hide(ServerPlayer player, BlockPos locker) { return true; }

        default void unhide(ServerPlayer player, BlockPos locker) {}

        /** A player read the note with this id (docs §3.11). */
        default void note(ServerPlayer player, String noteId) {}

        /** May this player open the lock? keys: "office", "back_door", "door_switch". */
        default boolean canUnlock(ServerPlayer player, String lock) { return true; }

        /** An automatic door opened (cause = the entity in the sensor, or null when it opened by itself). */
        default void autoDoorOpened(ServerLevel level, String door, @Nullable Entity cause) {}

        /** A circuit was switched (by a player at the breaker, or by the game / a trip). */
        default void circuitChanged(ServerLevel level, Circuit circuit, boolean on) {}
    }

    public static Listener LISTENER = new Listener() {};
}
