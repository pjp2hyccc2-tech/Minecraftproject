package com.nihonisshu.tabi.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Map;

/** A one-block futon that works like a bed: sleep through the night and set your spawn. */
public class FutonBlock extends FacingBlock {
    public FutonBlock(Properties props, Map<Direction, VoxelShape> shapes) {
        super(props, shapes, false);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            sp.setRespawnPosition(level.dimension(), pos.above(), sp.getYRot(), true, false);
            if (level.isDay()) {
                sp.displayClientMessage(Component.translatable("message.tabi.futon_day"), true);
                return InteractionResult.CONSUME;
            }
            sp.startSleepInBed(pos).ifLeft(problem -> {
                if (problem.getMessage() != null) sp.displayClientMessage(problem.getMessage(), true);
            });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, @Nullable Entity player) {
        return true;
    }

    @Override
    public Direction getBedDirection(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(FACING);
    }

    @Override
    public void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied) {
        // single-block futon has no occupied state
    }
}
