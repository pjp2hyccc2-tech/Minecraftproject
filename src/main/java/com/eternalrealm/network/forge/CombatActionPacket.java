package com.eternalrealm.network.forge;

import com.eternalrealm.combat.CombatRequest;
import com.eternalrealm.server.ServerContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * C2S: a combat intent ("light attack", "start charging", ...). Carries no position, damage or timing: the server
 * decides everything from its own state. A skill aimed by holding its key names the enemy it is locked on
 * ({@code targetId}, -1 for none; protocol 8), which the server checks before turning the skill toward it. The sequence
 * number is echoed in {@link CombatStatusPacket} so the client can match the answer to its prediction.
 */
public record CombatActionPacket(int requestId, int sequence, int targetId) {
    /** Three VarInts. */
    static final int MAX_BYTES = 15;

    public CombatActionPacket(CombatRequest request, int sequence) {
        this(request.networkId(), sequence, -1);
    }

    public CombatActionPacket(CombatRequest request, int sequence, int targetId) {
        this(request.networkId(), sequence, targetId);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(sequence);
        buffer.writeVarInt(targetId + 1); // 0: no target
    }

    public static CombatActionPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "CombatActionPacket");
        return new CombatActionPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt() - 1);
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
                    context.combat().handleRequest(accepted.player(), request.get(), packet.sequence(),
                            request.get().isSkill() ? packet.targetId() : -1);
                });
    }
}
