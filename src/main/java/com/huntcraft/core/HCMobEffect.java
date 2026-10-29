package com.huntcraft.core;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Generic effect used for blights / ailments / buffs. */
public class HCMobEffect extends MobEffect {
    public enum Behavior { NONE, FIREBLIGHT, POISON, LOCK }

    private final Behavior behavior;

    public HCMobEffect(MobEffectCategory category, int color, Behavior behavior) {
        super(category, color);
        this.behavior = behavior;
    }

    public Behavior getBehavior() {
        return behavior;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return;
        switch (behavior) {
            case FIREBLIGHT -> {
                entity.hurt(entity.damageSources().onFire(), 1.0F + amplifier);
                if (entity.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
                }
            }
            case POISON -> {
                entity.hurt(entity.damageSources().magic(), 1.0F + amplifier);
                if (entity.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.WITCH, entity.getX(), entity.getY() + entity.getBbHeight() * 0.7, entity.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return switch (behavior) {
            case FIREBLIGHT -> duration % 25 == 0;
            case POISON -> duration % 20 == 0;
            default -> false;
        };
    }
}
