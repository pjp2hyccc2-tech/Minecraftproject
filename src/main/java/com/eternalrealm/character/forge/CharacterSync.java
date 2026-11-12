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

    /** The snapshot with what the job and the equipment add (flat and percentage stats). */
    public static CharacterSnapshotPacket packet(ServerPlayer player, com.eternalrealm.character.CharacterSnapshot snapshot) {
        com.eternalrealm.job.ComposedStats composed = PlayerStats.compose(player, snapshot);
        return new CharacterSnapshotPacket(snapshot, composed.bonus(), composed.percents());
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
                    packet(player, snapshot)));
            com.eternalrealm.social.forge.NameplateTeams.update(player);
        }
    }
}
