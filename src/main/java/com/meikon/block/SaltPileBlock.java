package com.meikon.block;

import com.meikon.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 盛り塩: purifying salt the bride will not pass. It loses its power after a minute. */
public class SaltPileBlock extends Block {
    public static final int LIFETIME = 20 * 60;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 6, 12);

    public SaltPileBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, LIFETIME);
            GameManager.saltPlaced((ServerLevel) level, pos);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 20, 0.3, 0.1, 0.3, 0.01);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + random.nextDouble(), pos.getY() + 0.4, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }
}
