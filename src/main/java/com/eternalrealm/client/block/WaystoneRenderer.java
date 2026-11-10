package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.WaystoneBlockEntity;
import com.eternalrealm.core.ModInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The living part of a waystone (the stone itself is a block model): its carved runes glowing and breathing, a slowly
 * turning glyph circle on the ground, and above the cap a floating, spinning crystal with a soft glow and three small
 * shards circling it. Additive "eyes" layers for the light, so it reads at night from far away.
 */
public class WaystoneRenderer implements BlockEntityRenderer<WaystoneBlockEntity> {
    private static final ResourceLocation RUNES = new ResourceLocation(ModInfo.MOD_ID, "textures/entity/waystone/runes.png");
    private static final ResourceLocation CRYSTAL =
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/waystone/crystal.png");
    private static final ResourceLocation CIRCLE =
            new ResourceLocation(ModInfo.MOD_ID, "textures/entity/waystone/circle.png");
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    /** Shaft sections in block units (lower half's frame): {min, max, y0, y1} — must match the block models. */
    private static final double[] LOWER_SHAFT = {3.0 / 16.0, 13.0 / 16.0, 3.0 / 16.0, 1.0};
    private static final double[] UPPER_SHAFT = {3.5 / 16.0, 12.5 / 16.0, 1.0, 1.0 + 10.0 / 16.0};

    public WaystoneRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WaystoneBlockEntity stone, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = stone.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = stone.getBlockPos();
        double t = (level.getGameTime() + partialTick) / 20.0;
        double seed = Math.floorMod(pos.asLong(), 997L) * 0.37;
        float pulse = (float) (0.62 + 0.38 * Math.sin(t * 1.6 + seed));
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean near = camera.distanceToSqr(Vec3.atCenterOf(pos)) < 56.0 * 56.0;

        // the carved runes, glowing
        Mesh runes = new Mesh(pose.last(), buffers.getBuffer(RenderType.eyes(RUNES)), FULL_BRIGHT)
                .color(pulse, pulse, pulse, 1.0F);
        shaft(runes, LOWER_SHAFT, 0.0F, 0.5F);
        shaft(runes, UPPER_SHAFT, 0.5F, 1.0F);

        // the glyph circle on the ground, turning slowly
        if (near) {
            float c = 0.28F + 0.22F * pulse;
            Mesh circle = new Mesh(pose.last(), buffers.getBuffer(RenderType.eyes(CIRCLE)), FULL_BRIGHT)
                    .color(c * 0.85F, c, c, 1.0F);
            double angle = t * 0.12 + seed;
            double r = 1.75;
            double y = 0.035;
            double[][] p = new double[4][];
            for (int i = 0; i < 4; i++) {
                double a = angle + Math.PI * 0.25 + i * Math.PI * 0.5;
                p[i] = new double[]{0.5 + Math.cos(a) * r * Math.sqrt(2.0), y, 0.5 + Math.sin(a) * r * Math.sqrt(2.0)};
            }
            // counter-clockwise seen from above (normal up)
            circle.quad(p[0], p[3], p[2], p[1], 0.0F, 0.0F, 1.0F, 1.0F);
        }

        // the crystal: bobbing and spinning, with a glow shell
        double bob = 0.07 * Math.sin(t * 1.3 + seed);
        double spin = t * 0.7 + seed;
        double cy = WaystoneBlockEntity.CRYSTAL_Y + bob;
        Mesh crystal = new Mesh(pose.last(), buffers.getBuffer(RenderType.entityTranslucent(CRYSTAL)), FULL_BRIGHT)
                .color(1.0F, 1.0F, 1.0F, 0.92F);
        octahedron(crystal, 0.5, cy, 0.5, 0.26, 0.40, spin);
        Mesh shell = new Mesh(pose.last(), buffers.getBuffer(RenderType.eyes(CRYSTAL)), FULL_BRIGHT)
                .color(0.30F * pulse, 0.42F * pulse, 0.45F * pulse, 1.0F);
        octahedron(shell, 0.5, cy, 0.5, 0.36, 0.55, spin);
        if (near) {
            for (int i = 0; i < 3; i++) {
                double a = spin * 1.7 + i * Math.PI * 2.0 / 3.0;
                double sy = cy + 0.16 * Math.sin(t * 2.1 + i * 2.0);
                octahedron(crystal, 0.5 + Math.cos(a) * 0.58, sy, 0.5 + Math.sin(a) * 0.58, 0.06, 0.10, -spin * 2.0);
            }
        }
    }

    /** Glow quads just outside the four sides of a shaft section; u0..u1 is that section's part of the rune sheet. */
    private static void shaft(Mesh m, double[] s, float u0, float u1) {
        double e = 0.004;
        double a = s[0] - e;
        double b = s[1] + e;
        double y0 = s[2];
        double y1 = s[3];
        // seen from outside each face: top-left, bottom-left, bottom-right, top-right
        m.quad(new double[]{b, y1, a}, new double[]{b, y0, a}, new double[]{a, y0, a}, new double[]{a, y1, a},
                u0, 0.0F, u1, 1.0F);                                                           // north
        m.quad(new double[]{a, y1, b}, new double[]{a, y0, b}, new double[]{b, y0, b}, new double[]{b, y1, b},
                u0, 0.0F, u1, 1.0F);                                                           // south
        m.quad(new double[]{b, y1, b}, new double[]{b, y0, b}, new double[]{b, y0, a}, new double[]{b, y1, a},
                u0, 0.0F, u1, 1.0F);                                                           // east
        m.quad(new double[]{a, y1, a}, new double[]{a, y0, a}, new double[]{a, y0, b}, new double[]{a, y1, b},
                u0, 0.0F, u1, 1.0F);                                                           // west
    }

    /** An octahedron (eight triangles, outward facing) turned {@code spin} radians about the vertical. */
    private static void octahedron(Mesh m, double cx, double cy, double cz, double rx, double ry, double spin) {
        double[] top = {cx, cy + ry, cz};
        double[] bottom = {cx, cy - ry, cz};
        double[][] ring = new double[4][];
        for (int i = 0; i < 4; i++) {
            double a = spin + i * Math.PI * 0.5;
            ring[i] = new double[]{cx + Math.cos(a) * rx, cy, cz + Math.sin(a) * rx};
        }
        for (int i = 0; i < 4; i++) {
            double[] e0 = ring[i];
            double[] e1 = ring[(i + 1) % 4];
            triangle(m, top, e1, e0, 0.5F, 0.0F, 1.0F, 0.5F, 0.0F, 0.5F);
            triangle(m, bottom, e0, e1, 0.5F, 1.0F, 0.0F, 0.5F, 1.0F, 0.5F);
        }
    }

    private static void triangle(Mesh m, double[] p0, double[] p1, double[] p2, float u0, float v0, float u1, float v1,
                                 float u2, float v2) {
        double[] n = Mesh.normalOf(p0, p1, p2);
        m.vertex(p0, u0, v0, n);
        m.vertex(p1, u1, v1, n);
        m.vertex(p2, u2, v2, n);
        m.vertex(p2, u2, v2, n);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
