package com.eternalrealm.network.forge;

import com.eternalrealm.combat.DefenseEvent;
import com.eternalrealm.combat.RequestOutcome;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * S2C: the authoritative combat state of the receiving player.
 *
 * @param ackSequence    the last request sequence the server processed
 * @param answersRequest true when this packet is the answer to request {@code ackSequence} ({@code outcome} is
 *                       meaningful); false for periodic corrections and defense events
 * @param stamina        current stamina
 * @param cooldown       remaining skill cooldown in ticks
 * @param cooldownTotal  length of the current skill cooldown in ticks
 * @param event          defensive event that just happened, or NONE
 */
public record CombatStatusPacket(int ackSequence, boolean answersRequest, RequestOutcome outcome, float stamina,
                                 int cooldown, int cooldownTotal, DefenseEvent event) {
    static final int MAX_BYTES = 32;

    public CombatStatusPacket {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(event, "event");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(ackSequence);
        buffer.writeBoolean(answersRequest);
        buffer.writeByte(outcome.networkId());
        buffer.writeFloat(stamina);
        buffer.writeVarInt(cooldown);
        buffer.writeVarInt(cooldownTotal);
        buffer.writeByte(event.networkId());
    }

    public static CombatStatusPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "CombatStatusPacket");
        int ack = buffer.readVarInt();
        boolean answers = buffer.readBoolean();
        int outcomeId = buffer.readByte();
        float stamina = buffer.readFloat();
        int cooldown = buffer.readVarInt();
        int cooldownTotal = buffer.readVarInt();
        int eventId = buffer.readByte();
        RequestOutcome outcome = RequestOutcome.byNetworkId(outcomeId)
                .orElseThrow(() -> new DecoderException("Invalid combat outcome " + outcomeId));
        DefenseEvent event = DefenseEvent.byNetworkId(eventId)
                .orElseThrow(() -> new DecoderException("Invalid defense event " + eventId));
        if (!Float.isFinite(stamina) || stamina < 0.0F || cooldown < 0 || cooldownTotal < 0) {
            throw new DecoderException("Invalid CombatStatusPacket values");
        }
        return new CombatStatusPacket(ack, answers, outcome, stamina, cooldown, cooldownTotal, event);
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(CombatStatusPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onCombatStatus(packet);
    }
}
