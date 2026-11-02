package com.clockworkdiver.clock;

import net.minecraft.nbt.CompoundTag;

/** One faulty part of a commissioned clock (or, for story clocks, a clue hidden in it). */
public final class Defect {
    public static final int BOSS_NORMAL = 0;
    public static final int BOSS_SHADOW = 1;
    public static final int BOSS_SHADE = 2;
    public static final int BOSS_QUEEN = 3;

    public final int id;
    public final DefectType type;
    public final String part;
    /** Position on the clock drawing, normalised to -1..1. */
    public final float x;
    public final float y;
    public boolean found;
    public boolean repaired;
    /** {@link DiveMods} bits for the dive into this part. */
    public int mods;
    /** Time limit in seconds when {@link DiveMods#TIMED} is set. */
    public int timeLimit;
    /** Memory fragments hidden in the chamber, and the index of the first one's text in the story. */
    public int fragStart;
    public int fragCount;
    /** Time Eater variant for nests. */
    public int boss;

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
        if (mods != 0) tag.putInt("Mods", mods);
        if (timeLimit != 0) tag.putInt("Time", timeLimit);
        if (fragCount != 0) {
            tag.putInt("FragStart", fragStart);
            tag.putInt("Frags", fragCount);
        }
        if (boss != 0) tag.putInt("Boss", boss);
        return tag;
    }

    public static Defect load(CompoundTag tag) {
        Defect d = new Defect(tag.getInt("Id"), DefectType.byId(tag.getString("Type")), tag.getString("Part"),
                tag.getFloat("X"), tag.getFloat("Y"));
        d.found = tag.getBoolean("Found");
        d.repaired = tag.getBoolean("Repaired");
        d.mods = tag.getInt("Mods");
        d.timeLimit = tag.getInt("Time");
        d.fragStart = tag.getInt("FragStart");
        d.fragCount = tag.getInt("Frags");
        d.boss = tag.getInt("Boss");
        return d;
    }
}
