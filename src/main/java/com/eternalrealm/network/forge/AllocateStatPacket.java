package com.eternalrealm.network.forge;

import com.eternalrealm.character.AllocationResult;
import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.character.StatType;
import com.eternalrealm.server.ServerContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * C2S: "I want to spend {@code amount} points on {@code stat}". Only an intent: the server validates everything and
 * answers with a fresh {@link CharacterSnapshotPacket}; the client never changes its own numbers.
 */
public record AllocateStatPacket(int statNetworkId, int amount) {
    /** Two VarInts of at most 5 bytes each. */
    static final int MAX_BYTES = 10;

    public AllocateStatPacket(StatType stat, int amount) {
        this(stat.networkId(), amount);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(statNetworkId);
        buffer.writeVarInt(amount);
    }

    public static AllocateStatPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "AllocateStatPacket");
        return new AllocateStatPacket(buffer.readVarInt(), buffer.readVarInt());
    }

    /** Server thread (via consumerMainThread). */
    public static void handle(AllocateStatPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPacketGuard.accept(contextSupplier, "allocate_stat", ServerContext::allocateLimiter).ifPresent(accepted -> {
            ServerPlayer player = accepted.player();
            ServerContext context = accepted.context();
            Optional<StatType> stat = StatType.byNetworkId(packet.statNetworkId());
            if (stat.isEmpty()) {
                ServerPacketGuard.violation(context, player, "allocate_stat: unknown stat id " + packet.statNetworkId());
                return;
            }
            AllocationResult result = context.playerData().allocateStat(player.getUUID(), stat.get(), packet.amount());
            if (result.isSuccess()) {
                return;
            }
            boolean impossible = packet.amount() < 1 || packet.amount() > CharacterRules.MAX_POINTS_PER_REQUEST;
            if (result == AllocationResult.INVALID_AMOUNT && impossible) {
                // No legitimate client can send this, regardless of server config.
                ServerPacketGuard.violation(context, player, "allocate_stat: impossible amount " + packet.amount());
                return;
            }
            // Legitimate races (double click, config differences): explain and re-sync the authoritative state.
            player.displayClientMessage(Component.translatable(result.messageKey()), true);
            context.syncQueue().mark(player.getUUID());
        });
    }
}
