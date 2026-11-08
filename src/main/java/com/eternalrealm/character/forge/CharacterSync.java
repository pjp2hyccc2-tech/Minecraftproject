package com.eternalrealm.character.forge;

import com.eternalrealm.network.forge.CharacterSnapshotPacket;
import com.eternalrealm.job.forge.PlayerStats;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.server.ServerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.List;
import java.util.UUID;

/**
 * Sends the authoritative character snapshot to every player whose character changed this tick.
 */
public final class CharacterSync {
    private CharacterSync() {
    }

    public static void flush(ServerContext context) {
        List<UUID> changed = context.syncQueue().drain();
        if (changed.isEmpty()) {
            return;
        }
        PlayerList players = context.server().getPlayerList();
        for (UUID playerId : changed) {
            ServerPlayer player = players.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            context.playerData().snapshot(playerId).ifPresent(snapshot -> ERNetwork.sendToPlayer(player,
                    new CharacterSnapshotPacket(snapshot, PlayerStats.compose(player, snapshot).bonus())));
            com.eternalrealm.social.forge.NameplateTeams.update(player);
        }
    }
}
