package com.reisatsu.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes into the live level inside a box (used to put the restaurant back the way it was built). Only changed blocks
 * are written; neighbour-dependent blocks get their shape fixed in {@link #finish()}.
 */
public final class LevelSink implements BlockSink {
    private final ServerLevel level;
    private final int x1, z1, x2, z2;
    private final List<BlockPos> posts = new ArrayList<>();
    public int changed;

    public LevelSink(ServerLevel level, int x1, int z1, int x2, int z2) {
        this.level = level;
        this.x1 = Math.min(x1, x2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.z2 = Math.max(z1, z2);
    }

    @Override
    public boolean contains(int x, int z) {
        return x >= x1 && x <= x2 && z >= z1 && z <= z2 && level.isLoaded(new BlockPos(x, 64, z));
    }

    @Override
    public boolean touches(int ax, int az, int bx, int bz) {
        return Math.max(ax, bx) >= x1 && Math.min(ax, bx) <= x2 && Math.max(az, bz) >= z1 && Math.min(az, bz) <= z2;
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        if (y < 1 || y > 254 || !contains(x, z)) return;
        BlockPos p = new BlockPos(x, y, z);
        if (level.getBlockState(p) == state) return;
        level.setBlock(p, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        changed++;
    }

    @Override
    public void post(int x, int y, int z, BlockState state) {
        if (y < 1 || y > 254 || !contains(x, z)) return;
        set(x, y, z, state);
        posts.add(new BlockPos(x, y, z));
    }

    @Override
    public BlockState get(int x, int y, int z) {
        if (!contains(x, z)) return Blocks.AIR.defaultBlockState();
        return level.getBlockState(new BlockPos(x, y, z));
    }

    /** Fixes the shapes of stairs, panes, fences... written with {@link #post}. */
    public void finish() {
        for (BlockPos p : posts) {
            BlockState s = level.getBlockState(p);
            BlockState u = Block.updateFromNeighbourShapes(s, level, p);
            if (u != s) level.setBlock(p, u, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        posts.clear();
    }
}
