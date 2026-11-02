package com.meikon.block;

import com.meikon.game.GameManager;
import com.meikon.registry.ModItems;
import com.meikon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 燭台: a candle stand (gold / silver / black holder). Lit with matches; the butsuma puzzle uses three of them. */
public class CandleStandBlock extends Block {
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 2);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 13, 11);

    public CandleStandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLOR, 0).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR, LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (state.getValue(LIT)) return InteractionResult.PASS;
        if (!held.is(ModItems.MATCHES.get())) {
            if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
                player.displayClientMessage(Component.translatable("message.meikon.need_fire"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        level.setBlock(pos, state.setValue(LIT, true), 3);
        level.playSound(null, pos, ModSounds.GAME_CANDLE.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
        if (!player.isCreative()) held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        if (player instanceof ServerPlayer sp) GameManager.candleLit(sp, pos, state.getValue(COLOR));
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 0, 0, 0);
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.01, 0);
            }
        }
    }
}
