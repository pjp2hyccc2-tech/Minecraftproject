package com.eternalrealm.network.forge;

import com.eternalrealm.combat.CombatStats;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * S2C: the receiving player's derived combat values. Sent as doubles so the client's predicted action timings round
 * exactly like the server's.
 */
public record CombatStatsPacket(CombatStats stats) {
    static final int MAX_BYTES = 14 * 8;

    public CombatStatsPacket {
        Objects.requireNonNull(stats, "stats");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(stats.attackMultiplier());
        buffer.writeDouble(stats.maxHealthBonus());
        buffer.writeDouble(stats.damageReduction());
        buffer.writeDouble(stats.actionSpeedFactor());
        buffer.writeDouble(stats.critChance());
        buffer.writeDouble(stats.critMultiplier());
        buffer.writeDouble(stats.maxStamina());
        buffer.writeDouble(stats.staminaRegenPerTick());
        buffer.writeDouble(stats.rangedMultiplier());
        buffer.writeDouble(stats.magicMultiplier());
        buffer.writeDouble(stats.healingMultiplier());
        buffer.writeDouble(stats.guardBonus());
        buffer.writeDouble(stats.dodgeCostFactor());
        buffer.writeDouble(stats.moveSpeedBonus());
    }

    public static CombatStatsPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "CombatStatsPacket");
        try {
            return new CombatStatsPacket(new CombatStats(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        } catch (IllegalArgumentException e) {
            throw new DecoderException("Invalid CombatStatsPacket: " + e.getMessage(), e);
        }
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(CombatStatsPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onCombatStats(packet.stats());
    }
}
