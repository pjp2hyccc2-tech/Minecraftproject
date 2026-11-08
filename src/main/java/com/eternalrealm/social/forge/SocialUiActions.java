package com.eternalrealm.social.forge;

import com.eternalrealm.guild.Guild;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Journal requests of the party and guild pages. */
public final class SocialUiActions {
    private SocialUiActions() {
    }

    public static void register() {
        UiActions.register("party.invite", (player, context, args) -> {
            String name = UiArgs.string(args, "name", 16);
            Optional<ServerPlayer> target = Names.online(player.server, name);
            if (target.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.eternalrealm.player_not_online", name)
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            context.game().parties().invite(player, target.get());
        });
        UiActions.register("party.accept", (player, context, args) -> context.game().parties().accept(player, null));
        UiActions.register("party.decline", (player, context, args) -> context.game().parties().decline(player));
        UiActions.register("party.leave", (player, context, args) -> context.game().parties().leave(player));
        UiActions.register("party.raid", (player, context, args) -> context.game().parties().convertToRaid(player));
        UiActions.register("party.kick", (player, context, args) ->
                context.game().parties().kick(player, UiArgs.uuid(args, "id")));
        UiActions.register("party.promote", (player, context, args) ->
                context.game().parties().promote(player, UiArgs.uuid(args, "id")));

        UiActions.register("guild.view", (player, context, args) -> sendGuild(player, context));
        UiActions.register("guild.create", (player, context, args) -> {
            context.game().guilds().create(player, UiArgs.string(args, "name", 32), UiArgs.string(args, "tag", 8));
            sendGuild(player, context);
        });
        UiActions.register("guild.invite", (player, context, args) -> {
            String name = UiArgs.string(args, "name", 16);
            Optional<ServerPlayer> target = Names.online(player.server, name);
            if (target.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.eternalrealm.player_not_online", name)
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            context.game().guilds().invite(player, target.get());
        });
        UiActions.register("guild.accept", (player, context, args) -> {
            context.game().guilds().accept(player, null);
            sendGuild(player, context);
        });
        UiActions.register("guild.decline", (player, context, args) -> context.game().guilds().decline(player));
        UiActions.register("guild.leave", (player, context, args) -> {
            context.game().guilds().leave(player);
            sendGuild(player, context);
        });
        UiActions.register("guild.kick", (player, context, args) -> {
            context.game().guilds().kick(player, UiArgs.uuid(args, "id"));
            sendGuild(player, context);
        });
        UiActions.register("guild.promote", (player, context, args) -> {
            context.game().guilds().promote(player, UiArgs.uuid(args, "id"));
            sendGuild(player, context);
        });
        UiActions.register("guild.demote", (player, context, args) -> {
            context.game().guilds().demote(player, UiArgs.uuid(args, "id"));
            sendGuild(player, context);
        });
    }

    /** The {@code guild} page: the player's guild (members, ranks, contribution) or pending invites. */
    static void sendGuild(ServerPlayer player, ServerContext context) {
        GuildManager guilds = context.game().guilds();
        JsonObject data = new JsonObject();
        data.addProperty("available", guilds.available());
        Optional<Guild> guild = guilds.service().guildOf(player.getUUID());
        if (guild.isPresent()) {
            Guild g = guild.get();
            data.addProperty("name", g.name());
            data.addProperty("tag", g.tag());
            data.addProperty("level", guilds.service().level(g));
            data.addProperty("cap", guilds.service().memberCap(g));
            data.addProperty("contribution", g.totalContribution());
            data.addProperty("myRank", g.rankOf(player.getUUID()).map(r -> r.name().toLowerCase(java.util.Locale.ROOT)).orElse("member"));
            JsonArray members = new JsonArray();
            for (Map.Entry<UUID, com.eternalrealm.guild.GuildMember> entry : g.members().entrySet()) {
                JsonObject member = new JsonObject();
                member.addProperty("id", entry.getKey().toString());
                member.addProperty("name", Names.of(player.server, entry.getKey()));
                member.addProperty("rank", entry.getValue().rank().name().toLowerCase(java.util.Locale.ROOT));
                member.addProperty("online", player.server.getPlayerList().getPlayer(entry.getKey()) != null);
                members.add(member);
            }
            data.add("members", members);
        } else {
            JsonArray invites = new JsonArray();
            guilds.service().invitesFor(player.getUUID(), java.time.Instant.now()).forEach(invite ->
                    guilds.service().guild(invite.guildId()).ifPresent(g -> invites.add(g.name() + " [" + g.tag() + "]")));
            data.add("invites", invites);
        }
        UiData.send(player, "guild", data);
    }
}
