package com.eternalrealm.job.forge;

import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.DamageCalculator;
import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.combat.forge.ERMobEffects;
import com.eternalrealm.combat.forge.PvpRules;
import com.eternalrealm.job.SkillEffect;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes the non-damage effects of job skills on the server: heals, buffs, shields, taunts, aggro drops and visual
 * hints. Dashes move the caster on the client (movement is client-authoritative), so they are ignored here.
 */
public final class SkillEffectsExecutor {
    private SkillEffectsExecutor() {
    }

    /**
     * @param targets entities the frame damaged (for visuals placed on targets), empty for start/effect frames
     */
    public static void apply(ServerPlayer caster, CombatStats stats, List<SkillEffect> effects,
                             List<CombatHits.HitTarget> targets) {
        for (SkillEffect effect : effects) {
            if (effect instanceof SkillEffect.Heal heal) {
                heal(caster, stats, heal);
            } else if (effect instanceof SkillEffect.Buff buff) {
                buff(caster, buff);
            } else if (effect instanceof SkillEffect.Shield shield) {
                caster.setAbsorptionAmount(Math.max(caster.getAbsorptionAmount(),
                        (float) (caster.getMaxHealth() * shield.fraction())));
            } else if (effect instanceof SkillEffect.Taunt taunt) {
                for (Mob mob : hostiles(caster, taunt.radius())) {
                    mob.setTarget(caster);
                }
            } else if (effect instanceof SkillEffect.DropAggro drop) {
                for (Mob mob : hostiles(caster, drop.radius())) {
                    if (mob.getTarget() == caster) {
                        mob.setTarget(null);
                    }
                }
            } else if (effect instanceof SkillEffect.Visual visual) {
                visual(caster, visual.kind(), targets);
            }
        }
    }

    /** Afflictions of the frame (applied to what it damaged). */
    public static List<SkillEffect.Afflict> afflictions(List<SkillEffect> effects) {
        List<SkillEffect.Afflict> result = new ArrayList<>();
        for (SkillEffect effect : effects) {
            if (effect instanceof SkillEffect.Afflict afflict) {
                result.add(afflict);
            }
        }
        return result;
    }

    /** The caster and every other player within {@code radius} that the caster could not attack. */
    public static List<ServerPlayer> allies(ServerPlayer caster, double radius) {
        List<ServerPlayer> allies = new ArrayList<>();
        allies.add(caster);
        if (radius <= 0.0) {
            return allies;
        }
        for (ServerPlayer other : caster.serverLevel().players()) {
            if (other != caster && other.isAlive() && !other.isSpectator()
                    && other.distanceToSqr(caster) <= radius * radius && !PvpRules.canHurt(caster, other)) {
                allies.add(other);
            }
        }
        return allies;
    }

    private static void heal(ServerPlayer caster, CombatStats stats, SkillEffect.Heal heal) {
        ServerLevel level = caster.serverLevel();
        for (ServerPlayer ally : allies(caster, heal.radius())) {
            float amount = (float) DamageCalculator.healing(stats, ally.getMaxHealth(), heal.fraction(), heal.flat());
            ally.heal(amount);
            level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3, ally.getZ(),
                    4, 0.4, 0.2, 0.4, 0.0);
        }
    }

    private static void buff(ServerPlayer caster, SkillEffect.Buff buff) {
        List<ServerPlayer> targets = buff.target() == SkillEffect.BuffTarget.SELF ? List.of(caster)
                : allies(caster, buff.radius());
        for (ServerPlayer target : targets) {
            MobEffect effect;
            int amplifier;
            switch (buff.kind()) {
                case ATTACK_UP -> {
                    effect = ERMobEffects.ATTACK_UP.get();
                    amplifier = ERMobEffects.amplifierFor(buff.magnitude());
                }
                case DEFENSE_UP -> {
                    effect = ERMobEffects.DEFENSE_UP.get();
                    amplifier = ERMobEffects.amplifierFor(buff.magnitude());
                }
                case SPEED -> {
                    effect = MobEffects.MOVEMENT_SPEED;
                    amplifier = (int) Math.round(buff.magnitude());
                }
                case REGENERATION -> {
                    effect = MobEffects.REGENERATION;
                    amplifier = (int) Math.round(buff.magnitude());
                }
                case INVISIBILITY -> {
                    effect = MobEffects.INVISIBILITY;
                    amplifier = 0;
                }
                case FLAME_WEAPON -> {
                    effect = ERMobEffects.FLAME_WEAPON.get();
                    amplifier = 0;
                }
                default -> {
                    continue;
                }
            }
            target.addEffect(new MobEffectInstance(effect, buff.durationTicks(), amplifier, false, true, true), caster);
        }
    }

    private static List<Mob> hostiles(ServerPlayer caster, double radius) {
        AABB area = caster.getBoundingBox().inflate(radius);
        return caster.serverLevel().getEntitiesOfClass(Mob.class, area,
                mob -> mob.isAlive() && mob instanceof Enemy && mob.distanceToSqr(caster) <= radius * radius);
    }

    private static void visual(ServerPlayer caster, String kind, List<CombatHits.HitTarget> targets) {
        ServerLevel level = caster.serverLevel();
        double x = caster.getX();
        double y = caster.getY() + 1.0;
        double z = caster.getZ();
        switch (kind) {
            case "lightning" -> {
                for (CombatHits.HitTarget target : targets) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        LivingEntity entity = target.entity();
                        bolt.moveTo(entity.getX(), entity.getY(), entity.getZ());
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                    }
                }
            }
            case "heal", "blessing", "sanctuary" -> burst(level, ParticleTypes.HAPPY_VILLAGER, x, y, z, 30, 2.5,
                    SoundEvents.AMETHYST_BLOCK_CHIME, caster);
            case "war_cry" -> burst(level, ParticleTypes.ANGRY_VILLAGER, x, y + 0.8, z, 8, 1.0,
                    SoundEvents.RAVAGER_ROAR, caster);
            case "iron_wall" -> burst(level, ParticleTypes.ENCHANTED_HIT, x, y, z, 30, 1.2,
                    SoundEvents.ANVIL_PLACE, caster);
            case "smoke" -> burst(level, ParticleTypes.LARGE_SMOKE, x, y, z, 60, 2.0,
                    SoundEvents.FIRE_EXTINGUISH, caster);
            case "shadow" -> burst(level, ParticleTypes.SQUID_INK, x, y, z, 20, 0.6,
                    SoundEvents.ENDERMAN_TELEPORT, caster);
            case "flame_blade" -> burst(level, ParticleTypes.FLAME, x, y, z, 30, 0.8,
                    SoundEvents.FIRECHARGE_USE, caster);
            case "judgment", "slam_small" -> burst(level, ParticleTypes.EXPLOSION, x, caster.getY() + 0.2, z,
                    "judgment".equals(kind) ? 12 : 3, "judgment".equals(kind) ? 3.0 : 1.0,
                    SoundEvents.GENERIC_EXPLODE, caster);
            case "judgment_charge", "meteor_cast" -> burst(level, ParticleTypes.ENCHANT, x, y, z, 50, 1.2,
                    SoundEvents.BEACON_POWER_SELECT, caster);
            case "arrow_rain" -> burst(level, ParticleTypes.CRIT, x, y + 2.0, z, 10, 1.0,
                    SoundEvents.ARROW_SHOOT, caster);
            case "blade_storm" -> burst(level, ParticleTypes.SWEEP_ATTACK, x, y, z, 12, 2.0,
                    SoundEvents.PLAYER_ATTACK_SWEEP, caster);
            case "rift", "arcane_slash" -> burst(level, ParticleTypes.REVERSE_PORTAL, x, y, z, 40, 1.5,
                    SoundEvents.ILLUSIONER_CAST_SPELL, caster);
            default -> burst(level, ParticleTypes.ENCHANT, x, y, z, 20, 1.0, SoundEvents.EVOKER_CAST_SPELL, caster);
        }
    }

    private static void burst(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count,
                              double spread, SoundEvent sound, ServerPlayer caster) {
        level.sendParticles(particle, x, y, z, count, spread, spread * 0.5, spread, 0.05);
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, 0.8F, 1.0F);
    }
}
