package com.frostbound.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Furnace grate of the generator; glows while the generator runs. */
public class GrateBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public GrateBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LIT);
    }
}
