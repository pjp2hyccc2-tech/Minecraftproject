package com.eternalrealm.client.guide;

import com.eternalrealm.world.guide.PathSearch;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The client's loaded world as {@link PathSearch.Terrain}: open blocks have no collision (wooden doors and gates count
 * as open: the player opens them), a floor is a block whose top is between a slab and a full block (not a fence) or a
 * low floor in the feet's own cell (a carpet, a little snow), water is swum through at a cost, and lava, fire, magma,
 * cactus, berry bushes and powder snow are never walked into. Blocks of chunks the client has not loaded are neither.
 * Client thread only.
 */
final class ClientTerrain implements PathSearch.Terrain {
    private final ClientLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    ClientTerrain(ClientLevel level) {
        this.level = level;
    }

    private BlockState state(int x, int y, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return Blocks.AIR.defaultBlockState();
        }
        return level.getBlockState(cursor.set(x, y, z));
    }

    @Override
    public boolean open(int x, int y, int z) {
        BlockState state = state(x, y, z);
        if (state == null) {
            return false;
        }
        if (passable(state)) {
            return true;
        }
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        return state.getCollisionShape(level, cursor.set(x, y, z)).isEmpty();
    }

    /** Doors and gates the player opens by hand. */
    private static boolean passable(BlockState state) {
        return (state.getBlock() instanceof DoorBlock && !state.is(Blocks.IRON_DOOR))
                || state.getBlock() instanceof FenceGateBlock;
    }

    @Override
    public boolean standable(int x, int y, int z) {
        BlockState feet = state(x, y, z);
        if (feet == null) {
            return false;
        }
        if (open(x, y, z)) {
            if (!open(x, y + 1, z)) {
                return false;
            }
            return feet.getFluidState().is(FluidTags.WATER) || floorTop(x, y - 1, z) > LOW;
        }
        // a carpet, a few layers of snow: the floor is in the feet's own cell
        double low = lowFloor(x, y, z);
        return low >= 0.0 && open(x, y + 1, z) && (low <= 0.2 || open(x, y + 2, z));
    }

    /** Floors this low lie in the feet's own cell (a carpet, snow); higher ones (a slab) are the floor of the cell above. */
    private static final double LOW = 0.4;

    /**
     * The top of a low floor lying at the bottom of the cell (a carpet: 1/16, snow layers up to {@value #LOW}), or -1 when
     * the cell holds no such floor.
     */
    private double lowFloor(int x, int y, int z) {
        BlockState state = state(x, y, z);
        if (state == null || passable(state) || state.getFluidState().is(FluidTags.LAVA)) {
            return -1.0;
        }
        VoxelShape shape = state.getCollisionShape(level, cursor.set(x, y, z));
        if (shape.isEmpty() || shape.min(Direction.Axis.Y) > 1e-6) {
            return -1.0;
        }
        double top = shape.max(Direction.Axis.Y);
        return top <= LOW ? top : -1.0;
    }

    /** The top of the block's collision inside its cell (0..1), or 0 when it is no floor (air, a fence, unloaded). */
    private double floorTop(int x, int y, int z) {
        BlockState below = state(x, y, z);
        if (below == null || passable(below)) {
            return 0.0;
        }
        VoxelShape shape = below.getCollisionShape(level, cursor.set(x, y, z));
        if (shape.isEmpty()) {
            return 0.0;
        }
        double top = shape.max(Direction.Axis.Y);
        return top <= 1.0 + 1e-6 ? top : 0.0;
    }

    @Override
    public double penalty(int x, int y, int z) {
        BlockState feet = state(x, y, z);
        BlockState below = state(x, y - 1, z);
        if (feet == null || below == null || dangerous(feet) || dangerous(below)) {
            return -1.0;
        }
        double cost = 0.0;
        if (feet.getFluidState().is(FluidTags.WATER)) {
            cost += 2.5;
        }
        if (below.is(Blocks.SOUL_SAND) || below.is(Blocks.HONEY_BLOCK)) {
            cost += 0.5;
        }
        return cost;
    }

    private static boolean dangerous(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE);
    }

    /** Where a dot with its feet cell at {@code y} lies: on the floor below (a slab is half a block lower). */
    double dotHeight(double x, int y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        BlockState feet = state(bx, y, bz);
        if (feet != null && feet.getFluidState().is(FluidTags.WATER)) {
            return y + 0.9;
        }
        double low = lowFloor(bx, y, bz);
        if (low >= 0.0) {
            return y + low;
        }
        double top = floorTop(bx, y - 1, bz);
        return top > 0.0 ? y - 1 + top : y;
    }
}
