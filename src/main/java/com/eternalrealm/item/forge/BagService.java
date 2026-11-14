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
 *     <li>{@value #SORT}: equipment, consumables, materials, the rest, over the front and the back as one bag (the
 *     front first); matching stacks merged.</li>
 *     <li>{@value #DISCARD} {@code {slot}}: the stack is destroyed (the client asked for a confirmation).</li>
 *     <li>{@value #FORWARD} {@code {slot}}: a stack of the back comes to the front (changing places with the front's
 *     last stack when it is full).</li>
 * </ul>
 * A slot is a front slot ({@value Bag#FIRST_SLOT}..{@value Bag#LAST_SLOT}) or a back slot
 * ({@value Bag#BACK_FIRST}..{@value Bag#BACK_LAST}, {@link BagBack}); equipping from the back brings the piece forward
 * first.
 * Using a consumable goes through {@code UseItemPacket} ({@link QuickUse}).
 */
public final class BagService {
    public static final String EQUIP = "bag.equip";
    public static final String UNEQUIP = "bag.unequip";
    public static final String SORT = "bag.sort";
    public static final String DISCARD = "bag.discard";
    public static final String FORWARD = "bag.forward";

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
            int requested = bagSlot(args);
            TrinketSlots.Slot wanted = args.has("target")
                    ? TrinketSlots.Slot.byId(UiArgs.id(args, "target")).orElse(null) : null;
            if (Bag.isBackSlot(requested) && EquipmentStacks.read(BagBack.get(player,
                    Bag.backIndex(requested))).isEmpty()) {
                return; // nothing to wear there (the client's view was old)
            }
            int slot = BagBack.frontSlot(player, requested);
            if (slot < 0) {
                return;
            }
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
        UiActions.register(FORWARD, (player, context, args) -> {
            int slot = bagSlot(args);
            if (Bag.isBackSlot(slot)) {
                BagBack.bringForward(player, Bag.backIndex(slot));
            }
        });
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
            if (BagBack.roomFor(player, worn) <= 0) {
                return false;
            }
            BagBack.insert(player, worn.copy());
        } else {
            inventory.items.set(free, worn.copy());
        }
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
            if (BagBack.roomFor(player, worn) <= 0) {
                return false;
            }
            BagBack.insert(player, worn.copy());
        } else {
            inventory.items.set(free, worn.copy());
        }
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

    /**
     * Merges matching stacks, then orders the front and the back as one bag ({@link Bag#ORDER}, {@link Bag#slotAt}):
     * nothing is lost, nothing is made.
     */
    public static void sort(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> merged = new ArrayList<>();
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (!stack.isEmpty()) {
                BagBack.merge(merged, stack.copy(), true);
            }
            inventory.items.set(slot, ItemStack.EMPTY);
        }
        for (ItemStack stack : BagBack.stacks(player)) {
            if (!stack.isEmpty()) {
                BagBack.merge(merged, stack, true);
            }
        }
        List<BagEntry> entries = new ArrayList<>(merged.size());
        for (int i = 0; i < merged.size(); i++) {
            entries.add(Bags.entry(Bag.slotAt(i), merged.get(i)));
        }
        List<Integer> order = Bag.sortedSlots(entries);
        List<ItemStack> back = new ArrayList<>(Bag.BACK_SIZE);
        for (int i = 0; i < Bag.BACK_SIZE; i++) {
            back.add(ItemStack.EMPTY);
        }
        for (int position = 0; position < order.size(); position++) {
            int from = order.get(position);
            ItemStack stack = merged.get(Bag.isBagSlot(from) ? from - Bag.FIRST_SLOT
                    : Bag.FRONT_CAPACITY + Bag.backIndex(from));
            int to = Bag.slotAt(position);
            if (Bag.isBagSlot(to)) {
                inventory.items.set(to, stack);
            } else {
                back.set(Bag.backIndex(to), stack);
            }
        }
        BagBack.write(player, back);
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    public static void discard(ServerPlayer player, int slot) {
        if (Bag.isBackSlot(slot)) {
            if (!BagBack.get(player, Bag.backIndex(slot)).isEmpty()) {
                BagBack.set(player, Bag.backIndex(slot), ItemStack.EMPTY);
            }
            return;
        }
        Inventory inventory = player.getInventory();
        if (!inventory.items.get(slot).isEmpty()) {
            inventory.items.set(slot, ItemStack.EMPTY);
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }

    /** A front or back slot from the client (anything else is a violation). */
    private static int bagSlot(JsonObject args) {
        int slot = (int) UiArgs.number(args, "slot", Bag.FIRST_SLOT, Bag.BACK_LAST);
        if (!Bag.inBag(slot)) {
            throw new UiArgs.InvalidArgumentException("not a bag slot: " + slot);
        }
        return slot;
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
