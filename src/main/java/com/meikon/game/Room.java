package com.meikon.game;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** A room of the mansion: interior bounds (inclusive block coordinates) at floor level {@link MansionBuilder#Y}. */
public final class Room {
    public enum Type {
        SAFE, WEDDING, BUTSUMA, DOLLS, KITCHEN, BATH, CHILD, STUDY, BEDROOM, WASHITSU, STORAGE, KURA, CORRIDOR, COURTYARD
    }

    public final int id;
    public Type type;
    public final int x1, z1, x2, z2;

    public Room(int id, Type type, int x1, int z1, int x2, int z2) {
        this.id = id;
        this.type = type;
        this.x1 = Math.min(x1, x2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.z2 = Math.max(z1, z2);
    }

    public BlockPos center() {
        return new BlockPos((x1 + x2) / 2, MansionBuilder.Y + 1, (z1 + z2) / 2);
    }

    public int width() {
        return x2 - x1 + 1;
    }

    public int depth() {
        return z2 - z1 + 1;
    }

    public boolean contains(double x, double z) {
        return x >= x1 && x < x2 + 1 && z >= z1 && z < z2 + 1;
    }

    public AABB box() {
        return new AABB(x1, MansionBuilder.Y, z1, x2 + 1, MansionBuilder.Y + 6, z2 + 1);
    }

    public String key() {
        return type.name().toLowerCase(java.util.Locale.ROOT);
    }
}
