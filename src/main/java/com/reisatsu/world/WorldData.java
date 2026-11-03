package com.reisatsu.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The restaurant's persistent state (circuits, the auto doors' key switch and lock, burst tubes, the store music). */
public final class WorldData extends SavedData {
    private static final String NAME = "reisatsu_world";

    final Map<Circuit, Boolean> circuits = new EnumMap<>(Circuit.class);
    boolean keySwitch;
    boolean doorsLocked;
    boolean storeMusic;
    final Set<BlockPos> burst = new HashSet<>();

    WorldData() {
        reset();
    }

    /** The state at the start of the night (§3.9). */
    void reset() {
        for (Circuit c : Circuit.values()) circuits.put(c, c.startOn());
        keySwitch = false;
        doorsLocked = false;
        storeMusic = false;
        burst.clear();
        setDirty();
    }

    public boolean on(Circuit c) {
        return circuits.getOrDefault(c, c.startOn());
    }

    static WorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WorldData::load, WorldData::new, NAME);
    }

    static WorldData load(CompoundTag tag) {
        WorldData d = new WorldData();
        CompoundTag c = tag.getCompound("circuits");
        for (Circuit k : Circuit.values()) if (c.contains(k.key())) d.circuits.put(k, c.getBoolean(k.key()));
        d.keySwitch = tag.getBoolean("keySwitch");
        d.doorsLocked = tag.getBoolean("doorsLocked");
        d.storeMusic = tag.getBoolean("storeMusic");
        ListTag list = tag.getList("burst", Tag.TAG_LONG);
        for (Tag t : list) d.burst.add(BlockPos.of(((LongTag) t).getAsLong()));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag c = new CompoundTag();
        for (Map.Entry<Circuit, Boolean> e : circuits.entrySet()) c.putBoolean(e.getKey().key(), e.getValue());
        tag.put("circuits", c);
        tag.putBoolean("keySwitch", keySwitch);
        tag.putBoolean("doorsLocked", doorsLocked);
        tag.putBoolean("storeMusic", storeMusic);
        ListTag list = new ListTag();
        for (BlockPos p : burst) list.add(LongTag.valueOf(p.asLong()));
        tag.put("burst", list);
        return tag;
    }
}
