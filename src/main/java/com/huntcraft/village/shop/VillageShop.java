package com.huntcraft.village.shop;

import com.huntcraft.core.ShopRegistry;
import com.huntcraft.data.HunterData;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Village general store on top of the core {@link ShopRegistry}: a few extra village goods, three daily deals (25% off,
 * chosen per in-game day among the goods the hunter's rank unlocks), bulk buying and selling straight from the item box.
 * Stock grows with hunter rank because every ShopRegistry entry carries a required HR.
 */
public final class VillageShop {
    public static final float DEAL_DISCOUNT = 0.75F;
    public static final int DEALS = 3;

    private VillageShop() {
    }

    /** Extra goods sold in the village (called once from VillageModule.init). */
    public static void registerStock() {
        ShopRegistry.add("village_bamboo", () -> Items.BAMBOO, 12, 1, "material");
        ShopRegistry.add("village_first_aid_med", HCItems.FIRST_AID_MED, 45, 1, "recovery");
        ShopRegistry.add("village_well_done_steak", HCItems.WELL_DONE_STEAK, 90, 1, "food");
        ShopRegistry.add("village_monster_bone_s", HCItems.MONSTER_BONE_S, 60, 2, "material");
        ShopRegistry.add("village_sticky_grass", HCItems.STICKY_GRASS, 18, 2, "material");
        ShopRegistry.add("village_max_potion", HCItems.MAX_POTION, 2400, 8, "recovery");
    }

    public static long day(Level level) {
        return level.getGameTime() / 24000L;
    }

    /** Daily deal entry ids for this hunter rank and day (same result on client and server). */
    public static List<String> deals(long day, int hunterRank) {
        List<ShopRegistry.Entry> pool = new ArrayList<>();
        for (ShopRegistry.Entry e : ShopRegistry.stock()) if (e.requiredHR() <= hunterRank) pool.add(e);
        List<String> out = new ArrayList<>();
        long h = day * 6364136223846793005L + 1442695040888963407L;
        for (int i = 0; i < DEALS * 4 && out.size() < DEALS && !pool.isEmpty(); i++) {
            h ^= h >>> 33;
            h *= 0xff51afd7ed558ccdL;
            h ^= h >>> 33;
            ShopRegistry.Entry e = pool.get((int) Math.floorMod(h, (long) pool.size()));
            if (!out.contains(e.id())) out.add(e.id());
        }
        return out;
    }

    public static int unitPrice(ShopRegistry.Entry e, boolean deal) {
        return deal ? Math.max(1, Math.round(e.price() * DEAL_DISCOUNT)) : e.price();
    }

    /** Server action "village/buy" {id, count}. */
    public static boolean buy(ServerPlayer p, String id, int count) {
        ShopRegistry.Entry e = ShopRegistry.get(id);
        if (e == null || count <= 0) return false;
        count = Math.min(count, 99);
        HunterData d = HunterData.get(p);
        if (d.hunterRank < e.requiredHR()) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_hr", e.requiredHR()), true);
            return false;
        }
        boolean deal = deals(day(p.level()), d.hunterRank).contains(id);
        int total = unitPrice(e, deal) * count;
        if (!d.spendZenny(total)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny"), true);
            return false;
        }
        int left = count;
        int max = Math.max(1, new ItemStack(e.item().get()).getMaxStackSize());
        while (left > 0) {
            int n = Math.min(left, max);
            ItemStack st = new ItemStack(e.item().get(), n);
            if (!p.getInventory().add(st)) p.drop(st, false);
            left -= n;
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.ZENNY.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        p.displayClientMessage(Component.translatable("message.huntcraft.bought", new ItemStack(e.item().get()).getHoverName(), count, total), true);
        return true;
    }

    /** Server action "village/sell_box" {index, count}: sells from the item box. */
    public static boolean sellFromBox(ServerPlayer p, int index, int count) {
        HunterData d = HunterData.get(p);
        if (index < 0 || index >= d.itemBox.size()) return false;
        ItemStack st = d.itemBox.get(index);
        int price = ShopRegistry.sellPrice(st);
        if (st.isEmpty() || price <= 0) return false;
        int n = Math.min(Math.max(1, count), st.getCount());
        Component name = st.getHoverName();
        st.shrink(n);
        if (st.isEmpty()) d.itemBox.set(index, ItemStack.EMPTY);
        d.addZenny(price * n);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.ZENNY.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
        p.displayClientMessage(Component.translatable("message.huntcraft.sold", name, n, price * n), true);
        return true;
    }
}
