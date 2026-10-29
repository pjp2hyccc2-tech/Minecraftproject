package com.huntcraft.village.npc;

import net.minecraft.network.chat.Component;

/** Village NPC roles. Humanoid roles use 64x64 player skins, mofu roles the mofu hcmodel. */
public enum NpcRole {
    RECEPTIONIST("receptionist", false, true),
    BLACKSMITH("blacksmith", false, false),
    MERCHANT("merchant", false, false),
    CHEF("chef", true, false),
    ELDER("elder", true, false);

    public final String id;
    public final boolean mofu;
    public final boolean slim;

    NpcRole(String id, boolean mofu, boolean slim) {
        this.id = id;
        this.mofu = mofu;
        this.slim = slim;
    }

    public Component displayName() {
        return Component.translatable("npc.huntcraft." + id);
    }

    public static NpcRole byId(int i) {
        NpcRole[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    public static NpcRole byName(String s) {
        for (NpcRole r : values()) if (r.id.equals(s)) return r;
        return RECEPTIONIST;
    }
}
