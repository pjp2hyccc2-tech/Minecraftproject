package com.eternalrealm.job.forge;

import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.combat.forge.ERParticles;
import com.eternalrealm.job.SkillTheme;
import com.eternalrealm.sound.ERSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Server-side particles and sounds of job skills, broadcast to everyone around. Every visual kind is drawn in the
 * colours of the skill's {@link SkillTheme}: a {@code nova} of a frost skill is a ring of snow and ice crystals, the
 * same {@code nova} of a holy skill a ring of golden light. Hit frames and ground areas use the same palettes.
 */
public final class SkillVisuals {
    private SkillVisuals() {
    }

    /** Particles and sound of one theme. */
    record Palette(ParticleOptions primary, ParticleOptions secondary, DustParticleOptions dust, SoundEvent cast,
                   SoundEvent impact) {
    }

    static Palette palette(SkillTheme theme) {
        SkillTheme t = theme == null ? SkillTheme.STEEL : theme;
        return switch (t) {
            case STEEL -> new Palette(ParticleTypes.CRIT, ERParticles.SLASH.get(), dust(0xD8DEE8, 1.0F),
                    ERSounds.SKILL_SLASH.get(), ERSounds.SKILL_HEAVY_SLASH.get());
            case FIRE -> new Palette(ParticleTypes.FLAME, ERParticles.EMBER.get(), dust(0xFF7A14, 1.3F),
                    ERSounds.SKILL_FIRE_CAST.get(), ERSounds.SKILL_FIRE_EXPLOSION.get());
            case FROST -> new Palette(ParticleTypes.SNOWFLAKE, ERParticles.FROST.get(), dust(0x8EE0FF, 1.2F),
                    ERSounds.SKILL_ICE_CAST.get(), ERSounds.SKILL_ICE_SHATTER.get());
            case THUNDER -> new Palette(ParticleTypes.ELECTRIC_SPARK, ERParticles.SPARK.get(), dust(0xF4F07A, 1.1F),
                    ERSounds.SKILL_MAGIC_BOLT.get(), ERSounds.SKILL_THUNDER.get());
            case HOLY -> new Palette(ParticleTypes.END_ROD, ERParticles.HOLY.get(), dust(0xFFE070, 1.3F),
                    ERSounds.SKILL_HOLY_CHIME.get(), ERSounds.SKILL_HOLY_CHIME.get());
            case SHADOW -> new Palette(ParticleTypes.SQUID_INK, ERParticles.SHADOW.get(), dust(0x5A2A8A, 1.3F),
                    ERSounds.SKILL_SHADOW_WHOOSH.get(), ERSounds.SKILL_SHADOW_WHOOSH.get());
            case ARCANE -> new Palette(ParticleTypes.WITCH, ERParticles.RUNE.get(), dust(0xB070FF, 1.2F),
                    ERSounds.SKILL_MAGIC_BOLT.get(), ERSounds.SKILL_MAGIC_BOLT.get());
            case VENOM -> new Palette(ParticleTypes.ITEM_SLIME, ParticleTypes.SPORE_BLOSSOM_AIR, dust(0x6ACD3A, 1.3F),
                    ERSounds.SKILL_SHADOW_WHOOSH.get(), SoundEvents.SLIME_SQUISH);
            case BLOOD -> new Palette(ParticleTypes.DAMAGE_INDICATOR, ERParticles.SLASH.get(), dust(0xB0102A, 1.2F),
                    ERSounds.SKILL_HEAVY_SLASH.get(), ERSounds.SKILL_HEAVY_SLASH.get());
            case WIND -> new Palette(ParticleTypes.CLOUD, ParticleTypes.SWEEP_ATTACK, dust(0xE6F4F0, 1.0F),
                    ERSounds.SKILL_ARROW_VOLLEY.get(), ERSounds.SKILL_SLASH.get());
            case EARTH -> new Palette(ParticleTypes.CAMPFIRE_COSY_SMOKE, ParticleTypes.CRIT, dust(0x9A7A4A, 1.4F),
                    ERSounds.SKILL_HEAVY_SLASH.get(), SoundEvents.GENERIC_EXPLODE);
            case LIFE -> new Palette(ERParticles.HEAL.get(), ParticleTypes.HAPPY_VILLAGER, dust(0x7CFC7C, 1.2F),
                    ERSounds.SKILL_HEAL.get(), ERSounds.SKILL_HEAL.get());
        };
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F), size);
    }

    /**
     * Plays a generic visual {@code kind} for {@code caster}. {@code targets} are the entities the frame damaged
     * (visuals on targets fall back to the caster or the point it looks at). @return whether the kind is generic
     */
    public static boolean play(ServerPlayer caster, SkillTheme theme, String kind, List<CombatHits.HitTarget> targets) {
        ServerLevel level = caster.serverLevel();
        Palette p = palette(theme);
        Vec3 feet = caster.position();
        double x = feet.x;
        double y = feet.y + 1.0;
        double z = feet.z;
        Vec3 look = horizontalLook(caster);
        switch (kind) {
            case "cast" -> {
                ring(level, p.secondary(), x, feet.y + 0.1, z, 1.2, 14);
                level.sendParticles(p.primary(), x, y, z, 16, 0.4, 0.7, 0.4, 0.02);
                level.sendParticles(p.dust(), x, y + 0.4, z, 12, 0.5, 0.6, 0.5, 0.0);
                sound(level, x, y, z, p.cast(), 0.8F, 1.1F);
            }
            case "nova" -> {
                for (double r = 1.0; r <= 5.0; r += 1.0) {
                    ring(level, p.primary(), x, feet.y + 0.3, z, r, (int) (r * 8));
                }
                ring(level, p.dust(), x, feet.y + 0.5, z, 3.5, 28);
                sound(level, x, y, z, p.impact(), 1.0F, 0.9F);
            }
            case "pillar" -> {
                for (Vec3 at : focus(caster, targets)) {
                    for (double h = 0.0; h <= 4.0; h += 0.5) {
                        level.sendParticles(p.primary(), at.x, at.y + h, at.z, 3, 0.15, 0.1, 0.15, 0.01);
                    }
                    level.sendParticles(p.secondary(), at.x, at.y + 1.0, at.z, 10, 0.4, 1.2, 0.4, 0.02);
                }
                sound(level, x, y, z, p.impact(), 0.9F, 1.2F);
            }
            case "aura" -> {
                for (ServerPlayer ally : SkillEffectsExecutor.allies(caster, 10.0)) {
                    spiral(level, p.secondary(), ally.getX(), ally.getY(), ally.getZ(), 0.8, 2.2, 14);
                    level.sendParticles(p.dust(), ally.getX(), ally.getY() + 1.0, ally.getZ(), 6, 0.4, 0.6, 0.4, 0.0);
                }
                sound(level, x, y, z, ERSounds.SKILL_BUFF.get(), 0.8F, 1.1F);
            }
            case "shield" -> {
                sphere(level, p.secondary(), x, y, z, 1.4, 26);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 10, 0.6, 0.6, 0.6, 0.1);
                sound(level, x, y, z, ERSounds.COMBAT_GUARD.get(), 0.9F, 1.3F);
            }
            case "smite" -> {
                for (Vec3 at : focus(caster, targets)) {
                    for (double h = 0.0; h <= 8.0; h += 0.4) {
                        level.sendParticles(p.primary(), at.x, at.y + h, at.z, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                    ring(level, p.secondary(), at.x, at.y + 0.1, at.z, 1.2, 12);
                }
                sound(level, x, y, z, p.impact(), 1.0F, 1.0F);
            }
            case "quake" -> {
                BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground(level, caster));
                for (double r = 1.0; r <= 4.0; r += 1.0) {
                    ring(level, debris, x, feet.y + 0.1, z, r, (int) (r * 7));
                }
                level.sendParticles(ParticleTypes.CLOUD, x, feet.y + 0.2, z, 12, 1.5, 0.1, 1.5, 0.02);
                sound(level, x, y, z, SoundEvents.GENERIC_EXPLODE, 0.7F, 0.6F);
            }
            case "storm" -> {
                for (Vec3 at : focus(caster, targets)) {
                    if (theme == SkillTheme.THUNDER) {
                        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                        if (bolt != null) {
                            bolt.moveTo(at.x, at.y, at.z);
                            bolt.setVisualOnly(true);
                            level.addFreshEntity(bolt);
                        }
                    }
                    level.sendParticles(p.primary(), at.x, at.y + 1.0, at.z, 14, 0.3, 0.8, 0.3, 0.1);
                }
                sound(level, x, y, z, p.impact(), 0.8F, 1.2F);
            }
            case "vortex" -> {
                Vec3 at = targets.isEmpty() ? lookPoint(caster, 12.0) : targets.get(0).entity().position();
                for (int i = 0; i < 3; i++) {
                    spiral(level, p.primary(), at.x, at.y, at.z, 2.5 - i * 0.7, 1.5, 12);
                }
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.8, at.z, 20, 1.2, 0.5, 1.2, 0.05);
                sound(level, at.x, at.y, at.z, p.cast(), 0.9F, 0.7F);
            }
            case "mist" -> {
                level.sendParticles(p.dust(), x, y, z, 40, 2.0, 0.8, 2.0, 0.0);
                level.sendParticles(ParticleTypes.CLOUD, x, y, z, 12, 1.5, 0.5, 1.5, 0.01);
                level.sendParticles(p.secondary(), x, y, z, 12, 1.8, 0.6, 1.8, 0.01);
                sound(level, x, y, z, ERSounds.SKILL_SHADOW_WHOOSH.get(), 0.7F, 1.0F);
            }
            case "blink" -> {
                for (double d = 0.0; d <= 4.0; d += 0.5) {
                    level.sendParticles(p.secondary(), x - look.x * d, y, z - look.z * d, 2, 0.15, 0.4, 0.15, 0.0);
                }
                level.sendParticles(p.primary(), x, y, z, 10, 0.3, 0.5, 0.3, 0.05);
                sound(level, x, y, z, ERSounds.SKILL_SHADOW_WHOOSH.get(), 0.8F, 1.4F);
            }
            case "burst" -> {
                for (Vec3 at : focus(caster, targets)) {
                    sphere(level, p.primary(), at.x, at.y + 1.0, at.z, 1.2, 18);
                    level.sendParticles(p.dust(), at.x, at.y + 1.0, at.z, 10, 0.5, 0.5, 0.5, 0.0);
                }
                sound(level, x, y, z, p.impact(), 0.9F, 1.0F);
            }
            case "ray" -> {
                Vec3 eye = caster.getEyePosition();
                Vec3 dir = caster.getLookAngle();
                for (double d = 1.0; d <= 14.0; d += 0.5) {
                    level.sendParticles(p.primary(), eye.x + dir.x * d, eye.y - 0.2 + dir.y * d, eye.z + dir.z * d,
                            1, 0.03, 0.03, 0.03, 0.0);
                }
                level.sendParticles(p.secondary(), eye.x + dir.x * 2, eye.y + dir.y * 2, eye.z + dir.z * 2, 6, 0.2,
                        0.2, 0.2, 0.02);
                sound(level, x, y, z, p.cast(), 0.9F, 1.3F);
            }
            case "rain" -> {
                Vec3 at = lookPoint(caster, 14.0);
                for (int i = 0; i < 24; i++) {
                    double a = i * 2.399963;
                    double r = 0.4 + (i % 6) * 0.65;
                    level.sendParticles(p.primary(), at.x + Math.cos(a) * r, at.y + 6.0 - (i % 4), at.z
                            + Math.sin(a) * r, 1, 0.0, -0.5, 0.0, 0.3);
                }
                ring(level, p.secondary(), at.x, at.y + 0.1, at.z, 3.5, 20);
                sound(level, at.x, at.y, at.z, p.cast(), 0.9F, 0.9F);
            }
            case "sigil" -> {
                ring(level, p.secondary(), x, feet.y + 0.05, z, 2.6, 26);
                ring(level, p.secondary(), x, feet.y + 0.05, z, 1.6, 16);
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2.0 * i / 6.0;
                    level.sendParticles(p.primary(), x + Math.cos(a) * 2.1, feet.y + 0.1, z + Math.sin(a) * 2.1, 2,
                            0.05, 0.05, 0.05, 0.0);
                }
                sound(level, x, y, z, ERSounds.SKILL_HOLY_CHIME.get(), 0.6F, 0.8F);
            }
            case "impact" -> {
                for (Vec3 at : focus(caster, targets)) {
                    level.sendParticles(p.primary(), at.x, at.y + 1.0, at.z, 12, 0.3, 0.3, 0.3, 0.15);
                    level.sendParticles(p.secondary(), at.x, at.y + 1.0, at.z, 2, 0.2, 0.2, 0.2, 0.0);
                }
                sound(level, x, y, z, p.impact(), 0.8F, 1.1F);
            }
            case "roar" -> {
                ring(level, p.dust(), x, y, z, 2.0, 18);
                ring(level, p.dust(), x, y, z, 3.5, 26);
                level.sendParticles(ParticleTypes.SONIC_BOOM, x + look.x, y, z + look.z, 1, 0.0, 0.0, 0.0, 0.0);
                sound(level, x, y, z, SoundEvents.RAVAGER_ROAR, 0.5F, 1.4F);
            }
            case "slash" -> {
                for (int i = -3; i <= 3; i++) {
                    double a = Math.atan2(look.z, look.x) + i * 0.28;
                    level.sendParticles(p.primary(), x + Math.cos(a) * 2.4, y, z + Math.sin(a) * 2.4, 2, 0.05, 0.1,
                            0.05, 0.0);
                }
                level.sendParticles(p.secondary(), x + look.x * 1.6, y, z + look.z * 1.6, 1, 0.1, 0.1, 0.1, 0.0);
                sound(level, x, y, z, p.cast(), 0.7F, 1.2F);
            }
            case "spin" -> {
                ring(level, p.primary(), x, y - 0.2, z, 2.8, 24);
                ring(level, p.secondary(), x, y, z, 1.8, 8);
                sound(level, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.1F);
            }
            case "thrust" -> {
                for (double d = 1.0; d <= 6.0; d += 0.5) {
                    level.sendParticles(p.primary(), x + look.x * d, y + 0.3, z + look.z * d, 1, 0.05, 0.05, 0.05, 0.0);
                }
                sound(level, x, y, z, p.cast(), 0.7F, 1.4F);
            }
            case "breath" -> {
                for (int i = 0; i < 18; i++) {
                    double spread = (i % 5 - 2) * 0.12;
                    double d = 1.0 + (i % 6) * 0.9;
                    double a = Math.atan2(look.z, look.x) + spread;
                    level.sendParticles(p.primary(), x + Math.cos(a) * d, y + 0.3, z + Math.sin(a) * d, 1,
                            Math.cos(a) * 0.3, 0.02, Math.sin(a) * 0.3, 0.2);
                }
                level.sendParticles(p.dust(), x + look.x * 3, y + 0.3, z + look.z * 3, 10, 1.0, 0.3, 1.0, 0.0);
                sound(level, x, y, z, p.cast(), 0.8F, 0.8F);
            }
            case "chains" -> {
                for (CombatHits.HitTarget hit : targets) {
                    LivingEntity target = hit.entity();
                    Vec3 from = new Vec3(x, y, z);
                    Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
                    Vec3 step = to.subtract(from).scale(1.0 / 12.0);
                    for (int i = 1; i <= 12; i++) {
                        Vec3 at = from.add(step.scale(i));
                        level.sendParticles(p.dust(), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
                sound(level, x, y, z, SoundEvents.CHAIN_PLACE, 1.0F, 0.8F);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ hit frames, zones, projectiles

    /** Themed accent on a hit frame's shape (the plain sweep/crit particles come from the combat effects). */
    public static void frame(ServerPlayer caster, SkillTheme theme, double reach, boolean circle) {
        ServerLevel level = caster.serverLevel();
        Palette p = palette(theme);
        double y = caster.getY() + caster.getBbHeight() * 0.5;
        if (circle) {
            ring(level, p.primary(), caster.getX(), y, caster.getZ(), Math.min(6.0, reach * 0.8), 16);
        } else {
            Vec3 look = horizontalLook(caster);
            level.sendParticles(p.primary(), caster.getX() + look.x * reach * 0.6, y, caster.getZ()
                    + look.z * reach * 0.6, 8, reach * 0.2, 0.2, reach * 0.2, 0.02);
        }
    }

    /** One pulse of a ground area: the outline and a scatter inside. */
    public static void zonePulse(ServerLevel level, SkillTheme theme, Vec3 center, double radius, boolean heals) {
        Palette p = palette(theme);
        ring(level, p.secondary(), center.x, center.y + 0.1, center.z, radius, (int) (radius * 7));
        level.sendParticles(heals ? ERParticles.HEAL.get() : p.primary(), center.x, center.y + 0.4, center.z,
                (int) (radius * 5), radius * 0.5, 0.3, radius * 0.5, 0.02);
        level.sendParticles(p.dust(), center.x, center.y + 0.3, center.z, (int) (radius * 4), radius * 0.5, 0.2,
                radius * 0.5, 0.0);
    }

    /** Impact burst of an exploding projectile of a theme. */
    public static void explosion(ServerLevel level, SkillTheme theme, Vec3 at, double radius) {
        Palette p = palette(theme);
        level.sendParticles(radius >= 4.0 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at.x, at.y, at.z,
                1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(p.primary(), at.x, at.y, at.z, (int) (radius * 12), radius * 0.4, 0.4, radius * 0.4, 0.05);
        level.sendParticles(p.dust(), at.x, at.y + 0.3, at.z, (int) (radius * 8), radius * 0.4, 0.3, radius * 0.4, 0.0);
        level.playSound(null, at.x, at.y, at.z, p.impact(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    // ------------------------------------------------------------------ helpers

    private static List<Vec3> focus(ServerPlayer caster, List<CombatHits.HitTarget> targets) {
        if (targets.isEmpty()) {
            return List.of(caster.position());
        }
        return targets.stream().limit(8).map(hit -> hit.entity().position()).toList();
    }

    private static Vec3 horizontalLook(ServerPlayer caster) {
        double yaw = Math.toRadians(caster.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    /** Where the caster looks (blocks stop the ray), at most {@code range} blocks away. */
    static Vec3 lookPoint(ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        HitResult hit = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        return new Vec3(at.x, groundY(caster.serverLevel(), at), at.z);
    }

    /** The top of the ground below {@code at} (at most 6 blocks down). */
    static double groundY(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at.x, at.y + 0.5, at.z);
        for (int i = 0; i < 6; i++) {
            if (!level.getBlockState(pos.below()).isAir()) {
                return pos.getY();
            }
            pos = pos.below();
        }
        return at.y;
    }

    private static BlockState ground(ServerLevel level, ServerPlayer caster) {
        BlockState state = level.getBlockState(caster.blockPosition().below());
        return state.isAir() ? Blocks.DIRT.defaultBlockState() : state;
    }

    private static void ring(ServerLevel level, ParticleOptions particle, double x, double y, double z, double radius,
                             int count) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2.0 * i / count;
            level.sendParticles(particle, x + Math.cos(a) * radius, y, z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spiral(ServerLevel level, ParticleOptions particle, double x, double y, double z, double radius,
                               double height, int count) {
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double a = t * Math.PI * 4.0;
            level.sendParticles(particle, x + Math.cos(a) * radius, y + t * height, z + Math.sin(a) * radius, 1, 0.0,
                    0.0, 0.0, 0.0);
        }
    }

    private static void sphere(ServerLevel level, ParticleOptions particle, double x, double y, double z, double radius,
                               int count) {
        for (int i = 0; i < count; i++) {
            double yy = 1.0 - 2.0 * (i + 0.5) / count;
            double r = Math.sqrt(1.0 - yy * yy);
            double a = i * 2.399963;
            level.sendParticles(particle, x + Math.cos(a) * r * radius, y + yy * radius, z + Math.sin(a) * r * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void sound(ServerLevel level, double x, double y, double z, SoundEvent sound, float volume,
                              float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
