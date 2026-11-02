package com.birdiecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** White out-of-bounds stake. Thin post; balls can clip it. */
public class StakeBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(6.5, 0.0, 6.5, 9.5, 14.0, 9.5);

    public StakeBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
