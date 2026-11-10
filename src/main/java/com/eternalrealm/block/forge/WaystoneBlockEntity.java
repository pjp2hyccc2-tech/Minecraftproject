package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The lower half of a waystone: no data, only the client's ambient life — glyphs drifting from the ground circle
 * into the crystal, sparks rising from it and a soft chime now and then when someone is near.
 */
public class WaystoneBlockEntity extends BlockEntity {
    /** Height of the crystal's centre above the lower half's floor (blocks). */
    public static final double CRYSTAL_Y = 2.55;

    public WaystoneBlockEntity(BlockPos pos, BlockState state) {
        super(ERBlocks.WAYSTONE_ENTITY.get(), pos, state);
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, WaystoneBlockEntity stone) {
        RandomSource random = level.random;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + CRYSTAL_Y;
        double cz = pos.getZ() + 0.5;
        Player near = level.getNearestPlayer(cx, cy, cz, 32.0, false);
        if (near == null) {
            return;
        }
        if (random.nextInt(3) == 0) {
            // ENCHANT particles fly from (x + dx, y + dy, z + dz) to (x, y - 1.2, z): from the ground circle into
            // the crystal
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 1.2 + random.nextDouble() * 0.5;
            level.addParticle(ParticleTypes.ENCHANT, cx, cy + 1.2, cz, Math.cos(angle) * radius,
                    -CRYSTAL_Y - 1.1 + random.nextDouble() * 0.3, Math.sin(angle) * radius);
        }
        if (random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.END_ROD, cx + (random.nextDouble() - 0.5) * 0.3, cy + 0.2,
                    cz + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
        }
        if (random.nextInt(160) == 0 && near.distanceToSqr(cx, cy, cz) < 14.0 * 14.0) {
            level.playLocalSound(cx, cy, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F,
                    0.55F + random.nextFloat() * 0.3F, false);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 2.0, p.getY(), p.getZ() - 2.0, p.getX() + 3.0, p.getY() + 3.5, p.getZ() + 3.0);
    }
}
