package com.eternalrealm.economy.forge;

import com.eternalrealm.server.ServerContext;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * {@code /auction} opens the auction hall (as the O key does), {@code /mail} the mailbox. Listing and registering
 * items happen in the screens at the hall in the capital.
 */
public final class MarketCommands {
    private MarketCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("auction").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerContext.current().ifPresent(context -> context.game().market().watch(player));
            return 1;
        }));
        event.getDispatcher().register(Commands.literal("mail").executes(ctx -> {
            MarketScreens.openBoard(ctx.getSource().getPlayerOrException(), true);
            return 1;
        }));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> context.game().market().remind(player));
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> context.game().market().onLogout(player));
        }
    }
}
