package com.eternalrealm.item.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The back of the bag (カバンの奥, owner 2026-11-14: 「バッグパックに入る荷物の量を1.8倍ほどにして」): {@value Bag#BACK_SIZE}
 * slots beyond the {@value Bag#FRONT_CAPACITY} of the front (the vanilla inventory). Vanilla has no such slots, so the
 * stacks live in the player's persistent data ({@value #TAG}, like the trinkets): they stay with the character through
 * logouts, deaths and dimension changes, and the owning client sees them over {@value #CHANNEL}.
 *
 * <p>Things go there only when the front is full — what the game gives, loot, what a player touches on the ground — and
 * they come forward by themselves as soon as the front has room (every half second). So whatever works with the front
 * (the smithy, the market, the room's storage, the quick-use keys) sees them before long; the bag screen also equips,
 * uses and throws away straight from the back, and the sort orders front and back as one bag. Server thread.</p>
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BagBack {
    public static final String CHANNEL = "bag.back";
    static final String TAG = "ERBagBack";
    /** Ticks between two looks for room in the front. */
    private static final int REFILL_TICKS = 10;

    private BagBack() {
    }

    // ------------------------------------------------------------------ storage

    /** The back's stacks, {@value Bag#BACK_SIZE} of them (empty ones included; copies). Empty on the client. */
    public static List<ItemStack> stacks(Player player) {
        List<ItemStack> out = new ArrayList<>(Bag.BACK_SIZE);
        CompoundTag data = player.getPersistentData();
        ListTag list = data.contains(TAG, Tag.TAG_LIST) ? data.getList(TAG, Tag.TAG_COMPOUND) : new ListTag();
        for (int i = 0; i < Bag.BACK_SIZE; i++) {
            ItemStack stack = i < list.size() ? ItemStack.of(list.getCompound(i)) : ItemStack.EMPTY;
            out.add(stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        return out;
    }

    /** The stack at back index {@code index} (a copy). */
    public static ItemStack get(Player player, int index) {
        if (index < 0 || index >= Bag.BACK_SIZE) {
            return ItemStack.EMPTY;
        }
        return stacks(player).get(index);
    }

    /** Whether nothing waits in the back (cheap: no stack is read). */
    public static boolean isEmpty(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG, Tag.TAG_LIST)) {
            return true;
        }
        ListTag list = data.getList(TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            if (!list.getCompound(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Stores the stacks (list index = back index) and tells the client. */
    static void write(ServerPlayer player, List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (int i = 0; i < Bag.BACK_SIZE; i++) {
            ItemStack stack = i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
            list.add(stack.isEmpty() ? new CompoundTag() : stack.save(new CompoundTag()));
        }
        player.getPersistentData().put(TAG, list);
        sync(player);
    }

    /** Empties the back (tests, administration). */
    public static void clear(ServerPlayer player) {
        write(player, List.of());
    }

    /** Puts {@code stack} at back index {@code index} (empty clears it). */
    public static void set(ServerPlayer player, int index, ItemStack stack) {
        if (index < 0 || index >= Bag.BACK_SIZE) {
            throw new IllegalArgumentException("not a back index: " + index);
        }
        List<ItemStack> back = stacks(player);
        back.set(index, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        write(player, back);
    }

    /**
     * Moves as much of {@code stack} as fits into the back, onto matching stacks first; the stack shrinks by what went
     * in. @return how many went in
     */
    public static int insert(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        List<ItemStack> back = stacks(player);
        int before = stack.getCount();
        merge(back, stack);
        int added = before - stack.getCount();
        if (added > 0) {
            write(player, back);
        }
        return added;
    }

    /** How many of {@code stack} the back could still take. */
    public static int roomFor(Player player, ItemStack stack) {
        int room = 0;
        for (ItemStack held : stacks(player)) {
            if (held.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (held.isStackable() && ItemStack.isSameItemSameTags(held, stack)) {
                room += Math.max(0, held.getMaxStackSize() - held.getCount());
            }
        }
        return room;
    }

    /**
     * Onto matching stacks of {@code into}, then into its empty places, and with {@code grow} what is left as new
     * stacks at its end; {@code stack} shrinks.
     */
    static void merge(List<ItemStack> into, ItemStack stack, boolean grow) {
        merge(into, stack);
        while (grow && !stack.isEmpty()) {
            into.add(stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
        }
    }

    /** Onto matching stacks of {@code into}, then into its empty places; {@code stack} shrinks. */
    static void merge(List<ItemStack> into, ItemStack stack) {
        for (ItemStack held : into) {
            if (stack.isEmpty()) {
                return;
            }
            if (!held.isEmpty() && held.isStackable() && ItemStack.isSameItemSameTags(held, stack)
                    && held.getCount() < held.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 0; i < into.size() && !stack.isEmpty(); i++) {
            if (into.get(i).isEmpty()) {
                into.set(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
            }
        }
    }

    // ------------------------------------------------------------------ moving between front and back

    /** Moves what waits in the back to the front as far as the front has room. @return whether anything moved */
    public static boolean refill(ServerPlayer player) {
        if (isEmpty(player)) {
            return false;
        }
        Inventory inventory = player.getInventory();
        if (Bags.firstFree(inventory) < 0 && !hasPartialStack(inventory)) {
            return false; // the front is full to the brim: nothing can come forward
        }
        List<ItemStack> back = stacks(player);
        boolean moved = false;
        for (int i = 0; i < back.size(); i++) {
            ItemStack stack = back.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            int before = stack.getCount();
            Bags.insert(inventory, stack);
            if (stack.getCount() != before) {
                moved = true;
                back.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        }
        if (moved) {
            write(player, back);
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
        }
        return moved;
    }

    private static boolean hasPartialStack(Inventory inventory) {
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            ItemStack held = inventory.items.get(slot);
            if (!held.isEmpty() && held.isStackable() && held.getCount() < held.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Brings the stack at back index {@code index} into the front: into a free slot, else it changes places with the
     * front's last stack (which goes into the back). @return its front slot now, or -1 when that place was empty
     */
    public static int bringForward(ServerPlayer player, int index) {
        if (index < 0 || index >= Bag.BACK_SIZE) {
            return -1;
        }
        List<ItemStack> back = stacks(player);
        ItemStack stack = back.get(index);
        if (stack.isEmpty()) {
            return -1;
        }
        Inventory inventory = player.getInventory();
        int free = Bags.firstFree(inventory);
        int slot = free >= 0 ? free : Bag.LAST_SLOT;
        ItemStack displaced = inventory.items.get(slot);
        inventory.items.set(slot, stack);
        back.set(index, displaced.isEmpty() ? ItemStack.EMPTY : displaced);
        write(player, back);
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        return slot;
    }

    /** The front slot of a bag slot: itself in the front, the stack brought forward from the back, -1 if empty. */
    public static int frontSlot(ServerPlayer player, int slot) {
        if (Bag.isBagSlot(slot)) {
            return slot;
        }
        return Bag.isBackSlot(slot) ? bringForward(player, Bag.backIndex(slot)) : -1;
    }

    // ------------------------------------------------------------------ the client

    /** Sends the back's stacks (index order) to the owning client (login, respawn, every change). */
    public static void sync(ServerPlayer player) {
        JsonObject data = new JsonObject();
        JsonArray stacks = new JsonArray();
        for (ItemStack stack : stacks(player)) {
            stacks.add(stack.isEmpty() ? "" : stack.save(new CompoundTag()).toString());
        }
        data.add("stacks", stacks);
        UiData.send(player, CHANNEL, data);
    }

    // ------------------------------------------------------------------ events

    /** The back stays with the character through deaths (whatever the keep-inventory rule says) and the End. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(TAG, Tag.TAG_LIST)) {
            event.getEntity().getPersistentData().put(TAG, old.getList(TAG, Tag.TAG_COMPOUND).copy());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % REFILL_TICKS != 7) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isAlive() && !player.isSpectator()) {
                refill(player);
            }
        }
    }

    /**
     * A stack touched on the ground that the front cannot take whole: the front takes what it can and the back the
     * rest, with the pickup's sound, animation, statistics and events (the pickup feed, "collect" quests).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPickup(EntityItemPickupEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        ItemEntity entity = event.getItem();
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || entity.hasPickUpDelay()) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (Bags.roomFor(inventory, stack) >= stack.getCount() || roomFor(player, stack) <= 0
                || !mayPickUp(player, entity)) {
            return; // the front takes it all the usual way, or there is no room anywhere
        }
        ItemStack before = stack.copy();
        int count = stack.getCount();
        Bags.insert(inventory, stack);
        insert(player, stack);
        int taken = count - stack.getCount();
        if (taken <= 0) {
            return;
        }
        event.setCanceled(true);
        ForgeEventFactory.firePlayerItemPickupEvent(player, entity, before.copyWithCount(taken));
        player.take(entity, taken);
        player.awardStat(Stats.ITEM_PICKED_UP.get(before.getItem()), taken);
        if (stack.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(stack);
        }
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    /** Vanilla's rule: an item meant for someone else waits for them until its last ten seconds. */
    private static boolean mayPickUp(ServerPlayer player, ItemEntity entity) {
        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        if (!saved.hasUUID("Owner") || saved.getUUID("Owner").equals(player.getUUID())) {
            return true;
        }
        return entity.lifespan - entity.getAge() <= 200;
    }
}
