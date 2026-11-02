package com.clockworkdiver.block;

import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The slot in the head of a giant loose screw. Turn it with the wrench on the beat. */
public class ScrewHeadBlock extends Block {
    public ScrewHeadBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!player.getMainHandItem().is(ModItems.TINKER_WRENCH.get())) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.clockworkdiver.need_wrench"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) DiveManager.onScrewTurn(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
