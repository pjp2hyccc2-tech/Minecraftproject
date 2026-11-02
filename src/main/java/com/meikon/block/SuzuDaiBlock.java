package com.meikon.block;

import com.meikon.game.GameManager;
import com.meikon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 鈴台: a small pedestal holding one of the seven bells. While it holds the bell it chimes now and then. */
public class SuzuDaiBlock extends Block {
    public static final BooleanProperty HAS_BELL = BooleanProperty.create("has_bell");
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 10, 12);

    public SuzuDaiBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HAS_BELL, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_BELL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(HAS_BELL)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        level.setBlock(pos, state.setValue(HAS_BELL, false), 3);
        if (player instanceof ServerPlayer sp) GameManager.bellTaken(sp, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(HAS_BELL)) return;
        if (random.nextInt(3) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, ModSounds.GAME_BELL_CHIME.get(),
                    SoundSource.BLOCKS, 0.55f, 0.92f + random.nextFloat() * 0.16f, false);
        }
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.7,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.012, 0);
        }
    }
}
