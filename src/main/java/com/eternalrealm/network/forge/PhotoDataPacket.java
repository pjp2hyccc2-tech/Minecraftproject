package com.eternalrealm.network.forge;

import com.eternalrealm.social.phone.PhotoRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * S2C: a photo of the 魔導スマホ the client asked for ({@code phone.photo}): its id and the JPEG, or no bytes when it
 * is gone or not the player's to see.
 */
public record PhotoDataPacket(String id, byte[] data) {
    static final int MAX_BYTES = 16 * 3 + 5 + 5 + PhotoRules.MAX_BYTES;

    public PhotoDataPacket {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(data, "data");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(id, 16);
        buffer.writeByteArray(data);
    }

    public static PhotoDataPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "PhotoDataPacket");
        return new PhotoDataPacket(buffer.readUtf(16), buffer.readByteArray(PhotoRules.MAX_BYTES));
    }

    public static void handle(PhotoDataPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onPhoto(packet);
    }
}
