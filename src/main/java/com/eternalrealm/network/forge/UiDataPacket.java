package com.eternalrealm.network.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * S2C: view data for one of the mod's screens (journal tabs, lists), as a JSON document. The client only displays it;
 * every decision stays on the server.
 *
 * @param channel which view the data belongs to ({@code [a-z0-9_.]{1,64}})
 * @param json    a JSON document of at most {@link #MAX_JSON} characters
 */
public record UiDataPacket(String channel, String json) {
    public static final int MAX_JSON = 120_000;
    static final int MAX_BYTES = 64 * 3 + MAX_JSON * 3 + 16;

    public UiDataPacket {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(json, "json");
        if (json.length() > MAX_JSON) {
            throw new IllegalArgumentException("UI data for " + channel + " is too large: " + json.length());
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(channel, 64);
        buffer.writeUtf(json, MAX_JSON);
    }

    public static UiDataPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "UiDataPacket");
        return new UiDataPacket(buffer.readUtf(64), buffer.readUtf(MAX_JSON));
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(UiDataPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onUiData(packet.channel(), packet.json());
    }
}
