package com.clockworkdiver.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-wide save data (stored with the overworld) holding every player's {@link PlayerProgress}. */
public final class WorldProgress extends SavedData {
    private static final String NAME = "clockworkdiver_progress";
    private final Map<UUID, PlayerProgress> players = new HashMap<>();
    private int nextSlot;

    public static WorldProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorldProgress::load, WorldProgress::new, NAME);
    }

    /** Progress of a player; the data is marked dirty because callers usually change it. */
    public static PlayerProgress of(ServerPlayer player) {
        WorldProgress data = get(player.server);
        PlayerProgress p = data.byId(player.getUUID());
        data.setDirty();
        return p;
    }

    public PlayerProgress byId(UUID id) {
        PlayerProgress p = players.get(id);
        if (p == null) {
            p = new PlayerProgress(id);
            p.slot = nextSlot++;
            players.put(id, p);
            setDirty();
        }
        return p;
    }

    /** Reserves a slot that no player will ever use (self tests). */
    public int reserveSlot() {
        setDirty();
        return nextSlot++;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextSlot", nextSlot);
        ListTag list = new ListTag();
        for (PlayerProgress p : players.values()) list.add(p.save());
        tag.put("Players", list);
        return tag;
    }

    public static WorldProgress load(CompoundTag tag) {
        WorldProgress data = new WorldProgress();
        data.nextSlot = tag.getInt("NextSlot");
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PlayerProgress p = PlayerProgress.load(list.getCompound(i));
            data.players.put(p.id, p);
            data.nextSlot = Math.max(data.nextSlot, p.slot + 1);
        }
        return data;
    }
}
