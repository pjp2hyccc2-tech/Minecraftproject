package com.huntcraft.village.menu;

import com.huntcraft.data.HunterData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-side view of one page (54 slots) of the hunter's 108-slot item box. */
public class ItemBoxContainer implements Container {
    public static final int PAGE_SIZE = 54;
    private final Player player;
    private int page;

    public ItemBoxContainer(Player player) {
        this.player = player;
    }

    private NonNullList<ItemStack> box() {
        return HunterData.get(player).itemBox;
    }

    public int pages() {
        return Math.max(1, box().size() / PAGE_SIZE);
    }

    public int getPage() {
        return page;
    }

    public void setPage(int p) {
        this.page = Math.floorMod(p, pages());
    }

    private int index(int slot) {
        return page * PAGE_SIZE + slot;
    }

    @Override
    public int getContainerSize() {
        return PAGE_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < PAGE_SIZE; i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        int i = index(slot);
        NonNullList<ItemStack> b = box();
        return i >= 0 && i < b.size() ? b.get(i) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        int i = index(slot);
        if (i < 0 || i >= box().size()) return ItemStack.EMPTY;
        return ContainerHelper.removeItem(box(), i, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        int i = index(slot);
        if (i < 0 || i >= box().size()) return ItemStack.EMPTY;
        return ContainerHelper.takeItem(box(), i);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        int i = index(slot);
        if (i < 0 || i >= box().size()) return;
        box().set(i, stack);
        if (stack.getCount() > this.getMaxStackSize()) stack.setCount(this.getMaxStackSize());
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player p) {
        return p == player && p.isAlive();
    }

    @Override
    public void clearContent() {
        for (int s = 0; s < PAGE_SIZE; s++) setItem(s, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ helpers
    /** Inserts a stack into a box list (merging first). Returns what did not fit. */
    public static ItemStack insert(NonNullList<ItemStack> box, ItemStack stack) {
        ItemStack st = stack.copy();
        for (int i = 0; i < box.size() && !st.isEmpty(); i++) {
            ItemStack b = box.get(i);
            if (!b.isEmpty() && ItemStack.isSameItemSameTags(b, st)) {
                int room = Math.min(b.getMaxStackSize(), 64) - b.getCount();
                if (room > 0) {
                    int n = Math.min(room, st.getCount());
                    b.grow(n);
                    st.shrink(n);
                }
            }
        }
        for (int i = 0; i < box.size() && !st.isEmpty(); i++) {
            if (box.get(i).isEmpty()) {
                box.set(i, st.copy());
                st = ItemStack.EMPTY;
            }
        }
        return st;
    }

    /** Moves every item of the main inventory (not the hotbar / armor) into the box. */
    public static int depositAll(Player p) {
        NonNullList<ItemStack> box = HunterData.get(p).itemBox;
        int moved = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.isEmpty()) continue;
            ItemStack rest = insert(box, st);
            moved += st.getCount() - rest.getCount();
            p.getInventory().setItem(i, rest);
        }
        return moved;
    }

    /** Compacts and sorts the whole box by item id. */
    public static void sort(Player p) {
        NonNullList<ItemStack> box = HunterData.get(p).itemBox;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack st : box) if (!st.isEmpty()) items.add(st.copy());
        for (int i = 0; i < box.size(); i++) box.set(i, ItemStack.EMPTY);
        items.sort(Comparator.comparing((ItemStack s) -> String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem()))));
        for (ItemStack st : items) insert(box, st);
    }
}
