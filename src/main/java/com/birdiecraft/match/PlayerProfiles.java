package com.birdiecraft.match;

import com.birdiecraft.golf.engine.PlayStyle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Per-player golf settings and records, saved in the player's persistent NBT. */
public final class PlayerProfiles {
    private static final String KEY = "birdiecraft";

    private PlayerProfiles() {
    }

    private static CompoundTag data(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag mine = persisted.getCompound(KEY);
        persisted.put(KEY, mine);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        return mine;
    }

    public static PlayStyle style(ServerPlayer player) {
        return PlayStyle.byId(data(player).getInt("Style"));
    }

    public static void setStyle(ServerPlayer player, PlayStyle style) {
        data(player).putInt("Style", style.ordinal());
    }

    /** Records a full round; returns true when it is a new personal best for this course. */
    public static boolean recordRound(ServerPlayer player, String courseId, int holes, int strokes) {
        CompoundTag d = data(player);
        d.putInt("Rounds", d.getInt("Rounds") + 1);
        CompoundTag best = d.getCompound("Best");
        String key = courseId + "#" + holes;
        boolean record = !best.contains(key) || strokes < best.getInt(key);
        if (record) best.putInt(key, strokes);
        d.put("Best", best);
        return record;
    }

    public static int best(ServerPlayer player, String courseId, int holes) {
        CompoundTag best = data(player).getCompound("Best");
        String key = courseId + "#" + holes;
        return best.contains(key) ? best.getInt(key) : -1;
    }
}
