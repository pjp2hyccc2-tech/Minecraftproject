package com.eternalrealm.guild.house.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * What the guild houses need of the estates (docs/modules/estates.md): each guild's house is its own estate of kind
 * {@code guild} (owner = the guild's id, design = the catalogue id), built on a plot of its own, so two guilds owning
 * the same design never meet. {@code EstateGuildEstates} implements it on the shared estates core; without it
 * ({@link #UNAVAILABLE}) the gates refuse politely and nothing is sold.
 */
public interface GuildEstates {
    /** Outcome of an estate operation. */
    enum Result {
        OK, UNAVAILABLE, BUILDING, NOT_CLAIMED, FAILED
    }

    boolean available();

    /** Takes a plot for the guild's house and starts building {@code design}. */
    Result claim(UUID guild, String design);

    /** Rebuilds the guild's house as {@code design} (the stored things move along). */
    Result redesign(UUID guild, String design);

    /** Gives the house up: players inside are sent out, leftovers go to the guild's keepsafe. */
    Result release(UUID guild);

    /** Whether the guild's house stands and can be entered. */
    boolean ready(UUID guild);

    /** Build progress of the guild's house in percent (100 when it stands). */
    int percent(UUID guild);

    /** Teleports the player into their guild's house, remembering where they were. */
    Result enter(ServerPlayer player, UUID guild);

    /** Takes the player out of the guild house they are in, back where they came from. */
    boolean leave(ServerPlayer player);

    /** The guild whose house holds {@code pos} in {@code level}. */
    Optional<UUID> houseAt(Level level, BlockPos pos);

    /** The storage piece name ({@code chest.2}, {@code vault.1}) written on a container by the blueprint, if any. */
    Optional<String> pieceName(Level level, BlockPos pos);

    GuildEstates UNAVAILABLE = new GuildEstates() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public Result claim(UUID guild, String design) {
            return Result.UNAVAILABLE;
        }

        @Override
        public Result redesign(UUID guild, String design) {
            return Result.UNAVAILABLE;
        }

        @Override
        public Result release(UUID guild) {
            return Result.UNAVAILABLE;
        }

        @Override
        public boolean ready(UUID guild) {
            return false;
        }

        @Override
        public int percent(UUID guild) {
            return 0;
        }

        @Override
        public Result enter(ServerPlayer player, UUID guild) {
            return Result.UNAVAILABLE;
        }

        @Override
        public boolean leave(ServerPlayer player) {
            return false;
        }

        @Override
        public Optional<UUID> houseAt(Level level, BlockPos pos) {
            return Optional.empty();
        }

        @Override
        public Optional<String> pieceName(Level level, BlockPos pos) {
            return Optional.empty();
        }
    };
}
