package com.meikon.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A hanging scroll. The director flips FACE when nobody is looking: the painted woman turns to stare at you. */
public class KakejikuBlock extends ThinFacingBlock {
    public static final BooleanProperty FACE = BooleanProperty.create("face");

    public KakejikuBlock(Properties properties) {
        super(properties, 2, 0, 15, 14, 16, 16);
        registerDefaultState(defaultBlockState().setValue(FACE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }
}
