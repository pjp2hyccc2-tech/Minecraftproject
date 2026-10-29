package com.huntcraft.quest;

import com.huntcraft.core.HCEvents;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.Rank;
import com.huntcraft.core.SkillType;
import com.huntcraft.quest.block.GatherKind;
import com.huntcraft.quest.block.GatheringBlock;
import com.huntcraft.registry.HCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Gathering point loot per kind / map / rank + the gathering interaction itself. */
public final class GatherLoot {
    public record Entry(Supplier<? extends Item> item, int weight, int min, int max) {
    }

    private static final Map<UUID, Long> LAST_GATHER = new HashMap<>();
    private static Map<String, List<Entry>> tables;

    private GatherLoot() {
    }

    private static String key(GatherKind k, QuestMap m, Rank r) {
        return k.id() + "/" + m.id + "/" + r.name();
    }

    private static void put(Map<String, List<Entry>> t, GatherKind k, QuestMap m, Rank r, Object... e) {
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < e.length; i += 4) {
            @SuppressWarnings("unchecked")
            Supplier<? extends Item> s = (Supplier<? extends Item>) e[i];
            list.add(new Entry(s, (Integer) e[i + 1], (Integer) e[i + 2], (Integer) e[i + 3]));
        }
        t.put(key(k, m, r), list);
    }

    private static synchronized Map<String, List<Entry>> tables() {
        if (tables != null) return tables;
        Map<String, List<Entry>> t = new HashMap<>();
        QuestMap V = QuestMap.VERDANT_HILLS, F = QuestMap.FROSTPEAK_RANGE, E = QuestMap.EMBER_CALDERA;
        Rank L = Rank.LOW, H = Rank.HIGH;
        GatherKind HERB = GatherKind.HERB, MUSH = GatherKind.MUSHROOM, BUG = GatherKind.BUG, BONE = GatherKind.BONE, MINE = GatherKind.MINING;
        // ---- herbs
        put(t, HERB, V, L, HCItems.HERB, 40, 1, 2, HCItems.ANTIDOTE_HERB, 14, 1, 1, HCItems.STICKY_GRASS, 12, 1, 2, HCItems.SLEEP_HERB, 9, 1, 1,
                HCItems.MIGHT_SEED, 5, 1, 1, HCItems.ADAMANT_SEED, 5, 1, 1, HCItems.FIRE_HERB, 6, 1, 1, HCItems.HONEY, 7, 1, 1);
        put(t, HERB, V, H, HCItems.HERB, 32, 1, 3, HCItems.ANTIDOTE_HERB, 12, 1, 2, HCItems.STICKY_GRASS, 10, 1, 2, HCItems.SLEEP_HERB, 9, 1, 2,
                HCItems.MIGHT_SEED, 9, 1, 2, HCItems.ADAMANT_SEED, 9, 1, 2, HCItems.FIRE_HERB, 6, 1, 2, HCItems.HONEY, 10, 1, 2);
        put(t, HERB, F, L, HCItems.HERB, 30, 1, 2, HCItems.HOT_PEPPER, 26, 1, 2, HCItems.ANTIDOTE_HERB, 10, 1, 1, HCItems.SLEEP_HERB, 10, 1, 1,
                HCItems.STICKY_GRASS, 8, 1, 1, HCItems.MIGHT_SEED, 5, 1, 1);
        put(t, HERB, F, H, HCItems.HERB, 26, 1, 3, HCItems.HOT_PEPPER, 24, 1, 3, HCItems.ANTIDOTE_HERB, 10, 1, 2, HCItems.SLEEP_HERB, 10, 1, 2,
                HCItems.MIGHT_SEED, 9, 1, 2, HCItems.ADAMANT_SEED, 8, 1, 2);
        put(t, HERB, E, L, HCItems.FIRE_HERB, 30, 1, 2, HCItems.HOT_PEPPER, 14, 1, 1, HCItems.HERB, 20, 1, 2, HCItems.SLEEP_HERB, 8, 1, 1,
                HCItems.BLAST_NUT, 12, 1, 1, HCItems.MIGHT_SEED, 5, 1, 1, HCItems.ADAMANT_SEED, 5, 1, 1);
        put(t, HERB, E, H, HCItems.FIRE_HERB, 26, 1, 3, HCItems.HOT_PEPPER, 12, 1, 2, HCItems.HERB, 18, 1, 3, HCItems.BLAST_NUT, 16, 1, 2,
                HCItems.MIGHT_SEED, 9, 1, 2, HCItems.ADAMANT_SEED, 9, 1, 2);
        // ---- mushrooms
        put(t, MUSH, V, L, HCItems.BLUE_MUSHROOM, 45, 1, 2, HCItems.NUMB_MUSHROOM, 25, 1, 1, HCItems.HERB, 10, 1, 1, HCItems.SLEEP_HERB, 6, 1, 1);
        put(t, MUSH, V, H, HCItems.BLUE_MUSHROOM, 40, 1, 3, HCItems.NUMB_MUSHROOM, 28, 1, 2, HCItems.HERB, 10, 1, 2, HCItems.SLEEP_HERB, 8, 1, 2);
        put(t, MUSH, F, L, HCItems.BLUE_MUSHROOM, 38, 1, 2, HCItems.NUMB_MUSHROOM, 22, 1, 1, HCItems.HOT_PEPPER, 10, 1, 1);
        put(t, MUSH, F, H, HCItems.BLUE_MUSHROOM, 36, 1, 3, HCItems.NUMB_MUSHROOM, 24, 1, 2, HCItems.HOT_PEPPER, 12, 1, 2);
        put(t, MUSH, E, L, HCItems.BLUE_MUSHROOM, 20, 1, 1, HCItems.NUMB_MUSHROOM, 20, 1, 1, HCItems.FIRE_HERB, 20, 1, 1);
        put(t, MUSH, E, H, HCItems.BLUE_MUSHROOM, 20, 1, 2, HCItems.NUMB_MUSHROOM, 20, 1, 2, HCItems.FIRE_HERB, 20, 1, 2);
        // ---- bugs
        put(t, BUG, V, L, HCItems.FLASHBUG, 24, 1, 2, HCItems.THUNDERBUG, 24, 1, 2, HCItems.BITTERBUG, 24, 1, 2, HCItems.SPIDER_WEB, 14, 1, 1, HCItems.HONEY, 10, 1, 1);
        put(t, BUG, V, H, HCItems.FLASHBUG, 24, 1, 3, HCItems.THUNDERBUG, 24, 1, 3, HCItems.BITTERBUG, 22, 1, 3, HCItems.SPIDER_WEB, 14, 1, 2, HCItems.HONEY, 12, 1, 2);
        put(t, BUG, F, L, HCItems.FLASHBUG, 20, 1, 2, HCItems.THUNDERBUG, 28, 1, 2, HCItems.BITTERBUG, 22, 1, 2, HCItems.SPIDER_WEB, 14, 1, 1);
        put(t, BUG, F, H, HCItems.FLASHBUG, 20, 1, 3, HCItems.THUNDERBUG, 28, 1, 3, HCItems.BITTERBUG, 22, 1, 3, HCItems.SPIDER_WEB, 14, 1, 2);
        put(t, BUG, E, L, HCItems.THUNDERBUG, 26, 1, 2, HCItems.BITTERBUG, 26, 1, 2, HCItems.FLASHBUG, 20, 1, 2, HCItems.SPIDER_WEB, 10, 1, 1);
        put(t, BUG, E, H, HCItems.THUNDERBUG, 26, 1, 3, HCItems.BITTERBUG, 26, 1, 3, HCItems.FLASHBUG, 20, 1, 3, HCItems.SPIDER_WEB, 10, 1, 2);
        // ---- bones
        for (QuestMap m : QuestMap.values()) {
            put(t, BONE, m, L, HCItems.MONSTER_BONE_S, 46, 1, 2, HCItems.MONSTER_BONE_M, 24, 1, 1, HCItems.MONSTER_FLUID, 8, 1, 1, HCItems.RAW_MEAT, 6, 1, 1);
            put(t, BONE, m, H, HCItems.MONSTER_BONE_S, 26, 1, 3, HCItems.MONSTER_BONE_M, 34, 1, 2, HCItems.MONSTER_BONE_L, 14, 1, 1, HCItems.MONSTER_FLUID, 10, 1, 2);
        }
        // ---- mining (ores)
        put(t, MINE, V, L, HCItems.EARTH_CRYSTAL, 34, 1, 2, HCItems.AZURITE_ORE, 30, 1, 2, HCItems.LIGHTCRYSTAL, 3, 1, 1, HCItems.ARMOR_SPHERE, 4, 1, 1, HCItems.ICE_CRYSTAL, 4, 1, 1);
        put(t, MINE, V, H, HCItems.AZURITE_ORE, 24, 1, 3, HCItems.CRIMSONITE_ORE, 24, 1, 2, HCItems.EARTH_CRYSTAL, 20, 1, 3, HCItems.LIGHTCRYSTAL, 14, 1, 1,
                HCItems.NOVACRYSTAL, 3, 1, 1, HCItems.ARMOR_SPHERE_PLUS, 4, 1, 1);
        put(t, MINE, F, L, HCItems.ICE_CRYSTAL, 36, 1, 2, HCItems.AZURITE_ORE, 26, 1, 2, HCItems.EARTH_CRYSTAL, 18, 1, 1, HCItems.LIGHTCRYSTAL, 3, 1, 1, HCItems.ARMOR_SPHERE, 4, 1, 1);
        put(t, MINE, F, H, HCItems.ICE_CRYSTAL, 26, 1, 3, HCItems.CRIMSONITE_ORE, 22, 1, 2, HCItems.AZURITE_ORE, 20, 1, 3, HCItems.LIGHTCRYSTAL, 12, 1, 1,
                HCItems.NOVACRYSTAL, 4, 1, 1, HCItems.ARMOR_SPHERE_PLUS, 4, 1, 1);
        put(t, MINE, E, L, HCItems.EARTH_CRYSTAL, 32, 1, 2, HCItems.AZURITE_ORE, 20, 1, 2, HCItems.CRIMSONITE_ORE, 14, 1, 1, HCItems.LIGHTCRYSTAL, 4, 1, 1,
                HCItems.NOVACRYSTAL, 1, 1, 1, HCItems.ARMOR_SPHERE, 4, 1, 1);
        put(t, MINE, E, H, HCItems.CRIMSONITE_ORE, 30, 1, 3, HCItems.EARTH_CRYSTAL, 20, 1, 3, HCItems.LIGHTCRYSTAL, 12, 1, 1, HCItems.NOVACRYSTAL, 10, 1, 1,
                HCItems.ARMOR_SPHERE_PLUS, 5, 1, 1);
        tables = t;
        return t;
    }

    public static List<Entry> table(GatherKind kind, @Nullable QuestMap map, Rank rank) {
        List<Entry> l = tables().get(key(kind, map == null ? QuestMap.VERDANT_HILLS : map, rank));
        return l == null ? List.of() : l;
    }

    public static ItemStack roll(GatherKind kind, @Nullable QuestMap map, Rank rank, RandomSource r) {
        List<Entry> list = table(kind, map, rank);
        int total = 0;
        for (Entry e : list) total += e.weight();
        if (total <= 0) return ItemStack.EMPTY;
        int x = r.nextInt(total);
        for (Entry e : list) {
            x -= e.weight();
            if (x < 0) return new ItemStack(e.item().get(), e.min() + (e.max() > e.min() ? r.nextInt(e.max() - e.min() + 1) : 0));
        }
        return ItemStack.EMPTY;
    }

    /** Which gathering kind yields this item on the given map (for gather-quest reward rolls). */
    public static GatherKind kindFor(Item item, QuestMap map) {
        for (GatherKind k : GatherKind.values()) {
            for (Entry e : table(k, map, Rank.LOW)) if (e.item().get() == item) return k;
            for (Entry e : table(k, map, Rank.HIGH)) if (e.item().get() == item) return k;
        }
        return GatherKind.HERB;
    }

    // ------------------------------------------------------------------ interaction
    public static void gather(ServerPlayer sp, ServerLevel level, BlockPos pos, BlockState state, GatheringBlock block) {
        int charges = state.getValue(GatheringBlock.CHARGES);
        if (charges <= 0) {
            sp.displayClientMessage(Component.translatable("message.huntcraft.quest.gather_empty"), true);
            return;
        }
        long now = level.getGameTime();
        Long last = LAST_GATHER.get(sp.getUUID());
        if (last != null && now - last < 8 && now >= last) return;
        LAST_GATHER.put(sp.getUUID(), now);
        if (LAST_GATHER.size() > 512) LAST_GATHER.clear();

        QuestMap map = QuestMap.of(level);
        Rank rank = QuestManager.gatherRank(sp);
        RandomSource r = level.random;
        ItemStack st = roll(block.kind, map, rank, r);
        if (!st.isEmpty()) {
            ItemStack copy = st.copy();
            if (!sp.getInventory().add(st)) sp.drop(st, false);
            sp.displayClientMessage(Component.translatable("message.huntcraft.quest.gathered", copy.getHoverName(), copy.getCount()), true);
            MinecraftForge.EVENT_BUS.post(new HCEvents.GatherEvent(sp, copy));
        }
        SoundEvent sound = switch (block.kind) {
            case HERB -> SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES;
            case MUSHROOM -> SoundEvents.FUNGUS_BREAK;
            case BUG -> SoundEvents.BEE_POLLINATE;
            case BONE -> SoundEvents.BONE_BLOCK_BREAK;
            case MINING -> SoundEvents.STONE_BREAK;
        };
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 0.9F + r.nextFloat() * 0.2F);
        level.sendParticles(block.kind == GatherKind.MINING ? ParticleTypes.CRIT : ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
        sp.swing(InteractionHand.MAIN_HAND, true);

        int next = charges - 1;
        int skill = HunterSkills.level(sp, SkillType.GATHERING);
        if (skill > 0 && r.nextFloat() < 0.3F * skill) next = charges;
        else if (next > 0 && r.nextFloat() < 0.25F) next = 0;
        if (next != charges) level.setBlock(pos, state.setValue(GatheringBlock.CHARGES, next), 3);
        if (next == 0) {
            level.scheduleTick(pos, block, GatheringBlock.REGEN_TICKS);
            sp.displayClientMessage(Component.translatable("message.huntcraft.quest.gather_depleted"), true);
        }
    }

    /** Distinct items obtainable from a kind (for previews). */
    public static List<Item> preview(GatherKind kind, QuestMap map, Rank rank) {
        List<Item> out = new ArrayList<>();
        for (Entry e : table(kind, map, rank)) if (!out.contains(e.item().get())) out.add(e.item().get());
        return out;
    }

}
