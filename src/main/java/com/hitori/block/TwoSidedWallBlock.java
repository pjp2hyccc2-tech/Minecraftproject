package com.hitori.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * One block of a one-block-thick wall whose two sides belong to different rooms: the face toward {@code facing}
 * shows the {@code inner} texture, every other face the {@code outer} one (the tatami room's plaster on one side and
 * the closet's bare plywood on the other; the grey siding outside and the wallpaper inside). Only existing wall
 * textures are used, so this block needs no art of its own (world-only, not in DESIGN §7).
 */
public class TwoSidedWallBlock extends HorizontalDirectionalBlock {
    public TwoSidedWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(Props.INNER, Props.Face.PLASTER_WALL).setValue(Props.OUTER, Props.Face.APARTMENT_SIDING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Props.INNER, Props.OUTER);
    }

    public BlockState of(Direction innerSide, Props.Face inner, Props.Face outer) {
        return defaultBlockState().setValue(FACING, innerSide).setValue(Props.INNER, inner).setValue(Props.OUTER, outer);
    }
}
