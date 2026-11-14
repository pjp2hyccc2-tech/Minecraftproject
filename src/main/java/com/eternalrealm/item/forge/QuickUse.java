package com.eternalrealm.item.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Using an item straight from the inventory: the quick-use keys (1-3) and the bag's use button send the inventory slot
 * ({@code UseItemPacket}). Consumables work as when used from the hand, without the hand.
 */
public final class QuickUse {
    private QuickUse() {
    }

    /**
     * Uses the item in inventory slot {@code slot}, or in a slot of the bag's back (brought forward first, so the item
     * is used as from the front: a return scroll's channel finds it there).
     *
     * @return whether something happened or started (the return scroll's channel)
     */
    public static boolean useSlot(ServerPlayer player, int slot) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        if (com.eternalrealm.item.bag.Bag.isBackSlot(slot)) {
            ItemStack waiting = BagBack.get(player, com.eternalrealm.item.bag.Bag.backIndex(slot));
            if (!(waiting.getItem() instanceof ConsumableItem)) {
                return false;
            }
            slot = BagBack.frontSlot(player, slot);
        }
        if (slot < 0 || slot >= player.getInventory().items.size()) {
            return false;
        }
        ItemStack stack = player.getInventory().items.get(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof ConsumableItem item)) {
            return false;
        }
        return item.useFromInventory(player, stack);
    }
}
