package com.nihonisshu.tabi.client;

import net.minecraft.nbt.CompoundTag;

/** Client copy of the player's money, flags and journal unlocks. */
public final class ClientState {
    private ClientState() {}

    public static CompoundTag data = new CompoundTag();

    public static int yen() {
        return data.getInt("yen");
    }

    public static boolean unlocked(String key) {
        return data.getCompound("journal").getBoolean(key);
    }

    public static boolean flag(String key) {
        return data.getCompound("flags").getBoolean(key);
    }
}
