package com.eternalrealm.social.forge;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** Player names and chat helpers shared by the social systems (server thread). */
public final class Names {
    private Names() {
    }

    /** Name of an online or known offline player (profile cache), or a short UUID. */
    public static String of(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            Optional<GameProfile> profile = server.getProfileCache().get(id);
            if (profile.isPresent() && profile.get().getName() != null) {
                return profile.get().getName();
            }
        }
        return id.toString().substring(0, 8);
    }

    /** Online player by exact (case-insensitive) name. */
    public static Optional<ServerPlayer> online(MinecraftServer server, String name) {
        return Optional.ofNullable(server.getPlayerList().getPlayerByName(name));
    }

    public static void send(MinecraftServer server, UUID to, Component message) {
        ServerPlayer player = server.getPlayerList().getPlayer(to);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    /** A clickable chat button running {@code command}. */
    public static MutableComponent button(String key, String command, net.minecraft.ChatFormatting color) {
        return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }
}
