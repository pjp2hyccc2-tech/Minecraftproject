package com.huntcraft.village.companion;

import net.minecraft.network.chat.Component;

/** Support types of mofu companions. */
public enum MofuType {
    /** Melee specialist: quick claw-blade combos. */
    FIGHTER("fighter", 0xE8604C),
    /** Plays a healing horn when the hunter's health drops below half. */
    HEALER("healer", 0x6FD38A),
    /** Lobs small barrel bombs from range. */
    BOMBER("bomber", 0xF0A030);

    public final String id;
    public final int color;

    MofuType(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public Component displayName() {
        return Component.translatable("mofu.huntcraft.type." + id);
    }

    public Component description() {
        return Component.translatable("mofu.huntcraft.type." + id + ".desc");
    }

    public static MofuType byId(int i) {
        MofuType[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
