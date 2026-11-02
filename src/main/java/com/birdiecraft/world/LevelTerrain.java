package com.birdiecraft.world;

import com.birdiecraft.block.GreenBlock;
import com.birdiecraft.block.SlopeDir;
import com.birdiecraft.golf.engine.Lie;
import com.birdiecraft.golf.engine.Terrain;
import com.birdiecraft.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * {@link Terrain} backed by a live {@link Level}. Block lookups are cached, so create one per
 * simulation batch (the world may change between turns).
 */
public final class LevelTerrain implements Terrain {
    private static final byte WATER = 1;
    private static final byte LEAVES = 2;
    private static final byte CUP = 4;

    private record Entry(Box[] boxes, byte flags, Lie lie, byte slope, byte steep) {
    }

    private final Level level;
    private final int minY;
    private final Long2ObjectOpenHashMap<Entry> cache = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

    public LevelTerrain(Level level) {
        this.level = level;
        this.minY = level.getMinBuildHeight();
    }

    private Entry get(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        Entry e = cache.get(key);
        if (e == null) {
            e = compute(x, y, z);
            cache.put(key, e);
        }
        return e;
    }

    private Entry compute(int x, int y, int z) {
        mpos.set(x, y, z);
        BlockState s = level.getBlockState(mpos);
        FluidState f = s.getFluidState();
        byte flags = 0;
        if (f.is(FluidTags.WATER) || f.is(FluidTags.LAVA)) flags |= WATER;
        boolean leaves = s.is(BlockTags.LEAVES);
        if (leaves) flags |= LEAVES;
        if (s.is(ModBlocks.GOLF_CUP.get())) flags |= CUP;
        Box[] boxes;
        if (leaves || s.isAir()) {
            boxes = NONE;
        } else {
            VoxelShape shape = s.getCollisionShape(level, mpos);
            if (shape.isEmpty()) {
                boxes = NONE;
            } else if (shape == Shapes.block()) {
                boxes = FULL_BLOCK;
            } else {
                List<AABB> list = shape.toAabbs();
                boxes = new Box[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    AABB a = list.get(i);
                    boxes[i] = new Box(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ);
                }
            }
        }
        byte slope = -1;
        byte steep = 0;
        if (s.getBlock() instanceof GreenBlock) {
            SlopeDir d = s.getValue(GreenBlock.SLOPE);
            slope = (byte) d.index;
            steep = (byte) (int) s.getValue(GreenBlock.STEEP);
        }
        return new Entry(boxes, flags, SurfaceResolver.lieOf(s), slope, steep);
    }

    @Override
    public Box[] boxes(int x, int y, int z) {
        return get(x, y, z).boxes();
    }

    @Override
    public boolean isWater(int x, int y, int z) {
        return (get(x, y, z).flags() & WATER) != 0;
    }

    @Override
    public boolean isLeaves(int x, int y, int z) {
        return (get(x, y, z).flags() & LEAVES) != 0;
    }

    @Override
    public Lie lieAt(int x, int y, int z) {
        return get(x, y, z).lie();
    }

    @Override
    public int slopeDir(int x, int y, int z) {
        return get(x, y, z).slope();
    }

    @Override
    public int slopeSteep(int x, int y, int z) {
        return get(x, y, z).steep();
    }

    @Override
    public boolean isCup(int x, int y, int z) {
        return (get(x, y, z).flags() & CUP) != 0;
    }

    @Override
    public int minY() {
        return minY;
    }

    /** Lie of the block the ball rests on. */
    public Lie lieUnder(double x, double y, double z) {
        return lieAt(floor(x), floor(y - 0.02), floor(z));
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
