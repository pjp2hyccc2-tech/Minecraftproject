package com.eternalrealm.mount.forge;

import com.eternalrealm.mount.MountFamily;
import com.eternalrealm.mount.MountKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the breeds show and sound like (docs/modules/mounts.md). Every client shows a mount's particles from how it
 * moves (so the other players see them too): petals falling from the antlers of the bloom, sea spray under the tide's
 * hooves, puffs of cloud under the gale's wings, fire rising from the blaze (blue for the soul-fire coat) and bursting
 * from its hooves when it surges, light from the halohorn's horn and a ring under its hooves when it steps on the air;
 * dust under the lion griffin's paws, nothing but a little down from the owl, sparks from the storm's wingtips, snow
 * from the frost's wings and motes of the void around the star griffin. Wing beats are heard (but not the owl's). The
 * server adds each breed's own touch to the arrival of a called mount.
 */
public final class MountEffects {
    private MountEffects() {
    }

    // ------------------------------------------------------------------ calling (server)

    /** The sound of a call starting. */
    static SoundEvent callSound(MountKind kind) {
        return switch (kind.family()) {
            case HORSE -> SoundEvents.HORSE_AMBIENT;
            case GRIFFIN -> kind == MountKind.GRIFFIN_OWL ? SoundEvents.PARROT_IMITATE_PHANTOM : SoundEvents.PARROT_FLY;
            case BIKE -> SoundEvents.BEACON_POWER_SELECT;
        };
    }

    /** The sound of a mount arriving: the family's, then the breed's own touch. */
    static void arrive(ServerLevel level, MountEntity mount) {
        MountKind kind = mount.kind();
        SoundEvent family = switch (kind.family()) {
            case HORSE -> SoundEvents.HORSE_GALLOP;
            case GRIFFIN -> SoundEvents.ENDER_DRAGON_FLAP;
            case BIKE -> SoundEvents.BEACON_ACTIVATE;
        };
        level.playSound(null, mount.getX(), mount.getY(), mount.getZ(), family, SoundSource.NEUTRAL, 1.0F, 1.0F);
        double x = mount.getX();
        double y = mount.getY() + mount.getBbHeight() * 0.5;
        double z = mount.getZ();
        double w = mount.getBbWidth() * 0.6;
        switch (kind) {
            case HORSE_BLOOM -> {
                level.sendParticles(ParticleTypes.CHERRY_LEAVES, x, y + 0.6, z, 40, w, 0.6, w, 0.02);
                sound(level, mount, SoundEvents.CHERRY_LEAVES_BREAK, 0.9F, 1.1F);
            }
            case HORSE_TIDE -> {
                level.sendParticles(ParticleTypes.SPLASH, x, mount.getY() + 0.2, z, 60, w, 0.2, w, 0.3);
                level.sendParticles(ParticleTypes.FALLING_WATER, x, y + 0.6, z, 20, w, 0.4, w, 0.0);
                sound(level, mount, SoundEvents.GENERIC_SPLASH, 0.6F, 1.2F);
            }
            case HORSE_GALE -> {
                level.sendParticles(ParticleTypes.CLOUD, x, y, z, 30, w * 1.5, 0.3, w * 1.5, 0.05);
                sound(level, mount, SoundEvents.PHANTOM_FLAP, 0.8F, 1.3F);
            }
            case HORSE_BLAZE -> {
                level.sendParticles(mount.look().coat().equals("soulfire") ? ParticleTypes.SOUL_FIRE_FLAME
                        : ParticleTypes.FLAME, x, mount.getY() + 0.1, z, 50, w, 0.1, w, 0.06);
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 8, w, 0.3, w, 0.0);
                sound(level, mount, SoundEvents.FIRECHARGE_USE, 0.6F, 0.8F);
            }
            case HORSE_HALO -> {
                level.sendParticles(ParticleTypes.END_ROD, x, y + 0.8, z, 30, w, 0.6, w, 0.04);
                sound(level, mount, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
            }
            case GRIFFIN_GOLD -> {
                dust(level, mount, 30);
                sound(level, mount, SoundEvents.POLAR_BEAR_WARNING, 0.35F, 1.4F);
            }
            case GRIFFIN_OWL -> {
                level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 0.5, z, 60, w, 0.6, w, 0.01);
                sound(level, mount, SoundEvents.PHANTOM_AMBIENT, 0.25F, 1.8F);
            }
            case GRIFFIN_STORM -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, 40, w, 0.6, w, 0.3);
                sound(level, mount, SoundEvents.TRIDENT_THUNDER, 0.35F, 1.4F);
            }
            case GRIFFIN_FROST -> {
                level.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 0.5, z, 50, w, 0.6, w, 0.03);
                sound(level, mount, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8F, 1.4F);
            }
            case GRIFFIN_VOID -> {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.5, z, 60, w, 0.6, w, 0.05);
                sound(level, mount, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.6F, 0.8F);
            }
            default -> {
                // the first three: the family's arrival only
            }
        }
    }

    private static void sound(ServerLevel level, MountEntity mount, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, mount.getX(), mount.getY(), mount.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }

    private static void dust(ServerLevel level, MountEntity mount, int count) {
        BlockState below = level.getBlockState(BlockPos.containing(mount.getX(), mount.getY() - 0.2, mount.getZ()));
        if (below.getRenderShape() != RenderShape.INVISIBLE) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), mount.getX(), mount.getY() + 0.1,
                    mount.getZ(), count, mount.getBbWidth() * 0.5, 0.1, mount.getBbWidth() * 0.5, 0.15);
        }
    }

    // ------------------------------------------------------------------ riding (every client)

    /** A point of the model ({@code mx, my, mz} in model units: right is -x, ground at y 24, front is -z). */
    static Vec3 at(MountEntity mount, double mx, double my, double mz) {
        float yaw = mount.yBodyRot * Mth.DEG_TO_RAD;
        double forward = -mz / 16.0;
        double left = mx / 16.0;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        return new Vec3(mount.getX() + fx * forward + fz * left, mount.getY() + (24.0 - my) / 16.0,
                mount.getZ() + fz * forward - fx * left);
    }

    private static void spawn(Level level, ParticleOptions particle, Vec3 at, double vx, double vy, double vz) {
        level.addParticle(particle, at.x, at.y, at.z, vx, vy, vz);
    }

    /** One tick of a breed's particles, from how fast it moves ({@code speed} and {@code dy} in blocks per tick). */
    static void tick(MountEntity mount, float speed, float dy) {
        Level level = mount.level();
        RandomSource random = mount.getRandom();
        boolean air = mount.airTicks() > 3;
        float run = Math.min(1.0F, speed / 0.5F);
        switch (mount.kind()) {
            case HORSE_BLOOM -> {
                if (random.nextFloat() < 0.04F + 0.3F * run) {
                    double side = random.nextBoolean() ? 1.0 : -1.0;
                    spawn(level, ParticleTypes.CHERRY_LEAVES, at(mount, side * (3.0 + random.nextFloat() * 4.0),
                            -36.0 + random.nextFloat() * 6.0, -14.0 + random.nextFloat() * 8.0), 0.0, 0.0, 0.0);
                }
            }
            case HORSE_TIDE -> {
                boolean wet = mount.isInWater();
                if (speed > 0.08F && (wet || random.nextFloat() < 0.15F)) {
                    for (int i = 0; i < (wet ? 3 : 1); i++) {
                        spawn(level, ParticleTypes.SPLASH, at(mount, (random.nextFloat() - 0.5) * 8.0, 23.0,
                                (random.nextFloat() - 0.5) * 20.0), 0.0, 0.1, 0.0);
                    }
                }
                if (random.nextFloat() < 0.05F) {
                    spawn(level, ParticleTypes.DRIPPING_WATER, at(mount, (random.nextFloat() - 0.5) * 6.0, -14.0,
                            -10.0 + random.nextFloat() * 8.0), 0.0, 0.0, 0.0);
                }
            }
            case HORSE_GALE -> {
                if (mount.glide(1.0F) > 0.5F && random.nextFloat() < 0.6F) {
                    double side = random.nextBoolean() ? 1.0 : -1.0;
                    spawn(level, ParticleTypes.CLOUD, at(mount, side * (22.0 + random.nextFloat() * 6.0), 3.0, -4.0),
                            0.0, 0.02, 0.0);
                } else if (!air && speed > 0.3F && random.nextFloat() < 0.12F) {
                    spawn(level, ParticleTypes.CLOUD, at(mount, (random.nextFloat() - 0.5) * 8.0, 23.5,
                            (random.nextFloat() - 0.5) * 16.0), 0.0, 0.02, 0.0);
                }
            }
            case HORSE_BLAZE -> {
                ParticleOptions fire = mount.look().coat().equals("soulfire") ? ParticleTypes.SOUL_FIRE_FLAME
                        : ParticleTypes.FLAME;
                if (random.nextFloat() < 0.35F + 0.6F * run) {
                    boolean tail = random.nextFloat() < 0.35F;
                    Vec3 from = tail ? at(mount, (random.nextFloat() - 0.5) * 4.0, -4.0 + random.nextFloat() * 10.0, 16.0)
                            : at(mount, (random.nextFloat() - 0.5) * 4.0, -24.0 + random.nextFloat() * 12.0,
                            -12.0 + random.nextFloat() * 6.0);
                    spawn(level, random.nextFloat() < 0.6F ? ParticleTypes.SMALL_FLAME : fire, from, 0.0, 0.02, 0.0);
                }
                if (mount.surging() && random.nextFloat() < 0.8F) {
                    spawn(level, fire, at(mount, (random.nextFloat() - 0.5) * 8.0, 23.5, (random.nextFloat() - 0.5) * 18.0),
                            0.0, 0.04, 0.0);
                }
                if (random.nextFloat() < 0.02F) {
                    spawn(level, ParticleTypes.SMOKE, at(mount, 0.0, -28.0, -16.0), 0.0, 0.03, 0.0);
                }
            }
            case HORSE_HALO -> {
                if (random.nextFloat() < 0.08F) {
                    spawn(level, ParticleTypes.END_ROD, at(mount, 0.0, -38.0, -30.0), (random.nextFloat() - 0.5) * 0.02,
                            0.01, (random.nextFloat() - 0.5) * 0.02);
                }
                if (speed > 0.3F && random.nextFloat() < 0.25F) {
                    spawn(level, ParticleTypes.END_ROD, at(mount, (random.nextFloat() - 0.5) * 8.0, 23.0,
                            (random.nextFloat() - 0.5) * 18.0), 0.0, 0.01, 0.0);
                }
            }
            case GRIFFIN_GOLD -> {
                if (!air && speed > 0.25F && random.nextFloat() < 0.5F) {
                    BlockState below = level.getBlockState(BlockPos.containing(mount.getX(), mount.getY() - 0.2,
                            mount.getZ()));
                    if (below.getRenderShape() != RenderShape.INVISIBLE) {
                        spawn(level, new BlockParticleOption(ParticleTypes.BLOCK, below), at(mount,
                                (random.nextFloat() - 0.5) * 8.0, 23.8, 6.0 + random.nextFloat() * 6.0), 0.0, 0.08, 0.0);
                    }
                }
                if (mount.look().coat().equals("sunfire") && random.nextFloat() < 0.1F) {
                    spawn(level, ParticleTypes.WAX_ON, at(mount, (random.nextFloat() - 0.5) * 10.0, -6.0, -12.0), 0.0,
                            0.05, 0.0);
                }
            }
            case GRIFFIN_OWL -> {
                if (air && random.nextFloat() < 0.08F) {
                    spawn(level, ParticleTypes.WHITE_ASH, at(mount, (random.nextFloat() - 0.5) * 40.0, 4.0, 4.0), 0.0,
                            -0.01, 0.0);
                }
                if (mount.look().coat().equals("moonlit") && random.nextFloat() < 0.05F) {
                    spawn(level, ParticleTypes.END_ROD, at(mount, (random.nextFloat() - 0.5) * 12.0, -2.0,
                            (random.nextFloat() - 0.5) * 16.0), 0.0, 0.01, 0.0);
                }
            }
            case GRIFFIN_STORM -> {
                float rate = air ? 0.1F + run * 0.7F + (dy < -0.4F ? 0.6F : 0.0F) : 0.03F;
                if (random.nextFloat() < rate) {
                    double side = random.nextBoolean() ? 1.0 : -1.0;
                    Vec3 tip = air ? at(mount, side * (40.0 + random.nextFloat() * 6.0), 5.0, 2.0)
                            : at(mount, (random.nextFloat() - 0.5) * 4.0, -12.0, -14.0);
                    spawn(level, ParticleTypes.ELECTRIC_SPARK, tip, 0.0, 0.0, 0.0);
                }
            }
            case GRIFFIN_FROST -> {
                if (random.nextFloat() < (air ? 0.7F : 0.08F)) {
                    double side = random.nextBoolean() ? 1.0 : -1.0;
                    Vec3 from = air ? at(mount, side * (8.0 + random.nextFloat() * 34.0), 5.0, 6.0)
                            : at(mount, (random.nextFloat() - 0.5) * 14.0, -2.0, (random.nextFloat() - 0.5) * 16.0);
                    spawn(level, ParticleTypes.SNOWFLAKE, from, 0.0, -0.02, 0.0);
                }
            }
            case GRIFFIN_VOID -> {
                if (random.nextFloat() < 0.35F) {
                    spawn(level, ParticleTypes.REVERSE_PORTAL, at(mount, (random.nextFloat() - 0.5) * 18.0,
                            4.0 - random.nextFloat() * 16.0, (random.nextFloat() - 0.5) * 22.0), 0.0, 0.0, 0.0);
                }
                if (air && speed > 0.3F && random.nextFloat() < 0.5F) {
                    spawn(level, ParticleTypes.PORTAL, at(mount, (random.nextFloat() - 0.5) * 8.0, 8.0, 18.0), 0.0, 0.0,
                            0.0);
                }
            }
            default -> {
                // the first three show nothing more
            }
        }
    }

    /** One wing beat of a griffin (heard nearby; the owl's wings are silent). */
    static void wingBeat(MountEntity mount) {
        if (mount.kind().family() != MountFamily.GRIFFIN || mount.kind() == MountKind.GRIFFIN_OWL) {
            return;
        }
        mount.level().playLocalSound(mount.getX(), mount.getY() + 1.0, mount.getZ(), SoundEvents.PHANTOM_FLAP,
                SoundSource.NEUTRAL, 0.35F, 1.1F + mount.getRandom().nextFloat() * 0.2F, false);
    }

    /** The halohorn steps on the air: a ring of light under its hooves and a chime. */
    static void skyStep(MountEntity mount) {
        Level level = mount.level();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            level.addParticle(ParticleTypes.END_ROD, mount.getX() + Math.cos(a) * 0.9, mount.getY() + 0.05,
                    mount.getZ() + Math.sin(a) * 0.9, Math.cos(a) * 0.06, -0.02, Math.sin(a) * 0.06);
        }
        level.playLocalSound(mount.getX(), mount.getY(), mount.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.NEUTRAL, 1.2F, 1.6F, false);
    }

    /** The blaze flares up: a burst of fire from its hooves and a roar of flame. */
    static void surge(MountEntity mount) {
        Level level = mount.level();
        ParticleOptions fire = mount.look().coat().equals("soulfire") ? ParticleTypes.SOUL_FIRE_FLAME
                : ParticleTypes.FLAME;
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12.0;
            level.addParticle(fire, mount.getX() + Math.cos(a) * 0.6, mount.getY() + 0.1, mount.getZ() + Math.sin(a) * 0.6,
                    Math.cos(a) * 0.08, 0.05, Math.sin(a) * 0.08);
        }
        level.playLocalSound(mount.getX(), mount.getY(), mount.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL,
                0.7F, 0.7F, false);
    }
}
