package com.eternalrealm.social.forge;

import com.eternalrealm.server.ServerContext;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * {@code /party} ({@code /p <message>} party chat) and {@code /guild} ({@code /g <message>} guild chat), plus the guild
 * tag in front of player names.
 */
public final class SocialCommands {
    private SocialCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("party")
                .executes(ctx -> info(ctx))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> withParty(ctx, (parties, player) -> parties.invite(player, target(ctx))))))
                .then(Commands.literal("accept")
                        .executes(ctx -> withParty(ctx, (parties, player) -> parties.accept(player, null)))
                        .then(Commands.argument("inviter", StringArgumentType.word())
                                .executes(ctx -> withParty(ctx, (parties, player) ->
                                        parties.accept(player, StringArgumentType.getString(ctx, "inviter"))))))
                .then(Commands.literal("decline").executes(ctx -> withParty(ctx, PartyManager::decline)))
                .then(Commands.literal("leave").executes(ctx -> withParty(ctx, PartyManager::leave)))
                .then(Commands.literal("disband").executes(ctx -> withParty(ctx, PartyManager::disband)))
                .then(Commands.literal("raid").executes(ctx -> withParty(ctx, PartyManager::convertToRaid)))
                .then(Commands.literal("kick").then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> withParty(ctx, (parties, player) ->
                                memberByName(ctx, player).ifPresent(id -> parties.kick(player, id))))))
                .then(Commands.literal("promote").then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> withParty(ctx, (parties, player) ->
                                memberByName(ctx, player).ifPresent(id -> parties.promote(player, id)))))));
        dispatcher.register(Commands.literal("p").then(Commands.argument("message", MessageArgument.message())
                .executes(ctx -> withParty(ctx, (parties, player) ->
                        parties.chat(player, MessageArgument.getMessage(ctx, "message").getString())))));

        dispatcher.register(Commands.literal("guild")
                .executes(ctx -> guildInfo(ctx))
                .then(Commands.literal("create").then(Commands.argument("tag", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> withGuild(ctx, (guilds, player) -> guilds.create(player,
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "tag")))))))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> withGuild(ctx, (guilds, player) -> guilds.invite(player, target(ctx))))))
                .then(Commands.literal("accept")
                        .executes(ctx -> withGuild(ctx, (guilds, player) -> guilds.accept(player, null)))
                        .then(Commands.argument("guild", StringArgumentType.greedyString())
                                .executes(ctx -> withGuild(ctx, (guilds, player) -> guilds.accept(player,
                                        StringArgumentType.getString(ctx, "guild").replace('_', ' '))))))
                .then(Commands.literal("decline").executes(ctx -> withGuild(ctx, GuildManager::decline)))
                .then(Commands.literal("leave").executes(ctx -> withGuild(ctx, GuildManager::leave)))
                .then(Commands.literal("disband").executes(ctx -> withGuild(ctx, GuildManager::disband)))
                .then(Commands.literal("kick").then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> withGuild(ctx, (guilds, player) ->
                                guildMember(ctx, guilds, player).ifPresent(id -> guilds.kick(player, id))))))
                .then(Commands.literal("promote").then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> withGuild(ctx, (guilds, player) ->
                                guildMember(ctx, guilds, player).ifPresent(id -> guilds.promote(player, id))))))
                .then(Commands.literal("demote").then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> withGuild(ctx, (guilds, player) ->
                                guildMember(ctx, guilds, player).ifPresent(id -> guilds.demote(player, id))))))
                .then(Commands.literal("transfer").then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> withGuild(ctx, (guilds, player) ->
                                guildMember(ctx, guilds, player).ifPresent(id -> guilds.transfer(player, id)))))));
        dispatcher.register(Commands.literal("g").then(Commands.argument("message", MessageArgument.message())
                .executes(ctx -> withGuild(ctx, (guilds, player) ->
                        guilds.chat(player, MessageArgument.getMessage(ctx, "message").getString())))));
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) {
        try {
            return EntityArgument.getPlayer(ctx, "player");
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static int withParty(CommandContext<CommandSourceStack> ctx, BiConsumer<PartyManager, ServerPlayer> action)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<ServerContext> context = ServerContext.current();
        if (context.isEmpty()) {
            return 0;
        }
        action.accept(context.get().game().parties(), player);
        return 1;
    }

    private static int withGuild(CommandContext<CommandSourceStack> ctx, BiConsumer<GuildManager, ServerPlayer> action)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<ServerContext> context = ServerContext.current();
        if (context.isEmpty()) {
            return 0;
        }
        action.accept(context.get().game().guilds(), player);
        return 1;
    }

    /** A party member by name (online or offline). */
    private static Optional<UUID> memberByName(CommandContext<CommandSourceStack> ctx, ServerPlayer actor) {
        String name = StringArgumentType.getString(ctx, "player");
        return ServerContext.current().flatMap(context -> context.game().parties().service().partyOf(actor.getUUID()))
                .flatMap(party -> party.memberIds().stream()
                        .filter(id -> Names.of(actor.server, id).equalsIgnoreCase(name)).findFirst())
                .or(() -> {
                    actor.displayClientMessage(Component.translatable("message.eternalrealm.party.no_such_member", name), true);
                    return Optional.empty();
                });
    }

    private static Optional<UUID> guildMember(CommandContext<CommandSourceStack> ctx, GuildManager guilds,
                                              ServerPlayer actor) {
        String name = StringArgumentType.getString(ctx, "player");
        return guilds.service().guildOf(actor.getUUID())
                .flatMap(guild -> guild.members().keySet().stream()
                        .filter(id -> Names.of(actor.server, id).equalsIgnoreCase(name)).findFirst())
                .or(() -> {
                    actor.displayClientMessage(Component.translatable("message.eternalrealm.guild.no_such_member", name), true);
                    return Optional.empty();
                });
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerContext.current().ifPresent(context -> {
            var party = context.game().parties().service().partyOf(player.getUUID());
            if (party.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.eternalrealm.party.help"));
                return;
            }
            StringBuilder names = new StringBuilder();
            for (UUID id : party.get().memberIds()) {
                if (!names.isEmpty()) {
                    names.append(", ");
                }
                names.append(party.get().isLeader(id) ? "★" : "").append(Names.of(player.server, id));
            }
            player.sendSystemMessage(Component.translatable("message.eternalrealm.party.info",
                    party.get().size(), context.game().parties().service().capacity(party.get()), names.toString()));
        });
        return 1;
    }

    private static int guildInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerContext.current().ifPresent(context -> {
            GuildManager guilds = context.game().guilds();
            var guild = guilds.service().guildOf(player.getUUID());
            if (guild.isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.eternalrealm.guild.help"));
                return;
            }
            player.sendSystemMessage(Component.translatable("message.eternalrealm.guild.info", guild.get().name(),
                    guild.get().tag(), guilds.service().level(guild.get()), guild.get().size(),
                    guilds.service().memberCap(guild.get()), Names.of(player.server, guild.get().master())));
        });
        return 1;
    }

    /** "[TAG] Name" in chat, death messages and the player list. */
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().flatMap(context -> context.game().guilds().tagOf(player.getUUID()))
                    .ifPresent(tag -> event.setDisplayname(Component.literal("[" + tag + "] ")
                            .withStyle(net.minecraft.ChatFormatting.GREEN).append(event.getDisplayname())));
        }
    }
}
