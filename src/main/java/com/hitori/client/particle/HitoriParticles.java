package com.hitori.client.particle;

import com.hitori.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

import javax.annotation.Nullable;

/**
 * The particles of docs/DESIGN.md §7 (textures and JSONs: art): rice grains, stuffing, drips, salt spray, embers,
 * smoke, motes in the CRT's light. A particle whose sprites are missing (art not merged) is simply not created.
 */
public final class HitoriParticles {
    private HitoriParticles() {}

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.RICE_GRAIN.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Falling(l, x, y, z, dx, dy, dz, 0.08f, 0.9f, 30), s));
        event.registerSpriteSet(ModParticles.STUFFING.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Falling(l, x, y, z, dx, dy, dz, 0.16f, 0.12f, 50), s));
        event.registerSpriteSet(ModParticles.WATER_DRIP.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Falling(l, x, y, z, 0, -0.02, 0, 0.05f, 1.0f, 18), s));
        event.registerSpriteSet(ModParticles.SALT_SPRAY.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Falling(l, x, y, z, dx, dy, dz, 0.06f, 0.6f, 20), s));
        event.registerSpriteSet(ModParticles.EMBER.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Rising(l, x, y, z, dx, dy + 0.02, dz, 0.06f, 30, true), s));
        event.registerSpriteSet(ModParticles.SMOKE.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Rising(l, x, y, z, dx, dy + 0.015, dz, 0.25f, 60, false), s));
        event.registerSpriteSet(ModParticles.TV_STATIC_MOTE.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Rising(l, x, y, z, dx * 0.2, 0.002, dz * 0.2, 0.04f, 80, true), s));
    }

    @Nullable
    private static Particle make(Base p, SpriteSet sprites) {
        try {
            p.init(sprites);
            return p;
        } catch (RuntimeException e) {
            return null;
        }
    }

    abstract static class Base extends TextureSheetParticle {
        Base(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        void init(SpriteSet sprites) {
            pickSprite(sprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** Something small falling: rice, stuffing, drips, salt. */
    static class Falling extends Base {
        Falling(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, float size, float gravity, int life) {
            super(l, x, y, z);
            xd = dx + (random.nextDouble() - 0.5) * 0.04;
            yd = dy + random.nextDouble() * 0.03;
            zd = dz + (random.nextDouble() - 0.5) * 0.04;
            this.gravity = gravity;
            friction = 0.9f;
            lifetime = life + random.nextInt(Math.max(1, life / 2));
            quadSize = size * (0.7f + random.nextFloat() * 0.6f);
            hasPhysics = true;
        }
    }

    /** Something rising: embers (bright), smoke (growing, fading), motes. */
    static class Rising extends Base {
        private final boolean bright;
        private final float size0;

        Rising(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, float size, int life, boolean bright) {
            super(l, x, y, z);
            xd = dx + (random.nextDouble() - 0.5) * 0.01;
            yd = dy;
            zd = dz + (random.nextDouble() - 0.5) * 0.01;
            gravity = -0.01f;
            friction = 0.95f;
            lifetime = life + random.nextInt(Math.max(1, life / 2));
            quadSize = size0 = size * (0.7f + random.nextFloat() * 0.6f);
            this.bright = bright;
            hasPhysics = false;
        }

        @Override
        public void tick() {
            super.tick();
            float k = age / (float) lifetime;
            alpha = 1.0f - k;
            if (!bright) quadSize = size0 * (1.0f + k * 2.0f);
        }

        @Override
        protected int getLightColor(float partial) {
            return bright ? 0xF000F0 : super.getLightColor(partial);
        }
    }
}
