package com.reisatsu.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reisatsu.registry.ModParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

import javax.annotation.Nullable;

/**
 * The particles of docs/DESIGN.md §7 (textures and JSONs: art). A particle whose sprites are missing (art not merged)
 * is simply not created.
 */
public final class ReisatsuParticles {
    private ReisatsuParticles() {}

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.MENU_FLUTTER.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Menu(l, x, y, z, dx, dy, dz), s));
        event.registerSpriteSet(ModParticles.WATER_DROP.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Drop(l, x, y, z, dx, dy, dz), s));
        event.registerSpriteSet(ModParticles.WET_PRINT.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Print(l, x, y, z, (float) dx), s));
        event.registerSpriteSet(ModParticles.EMBER.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Ember(l, x, y, z, dx, dy, dz), s));
        event.registerSpriteSet(ModParticles.SPIRIT_MOTE.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Mote(l, x, y, z, dx, dy, dz), s));
        event.registerSpriteSet(ModParticles.FROST_BREATH.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Breath(l, x, y, z, dx, dy, dz), s));
        event.registerSpriteSet(ModParticles.PAPER_ASH.get(), s -> (t, l, x, y, z, dx, dy, dz) -> make(new Ash(l, x, y, z, dx, dy, dz), s));
    }

    @Nullable
    private static Particle make(Base p, SpriteSet sprites) {
        try {
            p.init(sprites);
            return p;
        } catch (RuntimeException e) {
            return null; // no sprites yet
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

    /** A laminated menu flying off a table, tumbling. */
    static class Menu extends Base {
        private final float spin;

        Menu(ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            super(l, x, y, z);
            xd = dx + (random.nextDouble() - 0.5) * 0.18;
            yd = dy + 0.12 + random.nextDouble() * 0.12;
            zd = dz + (random.nextDouble() - 0.5) * 0.18;
            gravity = 0.35f;
            friction = 0.92f;
            lifetime = 40 + random.nextInt(25);
            quadSize = 0.22f;
            spin = (random.nextFloat() - 0.5f) * 0.6f;
            hasPhysics = true;
        }

        @Override
        public void tick() {
            super.tick();
            oRoll = roll;
            if (!onGround) roll += spin;
            if (age > lifetime - 10) alpha = (lifetime - age) / 10.0f;
        }
    }

    /** Tap water. */
    static class Drop extends Base {
        Drop(ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            super(l, x, y, z);
            xd = dx;
            yd = dy - 0.05;
            zd = dz;
            gravity = 0.9f;
            lifetime = 14 + random.nextInt(8);
            quadSize = 0.035f + random.nextFloat() * 0.02f;
            setColor(0.75f, 0.85f, 0.95f);
            alpha = 0.8f;
        }
    }

    /** A wet footprint flat on the floor, drying over two minutes. */
    static class Print extends Base {
        private final float yaw;

        Print(ClientLevel l, double x, double y, double z, float yaw) {
            super(l, x, y, z);
            this.yaw = yaw;
            xd = yd = zd = 0;
            gravity = 0;
            hasPhysics = false;
            lifetime = 20 * 120;
            quadSize = 0.16f;
            alpha = 0.85f;
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) remove();
            alpha = 0.85f * (1.0f - (float) age / lifetime);
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partial) {
            Vec3 cam = camera.getPosition();
            float px = (float) (x - cam.x()), py = (float) (y + 0.012 - cam.y()), pz = (float) (z - cam.z());
            float c = Mth.cos(yaw * Mth.DEG_TO_RAD), s = Mth.sin(yaw * Mth.DEG_TO_RAD);
            float w = quadSize * 0.6f, len = quadSize;
            float[][] corners = {{-w, -len}, {-w, len}, {w, len}, {w, -len}};
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            float[][] uv = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
            int light = getLightColor(partial);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < 4; i++) {
                    int k = pass == 0 ? i : 3 - i;
                    float lx = corners[k][0], lz = corners[k][1];
                    float wx = lx * c - lz * s, wz = lx * s + lz * c;
                    buffer.vertex(px + wx, py, pz + wz).uv(uv[k][0], uv[k][1]).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
                }
            }
        }
    }

    /** The chef's embers: rising, flickering, glowing. */
    static class Ember extends Base {
        Ember(ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            super(l, x, y, z);
            xd = dx + (random.nextDouble() - 0.5) * 0.02;
            yd = 0.015 + random.nextDouble() * 0.02;
            zd = dz + (random.nextDouble() - 0.5) * 0.02;
            gravity = -0.02f;
            lifetime = 18 + random.nextInt(24);
            quadSize = 0.03f + random.nextFloat() * 0.03f;
            setColor(1.0f, 0.45f + random.nextFloat() * 0.2f, 0.1f);
        }

        @Override
        public void tick() {
            super.tick();
            alpha = (1.0f - (float) age / lifetime) * (0.6f + random.nextFloat() * 0.4f);
        }

        @Override
        protected int getLightColor(float partial) {
            return 0xF000F0;
        }
    }

    /** Pale motes of a spirit going (bound into a photo, released at the exorcism). */
    static class Mote extends Base {
        Mote(ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            super(l, x, y, z);
            xd = dx * 0.5 + (random.nextDouble() - 0.5) * 0.03;
            yd = 0.02 + random.nextDouble() * 0.03;
            zd = dz * 0.5 + (random.nextDouble() - 0.5) * 0.03;
            gravity = -0.01f;
            lifetime = 30 + random.nextInt(30);
            quadSize = 0.04f + random.nextFloat() * 0.05f;
            setColor(0.85f, 0.92f, 1.0f);
        }

        @Override
        public void tick() {
            super.tick();
            float k = (float) age / lifetime;
            alpha = k < 0.2f ? k / 0.2f : 1.0f - (k - 0.2f) / 0.8f;
        }

        @Override
        protected int getLightColor(float partial) {
            return 0xF000F0;
        }
    }

    /** Your breath in the walk-in fridge. */
    static class Breath extends Base {
        Breath(ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            super(l, x, y, z);
            xd = dx;
            yd = dy + 0.005;
            zd = dz;
            gravity = 0;
            friction = 0.9f;
            lifetime = 22 + random.nextInt(10);
            quadSize = 0.08f;
            alpha = 0.45f;
        }

        @Override
        public void tick() {
            super.tick();
            quadSize *= 1.04f;
            alpha = 0.45f * (1.0f - (float) age / lifetime);
        }
    }

    /** Burning paper: dark flakes lifted by the heat, then drifting down. */
    static class Ash extends Base {
        Ash(ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            super(l, x, y, z);
            xd = dx + (random.nextDouble() - 0.5) * 0.04;
            yd = 0.05 + random.nextDouble() * 0.05;
            zd = dz + (random.nextDouble() - 0.5) * 0.04;
            gravity = 0.04f;
            friction = 0.95f;
            lifetime = 40 + random.nextInt(30);
            quadSize = 0.04f + random.nextFloat() * 0.04f;
            float g = 0.15f + random.nextFloat() * 0.2f;
            setColor(g, g * 0.9f, g * 0.8f);
        }

        @Override
        public void tick() {
            super.tick();
            oRoll = roll;
            roll += 0.1f;
            if (age > lifetime - 15) alpha = (lifetime - age) / 15.0f;
        }
    }
}
