package com.eternalrealm.network.forge;

import com.eternalrealm.combat.Combatant;
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
 * @param cooldowns      remaining cooldown in ticks per skill slot (0 = weapon skill, 1..4 = job skills)
 * @param cooldownTotals length of each slot's current cooldown in ticks
 * @param event          defensive event that just happened, or NONE
 */
public record CombatStatusPacket(int ackSequence, boolean answersRequest, RequestOutcome outcome, float stamina,
                                 int[] cooldowns, int[] cooldownTotals, DefenseEvent event) {
    static final int MAX_BYTES = 16 + 2 * 5 * Combatant.SKILL_SLOTS;

    public CombatStatusPacket {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(event, "event");
        if (cooldowns.length != Combatant.SKILL_SLOTS || cooldownTotals.length != Combatant.SKILL_SLOTS) {
            throw new IllegalArgumentException("one cooldown per skill slot");
        }
        cooldowns = cooldowns.clone();
        cooldownTotals = cooldownTotals.clone();
    }

    @Override
    public int[] cooldowns() {
        return cooldowns.clone();
    }

    @Override
    public int[] cooldownTotals() {
        return cooldownTotals.clone();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(ackSequence);
        buffer.writeBoolean(answersRequest);
        buffer.writeByte(outcome.networkId());
        buffer.writeFloat(stamina);
        for (int slot = 0; slot < Combatant.SKILL_SLOTS; slot++) {
            buffer.writeVarInt(cooldowns[slot]);
            buffer.writeVarInt(cooldownTotals[slot]);
        }
        buffer.writeByte(event.networkId());
    }

    public static CombatStatusPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "CombatStatusPacket");
        int ack = buffer.readVarInt();
        boolean answers = buffer.readBoolean();
        int outcomeId = buffer.readByte();
        float stamina = buffer.readFloat();
        int[] cooldowns = new int[Combatant.SKILL_SLOTS];
        int[] totals = new int[Combatant.SKILL_SLOTS];
        for (int slot = 0; slot < Combatant.SKILL_SLOTS; slot++) {
            cooldowns[slot] = buffer.readVarInt();
            totals[slot] = buffer.readVarInt();
            if (cooldowns[slot] < 0 || totals[slot] < 0) {
                throw new DecoderException("Invalid cooldown in CombatStatusPacket");
            }
        }
        int eventId = buffer.readByte();
        RequestOutcome outcome = RequestOutcome.byNetworkId(outcomeId)
                .orElseThrow(() -> new DecoderException("Invalid combat outcome " + outcomeId));
        DefenseEvent event = DefenseEvent.byNetworkId(eventId)
                .orElseThrow(() -> new DecoderException("Invalid defense event " + eventId));
        if (!Float.isFinite(stamina) || stamina < 0.0F) {
            throw new DecoderException("Invalid CombatStatusPacket values");
        }
        return new CombatStatusPacket(ack, answers, outcome, stamina, cooldowns, totals, event);
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(CombatStatusPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onCombatStatus(packet);
    }
}
