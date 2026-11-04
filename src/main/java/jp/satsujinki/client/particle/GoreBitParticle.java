package jp.satsujinki.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Tiny bits of flesh and bone that bounce and settle. */
public class GoreBitParticle extends TextureSheetParticle {
    protected GoreBitParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 1.0F;
        this.friction = 0.96F;
        this.hasPhysics = true;
        this.lifetime = 80 + this.random.nextInt(80);
        this.quadSize = 0.04F + this.random.nextFloat() * 0.05F;
        this.roll = this.random.nextFloat() * 6.28F;
        this.oRoll = this.roll;
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        if (!this.onGround) this.roll += 0.3F;
        else {
            this.xd *= 0.5D;
            this.zd *= 0.5D;
        }
        int left = this.lifetime - this.age;
        if (left < 20) this.quadSize *= 0.92F;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new GoreBitParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
