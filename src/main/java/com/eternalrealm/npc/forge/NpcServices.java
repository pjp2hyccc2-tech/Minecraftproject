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
        Npcs.onInteract(NpcRole.ITEM_SHOP, (player, npc) -> {
            StoryNpcs.serviceLine(player, NpcRole.ITEM_SHOP);
            ShopMenu.open(player);
        });
        Npcs.onInteract(NpcRole.BLACKSMITH, (player, npc) -> {
            StoryNpcs.serviceLine(player, NpcRole.BLACKSMITH);
            BlacksmithMenu.open(player);
        });
        Npcs.onInteract(NpcRole.QUEST_BOARD, (player, npc) -> {
            StoryNpcs.serviceLine(player, NpcRole.QUEST_BOARD);
            ServerContext.current().ifPresent(context -> context.game().quests().sendView(player));
            openPage(player, "quests");
        });
        Npcs.onInteract(NpcRole.JOB_MASTER, (player, npc) -> {
            StoryNpcs.serviceLine(player, NpcRole.JOB_MASTER);
            openPage(player, "job");
        });
        Npcs.onInteract(NpcRole.GUILD_REGISTRAR, (player, npc) -> {
            StoryNpcs.serviceLine(player, NpcRole.GUILD_REGISTRAR);
            openPage(player, "guild");
        });
        Npcs.onInteract(NpcRole.HALL_OF_FAME, (player, npc) -> {
            StoryNpcs.serviceLine(player, NpcRole.HALL_OF_FAME);
            openPage(player, "ranking");
        });
        Npcs.onInteract(NpcRole.STORY, StoryNpcs::talk);
        Npcs.onInteract(NpcRole.GATEKEEPER, (player, npc) -> {
            StoryNpcs.serviceLine(player, NpcRole.GATEKEEPER);
            com.eternalrealm.dungeon.forge.GatekeeperMenu.open(player);
        });
        for (NpcRole role : new NpcRole[] {NpcRole.AUCTION, NpcRole.MAILBOX, NpcRole.ARENA_MASTER}) {
            Npcs.onInteract(role, (player, npc) -> {
                StoryNpcs.serviceLine(player, role);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.eternalrealm.npc.coming_soon").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            });
        }
    }

    /** Opens a journal page on the player's client. */
    public static void openPage(ServerPlayer player, String tab) {
        JsonObject request = new JsonObject();
        request.addProperty("tab", tab);
        request.addProperty("nonce", System.nanoTime()); // the same page twice is still a new request
        UiData.send(player, "open", request);
    }
}
