package com.eternalrealm.item.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.WeaponItem;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.Jobs;
import com.eternalrealm.server.ServerContext;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The main hand is inventory slot 0: there is no hotbar, the client keeps the selection there (outside creative and
 * spectator mode) and the number keys use quick-use items instead. Slot 0 holds the equipped weapon; equipping a
 * weapon swaps it into slot 0 ({@link #equip}), and at login a weapon found elsewhere is moved there when slot 0 holds
 * none ({@link #ensureWeapon}), so characters from before the change keep fighting.
 */
public final class MainHand {
    /** The inventory slot of the main hand. */
    public static final int SLOT = 0;

    private MainHand() {
    }

    /**
     * Selects slot 0 and, when it holds no weapon, swaps in the first weapon of the player's job found in the inventory
     * (or any weapon). @return whether a weapon was moved
     */
    public static boolean ensureWeapon(ServerPlayer player) {
        select(player);
        Inventory inventory = player.getInventory();
        if (isWeapon(inventory.items.get(SLOT))) {
            return false;
        }
        JobType job = ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::jobId).map(JobType::fromStored).orElse(JobType.NONE);
        int any = -1;
        for (int i = 0; i < inventory.items.size(); i++) {
            if (i == SLOT) {
                continue;
            }
            WeaponProfile profile = WeaponItem.profileOf(inventory.items.get(i));
            if (profile == null) {
                continue;
            }
            if (job.isChosen() && Jobs.get(job).weapons().contains(profile.type())) {
                swap(player, i);
                return true;
            }
            if (any < 0) {
                any = i;
            }
        }
        if (any >= 0) {
            swap(player, any);
            return true;
        }
        return false;
    }

    /** Equips the weapon in inventory slot {@code slot} (swaps it with slot 0). @return whether it is a weapon */
    public static boolean equip(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.items.size() || !isWeapon(inventory.items.get(slot))) {
            return false;
        }
        if (slot != SLOT) {
            swap(player, slot);
        }
        select(player);
        return true;
    }

    public static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty() && WeaponItem.profileOf(stack) != null;
    }

    private static void swap(ServerPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        ItemStack held = inventory.items.get(SLOT);
        inventory.items.set(SLOT, inventory.items.get(slot));
        inventory.items.set(slot, held);
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static void select(ServerPlayer player) {
        if (player.getInventory().selected != SLOT) {
            player.getInventory().selected = SLOT;
            player.connection.send(new ClientboundSetCarriedItemPacket(SLOT));
        }
    }
}
