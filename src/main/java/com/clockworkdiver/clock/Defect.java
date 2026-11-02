package com.clockworkdiver.clock;

import net.minecraft.nbt.CompoundTag;

/** One faulty part of a commissioned clock. */
public final class Defect {
    public final int id;
    public final DefectType type;
    public final String part;
    /** Position on the clock drawing, normalised to -1..1. */
    public final float x;
    public final float y;
    public boolean found;
    public boolean repaired;

    public Defect(int id, DefectType type, String part, float x, float y) {
        this.id = id;
        this.type = type;
        this.part = part;
        this.x = x;
        this.y = y;
    }

    public String partKey() {
        return DefectType.partKey(part);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putString("Type", type.id);
        tag.putString("Part", part);
        tag.putFloat("X", x);
        tag.putFloat("Y", y);
        tag.putBoolean("Found", found);
        tag.putBoolean("Repaired", repaired);
        return tag;
    }

    public static Defect load(CompoundTag tag) {
        Defect d = new Defect(tag.getInt("Id"), DefectType.byId(tag.getString("Type")), tag.getString("Part"),
                tag.getFloat("X"), tag.getFloat("Y"));
        d.found = tag.getBoolean("Found");
        d.repaired = tag.getBoolean("Repaired");
        return d;
    }
}
