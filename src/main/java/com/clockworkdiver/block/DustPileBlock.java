package com.clockworkdiver.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A heap of grey dust and moth silk choking a gear. Too soft to smash: blow it away with a Steam Burst. */
public class DustPileBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);

    public DustPileBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), pos.getX() + random.nextDouble(),
                    pos.getY() + 0.6 + random.nextDouble() * 0.6, pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + random.nextDouble(), pos.getY() + 0.7, pos.getZ() + random.nextDouble(),
                    0.0, 0.02, 0.0);
        }
    }
}
