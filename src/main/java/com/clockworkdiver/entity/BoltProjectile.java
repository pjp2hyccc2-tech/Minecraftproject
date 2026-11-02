package com.clockworkdiver.entity;

import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.registry.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A brass bolt thrown by cog gremlins. */
public class BoltProjectile extends ThrowableItemProjectile {
    public BoltProjectile(EntityType<? extends BoltProjectile> type, Level level) {
        super(type, level);
    }

    public BoltProjectile(Level level, LivingEntity shooter) {
        super(ModEntities.BOLT.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.BOLT.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        if (target instanceof Player) {
            target.hurt(damageSources().thrown(this, getOwner()), 3.0f);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            level().broadcastEntityEvent(this, (byte) 3);
            discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(getDefaultItem()));
            for (int i = 0; i < 6; i++) {
                level().addParticle(particle, getX(), getY(), getZ(), (random.nextDouble() - 0.5) * 0.1, 0.1, (random.nextDouble() - 0.5) * 0.1);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }
}
