package com.huntcraft.quest;

import com.huntcraft.core.Rank;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.quest.block.GatherKind;
import com.huntcraft.registry.HCItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Quest reward rolls (MonsterProfile reward tables + gathering materials + fixed sub rewards) and delivery:
 * rewards wait in the player's "quest" module tag until claimed from the result screen (inventory or item box).
 */
public final class QuestRewards {
    public static final String CAT_MAIN = "main";
    public static final String CAT_SUB = "sub";
    public static final String CAT_BREAK = "break";
    public static final String CAT_CAPTURE = "capture";

    private QuestRewards() {
    }

    private record Line(ItemStack stack, String cat) {
    }

    private static void addLine(List<Line> lines, ItemStack st, String cat) {
        if (st.isEmpty()) return;
        for (Line l : lines) {
            if (l.cat.equals(cat) && ItemStack.isSameItemSameTags(l.stack, st) && l.stack.getCount() + st.getCount() <= 99) {
                l.stack.grow(st.getCount());
                return;
            }
        }
        lines.add(new Line(st.copy(), cat));
    }

    private static ItemStack rollTable(@Nullable RewardTable t, Rank rank, RandomSource r) {
        return t == null || t.isEmpty() ? ItemStack.EMPTY : t.roll(r, rank);
    }

    private static ItemStack fallback(Rank rank, RandomSource r) {
        Item[] low = {HCItems.MONSTER_BONE_M.get(), HCItems.MONSTER_FLUID.get(), HCItems.ARMOR_SPHERE.get(), HCItems.AZURITE_ORE.get()};
        Item[] high = {HCItems.MONSTER_BONE_L.get(), HCItems.MONSTER_FLUID.get(), HCItems.ARMOR_SPHERE_PLUS.get(), HCItems.CRIMSONITE_ORE.get()};
        Item[] pool = rank == Rank.HIGH ? high : low;
        return new ItemStack(pool[r.nextInt(pool.length)]);
    }

    /** Builds the (success) result tag for one member: zenny, HRP and rolled items. */
    public static CompoundTag rollSuccess(QuestInstance inst, QuestDef q, RandomSource r) {
        List<Line> lines = new ArrayList<>();
        Rank rank = q.rank;
        switch (q.type) {
            case HUNT, CAPTURE -> {
                for (QuestDef.Target t : q.targets) {
                    MonsterProfile prof = MonsterRegistry.profile(t.id());
                    for (int i = 0; i < q.rolls; i++) {
                        ItemStack st = prof == null ? fallback(rank, r)
                                : rollTable(prof.captureRewards.isEmpty() ? prof.carves : prof.captureRewards, rank, r);
                        addLine(lines, st.isEmpty() ? fallback(rank, r) : st, CAT_MAIN);
                    }
                }
            }
            case SLAY -> {
                for (QuestDef.Target t : q.targets) {
                    MonsterProfile prof = MonsterRegistry.profile(t.id());
                    for (int i = 0; i < q.rolls; i++) {
                        ItemStack st = prof == null ? ItemStack.EMPTY : rollTable(prof.carves, rank, r);
                        addLine(lines, st.isEmpty() ? fallback(rank, r) : st, CAT_MAIN);
                    }
                }
                addLine(lines, new ItemStack(rank == Rank.HIGH ? HCItems.MONSTER_BONE_M.get() : HCItems.MONSTER_BONE_S.get(), 1 + r.nextInt(2)), CAT_MAIN);
            }
            case GATHER -> {
                for (QuestDef.Target t : q.targets) {
                    if (t.item() == null) continue;
                    GatherKind kind = GatherLoot.kindFor(t.item().get(), q.map);
                    for (int i = 0; i < q.rolls; i++) addLine(lines, GatherLoot.roll(kind, q.map, rank, r), CAT_MAIN);
                }
            }
        }
        for (String cap : inst.captured) {
            MonsterProfile prof = MonsterRegistry.profile(cap);
            if (prof == null) continue;
            for (int i = 0; i < 2; i++) addLine(lines, rollTable(prof.captureRewards, rank, r), CAT_CAPTURE);
        }
        for (String part : inst.brokenParts) {
            String[] s = part.split("\\|");
            if (s.length < 2) continue;
            MonsterProfile prof = MonsterRegistry.profile(s[0]);
            if (prof == null) continue;
            addLine(lines, rollTable(prof.breakRewards.get(s[1]), rank, r), CAT_BREAK);
        }
        for (QuestDef.Reward rw : q.rewards) addLine(lines, new ItemStack(rw.item().get(), rw.count()), CAT_SUB);

        CompoundTag tag = baseResult(inst, q, true);
        int zenny = q.zenny * Math.max(0, QuestManager.MAX_FAINTS - inst.faints) / QuestManager.MAX_FAINTS;
        tag.putInt("zenny", zenny);
        tag.putInt("hrp", q.hrp);
        ListTag items = new ListTag();
        for (Line l : lines) {
            CompoundTag it = l.stack.save(new CompoundTag());
            it.putString("cat", l.cat);
            items.add(it);
        }
        tag.put("items", items);
        tag.putInt("parts", inst.brokenParts.size());
        return tag;
    }

    public static CompoundTag baseResult(QuestInstance inst, QuestDef q, boolean success) {
        CompoundTag tag = new CompoundTag();
        tag.putString("quest", q.id);
        tag.putBoolean("success", success);
        tag.putLong("time", success ? inst.clearTicks : 0);
        tag.putInt("faints", inst.faints);
        tag.putString("reason", inst.failReason);
        return tag;
    }

    // ------------------------------------------------------------------ pending rewards
    public static ListTag pending(HunterData d) {
        return d.getModuleTag("quest").getList("pending", Tag.TAG_COMPOUND);
    }

    public static void addPending(HunterData d, ListTag items) {
        CompoundTag mod = d.getModuleTag("quest");
        ListTag list = mod.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag c = items.getCompound(i).copy();
            c.remove("cat");
            list.add(c);
        }
        mod.put("pending", list);
        d.markDirty();
    }

    /** Moves pending rewards to the inventory (overflow -> item box) or to the item box (overflow -> inventory). */
    public static int claim(ServerPlayer p, boolean toBox) {
        HunterData d = HunterData.get(p);
        CompoundTag mod = d.getModuleTag("quest");
        ListTag list = mod.getList("pending", Tag.TAG_COMPOUND);
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            ItemStack st = ItemStack.of(list.getCompound(i));
            if (st.isEmpty()) continue;
            n += st.getCount();
            if (toBox) {
                st = addToItemBox(d, st);
                if (!st.isEmpty() && !p.getInventory().add(st)) p.drop(st, false);
            } else {
                p.getInventory().add(st);
                if (!st.isEmpty()) st = addToItemBox(d, st);
                if (!st.isEmpty()) p.drop(st, false);
            }
        }
        mod.remove("pending");
        d.markDirty();
        return n;
    }

    /** Adds a stack to the 108-slot item box; returns the remainder. */
    public static ItemStack addToItemBox(HunterData d, ItemStack st) {
        for (int i = 0; i < d.itemBox.size() && !st.isEmpty(); i++) {
            ItemStack cur = d.itemBox.get(i);
            if (!cur.isEmpty() && ItemStack.isSameItemSameTags(cur, st) && cur.getCount() < cur.getMaxStackSize()) {
                int move = Math.min(st.getCount(), cur.getMaxStackSize() - cur.getCount());
                cur.grow(move);
                st.shrink(move);
            }
        }
        for (int i = 0; i < d.itemBox.size() && !st.isEmpty(); i++) {
            if (d.itemBox.get(i).isEmpty()) {
                int move = Math.min(st.getCount(), st.getMaxStackSize());
                d.itemBox.set(i, st.split(move));
            }
        }
        d.markDirty();
        return st;
    }
}
