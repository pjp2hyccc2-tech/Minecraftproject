package com.birdiecraft.course;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Small helper for fast bulk block placement while building courses. */
public final class BlockWriter {
    /** Notify clients but skip neighbour shape updates (we place everything ourselves). */
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public final ServerLevel level;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public BlockWriter(ServerLevel level) {
        this.level = level;
    }

    public BlockState get(int x, int y, int z) {
        pos.set(x, y, z);
        return level.getBlockState(pos);
    }

    public void set(int x, int y, int z, BlockState state) {
        pos.set(x, y, z);
        level.setBlock(pos, state, FLAGS);
    }

    public void set(int x, int y, int z, Block block) {
        set(x, y, z, block.defaultBlockState());
    }

    /** Places only into air or replaceable plants. */
    public boolean setIfFree(int x, int y, int z, BlockState state) {
        pos.set(x, y, z);
        BlockState cur = level.getBlockState(pos);
        if (cur.isAir() || cur.canBeReplaced()) {
            level.setBlock(pos, state, FLAGS);
            return true;
        }
        return false;
    }

    public boolean isFree(int x, int y, int z) {
        pos.set(x, y, z);
        BlockState cur = level.getBlockState(pos);
        return cur.isAir() || cur.canBeReplaced();
    }

    /** Leaves that never decay. */
    public static BlockState leaves(Block block) {
        BlockState s = block.defaultBlockState();
        if (s.hasProperty(LeavesBlock.PERSISTENT)) s = s.setValue(LeavesBlock.PERSISTENT, true);
        return s;
    }
}
