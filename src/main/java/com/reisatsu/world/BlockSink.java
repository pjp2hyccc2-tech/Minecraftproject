package com.reisatsu.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where the builders write: one chunk during generation ({@link ChunkSink}) or the live level when the restaurant is
 * restored ({@link LevelSink}). Writes outside the sink's area are ignored, so every builder can simply run its whole
 * program for every chunk.
 */
public interface BlockSink {
    /** True if the column is inside this sink. */
    boolean contains(int x, int z);

    /** True if the box (inclusive) touches this sink. */
    boolean touches(int x1, int z1, int x2, int z2);

    void set(int x, int y, int z, BlockState state);

    /** A block whose shape depends on its neighbours (stairs, fences, panes, chains): fixed up afterwards. */
    void post(int x, int y, int z, BlockState state);

    BlockState get(int x, int y, int z);

    /** Only replaces air (and replaceable blocks). */
    default void soft(int x, int y, int z, BlockState state) {
        if (!contains(x, z)) return;
        BlockState old = get(x, y, z);
        if (old.isAir() || old.canBeReplaced()) set(x, y, z, state);
    }

    default boolean isAir(int x, int y, int z) {
        return get(x, y, z).isAir();
    }

    default void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        int ay = Math.min(y1, y2), by = Math.max(y1, y2);
        if (!touches(ax, az, bx, bz)) return;
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++) {
                if (!contains(x, z)) continue;
                for (int y = ay; y <= by; y++) set(x, y, z, state);
            }
    }

    default void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        box(x1, y1, z1, x2, y2, z2, Blocks.AIR.defaultBlockState());
    }
}
