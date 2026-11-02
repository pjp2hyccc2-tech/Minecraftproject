package com.meikon.block;

import com.meikon.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** 箪笥: a chest of drawers. Searching it is noisy and may turn up oil, salt, matches... or something worse. */
public class TansuBlock extends FacingBlock {
    public static final BooleanProperty OPENED = BooleanProperty.create("opened");

    public TansuBlock(Properties properties) {
        super(properties, 1, 0, 3, 15, 16, 16);
        registerDefaultState(defaultBlockState().setValue(OPENED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPENED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(OPENED)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        level.setBlock(pos, state.setValue(OPENED, true), 3);
        if (player instanceof ServerPlayer sp) GameManager.tansuSearched(sp, pos);
        return InteractionResult.CONSUME;
    }
}
