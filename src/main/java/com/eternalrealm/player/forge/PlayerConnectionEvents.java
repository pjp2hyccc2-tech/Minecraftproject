package com.eternalrealm.player.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Connects vanilla login/logout to the UUID-keyed player session lifecycle.
 */
public final class PlayerConnectionEvents {
    private static final Logger LOG = LogUtils.getLogger();

    private PlayerConnectionEvents() {
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Optional<ServerContext> context = ServerContext.current();
        if (context.isEmpty()) {
            LOG.error("{} joined but the {} services are not running; disconnecting",
                    player.getGameProfile().getName(), ModInfo.DISPLAY_NAME);
            player.connection.disconnect(Component.translatable("disconnect.eternalrealm.unavailable"));
            return;
        }
        context.get().playerData().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        JsonObject jobRules = new JsonObject();
        jobRules.addProperty("freeBelow", context.get().game().jobRules().freeChangeBelowLevel());
        jobRules.addProperty("goldPerLevel", context.get().game().jobRules().goldPerLevel());
        jobRules.addProperty("resetGoldPerLevel", context.get().game().jobRules().skillResetGoldPerLevel());
        UiData.send(player, "job_rules", jobRules);
        context.get().game().parties().onLogin(player);
        com.eternalrealm.item.forge.MainHand.ensureWeapon(player); // the main hand is slot 0 (no hotbar)
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerContext.current().ifPresent(context -> {
            UUID playerId = player.getUUID();
            context.game().onLogout(playerId); // before the character is saved: it flushes data into it
            context.playerData().onPlayerLeave(playerId);
            context.allocateLimiter().forget(playerId);
            context.uiLimiter().forget(playerId);
            context.violations().forget(playerId);
            context.floods().forget(playerId);
            context.syncQueue().forget(playerId);
            context.combat().onPlayerLeft(playerId);
        });
    }
}
