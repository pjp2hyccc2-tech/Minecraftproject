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
 * The bag (its front: inventory slots 1..35, slot 0 is the equipped weapon; its back: {@link BagBack}): what the game
 * gives a player goes straight into it, first onto matching stacks, then into free slots of the front, then into the
 * back. What does not fit drops at the player's feet for them alone. Every acquisition, and every item picked up from
 * the ground, shows in the player's pickup feed.
 */
public final class Bags {
    private Bags() {
    }

    public static void install() {
        MinecraftForge.EVENT_BUS.addListener(Bags::onPickup);
    }

    /** Gives {@code stack} (consumed): into the bag, the rest at the player's feet; both show in the feed. */
    public static int give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ItemStack shown = stack.copyWithCount(1);
        int before = stack.getCount();
        insert(player.getInventory(), stack);
        if (!stack.isEmpty()) {
            BagBack.insert(player, stack);
        }
        int added = before - stack.getCount();
        if (added > 0) {
            feed(player, shown, added, 0);
        }
        if (!stack.isEmpty()) {
            feed(player, shown, stack.getCount(), PickupFeedPacket.BAG_FULL);
            dropFor(player, stack);
        }
        player.inventoryMenu.broadcastChanges();
        return added;
    }

    /**
     * Loot new to the world (monster drops, boss and dungeon rewards) into the bag, like {@link #give}, and what went
     * in counts for "collect" quest objectives. What does not fit drops at the player's feet with no thrower, so it
     * counts when picked up ({@code QuestActions.onItemPickup}): nothing is counted twice.
     *
     * @return how many went into the bag
     */
    public static int loot(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
        int added = give(player, stack);
        if (added > 0) {
            com.eternalrealm.server.ServerContext.current().ifPresent(context -> context.game().quests().event(player,
                    new com.eternalrealm.quest.GameEvent.ItemCollected(id, Math.min(added, 64 * 64))));
        }
        return added;
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

    /** How many of {@code stack} the front (slots 1..35) can still take. */
    public static int roomFor(Inventory inventory, ItemStack stack) {
        int room = 0;
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            ItemStack held = inventory.items.get(slot);
            if (held.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (held.isStackable() && ItemStack.isSameItemSameTags(held, stack)) {
                room += Math.max(0, held.getMaxStackSize() - held.getCount());
            }
        }
        return room;
    }

    /** Whether the whole of {@code stack} fits into the front and the back together. */
    public static boolean fits(ServerPlayer player, ItemStack stack) {
        int room = roomFor(player.getInventory(), stack);
        return room >= stack.getCount() || room + BagBack.roomFor(player, stack) >= stack.getCount();
    }

    /** The first empty slot of the front, or -1. */
    public static int firstFree(Inventory inventory) {
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            if (inventory.items.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** The occupied slots of the front and the back (server: the back is read from the player's data). */
    public static List<BagEntry> allEntries(Player player) {
        List<BagEntry> out = entries(player);
        List<ItemStack> back = BagBack.stacks(player);
        for (int i = 0; i < back.size(); i++) {
            if (!back.get(i).isEmpty()) {
                out.add(entry(Bag.BACK_FIRST + i, back.get(i)));
            }
        }
        return out;
    }

    /** The occupied slots of the front as the pure bag model sees them (tabs, sort). */
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
