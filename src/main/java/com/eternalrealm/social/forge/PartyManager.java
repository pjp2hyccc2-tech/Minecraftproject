package com.eternalrealm.social.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.party.ExperienceShare;
import com.eternalrealm.party.ExperienceSplit;
import com.eternalrealm.party.Party;
import com.eternalrealm.party.PartyEvent;
import com.eternalrealm.party.PartyInvite;
import com.eternalrealm.party.PartyMessages;
import com.eternalrealm.party.PartyResult;
import com.eternalrealm.party.PartyRules;
import com.eternalrealm.party.PartyService;
import com.eternalrealm.party.ShareRules;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Parties on the running server (not persisted: they end with the server). Turns {@link PartyEvent}s into chat
 * messages, keeps every member's party frame up to date ({@code party} UI channel), removes members that stay offline
 * too long and splits monster EXP between nearby members. Server thread only.
 */
public final class PartyManager {
    /** Offline members are removed after this long (5 minutes): long enough to reconnect after a crash. */
    private static final long OFFLINE_REMOVAL_TICKS = 20L * 60 * 5;
    private static final int HUD_INTERVAL_TICKS = 10;
    /** Members within this distance (same dimension) share kill EXP. */
    public static final double SHARE_RANGE = 48.0;

    private final MinecraftServer server;
    private final PlayerDataService playerData;
    private final PartyService service = new PartyService(PartyRules.defaults());
    private final Map<UUID, Long> offlineSince = new HashMap<>();
    private final Map<UUID, String> lastHud = new HashMap<>();
    private final ShareRules shareRules = ShareRules.defaults();

    public PartyManager(MinecraftServer server, PlayerDataService playerData) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerData = Objects.requireNonNull(playerData, "playerData");
    }

    public PartyService service() {
        return service;
    }

    private long now() {
        return server.getTickCount();
    }

    // ------------------------------------------------------------------ operations

    /** Invites {@code invitee}; a player without a party founds one first. */
    public void invite(ServerPlayer inviter, ServerPlayer invitee) {
        UUID id = inviter.getUUID();
        if (service.partyOf(id).isEmpty()) {
            PartyResult created = service.create(id, now());
            if (!report(inviter, created)) {
                return;
            }
        }
        report(inviter, service.invite(id, invitee.getUUID(), now()));
    }

    /** Accepts the invite from {@code inviterName}, or the newest one when null. */
    public void accept(ServerPlayer player, String inviterName) {
        List<PartyInvite> invites = service.invitesFor(player.getUUID(), now());
        Optional<PartyInvite> chosen = invites.stream()
                .filter(invite -> inviterName == null || Names.of(server, invite.inviter()).equalsIgnoreCase(inviterName))
                .reduce((first, second) -> second);
        if (chosen.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.party.no_invite")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        report(player, service.accept(player.getUUID(), chosen.get().partyId(), now()));
    }

    public void decline(ServerPlayer player) {
        for (PartyInvite invite : service.invitesFor(player.getUUID(), now())) {
            report(player, service.decline(player.getUUID(), invite.partyId(), now()));
        }
    }

    public void leave(ServerPlayer player) {
        report(player, service.leave(player.getUUID()));
        clearHud(player);
    }

    public void kick(ServerPlayer leader, UUID member) {
        report(leader, service.kick(leader.getUUID(), member));
    }

    public void promote(ServerPlayer leader, UUID member) {
        report(leader, service.promote(leader.getUUID(), member));
    }

    public void disband(ServerPlayer leader) {
        report(leader, service.disband(leader.getUUID()));
    }

    public void convertToRaid(ServerPlayer leader) {
        report(leader, service.convertToRaid(leader.getUUID()));
    }

    public void chat(ServerPlayer sender, String message) {
        Optional<Party> party = service.partyOf(sender.getUUID());
        if (party.isEmpty()) {
            sender.displayClientMessage(Component.translatable("message.eternalrealm.party.not_in_party")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        Component line = Component.translatable("chat.eternalrealm.party", sender.getDisplayName(),
                Component.literal(message)).withStyle(ChatFormatting.AQUA);
        for (UUID member : party.get().memberIds()) {
            Names.send(server, member, line);
        }
    }

    /** @return whether the operation succeeded; failures are shown to {@code actor} */
    private boolean report(ServerPlayer actor, PartyResult result) {
        deliver(result.events());
        if (!result.isSuccess()) {
            result.outcome().messageKey().ifPresent(key ->
                    actor.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true));
        }
        return result.isSuccess();
    }

    // ------------------------------------------------------------------ events -> chat

    private void deliver(List<PartyEvent> events) {
        for (PartyEvent event : events) {
            if (event instanceof PartyEvent.Created created) {
                Names.send(server, created.party().leader(), msg(PartyMessages.CREATED));
            } else if (event instanceof PartyEvent.Invited invited) {
                PartyInvite invite = invited.invite();
                toParty(invite.partyId(), msg(PartyMessages.INVITE_SENT, Names.of(server, invite.invitee())));
                String inviter = Names.of(server, invite.inviter());
                long seconds = Math.max(1, (invite.expiresAtTick() - now()) / 20);
                Names.send(server, invite.invitee(), msg(PartyMessages.INVITE_RECEIVED, inviter, seconds)
                        .append(" ").append(Names.button(PartyMessages.ACCEPT_BUTTON, "/party accept " + inviter,
                                ChatFormatting.GREEN))
                        .append(" ").append(Names.button(PartyMessages.DECLINE_BUTTON, "/party decline",
                                ChatFormatting.RED)));
            } else if (event instanceof PartyEvent.InviteExpired expired) {
                toParty(expired.partyId(), msg(PartyMessages.INVITE_EXPIRED_SENT, Names.of(server, expired.invite().invitee())));
                Names.send(server, expired.invite().invitee(),
                        msg(PartyMessages.INVITE_EXPIRED_RECEIVED, Names.of(server, expired.invite().inviter())));
            } else if (event instanceof PartyEvent.InviteDeclined declined) {
                toParty(declined.partyId(), msg(PartyMessages.INVITE_DECLINED, Names.of(server, declined.invite().invitee())));
            } else if (event instanceof PartyEvent.InviteCancelled cancelled) {
                Names.send(server, cancelled.invite().invitee(),
                        msg(PartyMessages.INVITE_CANCELLED, Names.of(server, cancelled.invite().inviter())));
            } else if (event instanceof PartyEvent.Joined joined) {
                toParty(joined.partyId(), msg(PartyMessages.JOINED, Names.of(server, joined.member())));
            } else if (event instanceof PartyEvent.Left left) {
                toParty(left.partyId(), msg(PartyMessages.LEFT, Names.of(server, left.member())));
                Names.send(server, left.member(), msg(PartyMessages.LEFT_SELF));
                clearHud(left.member());
            } else if (event instanceof PartyEvent.Kicked kicked) {
                toParty(kicked.partyId(), msg(PartyMessages.KICKED, Names.of(server, kicked.member())));
                Names.send(server, kicked.member(), msg(PartyMessages.KICKED_SELF));
                clearHud(kicked.member());
            } else if (event instanceof PartyEvent.LeaderChanged changed) {
                toParty(changed.partyId(), msg(PartyMessages.LEADER_CHANGED, Names.of(server, changed.newLeader())));
            } else if (event instanceof PartyEvent.ConvertedToRaid raid) {
                toParty(raid.partyId(), msg(PartyMessages.CONVERTED_TO_RAID, service.capacity(raid.party())));
            } else if (event instanceof PartyEvent.Disbanded disbanded) {
                for (UUID member : disbanded.party().memberIds()) {
                    Names.send(server, member, msg(PartyMessages.DISBANDED));
                    clearHud(member);
                }
            }
        }
    }

    private static net.minecraft.network.chat.MutableComponent msg(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.AQUA);
    }

    private void toParty(com.eternalrealm.party.PartyId partyId, Component message) {
        service.party(partyId).ifPresent(party -> {
            for (UUID member : party.memberIds()) {
                Names.send(server, member, message);
            }
        });
    }

    // ------------------------------------------------------------------ tick, HUD, lifecycle

    public void tick() {
        long now = now();
        deliver(service.expireInvites(now));
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(offlineSince.entrySet())) {
            if (now - entry.getValue() >= OFFLINE_REMOVAL_TICKS) {
                offlineSince.remove(entry.getKey());
                deliver(service.leave(entry.getKey()).events());
            }
        }
        if (now % HUD_INTERVAL_TICKS == 0) {
            syncHuds();
        }
    }

    public void onLogin(ServerPlayer player) {
        offlineSince.remove(player.getUUID());
        lastHud.remove(player.getUUID());
    }

    public void onLogout(UUID playerId) {
        if (service.partyOf(playerId).isPresent()) {
            offlineSince.put(playerId, now());
        }
        lastHud.remove(playerId);
    }

    private void syncHuds() {
        Set<UUID> inParty = new HashSet<>();
        for (Party party : service.parties()) {
            JsonObject data = hudData(party);
            String json = data.toString();
            for (UUID member : party.memberIds()) {
                inParty.add(member);
                ServerPlayer player = server.getPlayerList().getPlayer(member);
                if (player != null && !json.equals(lastHud.get(member))) {
                    lastHud.put(member, json);
                    UiData.send(player, "party", data);
                }
            }
        }
        // a player travelling with the legendary travellers alone: their frames as a party of their own
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (inParty.contains(id)) {
                continue;
            }
            JsonArray companions = com.eternalrealm.npc.forge.TravellerDirector.hudRows(id);
            if (companions.isEmpty()) {
                if (lastHud.containsKey(id) && !"{}".equals(lastHud.get(id))) {
                    clearHud(id);
                }
                continue;
            }
            inParty.add(id);
            JsonObject data = new JsonObject();
            data.addProperty("raid", false);
            data.addProperty("capacity", PartyRules.defaults().maxPartySize());
            JsonArray members = new JsonArray();
            members.add(memberRow(id, true));
            members.addAll(companions);
            data.add("members", members);
            String json = data.toString();
            if (!json.equals(lastHud.get(id))) {
                lastHud.put(id, json);
                UiData.send(player, "party", data);
            }
        }
        lastHud.keySet().removeIf(id -> !inParty.contains(id) && !lastHud.get(id).equals("{}"));
    }

    private JsonObject hudData(Party party) {
        JsonObject data = new JsonObject();
        data.addProperty("raid", party.isRaid());
        data.addProperty("capacity", service.capacity(party));
        JsonArray members = new JsonArray();
        for (UUID id : party.memberIds()) {
            members.add(memberRow(id, party.isLeader(id)));
        }
        for (UUID id : party.memberIds()) {
            members.addAll(com.eternalrealm.npc.forge.TravellerDirector.hudRows(id)); // single-player companions
        }
        data.add("members", members);
        return data;
    }

    private JsonObject memberRow(UUID id, boolean leader) {
        JsonObject member = new JsonObject();
        member.addProperty("id", id.toString());
        member.addProperty("name", Names.of(server, id));
        member.addProperty("leader", leader);
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        member.addProperty("online", player != null);
        if (player != null) {
            member.addProperty("hp", Math.round(player.getHealth() * 10.0) / 10.0);
            member.addProperty("maxHp", Math.round(player.getMaxHealth() * 10.0) / 10.0);
            member.addProperty("down", com.eternalrealm.revival.forge.RevivalService.isDown(id));
            Optional<CharacterSnapshot> snapshot = playerData.snapshot(id);
            member.addProperty("level", snapshot.map(CharacterSnapshot::level).orElse(1));
            // the class's id once advanced (the party screen names it, the HUD colours it by its job)
            member.addProperty("job", snapshot.map(s -> com.eternalrealm.job.JobPath.of(s.jobId()).storedId())
                    .orElse("none"));
        }
        return member;
    }

    private void clearHud(ServerPlayer player) {
        clearHud(player.getUUID());
    }

    private void clearHud(UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        lastHud.put(id, "{}");
        if (player != null) {
            UiData.send(player, "party", new JsonObject());
        }
    }

    // ------------------------------------------------------------------ queries

    public boolean sameParty(UUID first, UUID second) {
        return service.sameParty(first, second);
    }

    /**
     * EXP of a kill split between the killer and party members near the victim (same level, within
     * {@link #SHARE_RANGE}, alive). @return amount per player
     */
    public Map<UUID, Long> shareExperience(ServerPlayer killer, long experience, net.minecraft.world.phys.Vec3 at) {
        Map<UUID, Long> result = new HashMap<>();
        Optional<Party> party = service.partyOf(killer.getUUID());
        if (party.isEmpty() || experience <= 0) {
            result.put(killer.getUUID(), experience);
            return result;
        }
        List<UUID> eligible = new ArrayList<>();
        for (UUID id : party.get().memberIds()) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null && member.isAlive() && member.level() == killer.level()
                    && member.position().distanceToSqr(at) <= SHARE_RANGE * SHARE_RANGE) {
                eligible.add(id);
            }
        }
        if (!eligible.contains(killer.getUUID())) {
            eligible.add(killer.getUUID());
        }
        ExperienceSplit split = ExperienceShare.split(experience, eligible, killer.getUUID(), shareRules);
        result.putAll(split.shares());
        return result;
    }
}
