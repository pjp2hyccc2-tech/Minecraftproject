package com.reisatsu.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Block writes clipped to one chunk (structures that cross chunk borders are built piecewise). Blocks with a block
 * entity get a pending "DUMMY" entity tag, so the chunk creates their block entities when it becomes a full chunk
 * (a plain {@code setBlockState} on a proto chunk would leave them without one).
 */
public final class ChunkSink implements BlockSink {
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

    @Override
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    @Override
    public boolean touches(int x1, int z1, int x2, int z2) {
        return Math.max(x1, x2) >= minX && Math.min(x1, x2) <= maxX && Math.max(z1, z2) >= minZ && Math.min(z1, z2) <= maxZ;
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        if (y < 1 || y > 254 || !contains(x, z)) return;
        BlockState old = chunk.setBlockState(pos.set(x, y, z), state, false);
        if (state.hasBlockEntity()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "DUMMY");
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            chunk.setBlockEntityNbt(tag);
        } else if (old != null && old.hasBlockEntity()) {
            chunk.removeBlockEntity(pos.immutable());
        }
    }

    @Override
    public void post(int x, int y, int z, BlockState state) {
        if (y < 1 || y > 254 || !contains(x, z)) return;
        set(x, y, z, state);
        chunk.markPosForPostprocessing(new BlockPos(x, y, z));
    }

    @Override
    public BlockState get(int x, int y, int z) {
        if (!contains(x, z)) return Blocks.AIR.defaultBlockState();
        return chunk.getBlockState(pos.set(x, y, z));
    }
}
