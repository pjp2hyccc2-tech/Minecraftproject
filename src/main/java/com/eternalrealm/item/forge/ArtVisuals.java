package com.eternalrealm.item.forge;

import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.combat.forge.ERParticles;
import com.eternalrealm.sound.ERSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * The looks and sounds of the boss weapon arts ({@code com.eternalrealm.item.WeaponArts}): the {@code art_*} visuals
 * of their effects and the pulses of their ground areas (the zones' {@code look}). Server side, broadcast to everyone
 * around like every skill visual. Each art plays its own sound ({@code art.*}) when it starts.
 */
public final class ArtVisuals {
    private static final DustParticleOptions SHADOW_DUST = dust(0x3A1858, 1.6F);
    private static final DustParticleOptions BLACK_DUST = dust(0x101014, 1.8F);
    private static final DustParticleOptions THORN_DUST = dust(0x4FB33A, 1.4F);
    private static final DustParticleOptions SAND_DUST = dust(0xD9B66A, 1.6F);
    private static final DustParticleOptions ICE_DUST = dust(0x9EE8FF, 1.5F);
    private static final DustParticleOptions EMBER_DUST = dust(0xFF6A1A, 1.6F);
    private static final DustParticleOptions STORM_DUST = dust(0xF6F28A, 1.3F);
    private static final DustParticleOptions VOID_DUST = dust(0x7A2CC4, 1.7F);

    private ArtVisuals() {
    }

    /** Plays an {@code art_*} visual of {@code caster}; @return whether {@code kind} is an art visual */
    public static boolean play(ServerPlayer caster, String kind, List<CombatHits.HitTarget> targets) {
        if (!kind.startsWith("art_")) {
            return false;
        }
        ServerLevel level = caster.serverLevel();
        Vec3 feet = caster.position();
        double x = feet.x;
        double y = feet.y + 1.0;
        double z = feet.z;
        Vec3 look = horizontalLook(caster);
        switch (kind) {
            case "art_shadow_legion" -> {
                sound(level, x, y, z, ERSounds.ART_SHADOW_LEGION.get(), 1.2F);
                burst(level, ParticleTypes.LARGE_SMOKE, x, y, z, 40, 1.4, 0.02);
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * 2.0 * i / 6.0;
                    double px = x + Math.cos(angle) * 2.6;
                    double pz = z + Math.sin(angle) * 2.6;
                    column(level, SHADOW_DUST, px, feet.y, pz, 2.0, 8);
                    burst(level, ERParticles.SHADOW.get(), px, y, pz, 6, 0.3, 0.02);
                }
            }
            case "art_shadow_legion_cut" -> ring(level, ERParticles.SLASH.get(), x, y, z, 2.8, 10);
            case "art_shadow_legion_finish" -> {
                sound(level, x, y, z, ERSounds.SKILL_HEAVY_SLASH.get(), 1.0F);
                ring(level, ParticleTypes.SWEEP_ATTACK, x, y, z, 3.5, 14);
                ring(level, SHADOW_DUST, x, feet.y + 0.2, z, 4.5, 40);
                burst(level, ParticleTypes.DAMAGE_INDICATOR, x, y, z, 16, 2.0, 0.1);
                burst(level, ERParticles.SHADOW.get(), x, y, z, 40, 2.2, 0.05);
            }
            case "art_thornroot_bloom" -> {
                sound(level, x, y, z, ERSounds.ART_THORNROOT_BLOOM.get(), 1.1F);
                spiral(level, THORN_DUST, x, feet.y, z, 1.4, 2.6, 40);
                burst(level, ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 0.6, z, 30, 1.0, 0.02);
                burst(level, ERParticles.HEAL.get(), x, y, z, 12, 0.8, 0.02);
            }
            case "art_sand_maelstrom" -> {
                sound(level, x, y, z, ERSounds.ART_SAND_MAELSTROM.get(), 1.1F);
                spiral(level, sand(), x, feet.y, z, 1.6, 2.4, 50);
                Vec3 tip = feet.add(look.scale(7.0));
                line(level, SAND_DUST, feet.add(0, 1.0, 0), tip.add(0, 1.0, 0), 28);
            }
            case "art_doom_guillotine" -> {
                sound(level, x, y, z, ERSounds.ART_DOOM_GUILLOTINE.get(), 1.2F);
                Vec3 at = feet.add(look.scale(3.0));
                column(level, ICE_DUST, at.x, at.y + 2.0, at.z, 7.0, 30);
                column(level, ERParticles.FROST.get(), at.x, at.y + 2.0, at.z, 7.0, 12);
                burst(level, ParticleTypes.SNOWFLAKE, x, y + 1.0, z, 30, 0.8, 0.02);
            }
            case "art_doom_guillotine_fall" -> {
                Vec3 at = feet.add(look.scale(3.0));
                sound(level, at.x, at.y, at.z, ERSounds.SKILL_ICE_SHATTER.get(), 1.3F);
                line(level, ICE_DUST, at.add(0, 9.0, 0), at.add(0, 0.2, 0), 36);
                burst(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, 1.2, 0.0);
                ring(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                        at.x, at.y + 0.3, at.z, 3.6, 48);
                ring(level, ParticleTypes.SNOWFLAKE, at.x, at.y + 0.5, at.z, 2.5, 30);
            }
            case "art_frozen_prison" -> {
                sound(level, x, y, z, ERSounds.ART_FROZEN_PRISON.get(), 1.1F);
                ring(level, ERParticles.RUNE.get(), x, feet.y + 0.2, z, 1.6, 24);
                burst(level, ParticleTypes.SNOWFLAKE, x, y, z, 40, 1.2, 0.03);
            }
            case "art_meteor_slash" -> {
                sound(level, x, y, z, ERSounds.ART_METEOR_SLASH.get(), 1.2F);
                burst(level, ParticleTypes.FLAME, x, y + 1.2, z, 30, 0.5, 0.05);
                burst(level, ERParticles.EMBER.get(), x, y, z, 20, 0.8, 0.02);
            }
            case "art_meteor_slash_wave" -> {
                Vec3 end = feet.add(look.scale(7.0));
                line(level, ParticleTypes.FLAME, feet.add(0, 0.6, 0), end.add(0, 0.6, 0), 40);
                line(level, EMBER_DUST, feet.add(0, 1.2, 0), end.add(0, 1.2, 0), 28);
                ring(level, ParticleTypes.SWEEP_ATTACK, x, y, z, 2.2, 6);
            }
            case "art_heavens_chain" -> {
                sound(level, x, y, z, ERSounds.ART_HEAVENS_CHAIN.get(), 1.2F);
                ring(level, ParticleTypes.ELECTRIC_SPARK, x, y, z, 1.4, 30);
                column(level, STORM_DUST, x, feet.y, z, 3.0, 20);
            }
            case "art_gate_of_end" -> {
                sound(level, x, y, z, ERSounds.ART_GATE_OF_END.get(), 1.2F);
                spiral(level, ParticleTypes.REVERSE_PORTAL, x, feet.y, z, 1.2, 2.4, 50);
                Vec3 gate = feet.add(look.scale(5.0));
                ring(level, VOID_DUST, gate.x, gate.y + 1.4, gate.z, 1.6, 36);
            }
            case "art_black_sword_rain" -> {
                sound(level, x, y, z, ERSounds.ART_BLACK_SWORD_RAIN.get(), 1.3F);
                column(level, BLACK_DUST, x, feet.y, z, 5.0, 40);
                burst(level, ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.5, z, 30, 0.9, 0.04);
                ring(level, SHADOW_DUST, x, feet.y + 0.2, z, 7.0, 70);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** When a ground area with its own look is made (its marker is the telegraph). */
    public static void zoneCreated(ServerLevel level, String look, Vec3 at, double radius, int delayTicks) {
        if (look.startsWith("fx_")) {
            com.eternalrealm.combat.forge.ArtFx.zoneCreated(level, look, at, radius);
            return;
        }
        switch (look) {
            case "thornroot" -> ring(level, THORN_DUST, at.x, at.y + 0.2, at.z, radius, 40);
            case "ice_prison" -> ring(level, ICE_DUST, at.x, at.y + 0.2, at.z, radius, 40);
            case "void_gate" -> {
                ring(level, VOID_DUST, at.x, at.y + 0.2, at.z, radius, 50);
                ring(level, ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, radius * 0.6, 30);
            }
            case "black_swords" -> ring(level, BLACK_DUST, at.x, at.y + 0.2, at.z, radius, 60);
            default -> {
            }
        }
    }

    /** One pulse of an area with its own look; @return whether {@code look} is known */
    public static boolean zonePulse(ServerLevel level, String look, Vec3 at, double radius, int pulse) {
        if (look.startsWith("fx_")) {
            return com.eternalrealm.combat.forge.ArtFx.zonePulse(level, look, at, radius, pulse);
        }
        RandomSource random = level.getRandom();
        switch (look) {
            case "thornroot" -> {
                BlockParticleOption roots = new BlockParticleOption(ParticleTypes.BLOCK,
                        Blocks.MANGROVE_ROOTS.defaultBlockState());
                for (int i = 0; i < 6; i++) {
                    Vec3 spot = inside(random, at, radius);
                    column(level, roots, spot.x, spot.y, spot.z, 1.6, 8);
                    burst(level, THORN_DUST, spot.x, spot.y + 0.6, spot.z, 4, 0.2, 0.0);
                }
                burst(level, ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 1.0, at.z, 16, radius * 0.5, 0.0);
                sound(level, at.x, at.y, at.z, SoundEvents.ROOTED_DIRT_BREAK, 0.9F);
            }
            case "sand_vortex" -> {
                spiral(level, sand(), at.x, at.y, at.z, radius, 2.5, 40);
                burst(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.6, at.z, 4, radius * 0.4, 0.0);
                if (pulse == 0) {
                    sound(level, at.x, at.y, at.z, ERSounds.SKILL_SHADOW_WHOOSH.get(), 0.8F);
                }
            }
            case "ice_prison" -> {
                BlockParticleOption ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * 2.0 * i / 8.0;
                    double px = at.x + Math.cos(angle) * radius * 0.85;
                    double pz = at.z + Math.sin(angle) * radius * 0.85;
                    column(level, ice, px, at.y, pz, 3.0, 12);
                    column(level, ICE_DUST, px, at.y, pz, 3.0, 6);
                }
                burst(level, ParticleTypes.SNOWFLAKE, at.x, at.y + 1.0, at.z, 60, radius * 0.6, 0.02);
                sound(level, at.x, at.y, at.z, ERSounds.SKILL_ICE_CAST.get(), 1.2F);
            }
            case "ice_shatter" -> {
                BlockParticleOption ice = new BlockParticleOption(ParticleTypes.BLOCK,
                        Blocks.PACKED_ICE.defaultBlockState());
                burst(level, ice, at.x, at.y + 1.0, at.z, 90, radius * 0.6, 0.2);
                burst(level, ParticleTypes.SNOWFLAKE, at.x, at.y + 1.0, at.z, 50, radius * 0.6, 0.1);
                sound(level, at.x, at.y, at.z, ERSounds.SKILL_ICE_SHATTER.get(), 1.4F);
            }
            case "meteor" -> {
                Vec3 hit = inside(random, at, radius * 0.5);
                line(level, ParticleTypes.FLAME, hit.add(-3.0, 12.0, -3.0), hit.add(0, 0.3, 0), 36);
                line(level, ParticleTypes.LARGE_SMOKE, hit.add(-3.0, 12.0, -3.0), hit.add(0, 0.6, 0), 14);
                burst(level, ParticleTypes.EXPLOSION, hit.x, hit.y + 0.6, hit.z, 2, radius * 0.4, 0.0);
                burst(level, ParticleTypes.LAVA, hit.x, hit.y + 0.4, hit.z, 14, radius * 0.4, 0.0);
                ring(level, EMBER_DUST, hit.x, hit.y + 0.2, hit.z, radius, 30);
                sound(level, hit.x, hit.y, hit.z, ERSounds.SKILL_FIRE_EXPLOSION.get(), 1.2F);
            }
            case "sky_lightning" -> {
                Vec3 strike = inside(random, at, radius * 0.7);
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(strike.x, strike.y, strike.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                ring(level, ParticleTypes.ELECTRIC_SPARK, strike.x, strike.y + 0.3, strike.z, radius * 0.6, 24);
            }
            case "void_gate" -> {
                spiral(level, ParticleTypes.PORTAL, at.x, at.y, at.z, radius, 2.0, 30);
                burst(level, ParticleTypes.SQUID_INK, at.x, at.y + 1.2, at.z, 6, 0.6, 0.02);
            }
            case "void_gate_burst" -> {
                burst(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1.0, at.z, 1, 0.0, 0.0);
                burst(level, ParticleTypes.DRAGON_BREATH, at.x, at.y + 1.0, at.z, 60, radius * 0.6, 0.05);
                ring(level, VOID_DUST, at.x, at.y + 0.3, at.z, radius, 60);
                sound(level, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, 1.0F);
                sound(level, at.x, at.y, at.z, ERSounds.SKILL_SHADOW_WHOOSH.get(), 1.2F);
            }
            case "black_swords" -> {
                for (int i = 0; i < 5; i++) {
                    Vec3 spot = inside(random, at, radius);
                    line(level, BLACK_DUST, spot.add(0, 8.0, 0), spot.add(0, 0.2, 0), 18);
                    burst(level, ParticleTypes.CRIT, spot.x, spot.y + 0.3, spot.z, 6, 0.3, 0.2);
                    burst(level, ParticleTypes.SOUL_FIRE_FLAME, spot.x, spot.y + 0.2, spot.z, 3, 0.2, 0.01);
                }
                sound(level, at.x, at.y, at.z, ERSounds.SKILL_HEAVY_SLASH.get(), 0.9F);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private static BlockParticleOption sand() {
        return new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
    }

    private static Vec3 horizontalLook(ServerPlayer caster) {
        double yaw = Math.toRadians(caster.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private static Vec3 inside(RandomSource random, Vec3 center, double radius) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        return center.add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
    }

    private static void sound(ServerLevel level, double x, double y, double z, SoundEvent sound, float volume) {
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, 0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    private static void burst(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count,
                              double spread, double speed) {
        level.sendParticles(particle, x, y, z, count, spread, spread * 0.6, spread, speed);
    }

    private static void ring(ServerLevel level, ParticleOptions particle, double x, double y, double z, double radius,
                             int count) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            level.sendParticles(particle, x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius, 1, 0.0, 0.0,
                    0.0, 0.0);
        }
    }

    private static void column(ServerLevel level, ParticleOptions particle, double x, double y, double z,
                               double height, int count) {
        for (int i = 0; i < count; i++) {
            level.sendParticles(particle, x, y + height * i / Math.max(1, count - 1), z, 1, 0.08, 0.0, 0.08, 0.0);
        }
    }

    private static void spiral(ServerLevel level, ParticleOptions particle, double x, double y, double z,
                               double radius, double height, int count) {
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double angle = t * Math.PI * 6.0;
            double r = radius * (1.0 - 0.6 * t);
            level.sendParticles(particle, x + Math.cos(angle) * r, y + height * t, z + Math.sin(angle) * r, 1, 0.0,
                    0.0, 0.0, 0.0);
        }
    }

    private static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 at = from.lerp(to, i / (double) Math.max(1, count - 1));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F), size);
    }
}
