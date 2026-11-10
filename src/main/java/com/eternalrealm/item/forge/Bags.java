package com.eternalrealm.item.forge;

import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.item.bag.BagCategory;
import com.eternalrealm.item.bag.BagEntry;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.PickupFeedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The bag (inventory slots 1..35; slot 0 is the equipped weapon): what the game gives a player goes straight into it,
 * first onto matching stacks, then into free slots. What does not fit drops at the player's feet for them alone. Every
 * acquisition, and every item picked up from the ground, shows in the player's pickup feed.
 */
public final class Bags {
    private Bags() {
    }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(Bags::onPickup);
    }

    /** Gives {@code stack} (consumed): into the bag, the rest at the player's feet; both show in the feed. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack shown = stack.copyWithCount(1);
        int before = stack.getCount();
        insert(player.getInventory(), stack);
        int added = before - stack.getCount();
        if (added > 0) {
            feed(player, shown, added, 0);
        }
        if (!stack.isEmpty()) {
            feed(player, shown, stack.getCount(), PickupFeedPacket.BAG_FULL);
            dropFor(player, stack);
        }
        player.inventoryMenu.broadcastChanges();
    }

    /** Moves as much of {@code stack} as fits into the bag slots (the stack shrinks by what went in). */
    public static void insert(Inventory inventory, ItemStack stack) {
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT && !stack.isEmpty(); slot++) {
            ItemStack held = inventory.items.get(slot);
            if (!held.isEmpty() && held.isStackable() && ItemStack.isSameItemSameTags(held, stack)
                    && held.getCount() < held.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT && !stack.isEmpty(); slot++) {
            if (inventory.items.get(slot).isEmpty()) {
                inventory.items.set(slot, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
            }
        }
        inventory.setChanged();
    }

    /** The first empty bag slot, or -1. */
    public static int firstFree(Inventory inventory) {
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            if (inventory.items.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** The occupied bag slots as the pure bag model sees them (tabs, sort). */
    public static List<BagEntry> entries(Player player) {
        List<BagEntry> out = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (!stack.isEmpty()) {
                out.add(entry(slot, stack));
            }
        }
        return out;
    }

    public static BagEntry entry(int slot, ItemStack stack) {
        return new BagEntry(slot, Inventories.idOf(stack), stack.getCount(), category(stack), rarity(stack),
                EquipmentStacks.read(stack).map(data -> data.requiredLevel()).orElse(0));
    }

    /** The tab of an item: equipment, consumables (potions, scrolls), materials, everything else. */
    public static BagCategory category(ItemStack stack) {
        if (EquipmentStacks.isEquipment(stack)) {
            return BagCategory.EQUIPMENT;
        }
        if (stack.getItem() instanceof ConsumableItem) {
            return BagCategory.CONSUMABLE;
        }
        if (stack.getItem() instanceof MaterialItem) {
            return BagCategory.MATERIAL;
        }
        return BagCategory.OTHER;
    }

    public static ItemRarity rarity(ItemStack stack) {
        return EquipmentStacks.read(stack).map(data -> data.rarity()).orElse(ItemRarity.COMMON);
    }

    /** One row of the player's pickup feed. */
    public static void feed(ServerPlayer player, ItemStack stack, int count, int flags) {
        if (!stack.isEmpty() && count > 0) {
            ERNetwork.sendToPlayer(player, new PickupFeedPacket(stack.copyWithCount(1), count, flags));
        }
    }

    /** Dropped at the player's feet; only they can pick it up. */
    private static void dropFor(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        ItemEntity entity = new ItemEntity(level, player.getX(), player.getY() + 0.3, player.getZ(), stack.copy(),
                0.0, 0.2, 0.0);
        entity.setPickUpDelay(40);
        entity.setTarget(player.getUUID());
        level.addFreshEntity(entity);
        stack.setCount(0);
    }

    /** Items picked up from the ground show in the feed too. */
    private static void onPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack stack = event.getStack();
            feed(player, stack, stack.getCount(), 0);
        }
    }
}
