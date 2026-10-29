package com.huntcraft.village.npc;

import net.minecraft.network.chat.Component;

/**
 * Village NPC roles. Humanoid roles are drawn with their own hcmodel ("npc_&lt;id&gt;"), mofu roles with the shared mofu
 * hcmodel plus role outfit bones.
 */
public enum NpcRole {
    /** Guild receptionist in the gathering hall: opens the quest board. */
    RECEPTIONIST("receptionist", false),
    /** Master smith: forges and upgrades gear (SmithingRegistry). */
    BLACKSMITH("blacksmith", false),
    /** General store: buy / sell (ShopRegistry + village stock). */
    MERCHANT("merchant", false),
    /** Mofu chef of the diner: meals from ingredient combos. */
    CHEF("chef", true),
    /** Village chief: story chapters, hunter rank guidance and milestone rewards. */
    ELDER("elder", false),
    /** Mofu elder of the lodge: hires, equips, trains and sends out companions. */
    MOFU_ELDER("mofu_elder", true);

    public final String id;
    public final boolean mofu;

    NpcRole(String id, boolean mofu) {
        this.id = id;
        this.mofu = mofu;
    }

    /** "Title Name" shown above the NPC. */
    public Component displayName() {
        return Component.translatable("npc.huntcraft." + id);
    }

    public Component title() {
        return Component.translatable("npc.huntcraft." + id + ".title");
    }

    /** hcmodel / texture name of humanoid roles. */
    public String modelName() {
        return mofu ? "mofu" : "npc_" + id;
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
