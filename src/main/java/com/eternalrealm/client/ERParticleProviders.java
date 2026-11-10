package com.eternalrealm.client;

import com.eternalrealm.combat.forge.ERParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

/** Look and motion of the mod's particles: animated through their frames, fading out, most of them glowing. */
public final class ERParticleProviders {
    /** @param gravity negative rises; @param size quad size in blocks; @param glow full brightness in the dark */
    private record Style(int lifetime, float size, float gravity, float friction, boolean glow, boolean shrink) {
    }

    private ERParticleProviders() {
    }

    public static void register(RegisterParticleProvidersEvent event) {
        add(event, ERParticles.EMBER.get(), new Style(22, 0.10F, -0.025F, 0.92F, true, true));
        add(event, ERParticles.FROST.get(), new Style(26, 0.11F, 0.015F, 0.90F, true, false));
        add(event, ERParticles.HEAL.get(), new Style(24, 0.13F, -0.03F, 0.90F, true, false));
        add(event, ERParticles.HOLY.get(), new Style(20, 0.16F, -0.01F, 0.88F, true, true));
        add(event, ERParticles.RUNE.get(), new Style(26, 0.15F, -0.012F, 0.86F, true, false));
        add(event, ERParticles.SHADOW.get(), new Style(22, 0.18F, 0.01F, 0.90F, false, true));
        add(event, ERParticles.SLASH.get(), new Style(7, 0.55F, 0.0F, 0.5F, true, false));
        add(event, ERParticles.SPARK.get(), new Style(9, 0.09F, 0.0F, 0.80F, true, true));
        event.registerSpriteSet(ERParticles.MOTE.get(), sprites -> (type, level, x, y, z, dx, dy, dz) ->
                new MoteParticle(level, x, y, z, dx, dy, dz, sprites));
        event.registerSpriteSet(ERParticles.SPRAY.get(), sprites -> (type, level, x, y, z, dx, dy, dz) ->
                new SprayParticle(level, x, y, z, dx, dy, dz, sprites));
        event.registerSpriteSet(ERParticles.CHIMNEY_SMOKE.get(), sprites -> (type, level, x, y, z, dx, dy, dz) ->
                new ChimneySmokeParticle(level, x, y, z, dx, dy, dz, sprites, false));
        event.registerSpriteSet(ERParticles.FORGE_SMOKE.get(), sprites -> (type, level, x, y, z, dx, dy, dz) ->
                new ChimneySmokeParticle(level, x, y, z, dx, dy, dz, sprites, true));
    }

    private static void add(RegisterParticleProvidersEvent event, SimpleParticleType type, Style style) {
        event.registerSpriteSet(type, sprites -> new Provider(sprites, style));
    }

    private record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new ERParticle(level, x, y, z, dx, dy, dz, sprites, style);
        }
    }

    private static final class ERParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final Style style;
        private final float startSize;

        ERParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites,
                   Style style) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.style = style;
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.lifetime = style.lifetime() + random.nextInt(Math.max(1, style.lifetime() / 4));
            this.startSize = style.size() * (0.8F + random.nextFloat() * 0.4F);
            this.quadSize = startSize;
            this.gravity = style.gravity();
            this.friction = style.friction();
            this.hasPhysics = false;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) {
                setSpriteFromAge(sprites);
                float life = (float) age / (float) lifetime;
                alpha = life < 0.6F ? 1.0F : Math.max(0.0F, 1.0F - (life - 0.6F) / 0.4F);
                if (style.shrink()) {
                    quadSize = startSize * (1.0F - life * 0.5F);
                }
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return style.glow() ? 0xF000F0 : super.getLightColor(partialTick);
        }
    }

    /** A speck of dust in a sunbeam: fades in, drifts slowly, glints, fades out. */
    private static final class MoteParticle extends TextureSheetParticle {
        private final float peak;

        MoteParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z);
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.lifetime = 60 + random.nextInt(80);
            this.quadSize = 0.018F + random.nextFloat() * 0.022F;
            this.gravity = 0.0F;
            this.friction = 0.985F;
            this.hasPhysics = false;
            this.peak = 0.45F + random.nextFloat() * 0.45F;
            this.alpha = 0.0F;
            setSprite(sprites.get(random));
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) {
                float life = (float) age / (float) lifetime;
                float fade = life < 0.25F ? life / 0.25F : life > 0.7F ? Math.max(0.0F, (1.0F - life) / 0.3F) : 1.0F;
                alpha = peak * fade * (0.8F + 0.2F * net.minecraft.util.Mth.sin(age * 0.3F));
                xd += (random.nextFloat() - 0.5F) * 0.0004F;
                zd += (random.nextFloat() - 0.5F) * 0.0004F;
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }
    }

    /**
     * A drop of fountain water: falls under gravity, glints, and bursts into a splash when it reaches water or stone
     * (vanilla splash particles, a ring of them on water).
     */
    private static final class SprayParticle extends TextureSheetParticle {
        SprayParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                      SpriteSet sprites) {
            super(level, x, y, z);
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.lifetime = 60;
            this.quadSize = 0.035F + random.nextFloat() * 0.035F;
            this.gravity = 1.0F;                      // 0.04 blocks/tick^2, the jets' own gravity
            this.friction = 0.995F;
            this.hasPhysics = false;
            this.rCol = 0.78F;
            this.gCol = 0.9F;
            this.bCol = 1.0F;
            this.alpha = 0.85F;
            setSprite(sprites.get(random));
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) {
                return;
            }
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
            net.minecraft.world.level.material.FluidState fluid = level.getFluidState(pos);
            if (!fluid.isEmpty() && y <= pos.getY() + fluid.getHeight(level, pos)) {
                double surface = pos.getY() + fluid.getHeight(level, pos);
                if (random.nextFloat() < 0.55F) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, x, surface + 0.02, z,
                            0.0, 0.0, 0.0);
                }
                remove();
                return;
            }
            if (yd < 0.0 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                if (random.nextFloat() < 0.4F) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, x, y + 0.05, z,
                            0.0, 0.0, 0.0);
                }
                remove();
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /**
     * Chimney smoke: a soft puff that swells as it rises, slows, drifts off with the breeze and thins away; forge
     * smoke is darker, thicker and lives longer.
     */
    private static final class ChimneySmokeParticle extends TextureSheetParticle {
        private static final double DRIFT_X = 0.00035;
        private static final double DRIFT_Z = 0.00022;
        private final float startSize;
        private final float endSize;
        private final float peak;

        ChimneySmokeParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                             SpriteSet sprites, boolean heavy) {
            super(level, x, y, z);
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.lifetime = (heavy ? 170 : 140) + random.nextInt(heavy ? 90 : 70);
            this.startSize = (heavy ? 0.42F : 0.3F) * (0.85F + random.nextFloat() * 0.3F);
            this.endSize = startSize * (heavy ? 5.0F : 4.2F);
            this.quadSize = startSize;
            this.gravity = 0.0F;
            this.friction = 0.985F;
            this.hasPhysics = false;
            float grey = heavy ? 0.22F + random.nextFloat() * 0.08F : 0.78F + random.nextFloat() * 0.12F;
            this.rCol = grey;
            this.gCol = grey * 0.98F;
            this.bCol = grey * 0.96F;
            this.peak = heavy ? 0.78F : 0.5F;
            this.alpha = 0.0F;
            this.roll = random.nextFloat() * (float) Math.PI * 2.0F;
            this.oRoll = roll;
            setSprite(sprites.get(random));
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) {
                return;
            }
            float life = (float) age / (float) lifetime;
            float grow = 1.0F - (1.0F - life) * (1.0F - life);
            quadSize = startSize + (endSize - startSize) * grow;
            float fade = life < 0.08F ? life / 0.08F : life > 0.45F ? Math.max(0.0F, (1.0F - life) / 0.55F) : 1.0F;
            alpha = peak * fade;
            xd += DRIFT_X * (1.0 + random.nextGaussian() * 0.3);
            zd += DRIFT_Z * (1.0 + random.nextGaussian() * 0.3);
            yd = Math.max(yd, 0.004);
            oRoll = roll;
            roll += 0.004F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
