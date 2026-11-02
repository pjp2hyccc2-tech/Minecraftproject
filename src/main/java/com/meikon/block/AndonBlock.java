package com.meikon.block;

import com.meikon.registry.ModItems;
import com.meikon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 行灯: a paper floor lamp. The director blows them out; matches relight them. */
public class AndonBlock extends Block {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);

    public AndonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (state.getValue(LIT) || !held.is(ModItems.MATCHES.get())) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        level.setBlock(pos, state.setValue(LIT, true), 3);
        level.playSound(null, pos, ModSounds.GAME_CANDLE.get(), SoundSource.BLOCKS, 0.8f, 0.9f);
        if (!player.isCreative()) held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        return InteractionResult.CONSUME;
    }
}
