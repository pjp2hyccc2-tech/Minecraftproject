package com.eternalrealm.job.forge;

import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.DamageCalculator;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.forge.Telegraphs;
import com.eternalrealm.item.WeaponArt;
import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.combat.forge.ERMobEffects;
import com.eternalrealm.combat.forge.ERParticles;
import com.eternalrealm.combat.forge.PvpRules;
import com.eternalrealm.job.SkillDefinition;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillTheme;
import com.eternalrealm.server.ServerContext;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes the non-damage effects of job skills on the server: heals, buffs, shields, taunts, aggro drops, cleansing,
 * siphons, ground areas, stamina and cooldown effects and the visuals. Strengths grow with the skill level
 * ({@link SkillDefinition#effectScale}). Dashes and leaps move the caster on the client (movement is
 * client-authoritative), so they are ignored here; afflictions and executes belong to the damage path.
 */
public final class SkillEffectsExecutor {
    private SkillEffectsExecutor() {
    }

    /** Level-1 effects without a skill context (tests, scripted callers): ground areas need a skill and are skipped. */
    public static void apply(ServerPlayer caster, CombatStats stats, List<SkillEffect> effects,
                             List<CombatHits.HitTarget> targets) {
        apply(caster, stats, null, 1, null, 1.0, effects, targets);
    }

    /**
     * @param skill       the skill, or {@code null} without a skill context
     * @param level       its learned level (1..max)
     * @param weapon      the weapon of the hit (ground areas deal damage with it), or {@code null}
     * @param damageScale damage scale of the hit (skill level and passive skill power)
     * @param targets     entities the frame damaged (for visuals placed on targets and siphons), empty for start and
     *                    effect frames
     */
    public static void apply(ServerPlayer caster, CombatStats stats, SkillDefinition skill, int level,
                             WeaponProfile weapon, double damageScale, List<SkillEffect> effects,
                             List<CombatHits.HitTarget> targets) {
        double scale = skill == null ? 1.0 : skill.effectScale(Math.max(1, Math.min(level, skill.maxLevel())));
        SkillTheme theme = skill == null ? SkillTheme.STEEL : skill.theme();
        run(caster, stats, theme, skill == null ? null : skill.damageType(), scale, weapon, damageScale, effects,
                targets);
    }

    /**
     * The effects of a boss weapon's art ({@link WeaponArt}) at strength 1: ground areas deal the weapon's damage type
     * in the art's colours.
     */
    public static void applyArt(ServerPlayer caster, CombatStats stats, WeaponArt art, WeaponProfile weapon,
                                double damageScale, List<SkillEffect> effects, List<CombatHits.HitTarget> targets) {
        run(caster, stats, art.theme(), art.type().damageType(), 1.0, weapon, damageScale, effects, targets);
    }

    /**
     * @param type damage type of ground areas, or {@code null} without a skill context (areas are skipped)
     */
    private static void run(ServerPlayer caster, CombatStats stats, SkillTheme theme, DamageType type, double scale,
                            WeaponProfile weapon, double damageScale, List<SkillEffect> effects,
                            List<CombatHits.HitTarget> targets) {
        for (SkillEffect effect : effects) {
            if (effect instanceof SkillEffect.Heal heal) {
                heal(caster, stats, heal, scale);
            } else if (effect instanceof SkillEffect.Buff buff) {
                buff(caster, buff, scale);
            } else if (effect instanceof SkillEffect.Shield shield) {
                for (ServerPlayer ally : allies(caster, shield.radius())) {
                    ally.setAbsorptionAmount(Math.max(ally.getAbsorptionAmount(),
                            (float) (ally.getMaxHealth() * shield.fraction() * scale)));
                    ally.addEffect(new MobEffectInstance(ERMobEffects.GUARD_UP.get(), shield.durationTicks(), 0, false,
                            true, true), caster);
                }
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
            } else if (effect instanceof SkillEffect.Cleanse cleanse) {
                cleanse(caster, cleanse.radius());
            } else if (effect instanceof SkillEffect.Siphon siphon) {
                CombatHits.siphon(caster, targets, List.of(siphon));
            } else if (effect instanceof SkillEffect.Zone zone) {
                if (type != null && weapon != null) {
                    SkillZones.create(caster, stats, weapon, type, theme, damageScale, scale, zone);
                }
            } else if (effect instanceof SkillEffect.Mark mark) {
                Vec3 at = SkillZones.centerOf(caster, mark.placement(), mark.distance());
                Telegraphs.show(caster.serverLevel(), Telegraph.circle(Telegraph.Style.ALLY, at.x, at.y, at.z,
                        mark.radius(), mark.ticks()));
            } else if (effect instanceof SkillEffect.Revive revive) {
                revive(caster, revive, scale);
            } else if (effect instanceof SkillEffect.RestoreStamina restore) {
                ServerContext.current().ifPresent(context -> context.combat().restoreStamina(caster,
                        restore.amount()));
            } else if (effect instanceof SkillEffect.CooldownCut cut) {
                ServerContext.current().ifPresent(context -> context.combat().cutCooldowns(caster, cut.ticks()));
            } else if (effect instanceof SkillEffect.Visual visual) {
                visual(caster, theme, visual.kind(), targets);
            }
        }
    }

    /**
     * Raises fallen (戦闘不能) party members around the caster where they lie (the revival module decides who is
     * fallen); each gets the effect's share of their health, at most all of it.
     */
    private static void revive(ServerPlayer caster, SkillEffect.Revive revive, double scale) {
        double fraction = Math.min(1.0, revive.fraction() * scale);
        for (ServerPlayer fallen : com.eternalrealm.revival.forge.RevivalService.fallenNear(caster.serverLevel(),
                caster.position(), revive.radius())) {
            if (fallen == caster || PvpRules.canHurt(caster, fallen)) {
                continue;
            }
            if (com.eternalrealm.revival.forge.RevivalService.resurrect(caster, fallen, fraction)) {
                burst(caster.serverLevel(), ERParticles.HOLY.get(), fallen.getX(), fallen.getY() + 1.0, fallen.getZ(),
                        40, 1.2, com.eternalrealm.sound.ERSounds.SKILL_HOLY_CHIME.get());
                extra(caster.serverLevel(), ERParticles.HEAL.get(), fallen.getX(), fallen.getY() + 0.4,
                        fallen.getZ(), 24, 0.8);
            }
        }
    }

    /** Afflictions of the frame (applied to what it damaged). */
    public static List<SkillEffect.Afflict> afflictions(List<SkillEffect> effects) {
        return only(effects, SkillEffect.Afflict.class);
    }

    /** Effects of one record type, in order. */
    public static <T extends SkillEffect> List<T> only(List<SkillEffect> effects, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (SkillEffect effect : effects) {
            if (type.isInstance(effect)) {
                result.add(type.cast(effect));
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

    private static void heal(ServerPlayer caster, CombatStats stats, SkillEffect.Heal heal, double scale) {
        ServerLevel level = caster.serverLevel();
        for (ServerPlayer ally : allies(caster, heal.radius())) {
            float amount = (float) DamageCalculator.healing(stats, ally.getMaxHealth(),
                    Math.min(1.0, heal.fraction() * scale), heal.flat() * scale);
            ally.heal(amount);
            level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3, ally.getZ(),
                    4, 0.4, 0.2, 0.4, 0.0);
        }
    }

    private static void cleanse(ServerPlayer caster, double radius) {
        ServerLevel level = caster.serverLevel();
        for (ServerPlayer ally : allies(caster, radius)) {
            for (MobEffectInstance instance : new ArrayList<>(ally.getActiveEffects())) {
                if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                    ally.removeEffect(instance.getEffect());
                }
            }
            ally.clearFire();
            ally.setTicksFrozen(0);
            level.sendParticles(ERParticles.HOLY.get(), ally.getX(), ally.getY() + 1.0, ally.getZ(), 10, 0.4, 0.6, 0.4,
                    0.02);
        }
    }

    private static void buff(ServerPlayer caster, SkillEffect.Buff buff, double scale) {
        List<ServerPlayer> targets = buff.target() == SkillEffect.BuffTarget.SELF ? List.of(caster)
                : allies(caster, buff.radius());
        double magnitude = buff.kind().fractional() ? buff.magnitude() * scale : buff.magnitude();
        for (ServerPlayer target : targets) {
            MobEffect effect;
            int amplifier;
            switch (buff.kind()) {
                case ATTACK_UP -> {
                    effect = ERMobEffects.ATTACK_UP.get();
                    amplifier = ERMobEffects.amplifierFor(magnitude);
                }
                case DEFENSE_UP -> {
                    effect = ERMobEffects.DEFENSE_UP.get();
                    amplifier = ERMobEffects.amplifierFor(Math.min(0.9, magnitude));
                }
                case SPEED -> {
                    effect = ERMobEffects.HASTE.get();
                    amplifier = (int) Math.round(magnitude);
                }
                case REGENERATION -> {
                    effect = MobEffects.REGENERATION;
                    amplifier = (int) Math.round(magnitude);
                }
                case INVISIBILITY -> {
                    effect = MobEffects.INVISIBILITY;
                    amplifier = 0;
                }
                case FLAME_WEAPON -> {
                    effect = ERMobEffects.FLAME_WEAPON.get();
                    amplifier = 0;
                }
                case CRIT_UP -> {
                    effect = ERMobEffects.CRIT_UP.get();
                    amplifier = ERMobEffects.amplifierFor(magnitude);
                }
                case LIFESTEAL -> {
                    effect = ERMobEffects.LIFESTEAL.get();
                    amplifier = ERMobEffects.amplifierFor(Math.min(0.5, magnitude));
                }
                case FROST_WEAPON -> {
                    effect = ERMobEffects.FROST_WEAPON.get();
                    amplifier = 0;
                }
                case VENOM_WEAPON -> {
                    effect = ERMobEffects.VENOM_WEAPON.get();
                    amplifier = 0;
                }
                case THUNDER_WEAPON -> {
                    effect = ERMobEffects.THUNDER_WEAPON.get();
                    amplifier = 0;
                }
                case EVASION -> {
                    effect = ERMobEffects.EVASION.get();
                    amplifier = ERMobEffects.amplifierFor(Math.min(0.6, magnitude));
                }
                case THORNS -> {
                    effect = ERMobEffects.THORNS.get();
                    amplifier = ERMobEffects.amplifierFor(magnitude);
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

    /** The named effects of the original skills, else the generic themed kinds of {@link SkillVisuals}. */
    private static void visual(ServerPlayer caster, SkillTheme theme, String kind, List<CombatHits.HitTarget> targets) {
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
            case "heal", "blessing", "sanctuary" -> {
                burst(level, ERParticles.HEAL.get(), x, y, z, 30, 2.5, com.eternalrealm.sound.ERSounds.SKILL_HEAL.get());
                extra(level, ERParticles.HOLY.get(), x, y + 0.6, z, 16, 1.5);
            }
            case "war_cry" -> {
                burst(level, ParticleTypes.ANGRY_VILLAGER, x, y + 0.8, z, 8, 1.0,
                        com.eternalrealm.sound.ERSounds.SKILL_BUFF.get());
                extra(level, ERParticles.SPARK.get(), x, y + 0.4, z, 24, 2.0);
            }
            case "iron_wall" -> {
                burst(level, ERParticles.HOLY.get(), x, y, z, 30, 1.2, com.eternalrealm.sound.ERSounds.SKILL_HOLY_CHIME.get());
                extra(level, ParticleTypes.ENCHANTED_HIT, x, y, z, 12, 1.0);
            }
            case "smoke" -> {
                burst(level, ParticleTypes.LARGE_SMOKE, x, y, z, 50, 2.0,
                        com.eternalrealm.sound.ERSounds.SKILL_SHADOW_WHOOSH.get());
                extra(level, ERParticles.SHADOW.get(), x, y, z, 30, 1.8);
            }
            case "shadow" -> burst(level, ERParticles.SHADOW.get(), x, y, z, 28, 0.6,
                    com.eternalrealm.sound.ERSounds.SKILL_SHADOW_WHOOSH.get());
            case "flame_blade" -> {
                burst(level, ERParticles.EMBER.get(), x, y, z, 30, 0.8, com.eternalrealm.sound.ERSounds.SKILL_FIRE_CAST.get());
                extra(level, ParticleTypes.FLAME, x, y, z, 10, 0.6);
            }
            case "judgment", "slam_small" -> burst(level, ParticleTypes.EXPLOSION, x, caster.getY() + 0.2, z,
                    "judgment".equals(kind) ? 12 : 3, "judgment".equals(kind) ? 3.0 : 1.0,
                    com.eternalrealm.sound.ERSounds.SKILL_FIRE_EXPLOSION.get());
            case "judgment_charge", "meteor_cast" -> burst(level, ERParticles.RUNE.get(), x, y, z, 40, 1.2,
                    com.eternalrealm.sound.ERSounds.SKILL_HOLY_CHIME.get());
            case "arrow_rain" -> burst(level, ParticleTypes.CRIT, x, y + 2.0, z, 10, 1.0,
                    com.eternalrealm.sound.ERSounds.SKILL_ARROW_VOLLEY.get());
            case "blade_storm" -> {
                burst(level, ERParticles.SLASH.get(), x, y, z, 14, 2.0, com.eternalrealm.sound.ERSounds.SKILL_HEAVY_SLASH.get());
                extra(level, ParticleTypes.SWEEP_ATTACK, x, y, z, 6, 2.0);
            }
            case "rift", "arcane_slash" -> {
                burst(level, ERParticles.RUNE.get(), x, y, z, 30, 1.5, com.eternalrealm.sound.ERSounds.SKILL_MAGIC_BOLT.get());
                extra(level, ParticleTypes.REVERSE_PORTAL, x, y, z, 20, 1.5);
            }
            default -> {
                if (com.eternalrealm.item.forge.ArtVisuals.play(caster, kind, targets)) {
                    return;
                }
                if (!SkillVisuals.play(caster, theme, kind, targets)) {
                    burst(level, ERParticles.RUNE.get(), x, y, z, 20, 1.0,
                            com.eternalrealm.sound.ERSounds.SKILL_MAGIC_BOLT.get());
                }
            }
        }
    }

    /** More particles without a sound. */
    private static void extra(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count,
                              double spread) {
        level.sendParticles(particle, x, y, z, count, spread, spread * 0.5, spread, 0.03);
    }

    private static void burst(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count,
                              double spread, SoundEvent sound) {
        level.sendParticles(particle, x, y, z, count, spread, spread * 0.5, spread, 0.05);
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, 0.8F, 1.0F);
    }
}
