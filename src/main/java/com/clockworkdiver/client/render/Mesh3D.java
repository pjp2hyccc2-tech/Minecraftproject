package com.clockworkdiver.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Builds the meshes of clockwork parts on the fly: spoked gears, hooked escape wheels, balance wheels,
 * spiral springs and clock faces. All parts lie in the local XZ plane with their axis along +Y.
 */
public final class Mesh3D {
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float UV = 0.35f;

    private Mesh3D() {}

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int color, int light,
                             float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color((color >> 16) & 255, (color >> 8) & 255, color & 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, int color, int light, float nx, float ny, float nz,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             boolean sideUv) {
        Matrix4f m = p.pose();
        Matrix3f n = p.normal();
        if (sideUv) {
            vert(vc, m, n, x0, y0, z0, (x0 + z0) * UV, y0 * UV, color, light, nx, ny, nz);
            vert(vc, m, n, x1, y1, z1, (x1 + z1) * UV, y1 * UV, color, light, nx, ny, nz);
            vert(vc, m, n, x2, y2, z2, (x2 + z2) * UV, y2 * UV, color, light, nx, ny, nz);
            vert(vc, m, n, x3, y3, z3, (x3 + z3) * UV, y3 * UV, color, light, nx, ny, nz);
        } else {
            vert(vc, m, n, x0, y0, z0, x0 * UV, z0 * UV, color, light, nx, ny, nz);
            vert(vc, m, n, x1, y1, z1, x1 * UV, z1 * UV, color, light, nx, ny, nz);
            vert(vc, m, n, x2, y2, z2, x2 * UV, z2 * UV, color, light, nx, ny, nz);
            vert(vc, m, n, x3, y3, z3, x3 * UV, z3 * UV, color, light, nx, ny, nz);
        }
    }

    public static int shade(int color, float f) {
        int r = Math.min(255, (int) (((color >> 16) & 255) * f));
        int g = Math.min(255, (int) (((color >> 8) & 255) * f));
        int b = Math.min(255, (int) ((color & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** Axis-aligned box. */
    public static void box(PoseStack pose, VertexConsumer vc, int light, int color, float x0, float y0, float z0, float x1, float y1, float z1) {
        PoseStack.Pose p = pose.last();
        int side = shade(color, 0.82f);
        quad(vc, p, color, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, false);
        quad(vc, p, shade(color, 0.6f), light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, false);
        quad(vc, p, side, light, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, true);
        quad(vc, p, side, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, true);
        quad(vc, p, side, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, true);
        quad(vc, p, side, light, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, true);
    }

    /** Flat ring (annulus) between radii, both faces and both edges. */
    public static void ring(PoseStack pose, VertexConsumer vc, int light, int color, float r0, float r1, float h, int seg) {
        PoseStack.Pose p = pose.last();
        int side = shade(color, 0.8f);
        for (int i = 0; i < seg; i++) {
            float a0 = TAU * i / seg, a1 = TAU * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(vc, p, color, light, 0, 1, 0, c0 * r0, h, s0 * r0, c0 * r1, h, s0 * r1, c1 * r1, h, s1 * r1, c1 * r0, h, s1 * r0, false);
            quad(vc, p, shade(color, 0.6f), light, 0, -1, 0, c1 * r0, -h, s1 * r0, c1 * r1, -h, s1 * r1, c0 * r1, -h, s0 * r1, c0 * r0, -h, s0 * r0, false);
            float cm = (float) Math.cos((a0 + a1) * 0.5f), sm = (float) Math.sin((a0 + a1) * 0.5f);
            quad(vc, p, side, light, cm, 0, sm, c0 * r1, -h, s0 * r1, c1 * r1, -h, s1 * r1, c1 * r1, h, s1 * r1, c0 * r1, h, s0 * r1, true);
            if (r0 > 0.001f) {
                quad(vc, p, side, light, -cm, 0, -sm, c0 * r0, h, s0 * r0, c1 * r0, h, s1 * r0, c1 * r0, -h, s1 * r0, c0 * r0, -h, s0 * r0, true);
            }
        }
    }

    /**
     * A gear. {@code escape} gives hooked escape-wheel teeth; {@code missingEvery} leaves teeth out; large
     * gears get spokes like real clock wheels.
     */
    public static void gear(PoseStack pose, VertexConsumer vc, int light, int color, float r, int teeth, float thick, int missingEvery,
                            boolean escape) {
        PoseStack.Pose p = pose.last();
        float h = thick / 2.0f;
        float depth = Math.max(0.12f, Math.min(0.9f, r * (escape ? 0.2f : 0.11f)));
        float tip = r + depth;
        boolean spokes = r >= 1.4f;
        float inner = spokes ? r * 0.8f : Math.max(0.06f, r * 0.12f);
        int n = Math.max(6, teeth);
        float step = TAU / n;
        int side = shade(color, 0.78f);
        int bottom = shade(color, 0.6f);
        float[] ang = new float[n * 5 + 1];
        float[] rad = new float[n * 5 + 1];
        int k = 0;
        for (int i = 0; i < n; i++) {
            float a = i * step;
            boolean missing = missingEvery > 0 && i % missingEvery == 0;
            float t = missing ? r : tip;
            if (escape) {
                ang[k] = a - step * 0.35f; rad[k++] = r;
                ang[k] = a - step * 0.05f; rad[k++] = missing ? r : r + depth * 0.4f;
                ang[k] = a + step * 0.12f; rad[k++] = t;
                ang[k] = a + step * 0.16f; rad[k++] = missing ? r : tip * 0.985f;
                ang[k] = a + step * 0.2f; rad[k++] = r;
            } else {
                ang[k] = a - step * 0.27f; rad[k++] = r;
                ang[k] = a - step * 0.15f; rad[k++] = t;
                ang[k] = a + step * 0.15f; rad[k++] = t;
                ang[k] = a + step * 0.27f; rad[k++] = r;
                ang[k] = a + step * 0.5f; rad[k++] = r;
            }
        }
        ang[k] = ang[0] + TAU;
        rad[k] = rad[0];
        for (int i = 0; i < k; i++) {
            float a0 = ang[i], a1 = ang[i + 1];
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float o0x = c0 * rad[i], o0z = s0 * rad[i], o1x = c1 * rad[i + 1], o1z = s1 * rad[i + 1];
            float i0x = c0 * inner, i0z = s0 * inner, i1x = c1 * inner, i1z = s1 * inner;
            quad(vc, p, color, light, 0, 1, 0, i0x, h, i0z, o0x, h, o0z, o1x, h, o1z, i1x, h, i1z, false);
            quad(vc, p, bottom, light, 0, -1, 0, i1x, -h, i1z, o1x, -h, o1z, o0x, -h, o0z, i0x, -h, i0z, false);
            float ex = o1x - o0x, ez = o1z - o0z;
            float len = (float) Math.sqrt(ex * ex + ez * ez);
            float nx = len > 1.0E-5f ? ez / len : c0, nz = len > 1.0E-5f ? -ex / len : s0;
            quad(vc, p, side, light, nx, 0, nz, o0x, -h, o0z, o1x, -h, o1z, o1x, h, o1z, o0x, h, o0z, true);
            quad(vc, p, side, light, -c0, 0, -s0, i0x, h, i0z, i1x, h, i1z, i1x, -h, i1z, i0x, -h, i0z, true);
        }
        if (spokes) {
            int count = r > 6.0f ? 6 : r > 2.5f ? 5 : 4;
            float w = Math.max(0.08f, r * 0.07f);
            float hub = r * 0.2f;
            for (int sIdx = 0; sIdx < count; sIdx++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotation(sIdx * TAU / count));
                box(pose, vc, light, color, hub * 0.8f, -h * 0.8f, -w, inner + 0.02f, h * 0.8f, w);
                pose.popPose();
            }
            ring(pose, vc, light, color, r * 0.05f, hub, h * 1.1f, Math.max(12, n / 2));
        }
        ring(pose, vc, light, 0x8A1E28, Math.max(0.02f, r * 0.015f), Math.max(0.05f, r * 0.04f), h * 1.25f, 10);
    }

    /** Balance wheel: rim, three spokes, timing screws and a hairspring. */
    public static void balance(PoseStack pose, VertexConsumer vc, int light, int color, float r, float thick) {
        float h = thick / 2.0f;
        ring(pose, vc, light, color, r * 0.9f, r, h, 64);
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(i * TAU / 3.0f));
            box(pose, vc, light, color, 0.0f, -h * 0.7f, -r * 0.035f, r * 0.9f, h * 0.7f, r * 0.035f);
            pose.popPose();
        }
        for (int i = 0; i < 12; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(i * TAU / 12.0f));
            box(pose, vc, light, 0xD8DCE2, r, -h * 0.5f, -r * 0.03f, r * 1.07f, h * 0.5f, r * 0.03f);
            pose.popPose();
        }
        ring(pose, vc, light, color, 0.02f, r * 0.1f, h * 1.2f, 16);
        spiral(pose, vc, light, 0x9DB4D8, r * 0.12f, r * 0.62f, 5.0f, h * 0.6f, Math.max(0.03f, r * 0.012f));
    }

    /** A spiral ribbon spring. Vertical ribbon of height 2h; {@code width} is the strip thickness. */
    public static void spiral(PoseStack pose, VertexConsumer vc, int light, int color, float r0, float r1, float turns, float h, float width) {
        PoseStack.Pose p = pose.last();
        int seg = Math.max(24, (int) (turns * 56));
        int dark = shade(color, 0.7f);
        float px = 0, pz = 0;
        for (int i = 0; i <= seg; i++) {
            float t = i / (float) seg;
            float a = t * turns * TAU;
            float r = r0 + (r1 - r0) * t;
            float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
            if (i > 0) {
                float cx = (float) Math.cos(a), sz = (float) Math.sin(a);
                quad(vc, p, color, light, cx, 0, sz, px, -h, pz, x, -h, z, x, h, z, px, h, pz, true);
                quad(vc, p, dark, light, 0, 1, 0, px, h, pz, x, h, z, x * (1 + width / r), h, z * (1 + width / r), px * (1 + width / r), h, pz * (1 + width / r), false);
            }
            px = x;
            pz = z;
        }
    }

    /** Clock face lying in the XZ plane facing +Y; 12 o'clock points to -Z. Angles in radians. */
    public static void clockFace(PoseStack pose, VertexConsumer vc, int light, int caseColor, float r, float hourA, float minuteA, float secondA) {
        PoseStack.Pose p = pose.last();
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            float a0 = TAU * i / seg, a1 = TAU * (i + 1) / seg;
            float c0 = (float) Math.cos(a0) * r, s0 = (float) Math.sin(a0) * r, c1 = (float) Math.cos(a1) * r, s1 = (float) Math.sin(a1) * r;
            quad(vc, p, 0xF2E8CC, light, 0, 1, 0, 0, 0.01f, 0, c0, 0.01f, s0, c1, 0.01f, s1, 0, 0.01f, 0, false);
        }
        ring(pose, vc, light, caseColor, r, r * 1.16f, Math.max(0.03f, r * 0.08f), seg);
        ring(pose, vc, light, shade(caseColor, 0.7f), r * 0.2f, r * 1.12f, 0.005f, seg);
        for (int i = 0; i < 12; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(-i * TAU / 12.0f));
            float w = i % 3 == 0 ? r * 0.05f : r * 0.025f;
            box(pose, vc, light, 0x2A2018, -w, 0.01f, -r * 0.93f, w, 0.02f + r * 0.01f, -r * (i % 3 == 0 ? 0.72f : 0.8f));
            pose.popPose();
        }
        hand(pose, vc, light, 0x1E1812, hourA, r * 0.52f, r * 0.06f, 0.03f + r * 0.01f);
        hand(pose, vc, light, 0x1E1812, minuteA, r * 0.78f, r * 0.04f, 0.04f + r * 0.015f);
        hand(pose, vc, light, 0xB02020, secondA, r * 0.86f, r * 0.012f, 0.05f + r * 0.02f);
        ring(pose, vc, light, 0xE0B050, 0.0f, r * 0.07f, 0.06f + r * 0.025f, 12);
    }

    private static void hand(PoseStack pose, VertexConsumer vc, int light, int color, float angle, float len, float w, float y) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(-angle));
        box(pose, vc, light, color, -w, y, -len, w, y + Math.max(0.01f, w * 0.4f), len * 0.12f);
        pose.popPose();
    }
}
