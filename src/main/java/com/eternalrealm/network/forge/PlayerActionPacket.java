package com.eternalrealm.network.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * S2C, to the players tracking a fighter (never the fighter itself, which predicts its own actions): another player
 * started an action or a heavy attack charge, so the receiving clients can animate that player's arms.
 *
 * @param actionId   id of the started action (weapon combo step, heavy strike, skill or {@code dodge})
 * @param comboStep  combo step for light attacks, otherwise -1
 * @param duration   action length in ticks (for a charge: its maximum charge time)
 * @param charging   a heavy attack charge started (the action id is the heavy strike that will follow)
 */
public record PlayerActionPacket(int entityId, String actionId, int comboStep, int duration, boolean charging) {
    static final int MAX_BYTES = 5 + 64 * 3 + 3 + 5 + 1 + 8;

    public PlayerActionPacket {
        Objects.requireNonNull(actionId, "actionId");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeUtf(actionId, 64);
        buffer.writeByte(comboStep);
        buffer.writeVarInt(duration);
        buffer.writeBoolean(charging);
    }

    public static PlayerActionPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "PlayerActionPacket");
        return new PlayerActionPacket(buffer.readVarInt(), buffer.readUtf(64), buffer.readByte(),
                Math.max(1, Math.min(400, buffer.readVarInt())), buffer.readBoolean());
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(PlayerActionPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onPlayerAction(packet);
    }
}
