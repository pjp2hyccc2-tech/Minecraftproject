package com.sounan.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The little mountain shrine; offered = the sake has been offered and the fog has lifted. */
public class HokoraBlock extends InteractBlock {
    public static final BooleanProperty OFFERED = BooleanProperty.create("offered");

    public HokoraBlock(Properties properties, String action, double x1, double y1, double z1, double x2, double y2, double z2) {
        super(properties, action, x1, y1, z1, x2, y2, z2);
        registerDefaultState(defaultBlockState().setValue(OFFERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OFFERED);
    }
}
