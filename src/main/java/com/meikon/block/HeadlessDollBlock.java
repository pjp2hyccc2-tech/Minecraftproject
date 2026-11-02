package com.meikon.block;

import com.meikon.game.GameManager;
import com.meikon.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** The headless doll of the doll room. Give her back her head. */
public class HeadlessDollBlock extends FacingBlock {
    public static final BooleanProperty HEAD = BooleanProperty.create("head");

    public HeadlessDollBlock(Properties properties) {
        super(properties, 4, 0, 4, 12, 14, 12);
        registerDefaultState(defaultBlockState().setValue(HEAD, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HEAD);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(HEAD) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!held.is(ModItems.DOLL_HEAD.get())) {
            player.displayClientMessage(Component.translatable("message.meikon.doll_headless"), true);
            return InteractionResult.CONSUME;
        }
        if (!player.isCreative()) held.shrink(1);
        level.setBlock(pos, state.setValue(HEAD, true), 3);
        if (player instanceof ServerPlayer sp) GameManager.dollHeadPlaced(sp, pos);
        return InteractionResult.CONSUME;
    }
}
