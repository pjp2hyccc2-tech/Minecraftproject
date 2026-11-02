package com.birdiecraft.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Putting green. Each block stores the downhill direction and steepness that the ball physics
 * uses for break on putts. The putting grid overlay visualises it.
 */
public class GreenBlock extends Block {
    public static final EnumProperty<SlopeDir> SLOPE = EnumProperty.create("slope", SlopeDir.class);
    public static final IntegerProperty STEEP = IntegerProperty.create("steep", 1, 3);

    public GreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(SLOPE, SlopeDir.NONE).setValue(STEEP, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SLOPE, STEEP);
    }
}
