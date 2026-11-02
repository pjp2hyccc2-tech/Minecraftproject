package com.clockworkdiver.clock;

import com.clockworkdiver.customer.CustomerProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** A commissioned clock: its kind, owner and faulty parts. Stored on the player's progress and on the clock item. */
public final class ClockData {
    public static final String STACK_TAG = "Clock";

    public ClockType type = ClockType.POCKET_WATCH;
    public CustomerProfile profile = CustomerProfile.GREGORY;
    public long commissionId;
    public boolean tutorial;
    public int reward;
    public final List<Defect> defects = new ArrayList<>();

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.id);
        tag.putString("Profile", profile.id);
        tag.putLong("Commission", commissionId);
        tag.putBoolean("Tutorial", tutorial);
        tag.putInt("Reward", reward);
        ListTag list = new ListTag();
        for (Defect d : defects) list.add(d.save());
        tag.put("Defects", list);
        return tag;
    }

    @Nullable
    public static ClockData load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("Type")) return null;
        ClockData data = new ClockData();
        data.type = ClockType.byId(tag.getString("Type"));
        data.profile = CustomerProfile.byId(tag.getString("Profile"));
        data.commissionId = tag.getLong("Commission");
        data.tutorial = tag.getBoolean("Tutorial");
        data.reward = tag.getInt("Reward");
        ListTag list = tag.getList("Defects", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) data.defects.add(Defect.load(list.getCompound(i)));
        return data;
    }

    @Nullable
    public static ClockData fromStack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(STACK_TAG)) return null;
        return load(tag.getCompound(STACK_TAG));
    }

    public void writeTo(ItemStack stack) {
        stack.getOrCreateTag().put(STACK_TAG, save());
    }

    @Nullable
    public Defect defect(int id) {
        for (Defect d : defects) {
            if (d.id == id) return d;
        }
        return null;
    }

    public boolean allRepaired() {
        for (Defect d : defects) {
            if (!d.repaired) return false;
        }
        return !defects.isEmpty();
    }

    public int foundCount() {
        int n = 0;
        for (Defect d : defects) if (d.found) n++;
        return n;
    }

    public int repairedCount() {
        int n = 0;
        for (Defect d : defects) if (d.repaired) n++;
        return n;
    }

    /** 0 (tutorial) .. 5 (orrery with many defects). */
    public int difficulty() {
        if (tutorial) return 0;
        return Math.min(5, type.difficulty + (defects.size() >= 4 ? 1 : 0));
    }
}
