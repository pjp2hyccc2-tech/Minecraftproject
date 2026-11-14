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

    /**
     * Item id → count of the plain items in the main inventory and the bag's back (the back is the server's: the
     * client's smithy screen adds its own copy of it).
     */
    public static Map<String, Integer> counts(net.minecraft.world.entity.player.Player player) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            add(counts, stack);
        }
        if (!player.level().isClientSide()) {
            for (ItemStack stack : BagBack.stacks(player)) {
                add(counts, stack);
            }
        }
        return counts;
    }

    /** Adds a plain item (not equipment) to {@code counts}. */
    public static void add(Map<String, Integer> counts, ItemStack stack) {
        if (!stack.isEmpty() && !EquipmentStacks.isEquipment(stack)) {
            counts.merge(idOf(stack), stack.getCount(), Integer::sum);
        }
    }

    /**
     * Removes the items, from the main inventory first, then from the bag's back (callers checked the counts first).
     * @return whether everything was removed
     */
    public static boolean take(ServerPlayer player, Map<String, Integer> items) {
        Map<String, Integer> left = new HashMap<>(items);
        for (ItemStack stack : player.getInventory().items) {
            takeFrom(stack, left);
        }
        player.getInventory().setChanged();
        if (left.values().stream().anyMatch(count -> count > 0) && !BagBack.isEmpty(player)) {
            java.util.List<ItemStack> back = BagBack.stacks(player);
            boolean changed = false;
            for (int i = 0; i < back.size(); i++) {
                int before = back.get(i).getCount();
                takeFrom(back.get(i), left);
                if (back.get(i).getCount() != before) {
                    changed = true;
                    if (back.get(i).isEmpty()) {
                        back.set(i, ItemStack.EMPTY);
                    }
                }
            }
            if (changed) {
                BagBack.write(player, back);
            }
        }
        return left.values().stream().allMatch(count -> count <= 0);
    }

    private static void takeFrom(ItemStack stack, Map<String, Integer> left) {
        if (stack.isEmpty() || EquipmentStacks.isEquipment(stack)) {
            return;
        }
        String id = idOf(stack);
        int wanted = left.getOrDefault(id, 0);
        if (wanted <= 0) {
            return;
        }
        int taken = Math.min(wanted, stack.getCount());
        stack.shrink(taken);
        left.put(id, wanted - taken);
    }

    /** Into the bag (shown in the pickup feed), the rest dropped at the player's feet for them alone ({@link Bags}). */
    public static void give(ServerPlayer player, ItemStack stack) {
        Bags.give(player, stack);
    }

    /** Loot new to the world: like {@link #give}, and it counts for "collect" quest objectives ({@link Bags#loot}). */
    public static void loot(ServerPlayer player, ItemStack stack) {
        Bags.loot(player, stack);
    }

    public static Item item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? null : ForgeRegistries.ITEMS.getValue(location);
    }
}
