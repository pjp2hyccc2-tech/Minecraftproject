package com.huntcraft.core;

import com.huntcraft.data.HunterData;
import com.huntcraft.registry.HCSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Blacksmith recipes. Weapon / armor modules register recipes in their init(); the village module shows them.
 * Execution is server side through the actions "smith/forge" {id} and "smith/levelup" {slot}.
 * Materials are taken from the player's inventory first, then from the item box.
 */
public final class SmithingRegistry {
    public enum Category { WEAPON, ARMOR, COMPANION }

    public record Cost(Supplier<? extends Item> item, int count) {
        public static Cost of(Supplier<? extends Item> item, int count) {
            return new Cost(item, count);
        }
    }

    public static final class Recipe {
        public final String id;
        public final Category category;
        /** Grouping for the UI: weapon class name ("GREAT_SWORD") or armor set id ("ignivald"). */
        public final String group;
        public final Supplier<? extends Item> result;
        /** If set, this is an upgrade: consumes one of these (any level) and gives the result. */
        @Nullable
        public final Supplier<? extends Item> upgradeFrom;
        public final List<Cost> costs;
        public final int zenny;
        public final int requiredHR;

        public Recipe(String id, Category category, String group, Supplier<? extends Item> result, @Nullable Supplier<? extends Item> upgradeFrom,
                      List<Cost> costs, int zenny, int requiredHR) {
            this.id = id;
            this.category = category;
            this.group = group;
            this.result = result;
            this.upgradeFrom = upgradeFrom;
            this.costs = List.copyOf(costs);
            this.zenny = zenny;
            this.requiredHR = requiredHR;
        }
    }

    private static final Map<String, Recipe> RECIPES = new LinkedHashMap<>();

    private SmithingRegistry() {
    }

    public static Recipe register(Recipe r) {
        RECIPES.put(r.id, r);
        return r;
    }

    @Nullable
    public static Recipe get(String id) {
        return RECIPES.get(id);
    }

    public static List<Recipe> all() {
        return Collections.unmodifiableList(new ArrayList<>(RECIPES.values()));
    }

    public static List<Recipe> byCategory(Category c) {
        List<Recipe> l = new ArrayList<>();
        for (Recipe r : RECIPES.values()) if (r.category == c) l.add(r);
        return l;
    }

    // ------------------------------------------------------------------ material helpers
    public static int count(Player p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        for (ItemStack s : HunterData.get(p).itemBox) if (s.is(item)) n += s.getCount();
        return n;
    }

    public static boolean hasAll(Player p, List<Cost> costs) {
        for (Cost c : costs) if (count(p, c.item().get()) < c.count()) return false;
        return true;
    }

    public static void consume(Player p, Item item, int amount) {
        for (int i = 0; i < p.getInventory().getContainerSize() && amount > 0; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) {
                int take = Math.min(amount, s.getCount());
                s.shrink(take);
                amount -= take;
            }
        }
        HunterData d = HunterData.get(p);
        for (int i = 0; i < d.itemBox.size() && amount > 0; i++) {
            ItemStack s = d.itemBox.get(i);
            if (s.is(item)) {
                int take = Math.min(amount, s.getCount());
                s.shrink(take);
                amount -= take;
            }
        }
    }

    // ------------------------------------------------------------------ server execution
    public static boolean tryForge(ServerPlayer p, String recipeId) {
        Recipe r = RECIPES.get(recipeId);
        if (r == null) return false;
        HunterData d = HunterData.get(p);
        if (d.hunterRank < r.requiredHR) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_hr", r.requiredHR), true);
            return false;
        }
        if (d.zenny < r.zenny) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny"), true);
            return false;
        }
        if (!hasAll(p, r.costs)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_materials"), true);
            return false;
        }
        int fromSlot = -1;
        if (r.upgradeFrom != null) {
            Item from = r.upgradeFrom.get();
            if (p.getMainHandItem().is(from)) fromSlot = p.getInventory().selected;
            else {
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                    if (p.getInventory().getItem(i).is(from)) {
                        fromSlot = i;
                        break;
                    }
                }
            }
            if (fromSlot < 0) {
                p.displayClientMessage(Component.translatable("message.huntcraft.need_base_item"), true);
                return false;
            }
        }
        d.spendZenny(r.zenny);
        for (Cost c : r.costs) consume(p, c.item().get(), c.count());
        ItemStack out = new ItemStack(r.result.get());
        if (fromSlot >= 0) p.getInventory().getItem(fromSlot).shrink(1);
        if (!p.getInventory().add(out)) p.drop(out, false);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
        p.displayClientMessage(Component.translatable("message.huntcraft.forged", out.getHoverName()), false);
        d.markDirty();
        return true;
    }

    public static boolean tryLevelUp(ServerPlayer p, int slot) {
        if (slot < 0 || slot >= p.getInventory().getContainerSize()) return false;
        ItemStack stack = p.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof IUpgradeable up)) return false;
        int lv = up.getLevel(stack);
        if (lv >= up.getMaxLevel(stack)) return false;
        int next = lv + 1;
        HunterData d = HunterData.get(p);
        if (d.hunterRank < up.getRequiredHR(stack, next)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_hr", up.getRequiredHR(stack, next)), true);
            return false;
        }
        int zenny = up.getUpgradeZenny(stack, next);
        List<Cost> costs = up.getUpgradeCost(stack, next);
        if (d.zenny < zenny) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_zenny"), true);
            return false;
        }
        if (!hasAll(p, costs)) {
            p.displayClientMessage(Component.translatable("message.huntcraft.need_materials"), true);
            return false;
        }
        d.spendZenny(zenny);
        for (Cost c : costs) consume(p, c.item().get(), c.count());
        up.setLevel(stack, next);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HCSounds.LEVEL_UP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        p.displayClientMessage(Component.translatable("message.huntcraft.upgraded", stack.getHoverName(), next), false);
        d.markDirty();
        return true;
    }
}
