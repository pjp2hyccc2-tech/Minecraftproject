package com.huntcraft.village.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-dimension flag "village built" + the UUIDs of the village NPCs / dummies (for respawning). */
public class VillageSavedData extends SavedData {
    private static final String NAME = "huntcraft_village";
    public int builtVersion;
    private final Map<String, UUID> npcs = new HashMap<>();
    public final List<UUID> dummies = new ArrayList<>();

    public static VillageSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VillageSavedData::load, VillageSavedData::new, NAME);
    }

    public static VillageSavedData load(CompoundTag tag) {
        VillageSavedData d = new VillageSavedData();
        d.builtVersion = tag.getInt("built");
        CompoundTag n = tag.getCompound("npcs");
        for (String k : n.getAllKeys()) {
            if (n.hasUUID(k)) d.npcs.put(k, n.getUUID(k));
        }
        ListTag l = tag.getList("dummies", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < l.size(); i++) d.dummies.add(NbtUtils.loadUUID(l.get(i)));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("built", builtVersion);
        CompoundTag n = new CompoundTag();
        npcs.forEach(n::putUUID);
        tag.put("npcs", n);
        ListTag l = new ListTag();
        for (UUID u : dummies) l.add(NbtUtils.createUUID(u));
        tag.put("dummies", l);
        return tag;
    }

    @Nullable
    public UUID npcUuid(String role) {
        return npcs.get(role);
    }

    public void setNpc(String role, UUID id) {
        npcs.put(role, id);
        setDirty();
    }
}
