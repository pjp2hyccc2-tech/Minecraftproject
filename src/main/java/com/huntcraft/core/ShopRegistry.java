package com.huntcraft.core;

import com.huntcraft.data.HunterData;
import com.huntcraft.item.MaterialItem;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Merchant stock and sell prices. Server actions: "shop/buy" {id, count} and "shop/sell" {slot, count}.
 */
public final class ShopRegistry {
    public record Entry(String id, Supplier<? extends Item> item, int price, int requiredHR, String category) {
    }

    private static final Map<String, Entry> STOCK = new LinkedHashMap<>();
    private static final Map<Supplier<? extends Item>, Integer> SELL_OVERRIDES = new HashMap<>();

    private ShopRegistry() {
    }

    public static void add(String id, Supplier<? extends Item> item, int price, int requiredHR, String category) {
        STOCK.put(id, new Entry(id, item, price, requiredHR, category));
    }

    public static void setSellPrice(Supplier<? extends Item> item, int price) {
        SELL_OVERRIDES.put(item, price);
    }

    public static List<Entry> stock() {
        return Collections.unmodifiableList(new ArrayList<>(STOCK.values()));
    }

    @Nullable
    public static Entry get(String id) {
        return STOCK.get(id);
    }

    public static int sellPrice(ItemStack stack) {
        Item item = stack.getItem();
        for (Map.Entry<Supplier<? extends Item>, Integer> e : SELL_OVERRIDES.entrySet()) {
            if (e.getKey().get() == item) return e.getValue();
        }
        if (item instanceof MaterialItem m) {
            int r = m.getHcRarity();
            return 10 + r * r * 15;
        }
        for (Entry e : STOCK.values()) if (e.item().get() == item) return Math.max(1, e.price() / 4);
        return 0;
    }

    public static boolean buy(ServerPlayer p, String id, int count) {
        Entry e = STOCK.get(id);
        if (e == null || count <= 0) return false;
        HunterData d = HunterData.get(p);
        if (d.hunterRank < e.requiredHR()) return false;
        int total = e.price() * count;
        if (!d.spendZenny(total)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny"), true);
            return false;
        }
        ItemStack st = new ItemStack(e.item().get(), count);
        if (!p.getInventory().add(st)) p.drop(st, false);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.ZENNY.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        return true;
    }

    public static boolean sell(ServerPlayer p, int slot, int count) {
        if (slot < 0 || slot >= p.getInventory().getContainerSize()) return false;
        ItemStack st = p.getInventory().getItem(slot);
        int price = sellPrice(st);
        if (st.isEmpty() || price <= 0) return false;
        int n = Math.min(count, st.getCount());
        st.shrink(n);
        HunterData.get(p).addZenny(price * n);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.ZENNY.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
        return true;
    }

    /** Default consumables stock (core). */
    public static void registerDefaults() {
        add("potion", HCItems.POTION, 66, 1, "recovery");
        add("mega_potion", HCItems.MEGA_POTION, 180, 3, "recovery");
        add("antidote", HCItems.ANTIDOTE, 40, 1, "recovery");
        add("energy_drink", HCItems.ENERGY_DRINK, 80, 2, "recovery");
        add("ration", HCItems.RATION, 30, 1, "food");
        add("raw_meat", HCItems.RAW_MEAT, 20, 1, "food");
        add("bbq_spit", HCItems.BBQ_SPIT, 120, 1, "food");
        add("hot_drink", HCItems.HOT_DRINK, 60, 1, "drink");
        add("cool_drink", HCItems.COOL_DRINK, 60, 1, "drink");
        add("demondrug", HCItems.DEMONDRUG, 300, 4, "drink");
        add("armorskin", HCItems.ARMORSKIN, 300, 4, "drink");
        add("paintball", HCItems.PAINTBALL, 25, 1, "tool");
        add("flash_bomb", HCItems.FLASH_BOMB, 150, 2, "tool");
        add("dung_bomb", HCItems.DUNG_BOMB, 60, 2, "tool");
        add("tranq_bomb", HCItems.TRANQ_BOMB, 150, 2, "tool");
        add("pitfall_trap", HCItems.PITFALL_TRAP, 500, 2, "tool");
        add("shock_trap", HCItems.SHOCK_TRAP, 450, 2, "tool");
        add("barrel_bomb", HCItems.BARREL_BOMB, 200, 2, "tool");
        add("barrel_bomb_plus", HCItems.BARREL_BOMB_PLUS, 600, 5, "tool");
        add("farcaster", HCItems.FARCASTER, 200, 1, "tool");
        add("armor_sphere", HCItems.ARMOR_SPHERE, 300, 2, "smithing");
        add("armor_sphere_plus", HCItems.ARMOR_SPHERE_PLUS, 1200, 8, "smithing");
        add("earth_crystal", HCItems.EARTH_CRYSTAL, 60, 1, "smithing");
        add("herb", HCItems.HERB, 10, 1, "material");
        add("blue_mushroom", HCItems.BLUE_MUSHROOM, 15, 1, "material");
    }
}
