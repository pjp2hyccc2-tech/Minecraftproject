package com.reisatsu.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws a thin textured slab (a door leaf) in the current pose: front/back faces get the whole texture, edges a sliver. */
final class PanelQuads {
    private PanelQuads() {}

    /** Box x0..x1 (width), y0..y1, z -t..t; texture u 0..1 across the width (front as seen from +z), v 0..1 top to bottom. */
    static void panel(PoseStack pose, VertexConsumer vc, float x0, float x1, float y0, float y1, float t, int light, int overlay, int alpha) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        // front (+z)
        quad(vc, m, n, x0, y0, t, x1, y0, t, x1, y1, t, x0, y1, t, 0, 1, 1, 0, 0, 0, 1, light, overlay, alpha);
        // back (-z), mirrored
        quad(vc, m, n, x1, y0, -t, x0, y0, -t, x0, y1, -t, x1, y1, -t, 0, 1, 1, 0, 0, 0, -1, light, overlay, alpha);
        float e = 1.0f / 16.0f;
        // edges (left, right, top, bottom) with a sliver of the frame
        quad(vc, m, n, x0, y0, -t, x0, y0, t, x0, y1, t, x0, y1, -t, 0, 1, e, 0, -1, 0, 0, light, overlay, alpha);
        quad(vc, m, n, x1, y0, t, x1, y0, -t, x1, y1, -t, x1, y1, t, 1 - e, 1, 1, 0, 1, 0, 0, light, overlay, alpha);
        quad(vc, m, n, x0, y1, t, x1, y1, t, x1, y1, -t, x0, y1, -t, 0, e, 1, 0, 0, 1, 0, light, overlay, alpha);
        quad(vc, m, n, x0, y0, -t, x1, y0, -t, x1, y0, t, x0, y0, t, 0, 1, 1, 1 - e, 0, -1, 0, light, overlay, alpha);
    }

    /** Quad corners a (bottom-left), b (bottom-right), c (top-right), d (top-left); uv: u0 at a/d, u1 at b/c, v1 at the bottom, v0 at the top. */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float vBottom, float u1, float vTop, float nx, float ny, float nz, int light, int overlay, int alpha) {
        vc.vertex(m, ax, ay, az).color(255, 255, 255, alpha).uv(u0, vBottom).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, bx, by, bz).color(255, 255, 255, alpha).uv(u1, vBottom).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, cx, cy, cz).color(255, 255, 255, alpha).uv(u1, vTop).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, dx, dy, dz).color(255, 255, 255, alpha).uv(u0, vTop).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
