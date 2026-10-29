package com.huntcraft.quest;

import com.huntcraft.data.HunterData;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.network.HCScreens;
import com.huntcraft.registry.HCSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Quest board: accepting (受注), joining a nearby hunter's quest (参加), departing (出発) and cancelling (破棄).
 * Lobbies are transient (not saved). Contract fees are refunded when a lobby is cancelled or left.
 */
public final class QuestBoardService {
    public static final int MAX_PARTY = 4;
    public static final double JOIN_RANGE = 48.0;

    private static final class Lobby {
        final UUID leader;
        final String questId;
        final Set<UUID> members = new LinkedHashSet<>();
        final Map<UUID, Integer> paid = new HashMap<>();

        Lobby(UUID leader, String questId) {
            this.leader = leader;
            this.questId = questId;
        }
    }

    private static final Map<UUID, Lobby> LOBBIES = new LinkedHashMap<>();

    private QuestBoardService() {
    }

    public static void registerActions() {
        HCNetwork.registerServerAction("quest/board_request", (p, t) -> refresh(p));
        HCNetwork.registerServerAction("quest/accept", (p, t) -> accept(p, t.getString("q")));
        HCNetwork.registerServerAction("quest/join", (p, t) -> {
            if (t.hasUUID("leader")) join(p, t.getUUID("leader"));
        });
        HCNetwork.registerServerAction("quest/cancel", (p, t) -> cancelOrLeave(p));
        HCNetwork.registerServerAction("quest/depart", (p, t) -> depart(p));
        HCNetwork.registerServerAction("quest/claim", (p, t) -> {
            int n = QuestRewards.claim(p, t.getBoolean("box"));
            if (n > 0) p.displayClientMessage(Component.translatable(t.getBoolean("box") ? "message.huntcraft.quest.claimed_box" : "message.huntcraft.quest.claimed_inv", n), true);
        });
        HCNetwork.registerServerAction("quest/abandon", (p, t) -> QuestManager.abandon(p));
        HCNetwork.registerServerAction("quest/hud_request", (p, t) -> {
            QuestInstance inst = QuestManager.instanceOf(p);
            if (inst != null) QuestManager.syncHud(p.server, inst);
            else QuestManager.clearHud(p);
        });
    }

    public static void clear() {
        LOBBIES.clear();
    }

    @Nullable
    private static Lobby lobbyOf(UUID player) {
        for (Lobby l : LOBBIES.values()) if (l.members.contains(player)) return l;
        return null;
    }

    public static void open(ServerPlayer p) {
        HCScreens.open(p, HCScreens.QUEST_BOARD, data(p));
    }

    public static void refresh(ServerPlayer p) {
        HCNetwork.sendToPlayer(p, "quest/board_data", data(p));
    }

    private static void refreshLobby(MinecraftServer server, Lobby l) {
        for (UUID u : l.members) {
            ServerPlayer m = server.getPlayerList().getPlayer(u);
            if (m != null) refresh(m);
        }
    }

    public static CompoundTag data(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("data", true);
        t.putBoolean("inQuest", QuestManager.instanceOf(p) != null);
        HunterData d = HunterData.get(p);
        t.putInt("hr", d.hunterRank);
        t.putInt("zenny", d.zenny);
        ListTag done = new ListTag();
        for (String s : d.completedQuests) done.add(StringTag.valueOf(s));
        t.put("done", done);
        Lobby mine = lobbyOf(p.getUUID());
        if (mine != null) {
            CompoundTag l = new CompoundTag();
            l.putString("q", mine.questId);
            l.putBoolean("isLeader", mine.leader.equals(p.getUUID()));
            ListTag names = new ListTag();
            for (UUID u : mine.members) {
                ServerPlayer m = p.server.getPlayerList().getPlayer(u);
                names.add(StringTag.valueOf(m != null ? m.getGameProfile().getName() : "?"));
            }
            l.put("members", names);
            t.put("lobby", l);
        }
        ListTag join = new ListTag();
        if (mine == null) {
            for (Lobby l : LOBBIES.values()) {
                ServerPlayer leader = p.server.getPlayerList().getPlayer(l.leader);
                if (leader == null || leader == p || leader.level() != p.level() || leader.distanceTo(p) > JOIN_RANGE) continue;
                if (l.members.size() >= MAX_PARTY) continue;
                CompoundTag j = new CompoundTag();
                j.putUUID("leader", l.leader);
                j.putString("name", leader.getGameProfile().getName());
                j.putString("q", l.questId);
                j.putInt("n", l.members.size());
                join.add(j);
            }
        }
        t.put("joinable", join);
        return t;
    }

    private static boolean canTake(ServerPlayer p, QuestDef q, boolean notify) {
        HunterData d = HunterData.get(p);
        Quests.Status st = Quests.status(q, d.hunterRank, d.completedQuests);
        if (st == Quests.Status.LOCKED) {
            if (notify) p.displayClientMessage(Component.translatable("message.huntcraft.quest.locked").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (st == Quests.Status.NEED_HR) {
            if (notify) p.displayClientMessage(Component.translatable("message.huntcraft.need_hr", q.hrReq).withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    public static void accept(ServerPlayer p, String questId) {
        QuestDef q = Quests.get(questId);
        if (q == null) return;
        if (QuestManager.instanceOf(p) != null) {
            p.displayClientMessage(Component.translatable("message.huntcraft.quest.already_on_quest").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (lobbyOf(p.getUUID()) != null) cancelOrLeave(p);
        if (!canTake(p, q, true)) {
            refresh(p);
            return;
        }
        HunterData d = HunterData.get(p);
        int fee = q.contractFee();
        if (!p.isCreative() && !d.spendZenny(fee)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny").withStyle(ChatFormatting.RED), true);
            refresh(p);
            return;
        }
        Lobby l = new Lobby(p.getUUID(), q.id);
        l.members.add(p.getUUID());
        l.paid.put(p.getUUID(), p.isCreative() ? 0 : fee);
        LOBBIES.put(p.getUUID(), l);
        QuestManager.jingle(p, HCSounds.QUEST_ACCEPT.get());
        p.sendSystemMessage(Component.translatable("message.huntcraft.quest.accepted", q.title(), fee).withStyle(ChatFormatting.YELLOW));
        refresh(p);
    }

    public static void join(ServerPlayer p, UUID leaderId) {
        Lobby l = LOBBIES.get(leaderId);
        ServerPlayer leader = p.server.getPlayerList().getPlayer(leaderId);
        QuestDef q = l == null ? null : Quests.get(l.questId);
        if (l == null || leader == null || q == null) {
            refresh(p);
            return;
        }
        if (QuestManager.instanceOf(p) != null || lobbyOf(p.getUUID()) != null) return;
        if (l.members.size() >= MAX_PARTY) {
            p.displayClientMessage(Component.translatable("message.huntcraft.quest.party_full").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (leader.level() != p.level() || leader.distanceTo(p) > JOIN_RANGE) {
            p.displayClientMessage(Component.translatable("message.huntcraft.quest.too_far").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!canTake(p, q, true)) return;
        int fee = q.contractFee();
        if (!p.isCreative() && !HunterData.get(p).spendZenny(fee)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny").withStyle(ChatFormatting.RED), true);
            return;
        }
        l.members.add(p.getUUID());
        l.paid.put(p.getUUID(), p.isCreative() ? 0 : fee);
        QuestManager.jingle(p, HCSounds.QUEST_ACCEPT.get());
        for (UUID u : l.members) {
            ServerPlayer m = p.server.getPlayerList().getPlayer(u);
            if (m != null) m.sendSystemMessage(Component.translatable("message.huntcraft.quest.joined", p.getDisplayName(), q.title()).withStyle(ChatFormatting.AQUA));
        }
        refreshLobby(p.server, l);
    }

    /** 破棄: the leader cancels the accepted quest (fees refunded); a member just leaves. */
    public static void cancelOrLeave(ServerPlayer p) {
        Lobby l = lobbyOf(p.getUUID());
        if (l == null) {
            refresh(p);
            return;
        }
        MinecraftServer server = p.server;
        if (l.leader.equals(p.getUUID())) {
            LOBBIES.remove(l.leader);
            for (UUID u : l.members) {
                ServerPlayer m = server.getPlayerList().getPlayer(u);
                if (m == null) continue;
                refund(m, l);
                m.sendSystemMessage(Component.translatable("message.huntcraft.quest.cancelled").withStyle(ChatFormatting.GRAY));
                refresh(m);
            }
        } else {
            l.members.remove(p.getUUID());
            refund(p, l);
            p.sendSystemMessage(Component.translatable("message.huntcraft.quest.left_party").withStyle(ChatFormatting.GRAY));
            refresh(p);
            refreshLobby(server, l);
        }
    }

    private static void refund(ServerPlayer p, Lobby l) {
        Integer fee = l.paid.remove(p.getUUID());
        if (fee != null && fee > 0) HunterData.get(p).addZenny(fee);
    }

    /** 出発: the leader departs with every lobby member that is still nearby. */
    public static void depart(ServerPlayer p) {
        Lobby l = lobbyOf(p.getUUID());
        if (l == null) return;
        if (!l.leader.equals(p.getUUID())) {
            p.displayClientMessage(Component.translatable("message.huntcraft.quest.not_leader").withStyle(ChatFormatting.RED), true);
            return;
        }
        QuestDef q = Quests.get(l.questId);
        if (q == null) return;
        List<ServerPlayer> party = new ArrayList<>();
        party.add(p);
        for (UUID u : l.members) {
            if (u.equals(p.getUUID())) continue;
            ServerPlayer m = p.server.getPlayerList().getPlayer(u);
            if (m != null && m.level() == p.level() && m.distanceTo(p) <= JOIN_RANGE && QuestManager.instanceOf(m) == null) {
                party.add(m);
            } else if (m != null) {
                refund(m, l);
                m.sendSystemMessage(Component.translatable("message.huntcraft.quest.left_behind").withStyle(ChatFormatting.GRAY));
            }
        }
        LOBBIES.remove(l.leader);
        for (ServerPlayer m : party) m.closeContainer();
        QuestInstance inst = QuestManager.start(p.server, q, party);
        if (inst == null) {
            for (ServerPlayer m : party) refund(m, l);
        }
    }

    public static void onLogout(ServerPlayer p) {
        Lobby l = lobbyOf(p.getUUID());
        if (l == null) return;
        if (l.leader.equals(p.getUUID())) {
            cancelOrLeave(p);
        } else {
            l.members.remove(p.getUUID());
            refund(p, l);
            refreshLobby(p.server, l);
        }
    }
}
