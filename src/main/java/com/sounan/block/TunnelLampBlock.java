package com.sounan.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A dead sodium lamp of the old tunnel; the game lights them during the climax. */
public class TunnelLampBlock extends InteractBlock {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public TunnelLampBlock(Properties properties, String action, double x1, double y1, double z1, double x2, double y2, double z2) {
        super(properties, action, x1, y1, z1, x2, y2, z2);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }
}
