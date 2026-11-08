package com.eternalrealm.dungeon.forge;

import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.dungeon.RaidDefinition;
import com.eternalrealm.menu.MenuItems;
import com.eternalrealm.menu.ServerMenu;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.world.forge.WorldService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The gatekeeper: the eight dungeons and the final raid with recommended level, party size and whether their area is
 * open. Click: normal, right click: hard, shift-click: EX. Only the party leader (or a solo player) starts a run.
 */
public final class GatekeeperMenu {
    private static final Item[] ICONS = {Items.IRON_DOOR, Items.OAK_SAPLING, Items.SANDSTONE, Items.CHAIN, Items.BLUE_ICE,
            Items.MAGMA_BLOCK, Items.FEATHER, Items.CRYING_OBSIDIAN};

    private GatekeeperMenu() {
    }

    public static void open(ServerPlayer player) {
        ServerMenu.open(player, Component.translatable("screen.eternalrealm.gate.title"), 3, GatekeeperMenu::fill);
    }

    private static void fill(ServerMenu menu) {
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.set(slot, MenuItems.filler(), null);
        }
        WorldService world = ServerContext.current().map(context -> context.game().world()).orElse(null);
        List<DungeonDefinition> dungeons = Dungeons.all();
        for (int i = 0; i < dungeons.size(); i++) {
            DungeonDefinition dungeon = dungeons.get(i);
            boolean open = world != null && world.isUnlocked(dungeon.areaId());
            menu.set(9 + i, icon(i < ICONS.length ? ICONS[i] : Items.IRON_DOOR, dungeon.translationKey(),
                    dungeon.recommendedLevel(), dungeon.minPartySize(), dungeon.maxPartySize(), open),
                    open ? (player, button, shift) -> start(player, dungeon.id(), button, shift) : null);
        }
        RaidDefinition raid = Dungeons.FINAL_RAID;
        boolean raidOpen = world != null && world.progress().finalBossUnlocked();
        menu.set(17, icon(Items.DRAGON_HEAD, raid.translationKey(), raid.recommendedLevel(), raid.minPartySize(),
                raid.maxPartySize(), raidOpen), raidOpen ? (player, button, shift) -> start(player, raid.id(), button, shift)
                : null);
    }

    private static ItemStack icon(Item item, String nameKey, int level, int minParty, int maxParty, boolean open) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("screen.eternalrealm.gate.level", level).withStyle(ChatFormatting.AQUA));
        lore.add(Component.translatable("screen.eternalrealm.gate.party", minParty, maxParty));
        if (open) {
            lore.add(Component.translatable("screen.eternalrealm.gate.difficulty").withStyle(ChatFormatting.YELLOW));
        } else {
            lore.add(Component.translatable("screen.eternalrealm.gate.locked").withStyle(ChatFormatting.RED));
        }
        return MenuItems.icon(new ItemStack(open ? item : Items.BARRIER), Component.translatable(nameKey)
                .withStyle(open ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY), lore, false);
    }

    private static void start(ServerPlayer player, String id, int button, boolean shift) {
        Difficulty difficulty = shift ? Difficulty.EX : button == 1 ? Difficulty.HARD : Difficulty.NORMAL;
        player.closeContainer();
        ServerContext.current().ifPresent(context -> context.game().dungeons().start(player, id, difficulty));
    }
}
