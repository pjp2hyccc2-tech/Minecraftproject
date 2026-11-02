package com.sounan.block;

import com.sounan.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/** The chained iron gate of the old tunnel: locked until the hut key is used on it. */
public class LockedGateBlock extends SounanDoorBlock {
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public LockedGateBlock(Properties properties, Supplier<SoundEvent> sound) {
        super(properties, BlockSetType.IRON, sound, 12.0f);
        registerDefaultState(defaultBlockState().setValue(LOCKED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOCKED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : s.setValue(LOCKED, false);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(LOCKED)) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
                GameManager.interact(sp, lower, "gate");
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    protected boolean canPlayerOpen(BlockState state, Level level, BlockPos pos, Player player) {
        return !state.getValue(LOCKED);
    }

    /** Unlocks both halves. */
    public static void unlock(Level level, BlockPos lower) {
        BlockState lo = level.getBlockState(lower);
        BlockState hi = level.getBlockState(lower.above());
        if (lo.getBlock() instanceof LockedGateBlock) level.setBlock(lower, lo.setValue(LOCKED, false), 3);
        if (hi.getBlock() instanceof LockedGateBlock) level.setBlock(lower.above(), hi.setValue(LOCKED, false), 3);
    }
}
