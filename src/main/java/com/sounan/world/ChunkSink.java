package com.sounan.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Block writes clipped to one chunk (structures and trees that cross chunk borders are built piecewise). */
public final class ChunkSink {
    private final ChunkAccess chunk;
    public final int minX, minZ, maxX, maxZ;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public ChunkSink(ChunkAccess chunk) {
        this.chunk = chunk;
        this.minX = chunk.getPos().getMinBlockX();
        this.minZ = chunk.getPos().getMinBlockZ();
        this.maxX = minX + 15;
        this.maxZ = minZ + 15;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** True if the box (inclusive) touches this chunk. */
    public boolean touches(int x1, int z1, int x2, int z2) {
        return Math.max(x1, x2) >= minX && Math.min(x1, x2) <= maxX && Math.max(z1, z2) >= minZ && Math.min(z1, z2) <= maxZ;
    }

    public void set(int x, int y, int z, BlockState state) {
        if (y < 1 || y > 254 || !contains(x, z)) return;
        chunk.setBlockState(pos.set(x, y, z), state, false);
    }

    /** Sets a block whose shape depends on its neighbours (fences, panes, bars, stairs): fixed up when the chunk is finished. */
    public void post(int x, int y, int z, BlockState state) {
        if (y < 1 || y > 254 || !contains(x, z)) return;
        chunk.setBlockState(pos.set(x, y, z), state, false);
        chunk.markPosForPostprocessing(pos.immutable());
    }

    /** Only replaces air (and plants / decals). */
    public void soft(int x, int y, int z, BlockState state) {
        if (y < 1 || y > 254 || !contains(x, z)) return;
        BlockState old = chunk.getBlockState(pos.set(x, y, z));
        if (old.isAir() || old.canBeReplaced()) chunk.setBlockState(pos, state, false);
    }

    public BlockState get(int x, int y, int z) {
        if (!contains(x, z)) return Blocks.AIR.defaultBlockState();
        return chunk.getBlockState(pos.set(x, y, z));
    }

    public boolean isAir(int x, int y, int z) {
        return get(x, y, z).isAir();
    }

    public void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        int ax = Math.max(Math.min(x1, x2), minX), bx = Math.min(Math.max(x1, x2), maxX);
        int az = Math.max(Math.min(z1, z2), minZ), bz = Math.min(Math.max(z1, z2), maxZ);
        int ay = Math.min(y1, y2), by = Math.max(y1, y2);
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++)
                for (int y = ay; y <= by; y++) set(x, y, z, state);
    }

    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        box(x1, y1, z1, x2, y2, z2, Blocks.AIR.defaultBlockState());
    }
}
