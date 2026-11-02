package com.sounan.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The player's lost backpack at the stream (open = already emptied). */
public class LostBagBlock extends InteractBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public LostBagBlock(Properties properties, String action, double x1, double y1, double z1, double x2, double y2, double z2) {
        super(properties, action, x1, y1, z1, x2, y2, z2);
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }
}
