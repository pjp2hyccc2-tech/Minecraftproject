package com.eternalrealm.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A chest-style menu fully controlled by the server (shops, quest board, dungeon gate, market...). The client shows a
 * vanilla chest screen; every click on the top part is an action handled here and never moves an item, and the
 * client's predicted pickup is corrected right away. Clicks on the player's own inventory either do what vanilla does
 * (move items around inside the player inventory) or, when the page installs a handler, select that stack (selling,
 * listing, enhancing). Nothing can be put into or taken out of the display.
 */
public final class ServerMenu extends ChestMenu {
    /** What a click does. {@code button}: 0 left, 1 right; {@code shift} for shift-clicks. */
    @FunctionalInterface
    public interface Action {
        void run(ServerPlayer player, int button, boolean shift);
    }

    /** A click on a stack of the player's inventory (slot index in {@link Inventory#items} order 0..35). */
    @FunctionalInterface
    public interface InventoryAction {
        void run(ServerPlayer player, int inventorySlot, ItemStack stack, boolean shift);
    }

    private final ServerPlayer viewer;
    private final SimpleContainer display;
    private final int displaySlots;
    private final Map<Integer, Action> actions = new HashMap<>();
    private final Consumer<ServerMenu> page;
    private InventoryAction inventoryAction;

    private ServerMenu(int containerId, Inventory inventory, ServerPlayer viewer, int rows, Consumer<ServerMenu> page) {
        this(containerId, inventory, viewer, rows, new SimpleContainer(rows * 9), page);
    }

    private ServerMenu(int containerId, Inventory inventory, ServerPlayer viewer, int rows, SimpleContainer display,
                       Consumer<ServerMenu> page) {
        super(type(rows), containerId, inventory, display, rows);
        this.viewer = viewer;
        this.display = display;
        this.displaySlots = rows * 9;
        this.page = page;
        page.accept(this);
    }

    /** Opens a menu of {@code rows} (1..6) rows whose content {@code page} fills (and refills on {@link #refresh()}). */
    public static void open(ServerPlayer player, Component title, int rows, Consumer<ServerMenu> page) {
        Objects.requireNonNull(page, "page");
        int clamped = Math.max(1, Math.min(6, rows));
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ServerMenu(id, inventory, player, clamped, page),
                title));
    }

    private static MenuType<?> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    public ServerPlayer viewer() {
        return viewer;
    }

    /** Places an icon; {@code action} may be null for decoration. */
    public void set(int slot, ItemStack icon, Action action) {
        if (slot < 0 || slot >= displaySlots) {
            return;
        }
        display.setItem(slot, icon);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    public void onInventoryClick(InventoryAction action) {
        this.inventoryAction = action;
    }

    /** Clears and rebuilds the page (after an action changed what it shows). */
    public void refresh() {
        display.clearContent();
        actions.clear();
        inventoryAction = null;
        page.accept(this);
        broadcastChanges();
    }

    public int size() {
        return displaySlots;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer != viewer) {
            return;
        }
        if (slotId >= 0 && slotId < displaySlots) {
            Action action = actions.get(slotId);
            if (action != null && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
                action.run(serverPlayer, button, clickType == ClickType.QUICK_MOVE);
            }
            resync();
            return;
        }
        if (slotId >= displaySlots && slotId < slots.size() && inventoryAction != null
                && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
            int inventorySlot = slots.get(slotId).getContainerSlot();
            ItemStack stack = slots.get(slotId).getItem();
            if (!stack.isEmpty()) {
                inventoryAction.run(serverPlayer, inventorySlot, stack, clickType == ClickType.QUICK_MOVE);
            }
            resync();
            return;
        }
        if (clickType == ClickType.QUICK_MOVE || clickType == ClickType.QUICK_CRAFT || clickType == ClickType.PICKUP_ALL
                || clickType == ClickType.CLONE) {
            resync(); // could reach into the display
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void resync() {
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player == viewer && viewer.isAlive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        display.clearContent(); // icons are not real items: never drop them
    }
}
