package com.nihonisshu.tabi.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nihonisshu.tabi.data.TabiData;
import com.nihonisshu.tabi.network.TabiNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Shops, cafes, stalls, vending machines and the farmers' collection point (sell mode). */
public final class ShopManager {
    private ShopManager() {}

    private static JsonObject shop(String id) {
        JsonObject shops = TabiData.shops().getAsJsonObject("shops");
        return shops == null ? null : shops.getAsJsonObject(id);
    }

    public static void open(ServerPlayer p, String id) {
        JsonObject s = shop(id);
        if (s == null) {
            p.displayClientMessage(Component.literal("（準備中）"), true);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", TabiData.str(s, "name", id));
        tag.putString("greet", TabiData.str(s, "greet", ""));
        tag.putString("mode", TabiData.str(s, "mode", "buy"));
        ListTag items = new ListTag();
        JsonArray arr = s.getAsJsonArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JsonObject it = arr.get(i).getAsJsonObject();
                CompoundTag e = new CompoundTag();
                e.putString("item", TabiData.str(it, "item", "minecraft:air"));
                e.putInt("price", TabiData.integer(it, "price", 100));
                e.putString("note", TabiData.str(it, "note", ""));
                items.add(e);
            }
        }
        tag.put("items", items);
        tag.putInt("yen", PlayerData.yen(p));
        TabiNet.send(p, "shop", tag);
        String journal = TabiData.str(s, "journal", "");
        if (!journal.isEmpty()) PlayerData.unlock(p, journal);
    }

    public static void buy(ServerPlayer p, String id, int index, int count) {
        JsonObject s = shop(id);
        if (s == null || count <= 0 || count > 64) return;
        JsonArray arr = s.getAsJsonArray("items");
        if (arr == null || index < 0 || index >= arr.size()) return;
        JsonObject it = arr.get(index).getAsJsonObject();
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TabiData.str(it, "item", "minecraft:air")));
        if (item == null) return;
        int price = TabiData.integer(it, "price", 100) * count;
        if (!PlayerData.spend(p, price)) return;
        ItemStack stack = new ItemStack(item, count);
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        p.displayClientMessage(Component.literal(stack.getHoverName().getString() + " を買いました（" + String.format("%,d", price) + "円）"), true);
        String j = TabiData.str(it, "journal", "");
        if (!j.isEmpty()) PlayerData.unlock(p, j);
        open(p, id);
    }

    public static void sell(ServerPlayer p, String id, int index) {
        JsonObject s = shop(id);
        if (s == null || !TabiData.str(s, "mode", "buy").equals("sell")) return;
        JsonArray arr = s.getAsJsonArray("items");
        if (arr == null || index < 0 || index >= arr.size()) return;
        JsonObject it = arr.get(index).getAsJsonObject();
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TabiData.str(it, "item", "minecraft:air")));
        if (item == null) return;
        int price = TabiData.integer(it, "price", 10);
        int total = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(item)) {
                total += st.getCount();
                p.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (total == 0) {
            p.displayClientMessage(Component.literal("持っていません"), true);
            return;
        }
        PlayerData.earn(p, price * total, new ItemStack(item).getHoverName().getString() + " ×" + total);
        PlayerData.setFlag(p, "sold_once", true);
        open(p, id);
    }
}
