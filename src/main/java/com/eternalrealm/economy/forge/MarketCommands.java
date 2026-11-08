package com.eternalrealm.economy.forge;

import com.eternalrealm.server.ServerContext;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** {@code /auction sell <price>} (the item in hand), {@code /auction} and {@code /mail} open the menus. */
public final class MarketCommands {
    private MarketCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("auction")
                .executes(ctx -> {
                    AuctionMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("sell").then(Commands.argument("price", LongArgumentType.longArg(1, 1_000_000_000L))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            long price = LongArgumentType.getLong(ctx, "price");
                            ServerContext.current().ifPresent(context -> context.game().market().sellHeld(player, price));
                            return 1;
                        }))));
        event.getDispatcher().register(Commands.literal("mail").executes(ctx -> {
            AuctionMenu.openMailbox(ctx.getSource().getPlayerOrException());
            return 1;
        }));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> context.game().market().remind(player));
        }
    }
}
