package com.huntcraft.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A running quest: one region of a quest map, its party, timer, progress and supply box. Server only. */
public final class QuestInstance {
    public enum State { ACTIVE, COMPLETE, FAILED }

    public final String id;
    public final String questId;
    public final int slot;
    public final BlockPos center;
    public BlockPos camp;
    @Nullable
    public UUID leader;
    public final List<UUID> members = new ArrayList<>();
    public final Map<UUID, String> names = new HashMap<>();
    public final Map<UUID, CompoundTag> returnPoints = new HashMap<>();
    public long startTick;
    public long endTick;
    public long returnTick;
    public long clearTicks;
    public State state = State.ACTIVE;
    public int faints;
    public int[] progress = new int[0];
    /** "monsterId|part|uuid" of every part broken on quest monsters. */
    public final Set<String> brokenParts = new LinkedHashSet<>();
    /** monster ids captured during the quest (capture bonus rewards). */
    public final List<String> captured = new ArrayList<>();
    /** UUIDs of the large monsters spawned for this quest. */
    public final List<UUID> targets = new ArrayList<>();
    /** UUIDs of small monsters spawned for this quest. */
    public final List<UUID> smalls = new ArrayList<>();
    /** area number -> game time when the next small group may spawn there. */
    public final Map<Integer, Long> areaSpawn = new HashMap<>();
    public final SimpleContainer supplies = new SimpleContainer(27);
    public String failReason = "";
    public int warnedMinutes = 999;
    // transient
    public final Map<UUID, Long> roamAt = new HashMap<>();
    public final Map<UUID, Integer> orphanTicks = new HashMap<>();

    public QuestInstance(String id, String questId, int slot, BlockPos center, BlockPos camp) {
        this.id = id;
        this.questId = questId;
        this.slot = slot;
        this.center = center;
        this.camp = camp;
    }

    @Nullable
    public QuestDef quest() {
        return Quests.get(questId);
    }

    public boolean contains(double x, double z) {
        return QuestRegions.inside(center, x, z, 0);
    }

    public boolean isMember(UUID id) {
        return members.contains(id);
    }

    public boolean allTargetsDone() {
        QuestDef q = quest();
        if (q == null) return false;
        for (int i = 0; i < q.targets.size(); i++) {
            if (i >= progress.length || progress[i] < q.targets.get(i).count()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ NBT
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putString("quest", questId);
        t.putInt("slot", slot);
        t.put("center", NbtUtils.writeBlockPos(center));
        t.put("camp", NbtUtils.writeBlockPos(camp));
        if (leader != null) t.putUUID("leader", leader);
        ListTag mem = new ListTag();
        for (UUID u : members) {
            CompoundTag m = new CompoundTag();
            m.putUUID("u", u);
            m.putString("n", names.getOrDefault(u, "?"));
            CompoundTag rp = returnPoints.get(u);
            if (rp != null) m.put("rp", rp);
            mem.add(m);
        }
        t.put("members", mem);
        t.putLong("start", startTick);
        t.putLong("end", endTick);
        t.putLong("ret", returnTick);
        t.putLong("clear", clearTicks);
        t.putInt("state", state.ordinal());
        t.putInt("faints", faints);
        t.putIntArray("progress", progress);
        ListTag parts = new ListTag();
        for (String s : brokenParts) parts.add(StringTag.valueOf(s));
        t.put("parts", parts);
        ListTag cap = new ListTag();
        for (String s : captured) cap.add(StringTag.valueOf(s));
        t.put("captured", cap);
        t.put("targets", uuidList(targets));
        t.put("smalls", uuidList(smalls));
        t.put("supplies", supplies.createTag());
        t.putString("fail", failReason);
        t.putInt("warned", warnedMinutes);
        return t;
    }

    public static QuestInstance load(CompoundTag t) {
        QuestInstance q = new QuestInstance(t.getString("id"), t.getString("quest"), t.getInt("slot"),
                NbtUtils.readBlockPos(t.getCompound("center")), NbtUtils.readBlockPos(t.getCompound("camp")));
        if (t.hasUUID("leader")) q.leader = t.getUUID("leader");
        ListTag mem = t.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < mem.size(); i++) {
            CompoundTag m = mem.getCompound(i);
            if (!m.hasUUID("u")) continue;
            UUID u = m.getUUID("u");
            q.members.add(u);
            q.names.put(u, m.getString("n"));
            if (m.contains("rp", Tag.TAG_COMPOUND)) q.returnPoints.put(u, m.getCompound("rp"));
        }
        q.startTick = t.getLong("start");
        q.endTick = t.getLong("end");
        q.returnTick = t.getLong("ret");
        q.clearTicks = t.getLong("clear");
        int st = t.getInt("state");
        q.state = st >= 0 && st < State.values().length ? State.values()[st] : State.FAILED;
        q.faints = t.getInt("faints");
        q.progress = t.getIntArray("progress");
        QuestDef def = q.quest();
        if (def != null && q.progress.length != def.targets.size()) {
            int[] p = new int[def.targets.size()];
            System.arraycopy(q.progress, 0, p, 0, Math.min(p.length, q.progress.length));
            q.progress = p;
        }
        ListTag parts = t.getList("parts", Tag.TAG_STRING);
        for (int i = 0; i < parts.size(); i++) q.brokenParts.add(parts.getString(i));
        ListTag cap = t.getList("captured", Tag.TAG_STRING);
        for (int i = 0; i < cap.size(); i++) q.captured.add(cap.getString(i));
        readUuids(t.getList("targets", Tag.TAG_COMPOUND), q.targets);
        readUuids(t.getList("smalls", Tag.TAG_COMPOUND), q.smalls);
        q.supplies.fromTag(t.getList("supplies", Tag.TAG_COMPOUND));
        q.failReason = t.getString("fail");
        q.warnedMinutes = t.contains("warned") ? t.getInt("warned") : 999;
        return q;
    }

    private static ListTag uuidList(List<UUID> list) {
        ListTag l = new ListTag();
        for (UUID u : list) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", u);
            l.add(c);
        }
        return l;
    }

    private static void readUuids(ListTag l, List<UUID> out) {
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            if (c.hasUUID("u")) out.add(c.getUUID("u"));
        }
    }
}
