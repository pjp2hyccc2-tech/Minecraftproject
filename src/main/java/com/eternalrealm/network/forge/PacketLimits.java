package com.eternalrealm.network.forge;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Structural limits checked before decoding. A violation throws {@link DecoderException}, which makes Minecraft drop
 * the connection: a legitimate client can never produce an oversized packet.
 */
final class PacketLimits {
    private PacketLimits() {
    }

    static void requireMaxSize(FriendlyByteBuf buffer, int maxBytes, String packetName) {
        int readable = buffer.readableBytes();
        if (readable > maxBytes) {
            throw new DecoderException(packetName + " is too large: " + readable + " > " + maxBytes + " bytes");
        }
    }
}
