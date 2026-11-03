package com.reisatsu.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * One block of a one-block-thick wall whose two sides belong to different rooms: the face toward {@code facing}
 * shows the {@code inner} texture, every other face the {@code outer} one (wallpaper over the dining room and
 * white tiles in the restroom behind it; the beige siding outside and the wainscot inside). Only existing wall
 * textures are used, so this block needs no art of its own.
 */
public class TwoSidedWallBlock extends HorizontalDirectionalBlock {
    public TwoSidedWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(Props.INNER, Props.Face.WALLPAPER).setValue(Props.OUTER, Props.Face.EXTERIOR_WALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Props.INNER, Props.OUTER);
    }

    public BlockState of(Direction innerSide, Props.Face inner, Props.Face outer) {
        return defaultBlockState().setValue(FACING, innerSide).setValue(Props.INNER, inner).setValue(Props.OUTER, outer);
    }
}
