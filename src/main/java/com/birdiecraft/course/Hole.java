package com.birdiecraft.course;

import com.birdiecraft.golf.engine.GolfUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/** One hole: the tee marker block, the cup block and the par. */
public final class Hole {
    public final BlockPos tee;
    public final BlockPos cup;
    public int par;

    public Hole(BlockPos tee, BlockPos cup, int par) {
        this.tee = tee.immutable();
        this.cup = cup.immutable();
        this.par = par;
    }

    /** Ball start position (top centre of the tee marker). */
    public double teeX() {
        return tee.getX() + 0.5;
    }

    public double teeY() {
        return tee.getY() + 1.0005;
    }

    public double teeZ() {
        return tee.getZ() + 0.5;
    }

    /** Hole position (top centre of the cup block). */
    public double cupX() {
        return cup.getX() + 0.5;
    }

    public double cupY() {
        return cup.getY() + 1.0;
    }

    public double cupZ() {
        return cup.getZ() + 0.5;
    }

    public double lengthBlocks() {
        return GolfUnits.horizontalDistance(teeX(), teeZ(), cupX(), cupZ());
    }

    public int yards() {
        return GolfUnits.yardsRounded(lengthBlocks());
    }

    public static int autoPar(double lengthBlocks) {
        double yards = GolfUnits.toYards(lengthBlocks);
        if (yards <= 250) return 3;
        if (yards <= 470) return 4;
        return 5;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("Tee", NbtUtils.writeBlockPos(tee));
        t.put("Cup", NbtUtils.writeBlockPos(cup));
        t.putInt("Par", par);
        return t;
    }

    public static Hole load(CompoundTag t) {
        return new Hole(NbtUtils.readBlockPos(t.getCompound("Tee")), NbtUtils.readBlockPos(t.getCompound("Cup")),
                Math.max(3, Math.min(6, t.getInt("Par"))));
    }
}
