package com.clockworkdiver.block;

import com.clockworkdiver.interior.DiveManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A crusty lump of rust that seizes a gear. Smash it with the wrench; rust mites crawl out of it. */
public class RustClusterBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 13, 15);

    public RustClusterBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), pos.getX() + random.nextDouble(),
                    pos.getY() + 0.9, pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
        }
        if (random.nextInt(3) == 0) {
            // A faint orange shimmer marks the clusters as repair targets.
            level.addParticle(ParticleTypes.WAX_ON, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.6 + random.nextDouble() * 0.5,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.03, 0.0);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            DiveManager.onRustBroken(sp, pos);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
