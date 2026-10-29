package com.huntcraft.village.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Decorative glowing paper lantern (chouchin). */
public class PaperLanternBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 1, 3, 13, 15, 13);

    public PaperLanternBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
