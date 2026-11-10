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

    /** @return whether something happened or started (the return scroll's channel) */
    public static boolean useSlot(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= player.getInventory().items.size() || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        ItemStack stack = player.getInventory().items.get(slot);
        if (stack.isEmpty() || !(stack.getItem() instanceof ConsumableItem item)) {
            return false;
        }
        return item.useFromInventory(player, stack);
    }
}
