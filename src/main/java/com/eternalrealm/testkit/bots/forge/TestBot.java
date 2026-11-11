package com.eternalrealm.testkit.bots.forge;

import com.eternalrealm.testkit.bots.BotIds;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A test bot (docs/modules/testkit.md §4): a real server player, logged in through a {@link BotConnection}, played by
 * a {@link BotBrain} instead of a client. It moves by the same physics as a player (the brain sets its inputs), asks
 * the combat system for attacks and skills, uses the shops, the inn and parties through the same server actions a
 * client sends. Its id is fixed by its number ({@link BotIds}), so its character carries on between test sessions and
 * clients can tell it apart (they draw it in one colour).
 */
public final class TestBot extends ServerPlayer {
    private final int number;
    private final BotBrain mind;
    private Vec3 trackedAt;
    private Level trackedLevel;
    private long trackedTick;

    TestBot(MinecraftServer server, ServerLevel level, int number) {
        super(server, level, new GameProfile(BotIds.idOf(number), BotIds.nameOf(number)));
        this.number = number;
        this.mind = new BotBrain(this);
    }

    public int number() {
        return number;
    }

    public BotBrain mind() {
        return mind;
    }

    /**
     * The bot's own tick: the brain sets this tick's inputs, then the player's server tick and its movement (which a
     * client's position packets would drive).
     */
    @Override
    public void tick() {
        if (!isRemoved() && isAlive()) {
            try {
                mind.tick();
            } catch (RuntimeException e) {
                mind.failed(e);
            }
        }
        super.tick();
        if (!isRemoved()) {
            doTick();
        }
    }

    /**
     * After each server tick ({@link TestBots}): the chunk map follows the bot as it follows a player whose client
     * reports a move (every tick it moved, else once a second), so the ground around it is loaded and it keeps
     * ticking, also after being moved by any teleport.
     */
    void track(long now) {
        if (isRemoved()) {
            return;
        }
        boolean moved = trackedLevel != level() || trackedAt == null || position().distanceToSqr(trackedAt) > 1.0E-4;
        if (moved || now - trackedTick >= 20) {
            serverLevel().getChunkSource().move(this);
            connection.resetPosition(); // what the connection takes for the last position a client confirmed
            trackedAt = position();
            trackedLevel = level();
            trackedTick = now;
        }
    }
}
