package com.eternalrealm.combat.forge;

import com.eternalrealm.entity.ERBoss;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillEffect.AfflictionKind;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Applies a skill's afflictions to the targets one of its frames (or projectiles, or ground areas) damaged. Durations,
 * damage over time and debuff strengths grow with the skill level ({@code scale}); bosses are never stunned (they
 * stagger: a short strong slow) and never pulled or thrown.
 */
public final class Afflictions {
    /** Powder-snow frost overlay strength used for FREEZE (below vanilla's 140 ticks of freezing damage). */
    private static final int FROST_TICKS = 130;
    private static final int MAX_STUN_TICKS = 120;
    private static final double MAX_DEBUFF = 0.6;

    private Afflictions() {
    }

    /** Level-1 strength, pulling towards the source. */
    public static void apply(ServerPlayer source, List<CombatHits.HitTarget> targets, List<SkillEffect.Afflict> afflictions) {
        apply(source, targets, afflictions, 1.0, source.position());
    }

    /**
     * @param scale  effect scale of the skill level ({@code >= 1})
     * @param pullTo where PULL drags its targets (the caster, or the centre of a ground area)
     */
    public static void apply(ServerPlayer source, List<CombatHits.HitTarget> targets, List<SkillEffect.Afflict> afflictions,
                             double scale, Vec3 pullTo) {
        if (afflictions.isEmpty()) {
            return;
        }
        for (CombatHits.HitTarget hit : targets) {
            for (SkillEffect.Afflict affliction : afflictions) {
                apply(source, hit.entity(), hit.amount(), affliction.kind(), affliction.durationTicks(),
                        affliction.power(), scale, pullTo);
            }
        }
    }

    /** One affliction on one target that just took {@code damage}. */
    public static void apply(ServerPlayer source, LivingEntity target, float damage, AfflictionKind kind, int duration,
                             double power, double scale, Vec3 pullTo) {
        if (!target.isAlive()) {
            return;
        }
        int ticks = Math.max(1, (int) Math.round(duration * (kind.instant() ? 1.0 : scale)));
        boolean boss = target instanceof ERBoss;
        switch (kind) {
            case SLOW -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks,
                    (int) Math.round(power)), source);
            case FREEZE -> {
                target.addEffect(new MobEffectInstance(ERMobEffects.FROZEN.get(), ticks, 1), source);
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), FROST_TICKS));
                source.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, target.getX(),
                        target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            }
            case BURN -> target.setSecondsOnFire(Math.max(1, ticks / 20));
            case BLEED -> ERMobEffects.applyBleed(target, source, (float) (damage * power * scale), ticks);
            case BLIND -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0), source);
            case STUN -> {
                if (boss) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.max(10, ticks / 2), 3),
                            source);
                } else {
                    target.addEffect(new MobEffectInstance(ERMobEffects.STUNNED.get(), Math.min(MAX_STUN_TICKS, ticks),
                            0, false, true, true), source);
                    source.serverLevel().sendParticles(ParticleTypes.WAX_OFF, target.getX(),
                            target.getY() + target.getBbHeight() + 0.3, target.getZ(), 6, 0.3, 0.1, 0.3, 0.0);
                }
            }
            case POISON -> ERMobEffects.applyPoison(target, source, (float) (damage * power * scale), ticks);
            case WEAKEN -> target.addEffect(new MobEffectInstance(ERMobEffects.WEAKENED.get(), ticks,
                    ERMobEffects.amplifierFor(Math.min(MAX_DEBUFF, power * scale))), source);
            case VULNERABLE -> target.addEffect(new MobEffectInstance(ERMobEffects.VULNERABLE.get(), ticks,
                    ERMobEffects.amplifierFor(Math.min(MAX_DEBUFF, power * scale))), source);
            case PULL -> {
                if (!boss) {
                    Vec3 toward = pullTo.subtract(target.position());
                    double distance = Math.sqrt(toward.x * toward.x + toward.z * toward.z);
                    if (distance > 0.8) {
                        double speed = Math.min(power, distance * 0.35);
                        target.setDeltaMovement(toward.x / distance * speed, Math.max(0.15, target.getDeltaMovement().y),
                                toward.z / distance * speed);
                        target.hurtMarked = true;
                    }
                }
            }
            case LAUNCH -> {
                if (!boss) {
                    Vec3 motion = target.getDeltaMovement();
                    target.setDeltaMovement(motion.x * 0.3, power, motion.z * 0.3);
                    target.hurtMarked = true;
                }
            }
        }
    }
}
