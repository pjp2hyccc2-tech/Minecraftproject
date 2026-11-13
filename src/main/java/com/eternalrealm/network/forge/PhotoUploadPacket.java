package com.eternalrealm.network.forge;

import com.eternalrealm.network.TokenBucketRateLimiter;
import com.eternalrealm.social.phone.PhotoRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * C2S: one chunk of a photo for the 魔導スマホ (a screenshot made small, JPEG): {@code total} chunks of
 * {@link PhotoRules#CHUNK} bytes (the last may be shorter), sent in order under the client's {@code upload} number.
 * The server checks and keeps the photo, then answers on UI channel {@code phone.upload} (docs/modules/phone.md).
 */
public record PhotoUploadPacket(int upload, int index, int total, byte[] data) {
    static final int MAX_BYTES = 3 * 5 + 5 + PhotoRules.CHUNK;
    /** A few uploads' worth of chunks in a burst; one upload every few seconds after that. */
    private static final TokenBucketRateLimiter<UUID> CHUNKS = TokenBucketRateLimiter.systemClock(
            PhotoRules.MAX_CHUNKS * 2, 2.0);

    public PhotoUploadPacket {
        Objects.requireNonNull(data, "data");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(upload);
        buffer.writeVarInt(index);
        buffer.writeVarInt(total);
        buffer.writeByteArray(data);
    }

    public static PhotoUploadPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "PhotoUploadPacket");
        return new PhotoUploadPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readByteArray(PhotoRules.CHUNK));
    }

    /** Server thread (via consumerMainThread). */
    public static void handle(PhotoUploadPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPacketGuard.accept(contextSupplier, "photo_upload", context -> CHUNKS).ifPresent(accepted ->
                com.eternalrealm.social.phone.forge.PhoneService.current().ifPresent(service -> service.chunk(
                        accepted.player(), packet.upload(), packet.index(), packet.total(), packet.data())));
    }
}
