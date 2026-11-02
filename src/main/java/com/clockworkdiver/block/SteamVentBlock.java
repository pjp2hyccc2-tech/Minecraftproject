package com.clockworkdiver.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Hisses steam. Standing next to one inside a clock refills the player's steam pressure. */
public class SteamVentBlock extends Block {
    public SteamVentBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.05,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.03, 0.08 + random.nextDouble() * 0.06,
                    (random.nextDouble() - 0.5) * 0.03);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                    0.15f, 0.6f + random.nextFloat() * 0.3f, false);
        }
    }
}
