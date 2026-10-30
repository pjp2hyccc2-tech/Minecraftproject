package com.nihonisshu.tabi.block;

import com.nihonisshu.tabi.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/** Chairs, benches and zabuton: right click to sit. */
public class SeatBlock extends FacingBlock {
    private final double seatHeight;

    public SeatBlock(Properties props, Map<Direction, VoxelShape> shapes, double seatHeight) {
        super(props, shapes, false);
        this.seatHeight = seatHeight;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() || player.isPassenger()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            SeatEntity.sit(level, pos, seatHeight, state.getValue(FACING), player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
