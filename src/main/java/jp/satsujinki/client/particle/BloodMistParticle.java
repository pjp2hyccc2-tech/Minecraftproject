package jp.satsujinki.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A red puff of aerosolised blood that grows and fades. */
public class BloodMistParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float baseSize;

    protected BloodMistParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.02F;
        this.friction = 0.9F;
        this.hasPhysics = false;
        this.lifetime = 14 + this.random.nextInt(10);
        this.baseSize = 0.25F + this.random.nextFloat() * 0.3F;
        this.quadSize = this.baseSize;
        this.rCol = 0.55F + this.random.nextFloat() * 0.2F;
        this.gCol = 0.02F;
        this.bCol = 0.03F;
        this.alpha = 0.75F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        float k = (float) this.age / this.lifetime;
        this.quadSize = this.baseSize * (1.0F + k * 1.6F);
        this.alpha = 0.75F * (1.0F - k);
        this.setSpriteFromAge(this.sprites);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new BloodMistParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
