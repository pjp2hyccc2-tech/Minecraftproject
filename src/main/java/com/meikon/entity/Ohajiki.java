package com.meikon.entity;

import com.meikon.game.NoiseSystem;
import com.meikon.registry.ModEntities;
import com.meikon.registry.ModItems;
import com.meikon.registry.ModSounds;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** A thrown glass marble: it clatters loudly where it lands. */
public class Ohajiki extends ThrowableItemProjectile {
    public Ohajiki(EntityType<? extends Ohajiki> type, Level level) {
        super(type, level);
    }

    public Ohajiki(Level level, LivingEntity owner) {
        super(ModEntities.OHAJIKI.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.OHAJIKI.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), ModSounds.GAME_MARBLE.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.05);
            NoiseSystem.emit(level, position(), 22.0f, NoiseSystem.Source.MARBLE);
            discard();
        }
    }
}
