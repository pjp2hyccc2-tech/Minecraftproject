package com.sounan.client.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A soft, full-bright blob of light hanging in the fog round a headlamp. Used with a shader pack, where the pack's
 * fog swallows a small emissive point at thirty blocks: this is big enough to still read as "a light out there".
 * The size comes in as the x speed, the brightness as the y speed (particles carry no other parameters).
 */
public class LampGlowParticle extends TextureSheetParticle {
    LampGlowParticle(ClientLevel level, double x, double y, double z, float size, float alpha, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 2;
        this.gravity = 0.0f;
        this.hasPhysics = false;
        this.xd = this.yd = this.zd = 0.0;
        this.quadSize = size;
        this.alpha = alpha;
        this.setColor(1.0f, 0.92f, 0.78f);
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) this.remove();
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double size, double alpha, double unused) {
            return new LampGlowParticle(level, x, y, z, (float) size, (float) alpha, sprites);
        }
    }
}
