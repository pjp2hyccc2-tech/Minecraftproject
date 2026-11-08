package com.eternalrealm.npc.forge;

import com.eternalrealm.item.forge.BlacksmithMenu;
import com.eternalrealm.item.forge.ShopMenu;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

/** What each town NPC does: menus for shop and blacksmith, journal pages for the others. */
public final class NpcServices {
    private NpcServices() {
    }

    public static void register() {
        Npcs.onInteract(NpcRole.ITEM_SHOP, (player, npc) -> ShopMenu.open(player));
        Npcs.onInteract(NpcRole.BLACKSMITH, (player, npc) -> BlacksmithMenu.open(player));
        Npcs.onInteract(NpcRole.QUEST_BOARD, (player, npc) -> {
            ServerContext.current().ifPresent(context -> context.game().quests().sendView(player));
            openPage(player, "quests");
        });
        Npcs.onInteract(NpcRole.JOB_MASTER, (player, npc) -> openPage(player, "job"));
        Npcs.onInteract(NpcRole.GUILD_REGISTRAR, (player, npc) -> openPage(player, "guild"));
        Npcs.onInteract(NpcRole.HALL_OF_FAME, (player, npc) -> openPage(player, "ranking"));
        Npcs.onInteract(NpcRole.STORY, StoryNpcs::talk);
    }

    /** Opens a journal page on the player's client. */
    public static void openPage(ServerPlayer player, String tab) {
        JsonObject request = new JsonObject();
        request.addProperty("tab", tab);
        request.addProperty("nonce", System.nanoTime()); // the same page twice is still a new request
        UiData.send(player, "open", request);
    }
}
