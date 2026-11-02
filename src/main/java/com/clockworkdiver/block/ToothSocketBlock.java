package com.clockworkdiver.block;

import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An empty slot on a wheel where a gear tooth is missing. Right click it with a gear tooth. */
public class ToothSocketBlock extends Block {
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

    public ToothSocketBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(FILLED)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ModItems.GEAR_TOOTH.get())) {
            if (hand == InteractionHand.MAIN_HAND && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.clockworkdiver.need_tooth"), true);
            }
            return hand == InteractionHand.MAIN_HAND ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(FILLED, true), Block.UPDATE_ALL);
            if (!player.getAbilities().instabuild) held.shrink(1);
            level.playSound(null, pos, ModSounds.CLUNK.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 16, 0.4, 0.3, 0.4, 0.1);
            }
            if (player instanceof ServerPlayer sp) DiveManager.onSocketFilled(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FILLED) && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.WAX_ON, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.9,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.05, 0.0);
        }
    }
}
