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

/** A note lying on the floor. Picking it up gives the note item and opens it. */
public class LetterBlock extends ThinFacingBlock {
    public LetterBlock(Properties properties) {
        super(properties, 3, 0, 4, 13, 1, 12);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) GameManager.letterTaken(sp, pos);
        return InteractionResult.CONSUME;
    }
}
