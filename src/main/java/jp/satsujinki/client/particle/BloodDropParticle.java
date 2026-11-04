package jp.satsujinki.client.particle;

import jp.satsujinki.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A heavy drop of blood. Leaves a splat where it lands. */
public class BloodDropParticle extends TextureSheetParticle {
    protected BloodDropParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.85F;
        this.friction = 0.985F;
        this.lifetime = 40 + this.random.nextInt(40);
        this.quadSize = 0.045F + this.random.nextFloat() * 0.06F;
        float dark = 0.55F + this.random.nextFloat() * 0.45F;
        this.rCol = 0.45F * dark + 0.25F;
        this.gCol = 0.015F;
        this.bCol = 0.02F + this.random.nextFloat() * 0.02F;
        this.hasPhysics = true;
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.onGround && !this.removed) {
            if (this.random.nextInt(3) == 0) {
                this.level.addParticle(ModParticles.BLOOD_SPLAT.get(), this.x, this.y + 0.01D, this.z, 0.0D, 0.0D, 0.0D);
            }
            this.remove();
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new BloodDropParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
