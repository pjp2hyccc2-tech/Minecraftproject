package com.eternalrealm.guild.battle.forge;

import com.eternalrealm.combat.forge.PvpRules;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.estate.forge.EstateLevels;
import com.eternalrealm.estate.forge.EstateService;
import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildMerit;
import com.eternalrealm.guild.GuildRank;
import com.eternalrealm.guild.battle.BattleRecord;
import com.eternalrealm.guild.battle.BattleRules;
import com.eternalrealm.guild.battle.GuildBattle;
import com.eternalrealm.guild.battle.GuildBattles;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.social.forge.GuildManager;
import com.eternalrealm.social.forge.Names;
import com.eternalrealm.world.forge.blueprint.Blueprint;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Guild battles on the server (ギルド対抗戦, docs/modules/guild_battles.md): challenges between guilds, the muster,
 * the fight in one of the arenas (estates of kind {@value #KIND}), knock-outs, the score in a boss bar, the results,
 * rewards and records. The rules are {@link GuildBattle}; this class only does what they say. Server thread.
 *
 * <p>Players are moved only from the server tick (never from inside a packet handler), like the guild gates.</p>
 */
public final class GuildBattleService {
    private static final Logger LOG = LogUtils.getLogger();
    /** Estate kind and design of the arenas. */
    public static final String KIND = "arena";
    public static final String DESIGN = "colosseum";
    /** Arenas (battles at the same time). */
    public static final int ARENAS = 3;
    public static final String CHANNEL = "guild.battle";
    static final String SPAWN_RED = "spawn.red";
    static final String SPAWN_BLUE = "spawn.blue";
    /** Gold for every fighter still there at the end. */
    static final long GOLD_WIN = 3_000;
    static final long GOLD_DRAW = 2_000;
    static final long GOLD_LOSS = 1_000;
    /** A blow this recent counts as the knock-out of a fall that followed it (ticks). */
    static final long CREDIT_TICKS = 200;

    private static GuildBattleService current;

    final MinecraftServer server;
    final GuildBattles battles = new GuildBattles(BattleRules.defaults(), ARENAS);
    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();
    /** The last opponent who hurt a fighter, and when. */
    private final Map<UUID, UUID> lastHitBy = new HashMap<>();
    private final Map<UUID, Long> lastHitAt = new HashMap<>();

    private GuildBattleService(MinecraftServer server) {
        this.server = server;
    }

    public static Optional<GuildBattleService> get() {
        return Optional.ofNullable(current);
    }

    static void start(MinecraftServer server) {
        current = new GuildBattleService(server);
        current.prepareArenas();
    }

    static void stop() {
        if (current != null) {
            current.bars.values().forEach(ServerBossEvent::removeAllPlayers);
        }
        current = null;
    }

    long now() {
        return server.getTickCount();
    }

    // ------------------------------------------------------------------ the arenas

    /** The estate key of arena {@code slot} (owned by no one: a fixed id per arena). */
    public static EstateKey arena(int slot) {
        return new EstateKey(KIND, new UUID(0x4152454E41L, slot), DESIGN);
    }

    /** The arena number of an arena's owner id, or -1. */
    static int slotOf(UUID owner) {
        if (owner.getMostSignificantBits() != 0x4152454E41L) {
            return -1;
        }
        long slot = owner.getLeastSignificantBits();
        return slot >= 0 && slot < ARENAS ? (int) slot : -1;
    }

    /** The arenas are claimed (built in the background) when the server starts. */
    private void prepareArenas() {
        if (!EstateService.available()) {
            LOG.warn("The estates are not running: no guild battles");
            return;
        }
        for (int slot = 0; slot < ARENAS; slot++) {
            EstateKey key = arena(slot);
            if (EstateService.status(key).phase() == EstateStatus.Phase.NONE) {
                LOG.info("Building guild battle arena {}: {}", slot, EstateService.claim(key));
            }
        }
    }

    static boolean arenaReady(int slot) {
        return EstateService.ready(arena(slot));
    }

    /** The arena's access rule: the fighters of the battle in it, while it is on. */
    static boolean mayBeIn(ServerPlayer player, EstateKey key) {
        GuildBattleService service = current;
        int slot = slotOf(key.owner());
        if (service == null || slot < 0) {
            return false;
        }
        return service.battles.inArena(slot).filter(battle -> battle.phase() != GuildBattle.Phase.MUSTER)
                .map(battle -> battle.isPresent(player.getUUID())).orElse(false);
    }

    /** Whether {@code player} stands in the arena of {@code battle}. */
    static boolean inArena(ServerPlayer player, GuildBattle battle) {
        return EstateLevels.isEstateLevel(player.level())
                && EstateService.at(player).map(key -> KIND.equals(key.kind()) && slotOf(key.owner()) == battle.slot())
                .orElse(false);
    }

    // ------------------------------------------------------------------ PvP

    /** The arena rule of {@link PvpRules}: opponents in the same running fight, inside its arena. */
    static boolean mayHurt(ServerPlayer attacker, ServerPlayer victim) {
        GuildBattleService service = current;
        if (service == null) {
            return false;
        }
        Optional<GuildBattle> battle = service.battles.battleOfPlayer(attacker.getUUID());
        return battle.isPresent() && battle.get().mayHurt(attacker.getUUID(), victim.getUUID(), service.now())
                && inArena(attacker, battle.get()) && inArena(victim, battle.get());
    }

    // ------------------------------------------------------------------ challenges

    private static Optional<Guild> guildOf(ServerPlayer player) {
        return ServerContext.current().map(c -> c.game().guilds()).filter(GuildManager::available)
                .flatMap(g -> g.service().guildOf(player.getUUID()));
    }

    private static GuildBattle.Team team(Guild guild) {
        return new GuildBattle.Team(guild.id(), guild.name(), guild.tag());
    }

    private static boolean leads(Guild guild, ServerPlayer player) {
        return guild.rankOf(player.getUUID()).map(GuildRank::canInvite).orElse(false);
    }

    private static Optional<Guild> guildByName(String name) {
        return ServerContext.current().map(c -> c.game().guilds().service()).flatMap(service -> {
            String wanted = name == null ? "" : name.trim().replace('_', ' ');
            Optional<Guild> byTag = service.byTag(wanted);
            return byTag.isPresent() ? byTag : service.byName(wanted);
        });
    }

    /** Members of {@code guild} online now. */
    int online(Guild guild) {
        int n = 0;
        for (UUID member : guild.members().keySet()) {
            if (server.getPlayerList().getPlayer(member) != null) {
                n++;
            }
        }
        return n;
    }

    /** A master or officer of {@code player}'s guild challenges the guild named (or tagged) {@code target}. */
    void challenge(ServerPlayer player, String target) {
        Optional<Guild> own = guildOf(player);
        if (own.isEmpty()) {
            tell(player, "message.eternalrealm.guild_battle.not_in_guild");
            return;
        }
        if (!leads(own.get(), player)) {
            tell(player, "message.eternalrealm.guild_battle.leaders_only");
            return;
        }
        Optional<Guild> other = guildByName(target);
        if (other.isEmpty()) {
            tell(player, "message.eternalrealm.guild_battle.no_such_guild", target);
            return;
        }
        if (!other.get().id().equals(own.get().id()) && online(other.get()) == 0) {
            tell(player, "message.eternalrealm.guild_battle.nobody_online", other.get().name());
            return;
        }
        GuildBattles.Answer answer = battles.challenge(team(own.get()), team(other.get()), now());
        if (!answer.ok()) {
            tell(player, answer.messageKey(), other.get().name());
            return;
        }
        toGuild(own.get(), Component.translatable("message.eternalrealm.guild_battle.challenge_sent",
                player.getDisplayName(), other.get().name()).withStyle(ChatFormatting.GOLD));
        String tag = own.get().tag();
        Component buttons = Component.literal(" ")
                .append(Names.button("message.eternalrealm.guild_battle.accept_button", "/guildbattle accept " + tag,
                        ChatFormatting.GREEN))
                .append(" ")
                .append(Names.button("message.eternalrealm.guild_battle.decline_button",
                        "/guildbattle decline " + tag, ChatFormatting.RED));
        long seconds = battles.rules().challengeTicks() / BattleRules.TICKS_PER_SECOND;
        for (UUID member : other.get().members().keySet()) {
            ServerPlayer target2 = server.getPlayerList().getPlayer(member);
            if (target2 == null) {
                continue;
            }
            MutableComponent line = Component.translatable("message.eternalrealm.guild_battle.challenge_received",
                    own.get().name(), own.get().tag(), seconds).withStyle(ChatFormatting.GOLD);
            if (leads(other.get(), target2)) {
                line.append(buttons);
            }
            target2.sendSystemMessage(line);
            target2.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
        }
        pushAll(own.get());
        pushAll(other.get());
    }

    /** A master or officer accepts the challenge from {@code from} (null: the oldest). */
    void accept(ServerPlayer player, String from) {
        Optional<Guild> own = guildOf(player);
        if (own.isEmpty()) {
            tell(player, "message.eternalrealm.guild_battle.not_in_guild");
            return;
        }
        if (!leads(own.get(), player)) {
            tell(player, "message.eternalrealm.guild_battle.leaders_only");
            return;
        }
        UUID fromGuild = null;
        if (from != null && !from.isBlank()) {
            Optional<Guild> challenger = guildByName(from);
            if (challenger.isEmpty()) {
                tell(player, "message.eternalrealm.guild_battle.no_such_guild", from);
                return;
            }
            fromGuild = challenger.get().id();
        }
        GuildBattles.Accepted accepted = battles.accept(own.get().id(), fromGuild, UUID.randomUUID(), now(),
                GuildBattleService::arenaReady);
        if (!accepted.answer().ok()) {
            tell(player, accepted.answer().messageKey(), "");
            return;
        }
        GuildBattle battle = accepted.battle();
        long seconds = battle.rules().musterTicks() / BattleRules.TICKS_PER_SECOND;
        Component join = Names.button("message.eternalrealm.guild_battle.join_button", "/guildbattle join",
                ChatFormatting.GREEN);
        for (GuildBattle.Side side : GuildBattle.Side.values()) {
            GuildBattle.Team team = battle.team(side);
            GuildBattle.Team foe = battle.team(side.other());
            guild(team.guild()).ifPresent(guild -> {
                for (UUID member : guild.members().keySet()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(member);
                    if (p != null) {
                        p.sendSystemMessage(Component.translatable("message.eternalrealm.guild_battle.muster",
                                foe.name(), foe.tag(), seconds, battle.rules().maxPerSide()).withStyle(ChatFormatting.GOLD)
                                .append(" ").append(join));
                        p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
                    }
                }
            });
        }
        broadcast(Component.translatable("message.eternalrealm.guild_battle.announce", battle.red().name(),
                battle.blue().name()).withStyle(ChatFormatting.YELLOW));
        // the one who accepts is in at once
        join(player);
    }

    void decline(ServerPlayer player, String from) {
        Optional<Guild> own = guildOf(player);
        if (own.isEmpty() || !leads(own.get(), player)) {
            tell(player, own.isEmpty() ? "message.eternalrealm.guild_battle.not_in_guild"
                    : "message.eternalrealm.guild_battle.leaders_only");
            return;
        }
        UUID fromGuild = from == null || from.isBlank() ? null : guildByName(from).map(Guild::id).orElse(null);
        List<GuildBattles.Challenge> declined = battles.decline(own.get().id(), fromGuild, now());
        if (declined.isEmpty()) {
            tell(player, GuildBattles.Answer.NO_CHALLENGE.messageKey(), "");
            return;
        }
        for (GuildBattles.Challenge challenge : declined) {
            guild(challenge.from().guild()).ifPresent(g -> toGuild(g, Component.translatable(
                    "message.eternalrealm.guild_battle.declined", own.get().name()).withStyle(ChatFormatting.GRAY)));
            guild(challenge.from().guild()).ifPresent(this::pushAll);
        }
        toGuild(own.get(), Component.translatable("message.eternalrealm.guild_battle.declined_self",
                declined.get(0).from().name()).withStyle(ChatFormatting.GRAY));
        pushAll(own.get());
    }

    /** A master or officer takes their guild's challenge back. */
    void withdrawChallenge(ServerPlayer player) {
        Optional<Guild> own = guildOf(player);
        if (own.isEmpty() || !leads(own.get(), player)) {
            return;
        }
        battles.withdraw(own.get().id()).ifPresent(challenge -> {
            toGuild(own.get(), Component.translatable("message.eternalrealm.guild_battle.withdrawn",
                    challenge.to().name()).withStyle(ChatFormatting.GRAY));
            guild(challenge.to().guild()).ifPresent(this::pushAll);
        });
        pushAll(own.get());
    }

    /** A member of a guild that is mustering joins its side. */
    void join(ServerPlayer player) {
        Optional<Guild> own = guildOf(player);
        Optional<GuildBattle> battle = own.flatMap(g -> battles.battleOfGuild(g.id()));
        if (battle.isEmpty()) {
            tell(player, "message.eternalrealm.guild_battle.no_battle");
            return;
        }
        if (com.eternalrealm.dungeon.forge.DungeonLevels.isDungeonLevel(player.level())) {
            tell(player, "message.eternalrealm.guild_battle.busy_elsewhere");
            return;
        }
        GuildBattle.JoinResult result = battle.get().join(player.getUUID(), player.getGameProfile().getName(),
                own.get().id());
        switch (result) {
            case OK -> {
                GuildBattle b = battle.get();
                GuildBattle.Side side = b.sideOf(player.getUUID()).orElseThrow();
                toBattle(b, Component.translatable("message.eternalrealm.guild_battle.joined", player.getDisplayName(),
                        b.team(side).name(), b.count(side), b.rules().maxPerSide()).withStyle(sideColor(side)));
                if (b.count(GuildBattle.Side.RED) >= b.rules().maxPerSide()
                        && b.count(GuildBattle.Side.BLUE) >= b.rules().maxPerSide()) {
                    b.closeMusterEarly(now());   // both sides full: no need to wait
                }
                pushBattle(b);
            }
            case ALREADY_IN -> tell(player, "message.eternalrealm.guild_battle.already_in");
            case SIDE_FULL -> tell(player, "message.eternalrealm.guild_battle.side_full");
            case NOT_MUSTERING -> tell(player, "message.eternalrealm.guild_battle.too_late");
            case NOT_IN_THESE_GUILDS -> tell(player, "message.eternalrealm.guild_battle.no_battle");
        }
    }

    /** A fighter leaves the battle (before it, they are just off the list; during it, they go home). */
    void leave(ServerPlayer player) {
        Optional<GuildBattle> battle = battles.battleOfPlayer(player.getUUID());
        if (battle.isEmpty()) {
            return;
        }
        GuildBattle b = battle.get();
        boolean fighting = b.phase() != GuildBattle.Phase.MUSTER;
        handle(b, b.withdraw(player.getUUID(), now()));
        if (fighting && inArena(player, b)) {
            EstateService.leave(player);
        }
        tell(player, "message.eternalrealm.guild_battle.left");
        pushBattle(b);
    }

    /** A fighter logged out: out of the battle (a side with nobody left loses). */
    void loggedOut(ServerPlayer player) {
        battles.battleOfPlayer(player.getUUID()).ifPresent(battle -> {
            handle(battle, battle.withdraw(player.getUUID(), now()));
            pushBattle(battle);
        });
    }

    // ------------------------------------------------------------------ the tick

    void tick() {
        long now = now();
        for (GuildBattles.Challenge expired : battles.expire(now)) {
            guild(expired.from().guild()).ifPresent(g -> {
                toGuild(g, Component.translatable("message.eternalrealm.guild_battle.expired", expired.to().name())
                        .withStyle(ChatFormatting.GRAY));
                pushAll(g);
            });
            guild(expired.to().guild()).ifPresent(this::pushAll);
        }
        for (GuildBattle battle : battles.battles()) {
            // fighters who are gone (logged out, or left the arena some other way) are out of it
            if (battle.phase() == GuildBattle.Phase.PREPARE || battle.phase() == GuildBattle.Phase.FIGHT) {
                for (GuildBattle.Fighter fighter : battle.fighters()) {
                    if (!fighter.present()) {
                        continue;
                    }
                    ServerPlayer player = server.getPlayerList().getPlayer(fighter.id());
                    if (player == null || !inArena(player, battle)) {
                        handle(battle, battle.withdraw(fighter.id(), now));
                        if (player != null) {
                            tell(player, "message.eternalrealm.guild_battle.left");
                        }
                    }
                }
            } else if (battle.phase() == GuildBattle.Phase.MUSTER) {
                for (GuildBattle.Fighter fighter : battle.fighters()) {
                    if (server.getPlayerList().getPlayer(fighter.id()) == null) {
                        battle.withdraw(fighter.id(), now);
                    }
                }
            }
            if (battle.phase() == GuildBattle.Phase.PREPARE && now % 20 == 0) {
                long left = (battle.phaseEnds() - now + 19) / 20;
                if (left <= 5 && left > 0) {
                    titleAll(battle, Component.literal(Long.toString(left)).withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD), Component.empty(), 0, 18, 2);
                    soundAll(battle, SoundEvents.NOTE_BLOCK_HAT.value(), 1.2F);
                }
            }
            handle(battle, battle.tick(now));
            if (now % 10 == 0) {
                updateBar(battle);
            }
        }
    }

    // ------------------------------------------------------------------ what happens

    private void handle(GuildBattle battle, List<GuildBattle.Event> events) {
        for (GuildBattle.Event event : events) {
            try {
                if (event instanceof GuildBattle.Mustered mustered) {
                    mustered(battle, mustered);
                } else if (event instanceof GuildBattle.Cancelled cancelled) {
                    cancelled(battle, cancelled);
                } else if (event instanceof GuildBattle.Started) {
                    started(battle);
                } else if (event instanceof GuildBattle.KnockedOut ko) {
                    knockedOut(battle, ko);
                } else if (event instanceof GuildBattle.Ended ended) {
                    ended(battle, ended);
                } else if (event instanceof GuildBattle.Closed) {
                    closed(battle);
                }
            } catch (RuntimeException e) {
                LOG.error("Guild battle event {} failed", event, e);
            }
        }
    }

    /** The muster is over: the fighters go to the arena, each to their side's gate. */
    private void mustered(GuildBattle battle, GuildBattle.Mustered mustered) {
        EstateKey key = arena(battle.slot());
        long now = now();
        for (GuildBattle.Fighter fighter : mustered.fighters()) {
            ServerPlayer player = server.getPlayerList().getPlayer(fighter.id());
            if (player == null || player.isDeadOrDying()
                    || com.eternalrealm.dungeon.forge.DungeonLevels.isDungeonLevel(player.level())) {
                handle(battle, battle.withdraw(fighter.id(), now));
                continue;
            }
            if (!EstateService.enter(player, key).ok()) {
                handle(battle, battle.withdraw(fighter.id(), now));
                continue;
            }
            toGate(player, battle, fighter.side());
            player.setHealth(player.getMaxHealth());
            player.sendSystemMessage(Component.translatable("message.eternalrealm.guild_battle.arrived",
                    battle.team(fighter.side().other()).name()).withStyle(sideColor(fighter.side())));
        }
        if (battle.phase() != GuildBattle.Phase.PREPARE) {
            return;                                     // a side was left empty: it ended at once
        }
        ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.NOTCHED_10);
        bars.put(battle.id(), bar);
        updateBar(battle);
        titleAll(battle, Component.translatable("message.eternalrealm.guild_battle.prepare_title")
                        .withStyle(ChatFormatting.GOLD),
                Component.translatable("message.eternalrealm.guild_battle.versus", battle.red().name(),
                        battle.blue().name()), 10, 60, 10);
        pushBattle(battle);
    }

    private void cancelled(GuildBattle battle, GuildBattle.Cancelled cancelled) {
        Component why = Component.translatable("message.eternalrealm.guild_battle.cancelled." + cancelled.reason());
        for (GuildBattle.Side side : GuildBattle.Side.values()) {
            guild(battle.team(side).guild()).ifPresent(g -> toGuild(g, Component.translatable(
                    "message.eternalrealm.guild_battle.cancelled", battle.team(side.other()).name(), why)
                    .withStyle(ChatFormatting.GRAY)));
        }
        close(battle);
    }

    private void started(GuildBattle battle) {
        titleAll(battle, Component.translatable("message.eternalrealm.guild_battle.start_title")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.translatable(
                "message.eternalrealm.guild_battle.start_sub", battle.rules().knockOutTarget(),
                battle.rules().fightTicks() / BattleRules.TICKS_PER_SECOND / 60), 5, 40, 10);
        soundAll(battle, SoundEvents.RAID_HORN.value(), 1.0F);
        updateBar(battle);
    }

    private void knockedOut(GuildBattle battle, GuildBattle.KnockedOut ko) {
        Component line = ko.attacker() == null
                ? Component.translatable("message.eternalrealm.guild_battle.fell", ko.victim().name(), ko.red(),
                ko.blue())
                : Component.translatable("message.eternalrealm.guild_battle.knocked_out", ko.attacker().name(),
                ko.victim().name(), ko.red(), ko.blue());
        toBattle(battle, line.copy().withStyle(sideColor(ko.scored())));
        if (ko.attacker() != null) {
            ServerPlayer attacker = server.getPlayerList().getPlayer(ko.attacker().id());
            if (attacker != null) {
                attacker.playNotifySound(SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.4F);
            }
        }
        updateBar(battle);
    }

    private void ended(GuildBattle battle, GuildBattle.Ended ended) {
        ServerContext context = ServerContext.current().orElse(null);
        GuildBattleData data = GuildBattleData.get(server);
        for (GuildBattle.Side side : GuildBattle.Side.values()) {
            GuildBattle.Team team = battle.team(side);
            int own = battle.score(side);
            int other = battle.score(side.other());
            // the guild's merit and record
            if (context != null) {
                context.game().guilds().contributeGuild(team.guild(), GuildMerit.battle(own, other));
            }
            data.put(team.guild(), data.record(team.guild()).after(side, ended.winner(), own));
            boolean won = ended.winner().map(w -> w == side).orElse(false);
            boolean draw = ended.winner().isEmpty();
            long gold = won ? GOLD_WIN : draw ? GOLD_DRAW : GOLD_LOSS;
            for (GuildBattle.Fighter fighter : battle.fighters(side)) {
                ServerPlayer player = server.getPlayerList().getPlayer(fighter.id());
                if (player == null) {
                    continue;
                }
                if (fighter.present() && context != null) {
                    context.playerData().grantGold(player.getUUID(), gold);
                }
                title(player, Component.translatable(won ? "message.eternalrealm.guild_battle.won"
                                : draw ? "message.eternalrealm.guild_battle.draw" : "message.eternalrealm.guild_battle.lost")
                                .withStyle(won ? ChatFormatting.GOLD : draw ? ChatFormatting.WHITE : ChatFormatting.GRAY,
                                        ChatFormatting.BOLD),
                        Component.literal(battle.red().tag() + " " + ended.red() + " - " + ended.blue() + " "
                                + battle.blue().tag()), 5, 80, 20);
                player.playNotifySound(won ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.NOTE_BLOCK_BASS.value(),
                        SoundSource.PLAYERS, 0.9F, 1.0F);
            }
        }
        Component winner = ended.winner().map(side -> (Component) Component.literal(battle.team(side).name())
                .withStyle(sideColor(side), ChatFormatting.BOLD))
                .orElse(Component.translatable("message.eternalrealm.guild_battle.nobody"));
        Component mvp = ended.mvp().map(f -> (Component) Component.translatable(
                "message.eternalrealm.guild_battle.mvp", f.name(), f.knockOuts())).orElse(Component.empty());
        Component summary = Component.translatable("message.eternalrealm.guild_battle.result", battle.red().name(),
                ended.red(), ended.blue(), battle.blue().name(), winner).withStyle(ChatFormatting.GOLD)
                .append(" ").append(mvp);
        for (GuildBattle.Side side : GuildBattle.Side.values()) {
            guild(battle.team(side).guild()).ifPresent(g -> toGuild(g, summary));
        }
        broadcast(Component.translatable("message.eternalrealm.guild_battle.result_broadcast", battle.red().name(),
                ended.red(), ended.blue(), battle.blue().name()).withStyle(ChatFormatting.YELLOW));
        updateBar(battle);
        pushBattle(battle);
    }

    /** The results were shown: everyone still in the arena goes back where they came from. */
    private void closed(GuildBattle battle) {
        for (GuildBattle.Fighter fighter : battle.fighters()) {
            ServerPlayer player = server.getPlayerList().getPlayer(fighter.id());
            if (player != null && inArena(player, battle)) {
                player.setHealth(player.getMaxHealth());
                player.removeAllEffects();
                EstateService.leave(player);
            }
        }
        close(battle);
    }

    private void close(GuildBattle battle) {
        ServerBossEvent bar = bars.remove(battle.id());
        if (bar != null) {
            bar.removeAllPlayers();
        }
        for (UUID id : battle.fighterIds()) {
            lastHitBy.remove(id);
            lastHitAt.remove(id);
        }
        battles.close(battle, now());
        pushBattle(battle);
    }

    // ------------------------------------------------------------------ knock-outs

    /**
     * A fighter would die in the arena: they are knocked out instead (the point goes to the other side, credited to
     * the opponent who struck the last blow within ten seconds) and recover at their side's gate. @return whether the
     * death was a knock-out (cancel it)
     */
    boolean onDeath(ServerPlayer victim, DamageSource source) {
        Optional<GuildBattle> found = battles.battleOfPlayer(victim.getUUID());
        if (found.isEmpty() || !inArena(victim, found.get())) {
            return false;
        }
        GuildBattle battle = found.get();
        long now = now();
        UUID attacker = attackerOf(source);
        if (attacker == null && lastHitAt.getOrDefault(victim.getUUID(), Long.MIN_VALUE) + CREDIT_TICKS >= now) {
            attacker = lastHitBy.get(victim.getUUID());
        }
        victim.setHealth(victim.getMaxHealth());
        victim.clearFire();
        victim.removeAllEffects();
        victim.fallDistance = 0.0F;
        victim.setDeltaMovement(Vec3.ZERO);
        List<GuildBattle.Event> events = battle.knockOut(victim.getUUID(), attacker, now);
        lastHitBy.remove(victim.getUUID());
        lastHitAt.remove(victim.getUUID());
        battle.fighter(victim.getUUID()).ifPresent(fighter -> {
            toGate(victim, battle, fighter.side());
            int recover = (int) battle.rules().respawnTicks();
            int shelter = (int) (battle.rules().respawnTicks() + battle.rules().shieldTicks());
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.max(20, recover), 0, false, false));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, recover, 3, false, false));
            victim.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, shelter, 4, false, true));
            title(victim, Component.translatable("message.eternalrealm.guild_battle.ko_title")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty(), 0, 30, 10);
        });
        handle(battle, events);
        return true;
    }

    /** Remembers the opponent who hurt a fighter last (for the knock-out of a fall that follows). */
    void onHurt(ServerPlayer victim, DamageSource source) {
        UUID attacker = attackerOf(source);
        if (attacker != null && !attacker.equals(victim.getUUID())
                && battles.battleOfPlayer(victim.getUUID()).isPresent()) {
            lastHitBy.put(victim.getUUID(), attacker);
            lastHitAt.put(victim.getUUID(), now());
        }
    }

    /**
     * Whether a fighter in the arena must not be hurt now (by anything: before the fight, when recovering or
     * sheltered, after it).
     */
    boolean sheltered(ServerPlayer victim) {
        Optional<GuildBattle> found = battles.battleOfPlayer(victim.getUUID());
        if (found.isEmpty() || !inArena(victim, found.get())) {
            return false;
        }
        GuildBattle battle = found.get();
        return battle.phase() != GuildBattle.Phase.FIGHT
                || battle.fighter(victim.getUUID()).map(f -> f.sheltered(now())).orElse(true);
    }

    private static UUID attackerOf(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            return player.getUUID();
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer owner) {
            return owner.getUUID();
        }
        return null;
    }

    /** Puts a fighter at their side's gate in the arena. */
    private void toGate(ServerPlayer player, GuildBattle battle, GuildBattle.Side side) {
        EstateKey key = arena(battle.slot());
        Optional<Blueprint.Spot> spot = EstateService.spot(key, side == GuildBattle.Side.RED ? SPAWN_RED : SPAWN_BLUE)
                .or(() -> EstateService.spot(key, EstateService.ENTRY_SPOT));
        if (spot.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        // a few steps apart, so a whole side does not stand in one block
        int spread = battle.fighters(side).stream().map(GuildBattle.Fighter::id).toList().indexOf(player.getUUID());
        double dx = spread < 0 ? 0.0 : ((spread % 5) - 2) * 1.2;
        double dz = spread < 0 ? 0.0 : (spread / 5) * 1.5;
        double yaw = Math.toRadians(spot.get().yaw());
        double sx = Math.cos(yaw) * dx - Math.sin(yaw) * -dz;
        double sz = Math.sin(yaw) * dx + Math.cos(yaw) * -dz;
        player.fallDistance = 0.0F;
        player.teleportTo(level, spot.get().pos().getX() + 0.5 + sx, spot.get().pos().getY(),
                spot.get().pos().getZ() + 0.5 + sz, spot.get().yaw(), 0.0F);
    }

    // ------------------------------------------------------------------ the bar, titles, messages

    private void updateBar(GuildBattle battle) {
        ServerBossEvent bar = bars.get(battle.id());
        if (bar == null) {
            return;
        }
        long now = now();
        long left = Math.max(0, battle.phaseEnds() - now);
        long seconds = left / 20;
        String time = String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        String key = switch (battle.phase()) {
            case PREPARE -> "bossbar.eternalrealm.guild_battle.prepare";
            case FIGHT -> "bossbar.eternalrealm.guild_battle.fight";
            default -> "bossbar.eternalrealm.guild_battle.ended";
        };
        bar.setName(Component.translatable(key, Component.literal(battle.red().tag()).withStyle(ChatFormatting.RED),
                battle.score(GuildBattle.Side.RED), battle.score(GuildBattle.Side.BLUE),
                Component.literal(battle.blue().tag()).withStyle(ChatFormatting.BLUE), time));
        long total = switch (battle.phase()) {
            case PREPARE -> battle.rules().prepareTicks();
            case FIGHT -> battle.rules().fightTicks();
            default -> Math.max(1, battle.rules().endTicks());
        };
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, (float) left / total)));
        bar.setColor(battle.leader().map(side -> side == GuildBattle.Side.RED ? BossEvent.BossBarColor.RED
                : BossEvent.BossBarColor.BLUE).orElse(BossEvent.BossBarColor.YELLOW));
        for (GuildBattle.Fighter fighter : battle.fighters()) {
            ServerPlayer player = server.getPlayerList().getPlayer(fighter.id());
            boolean shown = player != null && bar.getPlayers().contains(player);
            boolean there = player != null && fighter.present() && inArena(player, battle);
            if (there && !shown) {
                bar.addPlayer(player);
            } else if (!there && shown) {
                bar.removePlayer(player);
            }
        }
    }

    private void titleAll(GuildBattle battle, Component title, Component subtitle, int in, int stay, int out) {
        for (GuildBattle.Fighter fighter : battle.fighters()) {
            ServerPlayer player = server.getPlayerList().getPlayer(fighter.id());
            if (player != null && fighter.present()) {
                title(player, title, subtitle, in, stay, out);
            }
        }
    }

    private static void title(ServerPlayer player, Component title, Component subtitle, int in, int stay, int out) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    private void soundAll(GuildBattle battle, net.minecraft.sounds.SoundEvent sound, float pitch) {
        for (GuildBattle.Fighter fighter : battle.fighters()) {
            ServerPlayer player = server.getPlayerList().getPlayer(fighter.id());
            if (player != null && fighter.present()) {
                player.playNotifySound(sound, SoundSource.PLAYERS, 1.0F, pitch);
            }
        }
    }

    private void toBattle(GuildBattle battle, Component message) {
        for (GuildBattle.Fighter fighter : battle.fighters()) {
            Names.send(server, fighter.id(), message);
        }
    }

    private void toGuild(Guild guild, Component message) {
        guild.members().keySet().forEach(member -> Names.send(server, member, message));
    }

    private void broadcast(Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static ChatFormatting sideColor(GuildBattle.Side side) {
        return side == GuildBattle.Side.RED ? ChatFormatting.RED : ChatFormatting.BLUE;
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), false);
    }

    private Optional<Guild> guild(UUID id) {
        return ServerContext.current().flatMap(c -> c.game().guilds().service().guild(id));
    }

    // ------------------------------------------------------------------ the page

    /** Every online member of the guilds of {@code battle} gets the battle page again. */
    void pushBattle(GuildBattle battle) {
        for (GuildBattle.Side side : GuildBattle.Side.values()) {
            guild(battle.team(side).guild()).ifPresent(this::pushAll);
        }
    }

    void pushAll(Guild guild) {
        for (UUID member : guild.members().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                GuildBattleUi.send(player, this);
            }
        }
    }

    /** Guild ids whose members are online, with their counts (for the page's list of guilds to challenge). */
    Map<UUID, Integer> onlineGuilds() {
        Map<UUID, Integer> counts = new HashMap<>();
        ServerContext.current().map(c -> c.game().guilds().service()).ifPresent(service -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                service.guildOf(player.getUUID()).ifPresent(g -> counts.merge(g.id(), 1, Integer::sum));
            }
        });
        return counts;
    }

    List<GuildBattle> all() {
        return new ArrayList<>(battles.battles());
    }
}
