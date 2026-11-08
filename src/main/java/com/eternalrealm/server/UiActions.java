package com.eternalrealm.server;

import com.eternalrealm.network.forge.ServerPacketGuard;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server handlers of {@code UiActionPacket}s, registered once during mod construction by each feature module.
 * Handlers run on the server thread, after the shared packet checks (sender, rate limit, data loaded).
 */
public final class UiActions {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<String, Handler> HANDLERS = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface Handler {
        /**
         * @throws UiArgs.InvalidArgumentException for arguments no genuine client can send (counted as a violation)
         */
        void handle(ServerPlayer player, ServerContext context, JsonObject args);
    }

    private UiActions() {
    }

    public static void register(String action, Handler handler) {
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.putIfAbsent(action, handler) != null) {
            throw new IllegalStateException("Duplicate UI action " + action);
        }
    }

    public static void dispatch(ServerPlayer player, ServerContext context, String action, JsonObject args) {
        Handler handler = HANDLERS.get(action);
        if (handler == null) {
            ServerPacketGuard.violation(context, player, "ui_action: unknown action " + action);
            return;
        }
        try {
            handler.handle(player, context, args);
        } catch (UiArgs.InvalidArgumentException e) {
            ServerPacketGuard.violation(context, player, "ui_action " + action + ": " + e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("UI action {} of {} failed", action, player.getGameProfile().getName(), e);
        }
    }
}
