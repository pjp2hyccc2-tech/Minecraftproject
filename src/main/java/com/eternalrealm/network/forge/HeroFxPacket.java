package com.eternalrealm.network.forge;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: one of the legendary travellers starts a move (docs/modules/travellers.md): its body plays the move's
 * animation and the client draws the move's light, aimed at {@code (x, y, z)}. {@code action} is the move's ordinal
 * ({@code Travellers.Move}) or one of the gestures of {@code TravellerMoves} (joining, standing up); {@code seed}
 * makes every client draw the same falling stars and cuts.
 */
public record HeroFxPacket(int entityId, int action, float x, float y, float z, int seed) {
    static final int MAX_BYTES = 32;
    /** Largest action code (the gestures are numbered from 100). */
    public static final int MAX_ACTION = 127;

    public HeroFxPacket {
        if (action < 0 || action > MAX_ACTION) {
            throw new IllegalArgumentException("hero action " + action);
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeByte(action);
        buffer.writeFloat(x);
        buffer.writeFloat(y);
        buffer.writeFloat(z);
        buffer.writeInt(seed);
    }

    public static HeroFxPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "HeroFxPacket");
        int entityId = buffer.readVarInt();
        int action = buffer.readByte();
        float x = buffer.readFloat();
        float y = buffer.readFloat();
        float z = buffer.readFloat();
        int seed = buffer.readInt();
        if (action < 0 || action > MAX_ACTION || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new DecoderException("Invalid HeroFxPacket");
        }
        return new HeroFxPacket(entityId, action, x, y, z, seed);
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(HeroFxPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onHeroFx(packet);
    }
}
