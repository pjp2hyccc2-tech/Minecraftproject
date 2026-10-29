package com.huntcraft.village.menu;

import com.huntcraft.village.VillageRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/** Item box menu: 54 visible box slots (2 pages) + player inventory. Buttons: 0 prev page, 1 next page, 2 deposit all, 3 sort. */
public class ItemBoxMenu extends AbstractContainerMenu {
    public static final int ROWS = 6;
    public static final int BOX_SLOTS = ItemBoxContainer.PAGE_SIZE;
    private final Container box;
    private final DataSlot page = DataSlot.standalone();

    /** Client constructor. */
    public ItemBoxMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(BOX_SLOTS));
    }

    public ItemBoxMenu(int id, Inventory inv, Container box) {
        super(VillageRegistry.ITEM_BOX_MENU.get(), id);
        this.box = box;
        box.startOpen(inv.player);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < 9; c++) this.addSlot(new Slot(box, c + r * 9, 8 + c * 18, 18 + r * 18));
        }
        int y0 = 18 + ROWS * 18 + 13;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y0 + r * 18));
        }
        for (int c = 0; c < 9; c++) this.addSlot(new Slot(inv, c, 8 + c * 18, y0 + 58));
        this.addDataSlot(page);
    }

    public static void open(ServerPlayer sp) {
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), VillageRegistry.BOX_OPEN.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new ItemBoxMenu(id, inv, new ItemBoxContainer(p)),
                Component.translatable("container.huntcraft.item_box")));
    }

    public int getPage() {
        return page.get();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(box instanceof ItemBoxContainer c)) return false;
        switch (id) {
            case 0 -> c.setPage(c.getPage() - 1);
            case 1 -> c.setPage(c.getPage() + 1);
            case 2 -> ItemBoxContainer.depositAll(player);
            case 3 -> ItemBoxContainer.sort(player);
            default -> {
                return false;
            }
        }
        page.set(c.getPage());
        this.broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack st = slot.getItem();
            result = st.copy();
            if (index < BOX_SLOTS) {
                if (!this.moveItemStackTo(st, BOX_SLOTS, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (!this.moveItemStackTo(st, 0, BOX_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (st.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return box.stillValid(player) || box instanceof SimpleContainer;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        box.stopOpen(player);
    }
}
