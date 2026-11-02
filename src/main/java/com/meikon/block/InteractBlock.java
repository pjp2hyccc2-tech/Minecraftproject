package com.meikon.block;

import com.meikon.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A facing block whose right-click is handled by the game (well, sake table...). */
public class InteractBlock extends FacingBlock {
    private final String action;

    public InteractBlock(Properties properties, String action, double x1, double y1, double z1, double x2, double y2, double z2) {
        super(properties, x1, y1, z1, x2, y2, z2);
        this.action = action;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) GameManager.interact(sp, pos, action);
        return InteractionResult.CONSUME;
    }
}
