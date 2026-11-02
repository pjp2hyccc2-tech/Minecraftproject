package com.clockworkdiver.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A copper steam pipe that can run along any axis. */
public class CopperPipeBlock extends RotatedPillarBlock {
    private static final VoxelShape SHAPE_Y = Block.box(4, 0, 4, 12, 16, 12);
    private static final VoxelShape SHAPE_X = Block.box(0, 4, 4, 16, 12, 12);
    private static final VoxelShape SHAPE_Z = Block.box(4, 4, 0, 12, 12, 16);

    public CopperPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Z -> SHAPE_Z;
            default -> SHAPE_Y;
        };
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // A rare leaking joint puffs a little steam.
        if (random.nextInt(60) == 0) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                    pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.4, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                    0.0, 0.02, 0.0);
        }
    }
}
