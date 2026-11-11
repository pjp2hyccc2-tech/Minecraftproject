package com.eternalrealm.item.forge;

import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentSlot;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The trinket slots (装身具, owner's request of 2026-11-11): two accessories and one charm next to the weapon and the
 * armor. Vanilla has no such slots, so the stacks live in the player's persistent data ({@value #TAG}): they stay with
 * the character through logouts, deaths (whatever the keep-inventory rule says) and dimension changes. The owning
 * client sees them over the UI data channel {@value #CHANNEL} (the equipment screen); their stats count like the
 * armor's ({@link GearEvents}). Server thread only.
 */
public final class TrinketSlots {
    public static final String CHANNEL = "trinkets";
    static final String TAG = "ERTrinkets";

    /** The three slots, in screen order. */
    public enum Slot {
        ACCESSORY_1(EquipmentSlot.ACCESSORY),
        ACCESSORY_2(EquipmentSlot.ACCESSORY),
        CHARM(EquipmentSlot.CHARM);

        private final EquipmentSlot kind;

        Slot(EquipmentSlot kind) {
            this.kind = kind;
        }

        /** {@code accessory_1}, {@code accessory_2}, {@code charm}: the UI action target. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** What fits: an accessory or the charm. */
        public EquipmentSlot kind() {
            return kind;
        }

        public static Optional<Slot> byId(String id) {
            for (Slot slot : values()) {
                if (slot.id().equals(id)) {
                    return Optional.of(slot);
                }
            }
            return Optional.empty();
        }
    }

    private TrinketSlots() {
    }

    public static ItemStack get(Player player, Slot slot) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG, Tag.TAG_LIST)) {
            return ItemStack.EMPTY;
        }
        ListTag list = data.getList(TAG, Tag.TAG_COMPOUND);
        int index = slot.ordinal();
        return index < list.size() ? ItemStack.of(list.getCompound(index)) : ItemStack.EMPTY;
    }

    /** Puts {@code stack} into {@code slot} (empty clears it); the stats are re-derived and the client told. */
    public static void set(ServerPlayer player, Slot slot, ItemStack stack) {
        ListTag list = new ListTag();
        for (Slot each : Slot.values()) {
            ItemStack value = each == slot ? stack : get(player, each);
            list.add(value.isEmpty() ? new CompoundTag() : value.save(new CompoundTag()));
        }
        player.getPersistentData().put(TAG, list);
        ServerContext.current().ifPresent(context -> context.combat().markStatsDirty(player.getUUID()));
        sync(player);
    }

    /** The equipment data of the worn trinkets (empty slots and stacks that are not trinkets left out). */
    public static List<EquipmentData> worn(Player player) {
        List<EquipmentData> worn = new ArrayList<>(Slot.values().length);
        for (Slot slot : Slot.values()) {
            EquipmentStacks.read(get(player, slot)).filter(data -> data.slot() == slot.kind()).ifPresent(worn::add);
        }
        return worn;
    }

    /**
     * Where a trinket of {@code kind} goes: {@code wanted} if it fits, else the first empty slot of its kind, else
     * the first slot of its kind (what is worn there comes off).
     */
    public static Slot targetFor(Player player, EquipmentSlot kind, Slot wanted) {
        if (wanted != null && wanted.kind() == kind) {
            return wanted;
        }
        Slot first = null;
        for (Slot slot : Slot.values()) {
            if (slot.kind() != kind) {
                continue;
            }
            if (first == null) {
                first = slot;
            }
            if (get(player, slot).isEmpty()) {
                return slot;
            }
        }
        if (first == null) {
            throw new IllegalArgumentException("Not a trinket kind: " + kind);
        }
        return first;
    }

    /** Sends the three slots to the owning client (login, respawn, every change). */
    public static void sync(ServerPlayer player) {
        JsonObject data = new JsonObject();
        JsonArray stacks = new JsonArray();
        for (Slot slot : Slot.values()) {
            ItemStack stack = get(player, slot);
            stacks.add(stack.isEmpty() ? "" : stack.save(new CompoundTag()).toString());
        }
        data.add("stacks", stacks);
        UiData.send(player, CHANNEL, data);
    }

    /** The trinkets stay with the character (respawn, return from the End). */
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(TAG, Tag.TAG_LIST)) {
            event.getEntity().getPersistentData().put(TAG, old.getList(TAG, Tag.TAG_COMPOUND).copy());
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }
}
