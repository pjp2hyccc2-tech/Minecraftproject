package com.huntcraft.village.companion;

import com.huntcraft.registry.HCItems;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Expedition destinations for idle mofu companions and their gathering loot. An expedition ends after its real-time
 * duration (it keeps running while the hunter is offline) or after {@link MofuData#EXPEDITION_HUNTS} large monster hunts
 * by the hunter, whichever comes first.
 */
public final class Expeditions {
    public record Loot(Supplier<? extends Item> item, int weight, int min, int max) {
    }

    public record Destination(String id, int minLevel, int requiredHR, int color, List<Loot> loot) {
        public Component displayName() {
            return Component.translatable("mofu.huntcraft.dest." + id);
        }

        public Component description() {
            return Component.translatable("mofu.huntcraft.dest." + id + ".desc");
        }
    }

    /** Expedition lengths: short / long (long trips bring more and rarer loot). */
    public enum Length {
        SHORT("short", 15L * 60L * 1000L, 1.0F, 40),
        LONG("long", 45L * 60L * 1000L, 2.2F, 110);

        public final String id;
        public final long millis;
        public final float lootMult;
        public final int xp;

        Length(String id, long millis, float lootMult, int xp) {
            this.id = id;
            this.millis = millis;
            this.lootMult = lootMult;
            this.xp = xp;
        }

        public Component displayName() {
            return Component.translatable("mofu.huntcraft.length." + id);
        }

        public static Length byIndex(int i) {
            return i == 1 ? LONG : SHORT;
        }
    }

    private static Loot l(Supplier<? extends Item> item, int weight, int min, int max) {
        return new Loot(item, weight, min, max);
    }

    public static final List<Destination> ALL = List.of(
            new Destination("verdant_hills", 1, 1, 0x7FC36A, List.of(
                    l(HCItems.HERB, 14, 1, 3), l(HCItems.BLUE_MUSHROOM, 10, 1, 2), l(HCItems.HONEY, 7, 1, 2),
                    l(HCItems.ANTIDOTE_HERB, 7, 1, 2), l(HCItems.MIGHT_SEED, 4, 1, 1), l(HCItems.ADAMANT_SEED, 4, 1, 1),
                    l(HCItems.STICKY_GRASS, 6, 1, 2), l(HCItems.SPIDER_WEB, 6, 1, 2), l(HCItems.FLASHBUG, 6, 1, 2),
                    l(HCItems.THUNDERBUG, 5, 1, 2), l(HCItems.BITTERBUG, 5, 1, 2), l(HCItems.SLEEP_HERB, 4, 1, 2),
                    l(HCItems.NUMB_MUSHROOM, 4, 1, 2), l(HCItems.EARTH_CRYSTAL, 6, 1, 2), l(HCItems.MONSTER_BONE_S, 6, 1, 2),
                    l(HCItems.JAGRAPTOR_HIDE, 4, 1, 1), l(HCItems.BOARFANG_HIDE, 4, 1, 1), l(HCItems.GRASICA_ANTLER, 3, 1, 1))),
            new Destination("frostpeak_range", 6, 2, 0x9BD7F2, List.of(
                    l(HCItems.ICE_CRYSTAL, 12, 1, 3), l(HCItems.HOT_PEPPER, 6, 1, 2), l(HCItems.AZURITE_ORE, 8, 1, 2),
                    l(HCItems.EARTH_CRYSTAL, 8, 1, 2), l(HCItems.BLIZRAPTOR_HIDE, 5, 1, 1), l(HCItems.YUKIMAMMO_FUR, 4, 1, 1),
                    l(HCItems.MONSTER_BONE_M, 6, 1, 1), l(HCItems.NOVACRYSTAL, 1, 1, 1), l(HCItems.HERB, 6, 1, 2),
                    l(HCItems.BLUE_MUSHROOM, 4, 1, 2), l(HCItems.FROST_SAC, 1, 1, 1))),
            new Destination("ember_caldera", 12, 4, 0xF08A4B, List.of(
                    l(HCItems.FIRE_HERB, 10, 1, 3), l(HCItems.BLAST_NUT, 6, 1, 2), l(HCItems.CRIMSONITE_ORE, 5, 1, 1),
                    l(HCItems.AZURITE_ORE, 6, 1, 2), l(HCItems.HEATGECKO_SCALE, 5, 1, 1), l(HCItems.GANSEKI_SHELL, 5, 1, 1),
                    l(HCItems.MONSTER_BONE_M, 5, 1, 1), l(HCItems.MONSTER_BONE_L, 1, 1, 1), l(HCItems.LIGHTCRYSTAL, 1, 1, 1),
                    l(HCItems.EARTH_CRYSTAL, 6, 1, 2), l(HCItems.FLAME_SAC, 1, 1, 1))));

    private Expeditions() {
    }

    public static Destination get(int i) {
        return ALL.get(Math.max(0, Math.min(ALL.size() - 1, i)));
    }

    public static List<ItemStack> roll(Destination d, int level, Length len, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        int total = 0;
        for (Loot l : d.loot()) total += l.weight();
        int n = Math.round((3 + level / 10 + r.nextInt(3)) * len.lootMult);
        for (int i = 0; i < n && total > 0; i++) {
            int pick = r.nextInt(total);
            for (Loot l : d.loot()) {
                pick -= l.weight();
                if (pick < 0) {
                    int c = l.min() + (l.max() > l.min() ? r.nextInt(l.max() - l.min() + 1) : 0);
                    out.add(new ItemStack(l.item().get(), c));
                    break;
                }
            }
        }
        float coin = (0.08F + level * 0.003F) * (len == Length.LONG ? 2.5F : 1.0F);
        if (r.nextFloat() < coin) out.add(new ItemStack(HCItems.MOFU_COIN.get()));
        return merge(out);
    }

    /** Merges stacks of the same item. */
    public static List<ItemStack> merge(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack st : in) {
            boolean merged = false;
            for (ItemStack o : out) {
                if (ItemStack.isSameItemSameTags(o, st) && o.getCount() + st.getCount() <= o.getMaxStackSize()) {
                    o.grow(st.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) out.add(st.copy());
        }
        return out;
    }
}
