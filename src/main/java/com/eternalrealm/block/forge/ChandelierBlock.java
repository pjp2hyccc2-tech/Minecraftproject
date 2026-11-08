package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A gold chandelier with eight candles (hang it under a chain). Gives full light; the candle flames flicker with
 * small flame particles like vanilla candles.
 */
public class ChandelierBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
    /** Candle tips in block coordinates (see the model in tools/art/capital_blocks.py). */
    private static final double[][] CANDLES = {
            {19.0, 8.0}, {-3.0, 8.0}, {8.0, 19.0}, {8.0, -3.0},
            {15.78, 15.78}, {0.22, 0.22}, {0.22, 15.78}, {15.78, 0.22}};

    public ChandelierBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (double[] candle : CANDLES) {
            double x = pos.getX() + candle[0] / 16.0;
            double y = pos.getY() + 12.9 / 16.0;
            double z = pos.getZ() + candle[1] / 16.0;
            float f = random.nextFloat();
            if (f < 0.25F) {
                level.addParticle(ParticleTypes.SMOKE, x, y + 0.05, z, 0.0, 0.0, 0.0);
                if (f < 0.03F) {
                    level.playLocalSound(x, y, z, SoundEvents.CANDLE_AMBIENT, SoundSource.BLOCKS,
                            0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
                }
            }
            level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
