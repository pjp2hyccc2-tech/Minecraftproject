package com.eternalrealm.client.guide;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.List;

/**
 * The quest guide's yellow dots on the ground ({@link GuideClient}): little glowing discs along the route, a slow wave
 * of light running through them towards the place, fading out in the distance; where the route reaches the place, a
 * pillar of golden light. Flat on the floor, polygon-offset, never writing depth.
 */
public final class GuideRenderer {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MAX_DOTS = 160;
    private static final double FADE_START = 56.0;
    private static final double FADE_END = 80.0;
    private static final int SEGMENTS = 10;
    private static final float CORE = 0.13F;
    private static final float GLOW = 0.32F;
    private static boolean disabled;

    private GuideRenderer() {
    }

    /** Translucent position-colour triangles. */
    private static final class Types extends RenderType {
        static final RenderType DOTS = RenderType.create("eternalrealm_guide", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLES, 1 << 15, false, true, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                      boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (disabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        List<double[]> dots = GuideClient.dots();
        if (dots.isEmpty()) {
            return;
        }
        try {
            draw(event, dots);
        } catch (RuntimeException e) {
            disabled = true;
            LOG.error("Quest guide dots disabled after a rendering error", e);
        }
    }

    private static void draw(RenderLevelStageEvent event, List<double[]> dots) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(Types.DOTS);
        Matrix4f matrix = pose.last().pose();
        float time = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + event.getPartialTick();
        int count = Math.min(dots.size(), MAX_DOTS);
        for (int i = 0; i < count; i++) {
            double[] dot = dots.get(i);
            double distance = Math.sqrt(sq(dot[0] - camera.x) + sq(dot[1] - camera.y) + sq(dot[2] - camera.z));
            float fade = (float) Mth.clamp(1.0 - (distance - FADE_START) / (FADE_END - FADE_START), 0.0, 1.0);
            fade *= Math.min(1.0F, 0.35F + i * 0.35F); // the dot under the player's feet is faint
            if (fade <= 0.01F) {
                continue;
            }
            float wave = (float) Math.pow(0.5 + 0.5 * Math.sin(time * 0.22 - i * 0.55), 3.0);
            float light = (0.55F + 0.45F * wave) * fade;
            float x = (float) (dot[0] - camera.x);
            float y = (float) (dot[1] - camera.y) + 0.03F;
            float z = (float) (dot[2] - camera.z);
            disc(out, matrix, x, y, z, GLOW * (1.0F + 0.15F * wave), 255, 194, 58, (int) (110 * light), 0);
            disc(out, matrix, x, y + 0.002F, z, CORE, 255, 232, 120, (int) (240 * light), (int) (200 * light));
        }
        if (GuideClient.reachesTarget() && dots.size() > 1) {
            double[] end = dots.get(dots.size() - 1);
            double distance = Math.hypot(end[0] - camera.x, end[2] - camera.z);
            if (distance < FADE_END) {
                pillar(out, matrix, (float) (end[0] - camera.x), (float) (end[1] - camera.y),
                        (float) (end[2] - camera.z), time);
            }
        }
        buffers.endBatch(Types.DOTS);
    }

    /** A flat disc: the centre at {@code centreAlpha}, the rim at {@code rimAlpha}. */
    private static void disc(VertexConsumer out, Matrix4f matrix, float x, float y, float z, float radius, int r, int g,
                             int b, int centreAlpha, int rimAlpha) {
        for (int s = 0; s < SEGMENTS; s++) {
            double a0 = Math.PI * 2.0 * s / SEGMENTS;
            double a1 = Math.PI * 2.0 * (s + 1) / SEGMENTS;
            out.vertex(matrix, x, y, z).color(r, g, b, centreAlpha).endVertex();
            out.vertex(matrix, x + (float) Math.cos(a0) * radius, y, z + (float) Math.sin(a0) * radius)
                    .color(r, g, b, rimAlpha).endVertex();
            out.vertex(matrix, x + (float) Math.cos(a1) * radius, y, z + (float) Math.sin(a1) * radius)
                    .color(r, g, b, rimAlpha).endVertex();
        }
    }

    /** A pillar of golden light over the place, with a pulsing ring at its foot. */
    private static void pillar(VertexConsumer out, Matrix4f matrix, float x, float y, float z, float time) {
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.15F);
        float width = 0.28F;
        float height = 7.0F;
        for (int k = 0; k < 2; k++) {
            float dx = k == 0 ? width : 0.0F;
            float dz = k == 0 ? 0.0F : width;
            quad(out, matrix, x - dx, y, z - dz, x + dx, y, z + dz, x + dx, y + height, z + dz, x - dx, y + height,
                    z - dz, 255, 214, 90, (int) (150 + 60 * pulse), 0);
        }
        float ring = 0.7F + 0.35F * pulse;
        for (int s = 0; s < 16; s++) {
            double a0 = Math.PI * 2.0 * s / 16;
            double a1 = Math.PI * 2.0 * (s + 1) / 16;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float inner = ring - 0.12F;
            quad(out, matrix, x + c0 * inner, y + 0.03F, z + s0 * inner, x + c0 * ring, y + 0.03F, z + s0 * ring,
                    x + c1 * ring, y + 0.03F, z + s1 * ring, x + c1 * inner, y + 0.03F, z + s1 * inner, 255, 222, 110,
                    (int) (200 * (1.0F - 0.4F * pulse)), (int) (200 * (1.0F - 0.4F * pulse)));
        }
    }

    /** Two triangles; the first two corners take {@code alphaLow}, the last two {@code alphaHigh}. */
    private static void quad(VertexConsumer out, Matrix4f matrix, float x0, float y0, float z0, float x1, float y1,
                             float z1, float x2, float y2, float z2, float x3, float y3, float z3, int r, int g, int b,
                             int alphaLow, int alphaHigh) {
        out.vertex(matrix, x0, y0, z0).color(r, g, b, alphaLow).endVertex();
        out.vertex(matrix, x1, y1, z1).color(r, g, b, alphaLow).endVertex();
        out.vertex(matrix, x2, y2, z2).color(r, g, b, alphaHigh).endVertex();
        out.vertex(matrix, x0, y0, z0).color(r, g, b, alphaLow).endVertex();
        out.vertex(matrix, x2, y2, z2).color(r, g, b, alphaHigh).endVertex();
        out.vertex(matrix, x3, y3, z3).color(r, g, b, alphaHigh).endVertex();
    }

    private static double sq(double value) {
        return value * value;
    }
}
