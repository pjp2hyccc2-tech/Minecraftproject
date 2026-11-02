package com.clockworkdiver.block;

import com.clockworkdiver.workshop.WorkshopManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

/** The iron-bound gate of the Great Clock tower. In the last chapter it leads to the Great Clock's inspection hatch. */
public class TowerGateBlock extends Block {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public TowerGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) WorkshopManager.useTowerGate(sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
