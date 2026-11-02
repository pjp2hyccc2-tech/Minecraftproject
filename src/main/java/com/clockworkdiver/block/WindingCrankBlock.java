package com.clockworkdiver.block;

import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The winding arbor of a run-down spring. Crank it with the wrench. */
public class WindingCrankBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);

    public WindingCrankBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!player.getMainHandItem().is(ModItems.TINKER_WRENCH.get())) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.clockworkdiver.need_wrench"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) DiveManager.onCrankWind(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
