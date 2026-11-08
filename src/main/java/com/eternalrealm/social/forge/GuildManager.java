package com.eternalrealm.social.forge;

import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildEvent;
import com.eternalrealm.guild.GuildInvite;
import com.eternalrealm.guild.GuildMessages;
import com.eternalrealm.guild.GuildRepository;
import com.eternalrealm.guild.GuildResult;
import com.eternalrealm.guild.GuildRules;
import com.eternalrealm.guild.GuildService;
import com.eternalrealm.season.SeasonId;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Guilds (permanent across seasons): operations from commands and the journal, event messages, guild chat, the guild
 * tag in player names, contribution and persistence ({@code guilds.json}, saved on the I/O thread when changed).
 * Server thread only. A failed load disables guilds instead of starting empty (an empty save would erase them).
 */
public final class GuildManager {
    private static final Logger LOG = LogUtils.getLogger();
    /** I/O key: guild saves are serialised with each other. */
    private static final UUID IO_KEY = new UUID(0L, 0x6175696c64L);

    private final MinecraftServer server;
    private final GuildRepository repository;
    private final KeyedSerialExecutor<UUID> io;
    private final Clock clock;
    private final SeasonId season;
    private final GuildService service;
    private final boolean available;
    private boolean saveInFlight;

    public GuildManager(MinecraftServer server, GuildRepository repository, KeyedSerialExecutor<UUID> io, Clock clock,
                        SeasonId season) {
        this.server = Objects.requireNonNull(server, "server");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.io = Objects.requireNonNull(io, "io");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.season = Objects.requireNonNull(season, "season");
        GuildService loaded;
        boolean ok;
        try {
            loaded = new GuildService(GuildRules.defaults(), new java.util.SplittableRandom(), repository.loadAll());
            ok = true;
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Could not load guilds; guild features are disabled until the data is fixed", e);
            loaded = new GuildService(GuildRules.defaults(), new java.util.SplittableRandom(), List.of());
            ok = false;
        }
        this.service = loaded;
        this.available = ok;
        LOG.info("Loaded {} guild(s)", service.size());
    }

    public GuildService service() {
        return service;
    }

    public boolean available() {
        return available;
    }

    private Instant now() {
        return clock.instant();
    }

    // ------------------------------------------------------------------ operations

    public void create(ServerPlayer founder, String name, String tag) {
        if (!checkAvailable(founder)) {
            return;
        }
        report(founder, service.create(founder.getUUID(), name, tag, now()));
        refresh(founder.getUUID());
    }

    public void invite(ServerPlayer inviter, ServerPlayer invitee) {
        if (checkAvailable(inviter)) {
            report(inviter, service.invite(inviter.getUUID(), invitee.getUUID(), now()));
        }
    }

    public void accept(ServerPlayer player, String guildName) {
        if (!checkAvailable(player)) {
            return;
        }
        Optional<GuildInvite> invite = service.invitesFor(player.getUUID(), now()).stream()
                .filter(each -> guildName == null || service.guild(each.guildId())
                        .map(g -> g.name().equalsIgnoreCase(guildName) || g.tag().equalsIgnoreCase(guildName)).orElse(false))
                .reduce((first, second) -> second);
        if (invite.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.guild.no_invite")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        report(player, service.accept(player.getUUID(), invite.get().guildId(), now()));
        refresh(player.getUUID());
    }

    public void decline(ServerPlayer player) {
        for (GuildInvite invite : service.invitesFor(player.getUUID(), now())) {
            report(player, service.decline(player.getUUID(), invite.guildId(), now()));
        }
    }

    public void leave(ServerPlayer player) {
        if (checkAvailable(player)) {
            report(player, service.leave(player.getUUID()));
            refresh(player.getUUID());
        }
    }

    public void kick(ServerPlayer actor, UUID target) {
        if (checkAvailable(actor)) {
            report(actor, service.kick(actor.getUUID(), target));
            refresh(target);
        }
    }

    public void promote(ServerPlayer master, UUID target) {
        if (checkAvailable(master)) {
            report(master, service.promote(master.getUUID(), target));
        }
    }

    public void demote(ServerPlayer master, UUID target) {
        if (checkAvailable(master)) {
            report(master, service.demote(master.getUUID(), target));
        }
    }

    public void transfer(ServerPlayer master, UUID target) {
        if (checkAvailable(master)) {
            report(master, service.transferMaster(master.getUUID(), target));
        }
    }

    public void disband(ServerPlayer master) {
        if (!checkAvailable(master)) {
            return;
        }
        Optional<Guild> guild = service.guildOf(master.getUUID());
        report(master, service.disband(master.getUUID()));
        guild.ifPresent(g -> g.members().keySet().forEach(this::refresh));
    }

    /** Season contribution of a member (quests, dungeons, bosses). */
    public void contribute(UUID member, long points) {
        if (available && points > 0 && service.guildOf(member).isPresent()) {
            deliver(service.addContribution(member, season, points).events());
        }
    }

    public void chat(ServerPlayer sender, String message) {
        Optional<Guild> guild = service.guildOf(sender.getUUID());
        if (guild.isEmpty()) {
            sender.displayClientMessage(Component.translatable("message.eternalrealm.guild.not_in_guild")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        Component line = Component.translatable("chat.eternalrealm.guild", guild.get().tag(), sender.getDisplayName(),
                Component.literal(message)).withStyle(ChatFormatting.GREEN);
        guild.get().members().keySet().forEach(member -> Names.send(server, member, line));
    }

    /** Guild tag shown before a player's name ("[TAG] ") or empty. */
    public Optional<String> tagOf(UUID player) {
        return service.guildOf(player).map(Guild::tag);
    }

    private boolean checkAvailable(ServerPlayer player) {
        if (!available) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.guild.unavailable")
                    .withStyle(ChatFormatting.RED), true);
        }
        return available;
    }

    private void refresh(UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            player.refreshDisplayName();
            NameplateTeams.update(player);
        }
    }

    private void report(ServerPlayer actor, GuildResult result) {
        deliver(result.events());
        if (!result.outcome().equals(com.eternalrealm.guild.GuildOutcome.OK)) {
            result.outcome().messageKey().ifPresent(key ->
                    actor.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true));
        }
    }

    // ------------------------------------------------------------------ events -> chat

    private void deliver(List<GuildEvent> events) {
        for (GuildEvent event : events) {
            if (event instanceof GuildEvent.Created created) {
                Names.send(server, created.guild().master(),
                        msg(GuildMessages.CREATED, created.guild().name(), created.guild().tag()));
            } else if (event instanceof GuildEvent.Invited invited) {
                GuildInvite invite = invited.invite();
                String guildName = service.guild(invite.guildId()).map(Guild::name).orElse("?");
                toGuild(invite.guildId(), msg(GuildMessages.INVITE_SENT, Names.of(server, invite.invitee())));
                long seconds = Math.max(1, invite.expiresAt().getEpochSecond() - now().getEpochSecond());
                Names.send(server, invite.invitee(), msg(GuildMessages.INVITE_RECEIVED,
                        Names.of(server, invite.inviter()), guildName, seconds)
                        .append(" ").append(Names.button(GuildMessages.ACCEPT_BUTTON,
                                "/guild accept " + guildName.replace(' ', '_'), ChatFormatting.GREEN))
                        .append(" ").append(Names.button(GuildMessages.DECLINE_BUTTON, "/guild decline",
                                ChatFormatting.RED)));
            } else if (event instanceof GuildEvent.InviteExpired expired) {
                GuildInvite invite = expired.invite();
                toGuild(invite.guildId(), msg(GuildMessages.INVITE_EXPIRED_SENT, Names.of(server, invite.invitee())));
                Names.send(server, invite.invitee(), msg(GuildMessages.INVITE_EXPIRED_RECEIVED,
                        service.guild(invite.guildId()).map(Guild::name).orElse("?")));
            } else if (event instanceof GuildEvent.InviteDeclined declined) {
                toGuild(declined.invite().guildId(),
                        msg(GuildMessages.INVITE_DECLINED, Names.of(server, declined.invite().invitee())));
            } else if (event instanceof GuildEvent.InviteCancelled cancelled) {
                Names.send(server, cancelled.invite().invitee(), msg(GuildMessages.INVITE_CANCELLED,
                        service.guild(cancelled.invite().guildId()).map(Guild::name).orElse("?")));
            } else if (event instanceof GuildEvent.Joined joined) {
                toGuild(joined.guild().id(), msg(GuildMessages.JOINED, Names.of(server, joined.member())));
            } else if (event instanceof GuildEvent.Left left) {
                toGuild(left.guild().id(), msg(GuildMessages.LEFT, Names.of(server, left.member())));
                Names.send(server, left.member(), msg(GuildMessages.LEFT_SELF, left.guild().name()));
            } else if (event instanceof GuildEvent.Kicked kicked) {
                toGuild(kicked.guild().id(), msg(GuildMessages.KICKED, Names.of(server, kicked.member())));
                Names.send(server, kicked.member(), msg(GuildMessages.KICKED_SELF, kicked.guild().name()));
            } else if (event instanceof GuildEvent.RankChanged changed) {
                toGuild(changed.guild().id(), msg(GuildMessages.RANK_CHANGED, Names.of(server, changed.member()),
                        Component.translatable(changed.newRank().translationKey())));
            } else if (event instanceof GuildEvent.MasterTransferred transferred) {
                toGuild(transferred.guild().id(),
                        msg(GuildMessages.MASTER_TRANSFERRED, Names.of(server, transferred.newMaster())));
            } else if (event instanceof GuildEvent.LevelUp levelUp) {
                toGuild(levelUp.guild().id(), msg(GuildMessages.LEVEL_UP, levelUp.newLevel(),
                        service.memberCap(levelUp.guild())));
            } else if (event instanceof GuildEvent.Disbanded disbanded) {
                for (UUID member : disbanded.guild().members().keySet()) {
                    Names.send(server, member, msg(GuildMessages.DISBANDED, disbanded.guild().name()));
                }
            }
        }
    }

    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GREEN);
    }

    private void toGuild(UUID guildId, Component message) {
        service.guild(guildId).ifPresent(guild ->
                guild.members().keySet().forEach(member -> Names.send(server, member, message)));
    }

    // ------------------------------------------------------------------ tick & persistence

    /** Every second: invite expiry and saving changed guilds. */
    public void tickSecond() {
        if (!available) {
            return;
        }
        deliver(service.expireInvites(now()));
        saveIfDirty();
    }

    public void saveIfDirty() {
        if (!available || saveInFlight || !service.isDirty()) {
            return;
        }
        List<Guild> snapshot = List.copyOf(service.guilds());
        long revision = service.revision();
        saveInFlight = true;
        io.submit(IO_KEY, () -> {
            boolean ok;
            try {
                repository.saveAll(snapshot);
                ok = true;
            } catch (DataAccessException | RuntimeException e) {
                LOG.error("Saving guilds failed; will retry", e);
                ok = false;
            }
            boolean saved = ok;
            server.execute(() -> {
                saveInFlight = false;
                if (saved) {
                    service.markSaved(revision);
                }
            });
            return null;
        });
    }

    /** Shutdown: save synchronously (the server loop has stopped). */
    public void flush() {
        if (!available || !service.isDirty()) {
            return;
        }
        try {
            repository.saveAll(List.copyOf(service.guilds()));
            service.markSaved(service.revision());
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Final guild save failed", e);
        }
    }
}
