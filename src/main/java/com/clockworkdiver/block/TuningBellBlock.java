package com.clockworkdiver.block;

import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tuning bell of a clock's chime comb. Four of them (one per note) stand in a tuning chamber: they light up
 * to play a melody, and the player strikes them back in the same order, on the beat.
 */
public class TuningBellBlock extends Block {
    public static final IntegerProperty NOTE = IntegerProperty.create("note", 0, 3);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 3, 14), Block.box(3, 3, 3, 13, 12, 13), Block.box(6, 12, 6, 10, 16, 10));

    public TuningBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NOTE, 0).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NOTE, LIT);
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
        if (!level.isClientSide && player instanceof ServerPlayer sp) DiveManager.onBellStruck(sp, pos, state.getValue(NOTE));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.NOTE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 1.1,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, state.getValue(NOTE) / 4.0 + 0.1, 0.0, 0.0);
            }
        }
    }
}
