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
}
