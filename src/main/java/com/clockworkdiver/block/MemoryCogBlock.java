package com.clockworkdiver.block;

import com.clockworkdiver.interior.DiveManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 記憶の欠片: a glowing cog holding a piece of the owner's memories that a Time Eater nibbled off. Walk into it
 * (or use it) to take it back.
 */
public class MemoryCogBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 2, 3, 13, 14, 13);

    public MemoryCogBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) DiveManager.onFragment(sp, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) DiveManager.onFragment(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.5 + random.nextDouble() * 0.6,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.02, 0.0);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 1.5, -0.6, (random.nextDouble() - 0.5) * 1.5);
        }
    }
}
