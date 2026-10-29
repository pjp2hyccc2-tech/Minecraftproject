package com.huntcraft.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent quest state (stored with the overworld): running instances, region slots, offline results. */
public final class QuestSavedData extends SavedData {
    public static final String NAME = "huntcraft_quests";

    public final Map<String, QuestInstance> instances = new LinkedHashMap<>();
    public final Map<String, BitSet> usedSlots = new HashMap<>();
    /** Results of quests that ended while a member was offline (applied on next login). */
    public final Map<UUID, CompoundTag> pendingResults = new HashMap<>();
    private int counter;

    public static QuestSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(QuestSavedData::load, QuestSavedData::new, NAME);
    }

    public String nextId() {
        counter++;
        setDirty();
        return "q" + counter;
    }

    /** Lowest free region slot of a map, or -1 when all are in use. */
    public int allocateSlot(QuestMap map) {
        BitSet bs = usedSlots.computeIfAbsent(map.id, k -> new BitSet());
        int slot = bs.nextClearBit(0);
        if (slot >= QuestRegions.MAX_SLOTS) return -1;
        bs.set(slot);
        setDirty();
        return slot;
    }

    public void freeSlot(QuestMap map, int slot) {
        BitSet bs = usedSlots.get(map.id);
        if (bs != null) bs.clear(slot);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("counter", counter);
        ListTag list = new ListTag();
        for (QuestInstance i : instances.values()) list.add(i.save());
        tag.put("instances", list);
        CompoundTag pend = new CompoundTag();
        pendingResults.forEach((k, v) -> pend.put(k.toString(), v));
        tag.put("pending", pend);
        return tag;
    }

    public static QuestSavedData load(CompoundTag tag) {
        QuestSavedData d = new QuestSavedData();
        d.counter = tag.getInt("counter");
        ListTag list = tag.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                QuestInstance inst = QuestInstance.load(list.getCompound(i));
                QuestDef q = inst.quest();
                if (q == null) continue; // quest removed in an update
                d.instances.put(inst.id, inst);
                d.usedSlots.computeIfAbsent(q.map.id, k -> new BitSet()).set(inst.slot);
            } catch (RuntimeException e) {
                com.huntcraft.HuntCraft.LOGGER.warn("Skipping broken quest instance", e);
            }
        }
        CompoundTag pend = tag.getCompound("pending");
        for (String k : pend.getAllKeys()) {
            try {
                d.pendingResults.put(UUID.fromString(k), pend.getCompound(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return d;
    }
}
