package com.eternalrealm.client.telegraph;

import com.eternalrealm.combat.Telegraph;
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

/**
 * Draws the ground telegraphs: a translucent area in the attack's colour, an inner area that grows until the hit
 * (so the timing can be read at a glance), a bright pulsing rim, and a white flash when it fires. Flat at the marker's
 * height, slightly lifted and polygon-offset against z-fighting, never writing depth.
 */
public final class TelegraphRenderer {
    private static final Logger LOG = LogUtils.getLogger();
    private static boolean disabled;

    private TelegraphRenderer() {
    }

    /** Translucent position-colour triangles on the ground. */
    private static final class Types extends RenderType {
        static final RenderType DECAL = RenderType.create("eternalrealm_telegraph", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLES, 1 << 16, false, true, CompositeState.builder()
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
        if (disabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ClientTelegraphs.isEmpty()) {
            return;
        }
        try {
            draw(event);
        } catch (RuntimeException e) {
            disabled = true;
            LOG.error("Ground telegraphs disabled after a rendering error", e);
        }
    }

    private static void draw(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(Types.DECAL);
        Matrix4f matrix = pose.last().pose();
        float time = ClientTelegraphs.now() + event.getPartialTick();
        for (ClientTelegraphs.Active active : ClientTelegraphs.active()) {
            Telegraph telegraph = active.telegraph;
            double distance = Math.hypot(telegraph.x() - camera.x, telegraph.z() - camera.z) - telegraph.boundingRadius();
            if (distance > 96.0) {
                continue;
            }
            Brush brush = new Brush(out, matrix, camera, telegraph);
            int color = color(telegraph.style());
            if (active.firedAt >= 0) {
                float f = Mth.clamp((time - active.firedAt) / ClientTelegraphs.FLASH_TICKS, 0.0F, 1.0F);
                int flash = mix(color, 0xFFFFFF, 0.65F * (1.0F - f));
                brush.fill(telegraph, 1.0F, flash, 0.75F * (1.0F - f) * (1.0F - f), 0.04F);
                brush.rim(telegraph, flash, 0.9F * (1.0F - f), 0.05F);
                continue;
            }
            float fade = active.cancelledAt < 0 ? 1.0F
                    : 1.0F - Mth.clamp((time - active.cancelledAt) / ClientTelegraphs.FADE_TICKS, 0.0F, 1.0F);
            float progress = Mth.clamp((time - active.start) / telegraph.durationTicks(), 0.0F, 1.0F);
            float pulseSpeed = progress > 0.8F ? 1.6F : 0.55F;
            float pulse = 0.5F + 0.5F * Mth.sin(time * pulseSpeed);
            float appear = Mth.clamp((time - active.start) / 3.0F, 0.0F, 1.0F); // quick fade-in
            float base = (0.16F + 0.06F * pulse) * fade * appear;
            brush.fill(telegraph, 1.0F, color, base, 0.02F);
            if (telegraph.style() != Telegraph.Style.SAFE) {
                brush.fill(telegraph, progress, mix(color, 0xFFFFFF, 0.08F), (0.22F + 0.1F * progress) * fade * appear,
                        0.03F);
            }
            brush.rim(telegraph, mix(color, 0xFFFFFF, 0.25F + 0.25F * pulse), (0.65F + 0.3F * pulse) * fade * appear,
                    0.045F);
        }
        buffers.endBatch(Types.DECAL);
    }

    static int color(Telegraph.Style style) {
        return switch (style) {
            case DANGER -> 0xFF2A1E;
            case MAGIC -> 0xB04BFF;
            case FIRE -> 0xFF7A14;
            case FROST -> 0x4EC8FF;
            case HOLY -> 0xFFD447;
            case DARK -> 0x7A1AAE;
            case SAFE -> 0x3CE65A;
        };
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    /** Writes the triangles of one marker in its own frame (along = forward, across = left). */
    private static final class Brush {
        private static final float RIM = 0.16F;
        private final VertexConsumer out;
        private final Matrix4f matrix;
        private final double ox;
        private final double oz;
        private final double y;
        private final double fx;
        private final double fz;
        private int r;
        private int g;
        private int b;
        private int a;
        private float lift;

        Brush(VertexConsumer out, Matrix4f matrix, Vec3 camera, Telegraph telegraph) {
            this.out = out;
            this.matrix = matrix;
            this.ox = telegraph.x() - camera.x;
            this.oz = telegraph.z() - camera.z;
            this.y = telegraph.y() - camera.y;
            double radians = Math.toRadians(telegraph.yaw());
            this.fx = -Math.sin(radians);
            this.fz = Math.cos(radians);
        }

        private void paint(int rgb, float alpha, float lift) {
            this.r = (rgb >> 16) & 0xFF;
            this.g = (rgb >> 8) & 0xFF;
            this.b = rgb & 0xFF;
            this.a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
            this.lift = lift;
        }

        /** A point given in the marker's frame. */
        private void vertex(double along, double across) {
            double wx = ox + fx * along + fz * across;
            double wz = oz + fz * along - fx * across;
            out.vertex(matrix, (float) wx, (float) (y + lift), (float) wz).color(r, g, b, a).endVertex();
        }

        private void triangle(double a1, double c1, double a2, double c2, double a3, double c3) {
            vertex(a1, c1);
            vertex(a2, c2);
            vertex(a3, c3);
        }

        private void quad(double a1, double c1, double a2, double c2, double a3, double c3, double a4, double c4) {
            triangle(a1, c1, a2, c2, a3, c3);
            triangle(a1, c1, a3, c3, a4, c4);
        }

        /** Axis-aligned box in the marker's frame. */
        private void box(double along0, double along1, double across0, double across1) {
            quad(along0, across0, along1, across0, along1, across1, along0, across1);
        }

        /** Annulus sector between radii, angles in radians measured from forward. */
        private void annulus(double inner, double outer, double from, double to) {
            int segments = Mth.clamp((int) (outer * Math.abs(to - from) * 3.0), 6, 160);
            for (int i = 0; i < segments; i++) {
                double t0 = from + (to - from) * i / segments;
                double t1 = from + (to - from) * (i + 1) / segments;
                double c0 = Math.cos(t0);
                double s0 = Math.sin(t0);
                double c1 = Math.cos(t1);
                double s1 = Math.sin(t1);
                if (inner <= 1.0E-4) {
                    triangle(0.0, 0.0, outer * c0, outer * s0, outer * c1, outer * s1);
                } else {
                    quad(inner * c0, inner * s0, outer * c0, outer * s0, outer * c1, outer * s1, inner * c1, inner * s1);
                }
            }
        }

        /** The marker's area, scaled to {@code amount} of its size (the charge fill grows with it). */
        void fill(Telegraph t, float amount, int rgb, float alpha, float lift) {
            if (amount <= 0.001F || alpha <= 0.003F) {
                return;
            }
            paint(rgb, alpha, lift);
            switch (t.kind()) {
                case CIRCLE -> annulus(0.0, t.a() * amount, 0.0, Math.PI * 2.0);
                case RING -> annulus(t.b() - (t.b() - t.a()) * amount, t.b(), 0.0, Math.PI * 2.0);
                case CONE -> {
                    double half = Math.toRadians(t.b());
                    annulus(0.0, t.a() * amount, -half, half);
                }
                case RECT -> box(0.0, t.a() * amount, -t.b() / 2.0, t.b() / 2.0);
                case SQUARE -> box(-t.a() * amount, t.a() * amount, -t.a() * amount, t.a() * amount);
                case CROSS -> {
                    double arm = t.a() * amount;
                    double half = t.b() / 2.0;
                    box(-arm, arm, -half, half);
                    box(-half, half, -arm, -half);
                    box(-half, half, half, arm);
                }
            }
        }

        /** The bright outline. */
        void rim(Telegraph t, int rgb, float alpha, float lift) {
            if (alpha <= 0.003F) {
                return;
            }
            paint(rgb, alpha, lift);
            double w = RIM;
            switch (t.kind()) {
                case CIRCLE -> annulus(Math.max(0.0, t.a() - w), t.a(), 0.0, Math.PI * 2.0);
                case RING -> {
                    annulus(t.a(), t.a() + w, 0.0, Math.PI * 2.0);
                    annulus(Math.max(0.0, t.b() - w), t.b(), 0.0, Math.PI * 2.0);
                }
                case CONE -> {
                    double half = Math.toRadians(t.b());
                    annulus(Math.max(0.0, t.a() - w), t.a(), -half, half);
                    if (t.b() < 179.0) {
                        for (double edge : new double[] {-half, half}) {
                            double c = Math.cos(edge);
                            double s = Math.sin(edge);
                            double nc = s * (edge < 0 ? -1 : 1); // inward normal of this edge
                            double ns = -c * (edge < 0 ? -1 : 1);
                            // thin strip along the edge, on the inside of the sector
                            quad(0.0, 0.0, t.a() * c, t.a() * s, t.a() * c + nc * w, t.a() * s + ns * w, nc * w, ns * w);
                        }
                    }
                }
                case RECT -> outline(0.0, t.a(), -t.b() / 2.0, t.b() / 2.0, w);
                case SQUARE -> outline(-t.a(), t.a(), -t.a(), t.a(), w);
                case CROSS -> {
                    double arm = t.a();
                    double half = t.b() / 2.0;
                    // outer ends and the four long sides of each arm
                    box(arm - w, arm, -half, half);
                    box(-arm, -arm + w, -half, half);
                    box(-half, half, arm - w, arm);
                    box(-half, half, -arm, -arm + w);
                    box(half, arm, half - w, half);
                    box(half, arm, -half, -half + w);
                    box(-arm, -half, half - w, half);
                    box(-arm, -half, -half, -half + w);
                    box(half - w, half, half, arm);
                    box(-half, -half + w, half, arm);
                    box(half - w, half, -arm, -half);
                    box(-half, -half + w, -arm, -half);
                }
            }
        }

        private void outline(double along0, double along1, double across0, double across1, double w) {
            box(along0, along1, across1 - w, across1);
            box(along0, along1, across0, across0 + w);
            box(along1 - w, along1, across0 + w, across1 - w);
            box(along0, along0 + w, across0 + w, across1 - w);
        }
    }
}
