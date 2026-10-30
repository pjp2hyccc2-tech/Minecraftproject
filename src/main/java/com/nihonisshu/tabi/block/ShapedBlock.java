package com.nihonisshu.tabi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Non-rotating block with a custom shape (lamps, tables, posts). */
public class ShapedBlock extends Block {
    private final VoxelShape shape;
    private final boolean noCollision;

    public ShapedBlock(Properties props, VoxelShape shape, boolean noCollision) {
        super(props);
        this.shape = shape;
        this.noCollision = noCollision;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return noCollision ? net.minecraft.world.phys.shapes.Shapes.empty() : shape;
    }
}
