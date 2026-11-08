package com.eternalrealm.network.forge;

import com.eternalrealm.combat.Telegraph;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * S2C: a ground telegraph appears ({@link #SHOW}, with its shape), hits now ({@link #FIRE}: flash, then gone) or is
 * called off ({@link #CANCEL}). Ids are unique per server run.
 */
public record TelegraphPacket(int id, int action, @Nullable Telegraph telegraph) {
    public static final int SHOW = 0;
    public static final int FIRE = 1;
    public static final int CANCEL = 2;
    static final int MAX_BYTES = 64;

    public TelegraphPacket {
        if (action < SHOW || action > CANCEL || (action == SHOW) != (telegraph != null)) {
            throw new IllegalArgumentException("telegraph packet action " + action);
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeByte(action);
        if (telegraph != null) {
            buffer.writeByte(telegraph.kind().ordinal());
            buffer.writeByte(telegraph.style().ordinal());
            buffer.writeDouble(telegraph.x());
            buffer.writeFloat((float) telegraph.y());
            buffer.writeDouble(telegraph.z());
            buffer.writeFloat((float) telegraph.yaw());
            buffer.writeFloat((float) telegraph.a());
            buffer.writeFloat((float) telegraph.b());
            buffer.writeVarInt(telegraph.durationTicks());
        }
    }

    public static TelegraphPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "TelegraphPacket");
        int id = buffer.readVarInt();
        int action = buffer.readByte();
        if (action != SHOW) {
            if (action != FIRE && action != CANCEL) {
                throw new DecoderException("Invalid TelegraphPacket action " + action);
            }
            return new TelegraphPacket(id, action, null);
        }
        int kind = buffer.readByte();
        int style = buffer.readByte();
        if (kind < 0 || kind >= Telegraph.Kind.values().length || style < 0 || style >= Telegraph.Style.values().length) {
            throw new DecoderException("Invalid TelegraphPacket shape");
        }
        try {
            return new TelegraphPacket(id, action, new Telegraph(Telegraph.Kind.values()[kind],
                    Telegraph.Style.values()[style], buffer.readDouble(), buffer.readFloat(), buffer.readDouble(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt()));
        } catch (IllegalArgumentException e) {
            throw new DecoderException("Invalid TelegraphPacket values", e);
        }
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(TelegraphPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onTelegraph(packet);
    }
}
