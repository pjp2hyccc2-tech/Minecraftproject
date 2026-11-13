package com.eternalrealm.dungeon.forge;

import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.dungeon.ReadyCheck;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UiDataPacket;
import com.eternalrealm.party.Party;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.social.forge.Names;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ready checks (出発の準備): a party leader who sets out from the gatekeeper's screen asks every member first. Each
 * member answers 準備OK or stays 準備中 on their screen ({@code ui.gate.ReadyCheckScreen}), with the buttons in chat
 * or with {@code /dungeon ready ok|wait}; once everyone is ready a short countdown runs and the party sets out
 * ({@link DungeonService#start}, which checks everything again). The check ends when the leader calls it off, the party
 * changes, a member goes offline or into a dungeon, or the party is not ready in time ({@link ReadyCheck}). The
 * dungeon's site is prepared while they get ready.
 *
 * <p>Channel {@value #CHANNEL}: the check as each member sees it ({@code active}, {@code open} when their screen should
 * open, the dungeon, difficulty, phase, ticks left, the members with names and answers), or {@code active: false} with
 * the outcome and a message when it ends. Actions {@value #SET} ({@code ready}), {@value #CANCEL} (the leader) and
 * {@value #OPEN}. Server thread only.</p>
 */
public final class ReadyChecks {
    public static final String CHANNEL = "ready_check";
    public static final String SET = "ready.set";
    public static final String CANCEL = "ready.cancel";
    public static final String OPEN = "ready.open";

    private final DungeonService dungeons;
    private final MinecraftServer server;
    private final List<ReadyCheck> checks = new ArrayList<>();
    private final Map<UUID, ReadyCheck> byMember = new HashMap<>();
    private final Map<ReadyCheck, ReadyCheck.Phase> shownPhase = new IdentityHashMap<>();

    ReadyChecks(DungeonService dungeons, MinecraftServer server) {
        this.dungeons = dungeons;
        this.server = server;
    }

    public static void register() {
        UiActions.register(SET, (player, context, args) -> context.game().dungeons().readyChecks()
                .answer(player, UiArgs.flag(args, "ready")));
        UiActions.register(CANCEL, (player, context, args) -> context.game().dungeons().readyChecks().callOff(player));
        UiActions.register(OPEN, (player, context, args) -> context.game().dungeons().readyChecks().show(player));
    }

    private long now() {
        return server.getTickCount();
    }

    /** Whether the player is part of a ready check that is running. */
    public boolean isChecking(UUID playerId) {
        return byMember.containsKey(playerId);
    }

    /**
     * The leader of a party of two or more set out from the gate: everyone is asked first.
     *
     * @return why not (the gate's screen shows it), or empty once the members have been asked
     */
    public Optional<Component> begin(ServerPlayer leader, Party party, String contentId, Difficulty difficulty) {
        for (UUID member : party.memberIds()) {
            if (byMember.containsKey(member)) {
                return Optional.of(red("message.eternalrealm.ready.busy"));
            }
        }
        Optional<Component> refusal = dungeons.refusal(leader, contentId);
        if (refusal.isPresent()) {
            return refusal;
        }
        ReadyCheck check = new ReadyCheck(contentId, difficulty, leader.getUUID(), party.memberIds(), now());
        checks.add(check);
        for (UUID member : check.members()) {
            byMember.put(member, check);
        }
        shownPhase.put(check, check.phase(now()));
        dungeons.prepareAhead(contentId);
        Component dungeon = Component.translatable(nameKey(contentId));
        Component level = Component.translatable(difficulty.translationKey());
        for (UUID member : check.members()) {
            ServerPlayer player = dungeons.player(member);
            if (player == null) {
                continue;
            }
            send(player, check, true);
            if (member.equals(check.leader())) {
                player.sendSystemMessage(Component.translatable("message.eternalrealm.ready.asked", dungeon, level)
                        .withStyle(ChatFormatting.GOLD));
            } else {
                player.sendSystemMessage(invitation(leader.getGameProfile().getName(), dungeon, level));
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.8F, 1.2F);
            }
        }
        return Optional.empty();
    }

    private static MutableComponent invitation(String leader, Component dungeon, Component level) {
        return Component.translatable("message.eternalrealm.ready.invite", leader, dungeon, level)
                .withStyle(ChatFormatting.GOLD)
                .append(" ")
                .append(Names.button("message.eternalrealm.ready.button_ok", "/dungeon ready ok", ChatFormatting.GREEN))
                .append(" ")
                .append(Names.button("message.eternalrealm.ready.button_wait", "/dungeon ready wait",
                        ChatFormatting.YELLOW))
                .append(" ")
                .append(Names.button("message.eternalrealm.ready.button_open", "/dungeon ready open",
                        ChatFormatting.AQUA));
    }

    /** A member's answer: 準備OK ({@code ready}) or back to 準備中 (the screen, the chat buttons, the command). */
    public void answer(ServerPlayer player, boolean ready) {
        ReadyCheck check = byMember.get(player.getUUID());
        if (check == null) {
            player.displayClientMessage(red("message.eternalrealm.ready.none"), true);
            return;
        }
        if (check.set(player.getUUID(), ready, now())) {
            shownPhase.put(check, check.phase(now()));
            broadcast(check);
        }
    }

    /** The leader calls the check off. */
    public void callOff(ServerPlayer player) {
        ReadyCheck check = byMember.get(player.getUUID());
        if (check == null) {
            player.displayClientMessage(red("message.eternalrealm.ready.none"), true);
            return;
        }
        if (!check.leader().equals(player.getUUID())) {
            player.displayClientMessage(red("message.eternalrealm.ready.leader_only"), true);
            return;
        }
        end(check, "cancelled", Component.translatable("message.eternalrealm.ready.cancelled",
                player.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW));
    }

    /** Opens the player's screen of their check again. */
    public void show(ServerPlayer player) {
        ReadyCheck check = byMember.get(player.getUUID());
        if (check == null) {
            player.displayClientMessage(red("message.eternalrealm.ready.none"), true);
            return;
        }
        send(player, check, true);
    }

    void tick(long now) {
        if (checks.isEmpty()) {
            return;
        }
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        for (ReadyCheck check : List.copyOf(checks)) {
            Optional<Party> party = context.game().parties().service().partyOf(check.leader());
            boolean intact = party.isPresent() && party.get().isLeader(check.leader())
                    && check.sameMembers(party.get().memberIds())
                    && check.members().stream().allMatch(id -> dungeons.player(id) != null
                    && !dungeons.isInDungeon(id));
            if (!intact) {
                end(check, "changed", Component.translatable("message.eternalrealm.ready.changed")
                        .withStyle(ChatFormatting.YELLOW));
                continue;
            }
            ReadyCheck.Phase phase = check.phase(now);
            switch (phase) {
                case TIMED_OUT -> end(check, "timeout", Component.translatable("message.eternalrealm.ready.timeout",
                        check.waitingFor().stream().map(id -> Names.of(server, id)).collect(Collectors.joining(", ")))
                        .withStyle(ChatFormatting.YELLOW));
                case GO -> go(check);
                default -> {
                    if (shownPhase.get(check) != phase) {
                        shownPhase.put(check, phase);
                        broadcast(check);
                    }
                }
            }
        }
    }

    private void go(ReadyCheck check) {
        remove(check);
        ServerPlayer leader = dungeons.player(check.leader());
        Optional<Component> refusal = leader == null ? Optional.of(red("message.eternalrealm.dungeon.member_offline"))
                : dungeons.start(leader, check.contentId(), check.difficulty());
        Component message = refusal.<Component>map(reason -> Component.translatable(
                "message.eternalrealm.ready.failed", reason).withStyle(ChatFormatting.RED)).orElse(null);
        for (UUID member : check.members()) {
            ServerPlayer player = dungeons.player(member);
            if (player != null) {
                sendEnd(player, refusal.isEmpty() ? "go" : "failed", message);
                if (message != null) {
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    private void end(ReadyCheck check, String outcome, Component message) {
        remove(check);
        for (UUID member : check.members()) {
            ServerPlayer player = dungeons.player(member);
            if (player != null) {
                sendEnd(player, outcome, message);
                player.sendSystemMessage(message);
            }
        }
    }

    private void remove(ReadyCheck check) {
        checks.remove(check);
        shownPhase.remove(check);
        byMember.values().removeIf(value -> value == check);
    }

    private void broadcast(ReadyCheck check) {
        for (UUID member : check.members()) {
            ServerPlayer player = dungeons.player(member);
            if (player != null) {
                send(player, check, false);
            }
        }
    }

    private void send(ServerPlayer player, ReadyCheck check, boolean open) {
        long now = now();
        JsonObject data = new JsonObject();
        data.addProperty("t", System.nanoTime());
        data.addProperty("active", true);
        data.addProperty("open", open);
        data.addProperty("id", check.contentId());
        data.addProperty("name", nameKey(check.contentId()));
        data.addProperty("difficulty", check.difficulty().id());
        data.addProperty("phase", check.phase(now) == ReadyCheck.Phase.COUNTDOWN ? "countdown" : "waiting");
        data.addProperty("left", check.ticksLeft(now));
        data.addProperty("leader", check.leader().toString());
        JsonArray members = new JsonArray();
        for (UUID id : check.members()) {
            JsonObject row = new JsonObject();
            row.addProperty("id", id.toString());
            row.addProperty("name", Names.of(server, id));
            row.addProperty("ready", check.isReady(id));
            row.addProperty("leader", id.equals(check.leader()));
            members.add(row);
        }
        data.add("members", members);
        ERNetwork.sendToPlayer(player, new UiDataPacket(CHANNEL, data.toString()));
    }

    private static void sendEnd(ServerPlayer player, String outcome, Component message) {
        JsonObject data = new JsonObject();
        data.addProperty("t", System.nanoTime());
        data.addProperty("active", false);
        data.addProperty("outcome", outcome);
        if (message != null) {
            data.addProperty("message", Component.Serializer.toJson(message));
        }
        ERNetwork.sendToPlayer(player, new UiDataPacket(CHANNEL, data.toString()));
    }

    private static String nameKey(String contentId) {
        return Dungeons.byId(contentId).map(DungeonDefinition::translationKey)
                .orElse(Dungeons.FINAL_RAID.translationKey());
    }

    private static Component red(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.RED);
    }
}
