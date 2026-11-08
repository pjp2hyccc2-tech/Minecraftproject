package com.eternalrealm.network.forge;

import com.eternalrealm.network.TokenBucketRateLimiter;
import com.eternalrealm.network.ViolationTracker;
import com.eternalrealm.server.ServerContext;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Checks shared by every client→server packet, applied before any packet-specific logic (server thread):
 * <ol>
 *     <li>the packet has an authenticated sender (never trust identifiers inside the payload),</li>
 *     <li>the sender is within the per-packet rate limit (excess requests are dropped and counted as a possible
 *         flood, not as protocol violations),</li>
 *     <li>the sender's data is loaded (session ACTIVE).</li>
 * </ol>
 */
public final class ServerPacketGuard {
    private static final Logger LOG = LogUtils.getLogger();

    private ServerPacketGuard() {
    }

    public record Accepted(ServerPlayer player, ServerContext context) {
    }

    public static Optional<Accepted> accept(Supplier<NetworkEvent.Context> contextSupplier, String packetName,
                                            Function<ServerContext, TokenBucketRateLimiter<UUID>> limiter) {
        ServerPlayer sender = contextSupplier.get().getSender();
        if (sender == null) {
            LOG.warn("Dropping {}: no sender", packetName);
            return Optional.empty();
        }
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return Optional.empty();
        }
        if (!limiter.apply(context).tryAcquire(sender.getUUID())) {
            // Not a protocol violation: legitimate players can click fast. Only a sustained flood disconnects.
            ViolationTracker.Verdict flood = context.floods().record(sender.getUUID());
            LOG.debug("Rate limit: dropped {} from {} ({} dropped in current window)", packetName,
                    sender.getGameProfile().getName(), flood.countInWindow());
            if (flood.shouldKick()) {
                LOG.warn("Disconnecting {} ({}): request flood ({} requests dropped in the current window)",
                        sender.getGameProfile().getName(), sender.getUUID(), flood.countInWindow());
                sender.connection.disconnect(Component.translatable("disconnect.eternalrealm.violations"));
            }
            return Optional.empty();
        }
        if (!context.playerData().isActive(sender.getUUID())) {
            LOG.debug("Dropping {} from {}: player data not loaded", packetName, sender.getGameProfile().getName());
            return Optional.empty();
        }
        return Optional.of(new Accepted(sender, context));
    }

    /** Records a protocol violation and disconnects the player when the configured threshold is reached. */
    public static void violation(ServerContext context, ServerPlayer player, String reason) {
        ViolationTracker.Verdict verdict = context.violations().record(player.getUUID());
        LOG.warn("Protocol violation by {} ({}): {} [{} in current window]", player.getGameProfile().getName(),
                player.getUUID(), reason, verdict.countInWindow());
        if (verdict.shouldKick()) {
            LOG.warn("Disconnecting {} for repeated protocol violations", player.getGameProfile().getName());
            player.connection.disconnect(Component.translatable("disconnect.eternalrealm.violations"));
        }
    }
}
