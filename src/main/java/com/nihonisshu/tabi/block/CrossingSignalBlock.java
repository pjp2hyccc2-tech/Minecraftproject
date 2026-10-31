package com.nihonisshu.tabi.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/** 踏切警報機: crossbuck with two red lamps; flash 0 = off, 1 / 2 = left / right lamp lit (driven by CrossingManager). */
public class CrossingSignalBlock extends FacingBlock {
    public static final IntegerProperty FLASH = IntegerProperty.create("flash", 0, 2);

    public CrossingSignalBlock(Properties props, Map<Direction, VoxelShape> shapes) {
        super(props.lightLevel(s -> s.getValue(FLASH) > 0 ? 9 : 0), shapes, false);
        registerDefaultState(defaultBlockState().setValue(FLASH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(FLASH);
    }
}
