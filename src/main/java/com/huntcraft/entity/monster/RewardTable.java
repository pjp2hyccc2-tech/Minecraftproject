package com.huntcraft.entity.monster;

import com.huntcraft.core.Rank;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Weighted reward list with separate low / high rank entries. */
public final class RewardTable {
    public record Entry(Supplier<? extends Item> item, int weight, int min, int max) {
    }

    private final List<Entry> low = new ArrayList<>();
    private final List<Entry> high = new ArrayList<>();

    public RewardTable low(Supplier<? extends Item> item, int weight) {
        return low(item, weight, 1, 1);
    }

    public RewardTable low(Supplier<? extends Item> item, int weight, int min, int max) {
        low.add(new Entry(item, weight, min, max));
        return this;
    }

    public RewardTable high(Supplier<? extends Item> item, int weight) {
        return high(item, weight, 1, 1);
    }

    public RewardTable high(Supplier<? extends Item> item, int weight, int min, int max) {
        high.add(new Entry(item, weight, min, max));
        return this;
    }

    /** Same entry in both ranks. */
    public RewardTable both(Supplier<? extends Item> item, int weight, int min, int max) {
        low(item, weight, min, max);
        return high(item, weight, min, max);
    }

    public List<Entry> entries(Rank rank) {
        List<Entry> l = rank == Rank.HIGH && !high.isEmpty() ? high : low;
        return Collections.unmodifiableList(l);
    }

    public boolean isEmpty() {
        return low.isEmpty() && high.isEmpty();
    }

    public ItemStack roll(RandomSource random, Rank rank) {
        List<Entry> list = entries(rank);
        int total = 0;
        for (Entry e : list) total += e.weight;
        if (total <= 0) return ItemStack.EMPTY;
        int r = random.nextInt(total);
        for (Entry e : list) {
            r -= e.weight;
            if (r < 0) {
                int count = e.min + (e.max > e.min ? random.nextInt(e.max - e.min + 1) : 0);
                return new ItemStack(e.item.get(), Math.max(1, count));
            }
        }
        return ItemStack.EMPTY;
    }

    /** Chance (0..1) of an item in this table for the notes screen. */
    public float chanceOf(Item item, Rank rank) {
        List<Entry> list = entries(rank);
        int total = 0, w = 0;
        for (Entry e : list) {
            total += e.weight;
            if (e.item.get() == item) w += e.weight;
        }
        return total == 0 ? 0 : (float) w / total;
    }
}
