package com.eternalrealm.testkit.bots.forge;

import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.ItemPrices;
import com.eternalrealm.item.forge.ConsumableItem;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.Inventories;
import com.eternalrealm.item.forge.MainHand;
import com.eternalrealm.testkit.bots.BotPlans;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** What a test bot carries: its potions, scrolls and loot, read from its inventory as a player would see them. */
final class BotBag {
    private BotBag() {
    }

    static Consumable consumableIn(ItemStack stack) {
        return stack.getItem() instanceof ConsumableItem item ? item.consumable() : null;
    }

    /** How many of the consumables {@code which} accepts the bot carries. */
    static int count(ServerPlayer bot, Predicate<Consumable> which) {
        int count = 0;
        for (ItemStack stack : bot.getInventory().items) {
            Consumable consumable = consumableIn(stack);
            if (consumable != null && which.test(consumable)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    static int potions(ServerPlayer bot) {
        return count(bot, BotPlans::isHealthPotion);
    }

    /** The inventory slot of {@code consumable}, -1 when the bot has none. */
    static int slotOf(ServerPlayer bot, Consumable consumable) {
        List<ItemStack> items = bot.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            if (consumableIn(items.get(slot)) == consumable) {
                return slot;
            }
        }
        return -1;
    }

    /** The slot of the strongest health potion the bot may drink at {@code level}; -1 when it has none. */
    static int bestPotionSlot(ServerPlayer bot, int level) {
        List<ItemStack> items = bot.getInventory().items;
        int best = -1;
        int bestHeal = -1;
        for (int slot = 0; slot < items.size(); slot++) {
            Consumable consumable = consumableIn(items.get(slot));
            if (consumable == null || !BotPlans.isHealthPotion(consumable) || consumable.requiredLevel() > level) {
                continue;
            }
            int strength = consumable.ordinal();
            if (strength > bestHeal) {
                bestHeal = strength;
                best = slot;
            }
        }
        return best;
    }

    static int freeSlots(ServerPlayer bot) {
        int free = 0;
        for (ItemStack stack : bot.getInventory().items) {
            if (stack.isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /** Slots of loot the shop buys back (materials), the main hand left alone. */
    static List<Integer> loot(ServerPlayer bot) {
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> items = bot.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (slot == MainHand.SLOT || stack.isEmpty() || consumableIn(stack) != null
                    || EquipmentStacks.read(stack).isPresent()) {
                continue;
            }
            if (ItemPrices.materialSellValue(Inventories.idOf(stack)).isPresent()) {
                slots.add(slot);
            }
        }
        return slots;
    }

    /** Slots of equipment the bot does not hold (dropped gear it has no use for), the main hand left alone. */
    static List<Integer> spareEquipment(ServerPlayer bot) {
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> items = bot.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            if (slot != MainHand.SLOT && EquipmentStacks.read(items.get(slot)).isPresent()) {
                slots.add(slot);
            }
        }
        return slots;
    }
}
