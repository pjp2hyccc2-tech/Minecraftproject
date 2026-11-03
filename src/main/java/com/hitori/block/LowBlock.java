package com.hitori.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A low solid block that is not a full cube: the genkan's wooden step (上がり框, 8 px). */
public class LowBlock extends Block {
    private final VoxelShape shape;

    public LowBlock(Properties properties, double heightPx) {
        super(properties);
        this.shape = Block.box(0, 0, 0, 16, heightPx, 16);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return type == PathComputationType.LAND && shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.5;
    }
}
