package com.clockworkdiver.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Immediate 2D vector drawing for the GUI: triangles, discs, rings, gears. Everything is drawn in GUI
 * coordinates. An optional circular "lens" mask fades vertices near the lens out (used to see through
 * the clock face with the Chrono Spectacles).
 */
public final class Gfx {
    private static BufferBuilder buf;
    private static Matrix4f mat;
    private static final float TAU = (float) (Math.PI * 2.0);

    public static boolean maskOn;
    public static float maskX;
    public static float maskY;
    public static float maskR;
    public static float maskFeather = 5.0f;
    public static float alphaMul = 1.0f;

    private Gfx() {}

    public static void begin(GuiGraphics g) {
        g.flush();
        mat = new Matrix4f(g.pose().last().pose());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    public static void end() {
        if (buf == null) return;
        BufferUploader.drawWithShader(buf.end());
        buf = null;
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ colours

    public static int argb(int a, int r, int g, int b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static int alpha(int color, float a) {
        return (clamp255((int) (((color >>> 24) & 255) * a)) << 24) | (color & 0xFFFFFF);
    }

    public static int withAlpha(int rgb, int a) {
        return (clamp255(a) << 24) | (rgb & 0xFFFFFF);
    }

    public static int shade(int color, float f) {
        int a = (color >>> 24) & 255;
        return argb(a, (int) (((color >> 16) & 255) * f), (int) (((color >> 8) & 255) * f), (int) ((color & 255) * f));
    }

    public static int lerp(int c0, int c1, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a = (int) (((c0 >>> 24) & 255) + ((((c1 >>> 24) & 255) - ((c0 >>> 24) & 255)) * t));
        int r = (int) (((c0 >> 16) & 255) + ((((c1 >> 16) & 255) - ((c0 >> 16) & 255)) * t));
        int g = (int) (((c0 >> 8) & 255) + ((((c1 >> 8) & 255) - ((c0 >> 8) & 255)) * t));
        int b = (int) ((c0 & 255) + (((c1 & 255) - (c0 & 255)) * t));
        return argb(a, r, g, b);
    }

    public static float smoothstep(float e0, float e1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - e0) / (e1 - e0)));
        return t * t * (3.0f - 2.0f * t);
    }

    public static float maskAt(float x, float y) {
        if (!maskOn) return 1.0f;
        float dx = x - maskX, dy = y - maskY;
        return smoothstep(maskR - maskFeather, maskR + maskFeather, (float) Math.sqrt(dx * dx + dy * dy));
    }

    // ------------------------------------------------------------------ primitives

    private static void v(float x, float y, int color) {
        float a = ((color >>> 24) & 255) / 255.0f * alphaMul;
        if (maskOn) a *= maskAt(x, y);
        buf.vertex(mat, x, y, 0.0f).color(((color >> 16) & 255) / 255.0f, ((color >> 8) & 255) / 255.0f, (color & 255) / 255.0f, a)
                .endVertex();
    }

    public static void tri(float x0, float y0, float x1, float y1, float x2, float y2, int color) {
        v(x0, y0, color);
        v(x1, y1, color);
        v(x2, y2, color);
    }

    public static void tri(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
        v(x0, y0, c0);
        v(x1, y1, c1);
        v(x2, y2, c2);
    }

    public static void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        tri(x0, y0, x1, y1, x2, y2, color);
        tri(x0, y0, x2, y2, x3, y3, color);
    }

    public static void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        tri(x0, y0, c0, x1, y1, c1, x2, y2, c2);
        tri(x0, y0, c0, x2, y2, c2, x3, y3, c3);
    }

    public static void rect(float x0, float y0, float x1, float y1, int color) {
        quad(x0, y0, x1, y0, x1, y1, x0, y1, color);
    }

    /** Vertical gradient rectangle. */
    public static void rectV(float x0, float y0, float x1, float y1, int top, int bottom) {
        quad(x0, y0, top, x1, y0, top, x1, y1, bottom, x0, y1, bottom);
    }

    /** Rectangle subdivided into a grid so the lens mask fades it smoothly. */
    public static void rectFine(float x0, float y0, float x1, float y1, int color, float cell) {
        int nx = Math.max(1, (int) Math.ceil((x1 - x0) / cell));
        int ny = Math.max(1, (int) Math.ceil((y1 - y0) / cell));
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                float ax = x0 + (x1 - x0) * i / nx, bx = x0 + (x1 - x0) * (i + 1) / nx;
                float ay = y0 + (y1 - y0) * j / ny, by = y0 + (y1 - y0) * (j + 1) / ny;
                rect(ax, ay, bx, by, color);
            }
        }
    }

    public static void line(float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4f) return;
        float nx = -dy / len * width * 0.5f, ny = dx / len * width * 0.5f;
        quad(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, color);
    }

    /** Tapered clock hand from (x0,y0) to (x1,y1). */
    public static void hand(float x0, float y0, float x1, float y1, float baseWidth, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4f) return;
        float nx = -dy / len * baseWidth * 0.5f, ny = dx / len * baseWidth * 0.5f;
        float bx = x0 - dx * 0.15f, by = y0 - dy * 0.15f;
        quad(bx + nx, by + ny, x0 + nx * 1.3f, y0 + ny * 1.3f, x1, y1, x0 - nx * 1.3f, y0 - ny * 1.3f, color);
        quad(bx + nx, by + ny, x0 + nx * 1.3f, y0 + ny * 1.3f, x0 - nx * 1.3f, y0 - ny * 1.3f, bx - nx, by - ny, color);
    }

    /** Filled circle as a triangle fan (fine for unmasked shapes). */
    public static void circle(float cx, float cy, float r, int color) {
        int seg = segments(r);
        for (int i = 0; i < seg; i++) {
            float a0 = TAU * i / seg, a1 = TAU * (i + 1) / seg;
            tri(cx, cy, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r,
                    cy + (float) Math.sin(a1) * r, color);
        }
    }

    /** Circle with a radial gradient. */
    public static void circleGradient(float cx, float cy, float r, int inner, int outer) {
        int seg = segments(r);
        for (int i = 0; i < seg; i++) {
            float a0 = TAU * i / seg, a1 = TAU * (i + 1) / seg;
            tri(cx, cy, inner, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, outer,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, outer);
        }
    }

    /** Filled disc built from a polar grid, so a lens mask can cut a soft hole into it. */
    public static void disc(float cx, float cy, float r, int color) {
        int rings = Math.max(2, (int) (r / 5.0f));
        int seg = Math.max(24, segments(r) * 2);
        for (int k = 0; k < rings; k++) {
            float r0 = r * k / rings, r1 = r * (k + 1) / rings;
            ring(cx, cy, r0, r1, color, seg, 0.0f);
        }
    }

    public static void ring(float cx, float cy, float r0, float r1, int color) {
        ring(cx, cy, r0, r1, color, segments(r1), 0.0f);
    }

    public static void ring(float cx, float cy, float r0, float r1, int color, int seg, float rot) {
        for (int i = 0; i < seg; i++) {
            float a0 = rot + TAU * i / seg, a1 = rot + TAU * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(cx + c0 * r0, cy + s0 * r0, cx + c0 * r1, cy + s0 * r1, cx + c1 * r1, cy + s1 * r1, cx + c1 * r0, cy + s1 * r0, color);
        }
    }

    /** Ring with a colour gradient from inner to outer edge. */
    public static void ringGradient(float cx, float cy, float r0, float r1, int inner, int outer) {
        int seg = segments(r1);
        for (int i = 0; i < seg; i++) {
            float a0 = TAU * i / seg, a1 = TAU * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(cx + c0 * r0, cy + s0 * r0, inner, cx + c0 * r1, cy + s0 * r1, outer, cx + c1 * r1, cy + s1 * r1, outer,
                    cx + c1 * r0, cy + s1 * r0, inner);
        }
    }

    /** Ring with a light top-left and a dark bottom-right, like polished metal. */
    public static void metalRing(float cx, float cy, float r0, float r1, int base) {
        int seg = segments(r1);
        for (int i = 0; i < seg; i++) {
            float a0 = TAU * i / seg, a1 = TAU * (i + 1) / seg;
            float light = 0.75f + 0.45f * (float) Math.cos((a0 + a1) * 0.5f + Math.PI * 0.75);
            int c = shade(base, light);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(cx + c0 * r0, cy + s0 * r0, cx + c0 * r1, cy + s0 * r1, cx + c1 * r1, cy + s1 * r1, cx + c1 * r0, cy + s1 * r0, c);
        }
    }

    public static void arc(float cx, float cy, float r0, float r1, float from, float to, int color) {
        int seg = Math.max(4, (int) (segments(r1) * Math.abs(to - from) / TAU));
        for (int i = 0; i < seg; i++) {
            float a0 = from + (to - from) * i / seg, a1 = from + (to - from) * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(cx + c0 * r0, cy + s0 * r0, cx + c0 * r1, cy + s0 * r1, cx + c1 * r1, cy + s1 * r1, cx + c1 * r0, cy + s1 * r0, color);
        }
    }

    /** A clock gear: rim, teeth, spokes and hub. {@code missingEvery} > 0 leaves every n-th tooth out. */
    public static void gear(float cx, float cy, float rRoot, float rTip, int teeth, float angle, int color, int spokes, int missingEvery) {
        float step = TAU / teeth;
        int dark = shade(color, 0.72f);
        for (int i = 0; i < teeth; i++) {
            if (missingEvery > 0 && i % missingEvery == 0) continue;
            float a = angle + i * step;
            float ra = a - step * 0.27f, rb = a + step * 0.27f, ta = a - step * 0.16f, tb = a + step * 0.16f;
            quad(cx + (float) Math.cos(ra) * rRoot * 0.98f, cy + (float) Math.sin(ra) * rRoot * 0.98f,
                    cx + (float) Math.cos(ta) * rTip, cy + (float) Math.sin(ta) * rTip,
                    cx + (float) Math.cos(tb) * rTip, cy + (float) Math.sin(tb) * rTip,
                    cx + (float) Math.cos(rb) * rRoot * 0.98f, cy + (float) Math.sin(rb) * rRoot * 0.98f, color);
        }
        if (spokes <= 0) {
            ring(cx, cy, rRoot * 0.18f, rRoot, color, Math.max(16, teeth * 2), angle);
            ring(cx, cy, rRoot * 0.62f, rRoot * 0.68f, dark, Math.max(16, teeth * 2), angle);
        } else {
            ring(cx, cy, rRoot * 0.78f, rRoot, color, Math.max(16, teeth * 2), angle);
            ring(cx, cy, rRoot * 0.74f, rRoot * 0.79f, dark, Math.max(16, teeth * 2), angle);
            for (int s = 0; s < spokes; s++) {
                float a = angle + s * TAU / spokes;
                float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                float w = rRoot * 0.09f;
                float x0 = cx + c * rRoot * 0.2f, y0 = cy + sn * rRoot * 0.2f, x1 = cx + c * rRoot * 0.8f, y1 = cy + sn * rRoot * 0.8f;
                quad(x0 - sn * w, y0 + c * w, x1 - sn * w * 0.7f, y1 + c * w * 0.7f, x1 + sn * w * 0.7f, y1 - c * w * 0.7f, x0 + sn * w, y0 - c * w, color);
            }
            ring(cx, cy, rRoot * 0.08f, rRoot * 0.24f, color, 20, angle);
        }
        circle(cx, cy, Math.max(1.0f, rRoot * 0.08f), 0xFF3A2A1A);
        circle(cx, cy, Math.max(0.6f, rRoot * 0.04f), 0xFFE0405A);
    }

    /** Flat spiral (hairspring / mainspring). */
    public static void spiral(float cx, float cy, float r0, float r1, float turns, float width, float rot, int color) {
        int seg = (int) (turns * 48);
        float px = 0, py = 0;
        for (int i = 0; i <= seg; i++) {
            float t = i / (float) seg;
            float a = rot + t * turns * TAU;
            float r = r0 + (r1 - r0) * t;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i > 0) line(px, py, x, y, width, color);
            px = x;
            py = y;
        }
    }

    public static void polygon(float cx, float cy, float r, int sides, float rot, int color) {
        for (int i = 0; i < sides; i++) {
            float a0 = rot + TAU * i / sides, a1 = rot + TAU * (i + 1) / sides;
            tri(cx, cy, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r,
                    cy + (float) Math.sin(a1) * r, color);
        }
    }

    /** A riveted brass frame for panels. */
    public static void brassFrame(float x0, float y0, float x1, float y1, float t, int fill) {
        rect(x0, y0, x1, y1, 0xFF3B2614);
        rectV(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFE3B65A, 0xFF9C6B26);
        rect(x0 + t, y0 + t, x1 - t, y1 - t, 0xFF3B2614);
        rect(x0 + t + 1, y0 + t + 1, x1 - t - 1, y1 - t - 1, fill);
        float rr = Math.max(1.0f, t * 0.35f);
        for (float[] p : new float[][]{{x0 + t * 0.5f, y0 + t * 0.5f}, {x1 - t * 0.5f, y0 + t * 0.5f}, {x0 + t * 0.5f, y1 - t * 0.5f},
                {x1 - t * 0.5f, y1 - t * 0.5f}}) {
            circle(p[0], p[1], rr + 0.6f, 0xFF5A3A16);
            circle(p[0] - 0.3f, p[1] - 0.3f, rr, 0xFFF4D488);
        }
    }

    private static int segments(float r) {
        return Math.max(12, Math.min(96, (int) (r * 0.9f) + 12));
    }
}
