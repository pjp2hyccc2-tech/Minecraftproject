package com.eternalrealm.item.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/** Counting, taking and giving items of a player's main inventory by item id (equipment never counts as a material). */
public final class Inventories {
    private Inventories() {
    }

    public static String idOf(ItemStack stack) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /** Item id → count of the plain items in the main inventory (also used by the client's smithy screen). */
    public static Map<String, Integer> counts(net.minecraft.world.entity.player.Player player) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && !EquipmentStacks.isEquipment(stack)) {
                counts.merge(idOf(stack), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** Removes the items (callers checked the counts first). @return whether everything was removed */
    public static boolean take(ServerPlayer player, Map<String, Integer> items) {
        Map<String, Integer> left = new HashMap<>(items);
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || EquipmentStacks.isEquipment(stack)) {
                continue;
            }
            String id = idOf(stack);
            int wanted = left.getOrDefault(id, 0);
            if (wanted <= 0) {
                continue;
            }
            int taken = Math.min(wanted, stack.getCount());
            stack.shrink(taken);
            left.put(id, wanted - taken);
        }
        player.getInventory().setChanged();
        return left.values().stream().allMatch(count -> count <= 0);
    }

    /** Into the inventory, the rest dropped at the player's feet. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    public static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : ForgeRegistries.ITEMS.getValue(location);
    }
}
