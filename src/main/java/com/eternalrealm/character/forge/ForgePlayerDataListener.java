package com.eternalrealm.character.forge;

import com.eternalrealm.character.ExperienceResult;
import com.eternalrealm.core.concurrent.MainThreadQueue;
import com.eternalrealm.player.PlayerDataListener;
import com.eternalrealm.server.SyncQueue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Objects;
import java.util.UUID;

/**
 * Turns player data events into Minecraft side effects: client sync, level-up feedback and rejecting players
 * whose data could not be loaded. Invoked on the server thread.
 */
public final class ForgePlayerDataListener implements PlayerDataListener {
    private final MinecraftServer server;
    private final MainThreadQueue mainThread;
    private final SyncQueue syncQueue;
    private final SyncQueue combatStatsQueue;

    /**
     * @param syncQueue        players whose snapshot must be sent
     * @param combatStatsQueue players whose combat values (derived from stats and level) must be recomputed
     */
    public ForgePlayerDataListener(MinecraftServer server, MainThreadQueue mainThread, SyncQueue syncQueue,
                                   SyncQueue combatStatsQueue) {
        this.server = Objects.requireNonNull(server, "server");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.syncQueue = Objects.requireNonNull(syncQueue, "syncQueue");
        this.combatStatsQueue = Objects.requireNonNull(combatStatsQueue, "combatStatsQueue");
    }

    @Override
    public void onCharacterReady(UUID playerId) {
        syncQueue.mark(playerId);
        combatStatsQueue.mark(playerId);
    }

    @Override
    public void onCharacterChanged(UUID playerId) {
        syncQueue.mark(playerId);
        combatStatsQueue.mark(playerId);
    }

    @Override
    public void onLevelUp(UUID playerId, ExperienceResult result) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.displayClientMessage(Component.translatable("message.eternalrealm.level_up",
                result.newLevel(), result.statPointsGained()).withStyle(ChatFormatting.GOLD), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    @Override
    public void onLoadFailed(UUID playerId, String incidentId) {
        // Deferred to the next tick: disconnecting re-enters the logout handler, which must not happen while the
        // player data service is still inside its own callback.
        mainThread.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.connection.disconnect(Component.translatable("disconnect.eternalrealm.load_failed", incidentId));
            }
        });
    }
}
