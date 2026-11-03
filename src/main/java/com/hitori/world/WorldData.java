package com.hitori.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Room 203's persistent state: the breaker, every room's light switch, the TV's own switch, the ritual items' places. */
public final class WorldData extends SavedData {
    private static final String NAME = "hitori_world";
    static final String[] PRESENT_KEYS = {"knife", "plush", "lighter", "laptop"};

    boolean breaker;
    final Map<String, Boolean> switches = new HashMap<>();
    boolean tvSwitch;
    final Map<String, Boolean> present = new HashMap<>();

    WorldData() {
        reset();
    }

    /** The evening's start: the breaker on, the light on in the western room only, the TV off, everything in its place. */
    void reset() {
        breaker = true;
        switches.clear();
        for (String r : Layout.LIGHT_ROOMS) switches.put(r, "western".equals(r));
        tvSwitch = false;
        present.clear();
        for (String k : PRESENT_KEYS) present.put(k, true);
        setDirty();
    }

    boolean switchOn(String room) {
        return switches.getOrDefault(room, false);
    }

    boolean isPresent(String key) {
        return present.getOrDefault(key, true);
    }

    static WorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WorldData::load, WorldData::new, NAME);
    }

    static WorldData load(CompoundTag tag) {
        WorldData d = new WorldData();
        d.breaker = !tag.contains("breaker") || tag.getBoolean("breaker");
        CompoundTag sw = tag.getCompound("switches");
        for (String r : Layout.LIGHT_ROOMS) if (sw.contains(r)) d.switches.put(r, sw.getBoolean(r));
        d.tvSwitch = tag.getBoolean("tv");
        CompoundTag pr = tag.getCompound("present");
        for (String k : PRESENT_KEYS) if (pr.contains(k)) d.present.put(k, pr.getBoolean(k));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("breaker", breaker);
        CompoundTag sw = new CompoundTag();
        for (Map.Entry<String, Boolean> e : switches.entrySet()) sw.putBoolean(e.getKey(), e.getValue());
        tag.put("switches", sw);
        tag.putBoolean("tv", tvSwitch);
        CompoundTag pr = new CompoundTag();
        for (Map.Entry<String, Boolean> e : present.entrySet()) pr.putBoolean(e.getKey(), e.getValue());
        tag.put("present", pr);
        return tag;
    }
}
