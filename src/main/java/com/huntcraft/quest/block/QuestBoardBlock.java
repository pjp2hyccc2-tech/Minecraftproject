package com.huntcraft.quest.block;

import com.huntcraft.quest.QuestBoardService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** クエストボード: standing notice board; opens the quest board GUI (place it in the gathering hall). */
public class QuestBoardBlock extends SupplyBoxBlock {
    private static final VoxelShape NS = Shapes.or(Block.box(0, 4, 7, 16, 16, 9), Block.box(1, 0, 7, 3, 4, 9), Block.box(13, 0, 7, 15, 4, 9));
    private static final VoxelShape EW = Shapes.or(Block.box(7, 4, 0, 9, 16, 16), Block.box(7, 0, 1, 9, 4, 3), Block.box(7, 0, 13, 9, 4, 15));

    public QuestBoardBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction d = state.getValue(FACING);
        return d.getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) QuestBoardService.open(sp);
        return InteractionResult.CONSUME;
    }
}
