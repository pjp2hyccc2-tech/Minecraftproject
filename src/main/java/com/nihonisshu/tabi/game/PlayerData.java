package com.nihonisshu.tabi.game;

import com.google.gson.JsonObject;
import com.nihonisshu.tabi.data.TabiData;
import com.nihonisshu.tabi.network.TabiNet;
import com.nihonisshu.tabi.registry.TabiSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Money (yen), story flags and travel-journal unlocks, kept in the player's persisted Forge data. */
public final class PlayerData {
    private PlayerData() {}

    public static CompoundTag get(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains("tabi")) persisted.put("tabi", new CompoundTag());
        return persisted.getCompound("tabi");
    }

    private static CompoundTag sub(Player p, String key) {
        CompoundTag t = get(p);
        if (!t.contains(key)) t.put(key, new CompoundTag());
        return t.getCompound(key);
    }

    public static int yen(Player p) {
        return get(p).getInt("yen");
    }

    public static void setYen(ServerPlayer p, int v) {
        get(p).putInt("yen", Math.max(0, v));
        sync(p);
    }

    public static void earn(ServerPlayer p, int amount, String reason) {
        setYen(p, yen(p) + amount);
        p.displayClientMessage(Component.literal("+" + String.format("%,d", amount) + "円" + (reason.isEmpty() ? "" : "（" + reason + "）"))
                .withStyle(ChatFormatting.GOLD), true);
        p.level().playSound(null, p.blockPosition(), TabiSounds.COIN.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    public static boolean spend(ServerPlayer p, int amount) {
        int have = yen(p);
        if (have < amount) {
            p.displayClientMessage(Component.literal("お金が足りません（あと" + String.format("%,d", amount - have) + "円）").withStyle(ChatFormatting.RED), true);
            return false;
        }
        setYen(p, have - amount);
        p.level().playSound(null, p.blockPosition(), TabiSounds.COIN.get(), SoundSource.PLAYERS, 0.6f, 0.9f);
        return true;
    }

    public static boolean flag(Player p, String name) {
        return sub(p, "flags").getBoolean(name);
    }

    public static void setFlag(ServerPlayer p, String name, boolean value) {
        if (value) sub(p, "flags").putBoolean(name, true);
        else sub(p, "flags").remove(name);
        sync(p);
    }

    public static boolean unlocked(Player p, String key) {
        return sub(p, "journal").getBoolean(key);
    }

    /** unlock a travel journal entry; shows a notice the first time. */
    public static boolean unlock(ServerPlayer p, String key) {
        CompoundTag j = sub(p, "journal");
        if (j.getBoolean(key)) return false;
        j.putBoolean(key, true);
        JsonObject entries = TabiData.journal().getAsJsonObject("entries");
        String title = key;
        if (entries != null && entries.has(key)) {
            title = TabiData.str(entries.getAsJsonObject(key), "title", key);
        }
        p.displayClientMessage(Component.literal("旅の手帳に記録しました：「" + title + "」").withStyle(ChatFormatting.GREEN), false);
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        sync(p);
        return true;
    }

    public static void sync(ServerPlayer p) {
        TabiNet.send(p, "sync", get(p).copy());
    }
}
