package com.eternalrealm.estate.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.EstateStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * {@code /eternalrealm estate ...} (operators): {@code status} (estates, plots waiting to be wiped, queued work),
 * {@code info <player>} (their room), {@code visit <player>} (into their room; creative mode), {@code leave},
 * {@code release <player>} (their room is given up; what is inside goes to their keepsafe).
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EstateCommands {
    private EstateCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("eternalrealm")
                .then(Commands.literal("estate").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(EstateService.describe()), false);
                            return 1;
                        }))
                        .then(Commands.literal("info").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                    Optional<EstateKey> room = EstateService.estate(EstateKey.ROOM, player.getUUID());
                                    String text = room.map(key -> {
                                        EstateStatus status = EstateService.status(key);
                                        return key.design() + " " + status.phase() + " " + status.percent() + "%";
                                    }).orElse("no room");
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            player.getGameProfile().getName() + ": " + text + ", keepsafe "
                                                    + EstateService.keepsafe(player.getUUID()).size()), false);
                                    return 1;
                                })))
                        .then(Commands.literal("visit").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    ServerPlayer self = ctx.getSource().getPlayerOrException();
                                    return EstateService.estate(EstateKey.ROOM, target.getUUID())
                                            .map(key -> report(ctx.getSource(), EstateService.enter(self, key)))
                                            .orElse(0);
                                })))
                        .then(Commands.literal("leave").executes(ctx ->
                                EstateService.leave(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(Commands.literal("release").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    return EstateService.estate(EstateKey.ROOM, target.getUUID())
                                            .map(key -> report(ctx.getSource(), EstateService.release(key)))
                                            .orElse(0);
                                })))));
    }

    private static int report(CommandSourceStack source, EstateResult result) {
        source.sendSuccess(() -> Component.translatable(result.messageKey(), 0), false);
        return result.ok() ? 1 : 0;
    }
}
