package com.eternalrealm.combat.forge;

import com.eternalrealm.job.SkillEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Applies a skill's afflictions to the targets one of its frames damaged. */
public final class Afflictions {
    /** Powder-snow frost overlay strength used for FREEZE (below vanilla's 140 ticks of freezing damage). */
    private static final int FROST_TICKS = 130;

    private Afflictions() {
    }

    public static void apply(ServerPlayer source, List<CombatHits.HitTarget> targets, List<SkillEffect.Afflict> afflictions) {
        if (afflictions.isEmpty()) {
            return;
        }
        for (CombatHits.HitTarget hit : targets) {
            LivingEntity target = hit.entity();
            if (!target.isAlive()) {
                continue;
            }
            for (SkillEffect.Afflict affliction : afflictions) {
                int ticks = affliction.durationTicks();
                switch (affliction.kind()) {
                    case SLOW -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks,
                            (int) Math.round(affliction.power())), source);
                    case FREEZE -> {
                        target.addEffect(new MobEffectInstance(ERMobEffects.FROZEN.get(), ticks, 1), source);
                        target.setTicksFrozen(Math.max(target.getTicksFrozen(), FROST_TICKS));
                        source.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, target.getX(),
                                target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
                    }
                    case BURN -> target.setSecondsOnFire(Math.max(1, ticks / 20));
                    case BLEED -> ERMobEffects.applyBleed(target, source,
                            (float) (hit.amount() * affliction.power()), ticks);
                    case BLIND -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0), source);
                }
            }
        }
    }
}
