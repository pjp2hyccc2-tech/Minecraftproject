package com.eternalrealm.server;

import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiDataPacket;
import com.google.gson.JsonElement;
import net.minecraft.server.level.ServerPlayer;

/** Sends view data to a player's screens. */
public final class UiData {
    private UiData() {
    }

    public static void send(ServerPlayer player, String channel, JsonElement data) {
        ERNetwork.sendToPlayer(player, new UiDataPacket(channel, data.toString()));
    }
}
