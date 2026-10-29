package com.huntcraft.village.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Decorative glowing paper lantern (chouchin), red or white. */
public class PaperLanternBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 2, 3, 13, 14, 13), Block.box(5, 0, 5, 11, 16, 11));

    public PaperLanternBlock(Properties props) {
        super(props);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
