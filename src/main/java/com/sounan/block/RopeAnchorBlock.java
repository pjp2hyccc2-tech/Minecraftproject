package com.sounan.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The climbers' iron stake at the cliff; roped = a rope hangs down from it. */
public class RopeAnchorBlock extends InteractBlock {
    public static final BooleanProperty ROPED = BooleanProperty.create("roped");

    public RopeAnchorBlock(Properties properties, String action, double x1, double y1, double z1, double x2, double y2, double z2) {
        super(properties, action, x1, y1, z1, x2, y2, z2);
        registerDefaultState(defaultBlockState().setValue(ROPED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ROPED);
    }
}
