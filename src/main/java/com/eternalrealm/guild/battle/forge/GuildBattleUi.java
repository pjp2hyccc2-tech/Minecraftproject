package com.eternalrealm.guild.battle.forge;

import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildRank;
import com.eternalrealm.guild.battle.BattleRecord;
import com.eternalrealm.guild.battle.BattleRules;
import com.eternalrealm.guild.battle.GuildBattle;
import com.eternalrealm.guild.battle.GuildBattles;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The battle page of the guild screen ({@value GuildBattleService#CHANNEL}): the guild's record, the battle under way
 * or the challenges open, and the guilds online to challenge. The screen only shows it and sends requests.
 */
public final class GuildBattleUi {
    public static final String VIEW = "guild.battle.view";
    public static final String CHALLENGE = "guild.battle.challenge";
    public static final String ACCEPT = "guild.battle.accept";
    public static final String DECLINE = "guild.battle.decline";
    public static final String WITHDRAW = "guild.battle.withdraw";
    public static final String JOIN = "guild.battle.join";
    public static final String LEAVE = "guild.battle.leave";
    /** At most this many guilds in the list. */
    static final int MAX_GUILDS = 24;

    private GuildBattleUi() {
    }

    static void register() {
        UiActions.register(VIEW, (player, context, args) -> GuildBattleService.get().ifPresent(s -> send(player, s)));
        UiActions.register(CHALLENGE, (player, context, args) -> GuildBattleService.get().ifPresent(s -> {
            s.challenge(player, UiArgs.string(args, "tag", 40));
            send(player, s);
        }));
        UiActions.register(ACCEPT, (player, context, args) -> GuildBattleService.get().ifPresent(s -> {
            s.accept(player, args.has("tag") ? UiArgs.string(args, "tag", 40) : null);
            send(player, s);
        }));
        UiActions.register(DECLINE, (player, context, args) -> GuildBattleService.get().ifPresent(s -> {
            s.decline(player, args.has("tag") ? UiArgs.string(args, "tag", 40) : null);
            send(player, s);
        }));
        UiActions.register(WITHDRAW, (player, context, args) -> GuildBattleService.get().ifPresent(s -> {
            s.withdrawChallenge(player);
            send(player, s);
        }));
        UiActions.register(JOIN, (player, context, args) -> GuildBattleService.get().ifPresent(s -> {
            s.join(player);
            send(player, s);
        }));
        UiActions.register(LEAVE, (player, context, args) -> GuildBattleService.get().ifPresent(s -> {
            s.leave(player);
            send(player, s);
        }));
    }

    static void send(ServerPlayer player, GuildBattleService service) {
        UiData.send(player, GuildBattleService.CHANNEL, document(player, service));
    }

    static JsonObject document(ServerPlayer player, GuildBattleService service) {
        JsonObject data = new JsonObject();
        data.addProperty("nonce", System.nanoTime());
        long now = service.now();
        Optional<Guild> own = ServerContext.current().flatMap(c -> c.game().guilds().service().guildOf(player.getUUID()));
        data.addProperty("inGuild", own.isPresent());
        if (own.isEmpty()) {
            return data;
        }
        Guild guild = own.get();
        boolean leads = guild.rankOf(player.getUUID()).map(GuildRank::canInvite).orElse(false);
        data.addProperty("leads", leads);
        BattleRecord record = GuildBattleData.get(service.server).record(guild.id());
        JsonObject rec = new JsonObject();
        rec.addProperty("w", record.wins());
        rec.addProperty("d", record.draws());
        rec.addProperty("l", record.losses());
        rec.addProperty("k", record.knockOuts());
        data.add("record", rec);
        GuildBattles battles = service.battles;
        long resting = battles.restingUntil(guild.id()) - now;
        data.addProperty("resting", Math.max(0L, resting / BattleRules.TICKS_PER_SECOND));
        battles.battleOfGuild(guild.id()).ifPresent(battle -> data.add("battle", battle(battle, player, now)));
        JsonArray incoming = new JsonArray();
        for (GuildBattles.Challenge challenge : battles.challengesTo(guild.id(), now)) {
            JsonObject c = new JsonObject();
            c.addProperty("name", challenge.from().name());
            c.addProperty("tag", challenge.from().tag());
            c.addProperty("left", Math.max(0L, (challenge.expiresAt() - now) / BattleRules.TICKS_PER_SECOND));
            incoming.add(c);
        }
        data.add("incoming", incoming);
        battles.challengeFrom(guild.id(), now).ifPresent(challenge -> {
            JsonObject c = new JsonObject();
            c.addProperty("name", challenge.to().name());
            c.addProperty("tag", challenge.to().tag());
            c.addProperty("left", Math.max(0L, (challenge.expiresAt() - now) / BattleRules.TICKS_PER_SECOND));
            data.add("outgoing", c);
        });
        JsonArray guilds = new JsonArray();
        List<Map.Entry<UUID, Integer>> online = new ArrayList<>(service.onlineGuilds().entrySet());
        online.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        ServerContext.current().map(c -> c.game().guilds()).ifPresent(manager -> {
            for (Map.Entry<UUID, Integer> entry : online) {
                if (entry.getKey().equals(guild.id()) || guilds.size() >= MAX_GUILDS) {
                    continue;
                }
                manager.service().guild(entry.getKey()).ifPresent(other -> {
                    JsonObject g = new JsonObject();
                    g.addProperty("name", other.name());
                    g.addProperty("tag", other.tag());
                    g.addProperty("level", manager.service().level(other));
                    g.addProperty("online", entry.getValue());
                    g.addProperty("busy", battles.battleOfGuild(other.id()).isPresent()
                            || battles.restingUntil(other.id()) > now);
                    BattleRecord theirs = GuildBattleData.get(service.server).record(other.id());
                    g.addProperty("w", theirs.wins());
                    g.addProperty("l", theirs.losses());
                    guilds.add(g);
                });
            }
        });
        data.add("guilds", guilds);
        return data;
    }

    private static JsonObject battle(GuildBattle battle, ServerPlayer player, long now) {
        JsonObject b = new JsonObject();
        b.addProperty("phase", battle.phase().id());
        b.addProperty("left", Math.max(0L, (battle.phaseEnds() - now) / BattleRules.TICKS_PER_SECOND));
        b.addProperty("max", battle.rules().maxPerSide());
        b.addProperty("target", battle.rules().knockOutTarget());
        b.addProperty("mySide", battle.sideOf(player.getUUID()).map(GuildBattle.Side::id).orElse(""));
        for (GuildBattle.Side side : GuildBattle.Side.values()) {
            JsonObject s = new JsonObject();
            s.addProperty("name", battle.team(side).name());
            s.addProperty("tag", battle.team(side).tag());
            s.addProperty("score", battle.score(side));
            JsonArray fighters = new JsonArray();
            for (GuildBattle.Fighter fighter : battle.fighters(side)) {
                JsonObject f = new JsonObject();
                f.addProperty("name", fighter.name());
                f.addProperty("ko", fighter.knockOuts());
                f.addProperty("falls", fighter.falls());
                f.addProperty("present", fighter.present());
                fighters.add(f);
            }
            s.add("fighters", fighters);
            b.add(side.id(), s);
        }
        battle.result().ifPresent(result -> b.addProperty("winner",
                result.winner().map(GuildBattle.Side::id).orElse("draw")));
        return b;
    }
}
