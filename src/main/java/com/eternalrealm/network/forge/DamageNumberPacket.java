package com.eternalrealm.network.forge;

import com.eternalrealm.combat.ActionKind;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: damage the receiving player just dealt, for the floating damage numbers (shown to the attacker only).
 *
 * @param flags {@link #CRITICAL}, {@link #HEAVY}, {@link #SKILL}
 */
public record DamageNumberPacket(int entityId, float x, float y, float z, float amount, int flags) {
    public static final int CRITICAL = 1;
    public static final int HEAVY = 2;
    public static final int SKILL = 4;
    static final int MAX_BYTES = 32;

    public static int flags(boolean critical, ActionKind kind) {
        int flags = critical ? CRITICAL : 0;
        if (kind == ActionKind.HEAVY) {
            flags |= HEAVY;
        } else if (kind == ActionKind.SKILL) {
            flags |= SKILL;
        }
        return flags;
    }

    public boolean critical() {
        return (flags & CRITICAL) != 0;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeFloat(x);
        buffer.writeFloat(y);
        buffer.writeFloat(z);
        buffer.writeFloat(amount);
        buffer.writeByte(flags);
    }

    public static DamageNumberPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "DamageNumberPacket");
        DamageNumberPacket packet = new DamageNumberPacket(buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readByte() & 0xFF);
        if (!Float.isFinite(packet.x) || !Float.isFinite(packet.y) || !Float.isFinite(packet.z)
                || !Float.isFinite(packet.amount) || packet.amount < 0.0F) {
            throw new DecoderException("Invalid DamageNumberPacket values");
        }
        return packet;
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(DamageNumberPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onDamageNumber(packet);
    }
}
