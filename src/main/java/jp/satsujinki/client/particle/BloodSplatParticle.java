package jp.satsujinki.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A flat splat of blood lying on the floor for a while, then fading. */
public class BloodSplatParticle extends TextureSheetParticle {
    private final float rot;

    protected BloodSplatParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.lifetime = 240 + this.random.nextInt(220);
        this.quadSize = 0.12F + this.random.nextFloat() * 0.28F;
        this.rot = this.random.nextFloat() * Mth.TWO_PI;
        float dark = 0.6F + this.random.nextFloat() * 0.4F;
        this.rCol = 0.5F * dark + 0.2F;
        this.gCol = 0.01F;
        this.bCol = 0.02F;
        this.alpha = 0.95F;
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        int left = this.lifetime - this.age;
        if (left < 60) this.alpha = 0.95F * left / 60.0F;
        // fresh blood darkens as it dries
        float k = Math.min(1.0F, this.age / 200.0F);
        this.rCol = Math.max(0.28F, this.rCol - 0.0008F * k);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float pt) {
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(pt, this.xo, this.x) - cam.x());
        float y = (float) (Mth.lerp(pt, this.yo, this.y) - cam.y()) + 0.002F;
        float z = (float) (Mth.lerp(pt, this.zo, this.z) - cam.z());
        float s = this.getQuadSize(pt);
        float cos = Mth.cos(this.rot) * s, sin = Mth.sin(this.rot) * s;
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        int light = this.getLightColor(pt);
        float[][] c = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int i = 0; i < 4; i++) {
            float dx = c[i][0] * cos - c[i][1] * sin;
            float dz = c[i][0] * sin + c[i][1] * cos;
            buffer.vertex(x + dx, y, z + dz).uv(uv[i][0], uv[i][1]).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        }
        for (int i = 3; i >= 0; i--) {
            float dx = c[i][0] * cos - c[i][1] * sin;
            float dz = c[i][0] * sin + c[i][1] * cos;
            buffer.vertex(x + dx, y, z + dz).uv(uv[i][0], uv[i][1]).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new BloodSplatParticle(level, x, y, z, this.sprites);
        }
    }
}
