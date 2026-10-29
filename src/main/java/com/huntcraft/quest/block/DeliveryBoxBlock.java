package com.huntcraft.quest.block;

import com.huntcraft.quest.QuestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 納品ボックス: red box at the base camp; gather quests are completed by delivering items here. */
public class DeliveryBoxBlock extends SupplyBoxBlock {
    public DeliveryBoxBlock(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) QuestManager.deliver(sp, pos);
        return InteractionResult.CONSUME;
    }
}
