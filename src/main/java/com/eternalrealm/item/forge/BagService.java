package com.eternalrealm.item.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentSlot;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.item.bag.BagEntry;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The server side of the bag and equipment screens (UI actions; every change is checked here):
 * <ul>
 *     <li>{@value #EQUIP} {@code {slot, target?}}: a weapon from the bag swaps into slot 0 ({@link MainHand#equip}), an
 *     armor piece into its armor slot, an accessory or a charm into a trinket slot ({@link TrinketSlots}: the
 *     {@code target} one if given, else the first free one); what was worn goes back into the bag slot. The job's
 *     weapons, the level and (trinkets) one copy of each named trinket count.</li>
 *     <li>{@value #UNEQUIP} {@code {target: weapon|head|chest|legs|feet|accessory_1..4|charm|charm_2}}: into the
 *     first free bag slot.</li>
 *     <li>{@value #SORT}: equipment, consumables, materials, the rest; matching stacks merged.</li>
 *     <li>{@value #DISCARD} {@code {slot}}: the stack is destroyed (the client asked for a confirmation).</li>
 * </ul>
 * Using a consumable goes through {@code UseItemPacket} ({@link QuickUse}).
 */
public final class BagService {
    public static final String EQUIP = "bag.equip";
    public static final String UNEQUIP = "bag.unequip";
    public static final String SORT = "bag.sort";
    public static final String DISCARD = "bag.discard";

    /** Why an equip was refused (also the message key suffix). */
    public enum EquipResult {
        OK,
        NOT_EQUIPMENT,
        LEVEL,
        JOB,
        /** The same named trinket is already worn. */
        DUPLICATE
    }

    private BagService() {
    }

    public static void register() {
        UiActions.register(EQUIP, (player, context, args) -> {
            int slot = bagSlot(args);
            TrinketSlots.Slot wanted = args.has("target")
                    ? TrinketSlots.Slot.byId(UiArgs.id(args, "target")).orElse(null) : null;
            EquipResult result = equip(player, slot, wanted);
            if (result != EquipResult.OK) {
                tellRefusal(player, result, player.getInventory().items.get(slot));
            }
        });
        UiActions.register(UNEQUIP, (player, context, args) -> {
            String id = UiArgs.id(args, "target");
            Optional<TrinketSlots.Slot> trinket = TrinketSlots.Slot.byId(id);
            boolean done = trinket.isPresent() ? unequip(player, trinket.get()) : unequip(player, target(id));
            if (!done) {
                tell(player, "message.eternalrealm.bag.full");
            }
        });
        UiActions.register(SORT, (player, context, args) -> sort(player));
        UiActions.register(DISCARD, (player, context, args) -> discard(player, bagSlot(args)));
    }

    /** Equips the piece in bag slot {@code slot}. */
    public static EquipResult equip(ServerPlayer player, int slot) {
        return equip(player, slot, null);
    }

    /** Equips the piece in bag slot {@code slot}; a trinket goes to {@code wanted} if it fits there. */
    public static EquipResult equip(ServerPlayer player, int slot, TrinketSlots.Slot wanted) {
        Inventory inventory = player.getInventory();
        ItemStack stack = inventory.items.get(slot);
        Optional<EquipmentData> data = EquipmentStacks.read(stack);
        if (data.isEmpty()) {
            return EquipResult.NOT_EQUIPMENT;
        }
        int level = snapshot(player).map(CharacterSnapshot::level).orElse(1);
        if (!data.get().canBeUsedBy(level, player.getUUID())) {
            return EquipResult.LEVEL;
        }
        if (data.get().isTrinket()) {
            TrinketSlots.Slot target = TrinketSlots.targetFor(player, data.get().slot(), wanted);
            String id = data.get().design() == null ? null : data.get().design().id();
            for (TrinketSlots.Slot other : TrinketSlots.Slot.values()) {
                if (other != target && id != null && EquipmentStacks.designOf(TrinketSlots.get(player, other))
                        .map(design -> design.id().equals(id)).orElse(false)) {
                    return EquipResult.DUPLICATE;
                }
            }
            ItemStack worn = TrinketSlots.get(player, target);
            TrinketSlots.set(player, target, stack.copy());
            inventory.items.set(slot, worn.copy());
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            return EquipResult.OK;
        }
        if (data.get().isWeapon()) {
            JobType job = job(player);
            if (job.isChosen() && !Jobs.canUse(job, data.get().weaponType())) {
                return EquipResult.JOB;
            }
            return MainHand.equip(player, slot) ? EquipResult.OK : EquipResult.NOT_EQUIPMENT;
        }
        net.minecraft.world.entity.EquipmentSlot vanilla = GearArmorItem.vanillaSlot(data.get().slot());
        ItemStack worn = player.getItemBySlot(vanilla);
        player.setItemSlot(vanilla, stack.copy());
        inventory.items.set(slot, worn.copy());
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        return EquipResult.OK;
    }

    /** Takes off the weapon or an armor piece into the first free bag slot. @return false when the bag is full */
    public static boolean unequip(ServerPlayer player, EquipmentSlot target) {
        Inventory inventory = player.getInventory();
        ItemStack worn = target == EquipmentSlot.WEAPON ? inventory.items.get(MainHand.SLOT)
                : player.getItemBySlot(GearArmorItem.vanillaSlot(target));
        if (worn.isEmpty()) {
            return true;
        }
        int free = Bags.firstFree(inventory);
        if (free < 0) {
            return false;
        }
        inventory.items.set(free, worn.copy());
        if (target == EquipmentSlot.WEAPON) {
            inventory.items.set(MainHand.SLOT, ItemStack.EMPTY);
        } else {
            player.setItemSlot(GearArmorItem.vanillaSlot(target), ItemStack.EMPTY);
        }
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /** Takes off a trinket into the first free bag slot. @return false when the bag is full */
    public static boolean unequip(ServerPlayer player, TrinketSlots.Slot target) {
        ItemStack worn = TrinketSlots.get(player, target);
        if (worn.isEmpty()) {
            return true;
        }
        Inventory inventory = player.getInventory();
        int free = Bags.firstFree(inventory);
        if (free < 0) {
            return false;
        }
        inventory.items.set(free, worn.copy());
        TrinketSlots.set(player, target, ItemStack.EMPTY);
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /** "装備にはレベル55が必要です" (with the level), or the plain refusal. */
    static void tellRefusal(ServerPlayer player, EquipResult result, ItemStack stack) {
        String key = "message.eternalrealm.bag.equip." + result.name().toLowerCase(java.util.Locale.ROOT);
        if (result == EquipResult.LEVEL) {
            int needed = EquipmentStacks.requiredLevel(stack).orElse(1);
            player.displayClientMessage(Component.translatable("message.eternalrealm.equipment.level_required",
                    needed, stack.getHoverName()).withStyle(ChatFormatting.RED), true);
            return;
        }
        tell(player, key);
    }

    /** Merges matching stacks, then orders the bag ({@link Bag#ORDER}). */
    public static void sort(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
            inventory.items.set(slot, ItemStack.EMPTY);
        }
        for (ItemStack stack : stacks) {
            Bags.insert(inventory, stack); // merges into matching stacks first
        }
        List<BagEntry> entries = Bags.entries(player);
        List<Integer> order = Bag.sortedSlots(entries);
        List<ItemStack> sorted = new ArrayList<>(order.size());
        for (int slot : order) {
            sorted.add(inventory.items.get(slot));
        }
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            int index = slot - Bag.FIRST_SLOT;
            inventory.items.set(slot, index < sorted.size() ? sorted.get(index) : ItemStack.EMPTY);
        }
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    public static void discard(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        if (!inventory.items.get(slot).isEmpty()) {
            inventory.items.set(slot, ItemStack.EMPTY);
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static int bagSlot(JsonObject args) {
        return (int) UiArgs.number(args, "slot", Bag.FIRST_SLOT, Bag.LAST_SLOT);
    }

    private static EquipmentSlot target(String id) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.id().equals(id) && !slot.isTrinket()) {
                return slot;
            }
        }
        throw new UiArgs.InvalidArgumentException("unknown equipment slot " + id);
    }

    private static Optional<CharacterSnapshot> snapshot(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()));
    }

    private static JobType job(ServerPlayer player) {
        return snapshot(player).map(CharacterSnapshot::jobId).map(JobType::fromStored).orElse(JobType.NONE);
    }

    private static void tell(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }
}
