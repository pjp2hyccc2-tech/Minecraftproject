package com.huntcraft.block;

import com.huntcraft.core.CameraShake;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Large barrel bomb: explodes when struck, shot or burnt. Huge fixed damage to monsters, no terrain damage. */
public class BarrelBombBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);
    private final float monsterDamage;
    private final float radius;

    public BarrelBombBlock(Properties props, float monsterDamage, float radius) {
        super(props);
        this.monsterDamage = monsterDamage;
        this.radius = radius;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        detonate(level, pos, player);
    }

    @Override
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        detonate(level, hit.getBlockPos(), projectile.getOwner() instanceof Player p ? p : null);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        detonate(level, pos, null);
    }

    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable net.minecraft.core.Direction face, @Nullable LivingEntity igniter) {
        detonate(level, pos, igniter instanceof Player p ? p : null);
    }

    public void detonate(Level level, BlockPos pos, @Nullable Player player) {
        if (level.isClientSide || !(level instanceof ServerLevel sl)) return;
        level.removeBlock(pos, false);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.FLAME, x, y, z, 60, radius * 0.4, radius * 0.3, radius * 0.4, 0.08);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1, z, 30, radius * 0.4, radius * 0.4, radius * 0.4, 0.04);
        sl.playSound(null, x, y, z, HCSounds.EXPLOSION_SMALL.get(), SoundSource.BLOCKS, 4.0F, 0.7F);
        CameraShake.shake(sl, new net.minecraft.world.phys.Vec3(x, y, z), radius * 5, 1.0F, 14);
        AABB box = new AABB(pos).inflate(radius);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box)) {
            double d = e.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
            if (d > radius + e.getBbWidth()) continue;
            e.invulnerableTime = 0;
            if (e instanceof HuntMonster m) {
                float dmg = monsterDamage * (m.isSleepingState() ? 2.0F : 1.0F);
                m.hurt(sl.damageSources().explosion(null, player), dmg);
                m.onHunterDamage("head", com.huntcraft.core.DamageKind.BLUNT, dmg, com.huntcraft.core.Element.FIRE, 0, player);
            } else {
                e.hurt(sl.damageSources().explosion(null, player), Math.min(12.0F, monsterDamage * 0.12F));
            }
        }
        // chain reaction
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 2, 3))) {
            if (sl.getBlockState(p).getBlock() instanceof BarrelBombBlock other && !p.equals(pos)) {
                BlockPos imm = p.immutable();
                sl.getServer().tell(new net.minecraft.server.TickTask(sl.getServer().getTickCount() + 4, () -> other.detonate(sl, imm, player)));
            }
        }
    }
}
