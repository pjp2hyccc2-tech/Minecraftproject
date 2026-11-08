package com.eternalrealm.network.forge;

import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.server.ServerContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * C2S: a combat intent ("light attack", "start charging", ...). Carries no target, position, damage or timing: the
 * server decides everything from its own state. The sequence number is echoed in {@link CombatStatusPacket} so the
 * client can match the answer to its prediction.
 */
public record CombatActionPacket(int requestId, int sequence) {
    /** Two VarInts. */
    static final int MAX_BYTES = 10;

    public CombatActionPacket(CombatRequest request, int sequence) {
        this(request.networkId(), sequence);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(sequence);
    }

    public static CombatActionPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "CombatActionPacket");
        return new CombatActionPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    /** Server thread (via consumerMainThread). */
    public static void handle(CombatActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPacketGuard.accept(contextSupplier, "combat_action", context -> context.combat().limiter())
                .ifPresent(accepted -> {
                    ServerContext context = accepted.context();
                    Optional<CombatRequest> request = CombatRequest.byNetworkId(packet.requestId());
                    if (request.isEmpty()) {
                        ServerPacketGuard.violation(context, accepted.player(),
                                "combat_action: unknown request id " + packet.requestId());
                        return;
                    }
                    // Rejections (busy, no stamina, cooldown...) are normal under latency and answered, not punished.
                    context.combat().handleRequest(accepted.player(), request.get(), packet.sequence());
                });
    }
}
