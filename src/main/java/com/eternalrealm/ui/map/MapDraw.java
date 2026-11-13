package com.eternalrealm.ui.map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Shapes the map views draw that {@link GuiGraphics} has no call for: filled and textured discs, rings, triangles and
 * free quads, at float positions. Every call flushes the pending GUI batch first so that the drawing order holds.
 * Colours are ARGB. Render thread only.
 */
public final class MapDraw {
    private MapDraw() {
    }

    private static BufferBuilder begin(GuiGraphics graphics, boolean textured) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(textured ? GameRenderer::getPositionTexShader : GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, textured ? DefaultVertexFormat.POSITION_TEX
                : DefaultVertexFormat.POSITION_COLOR);
        return buffer;
    }

    private static void end(BufferBuilder buffer) {
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    private static void colored(BufferBuilder buffer, Matrix4f matrix, float x, float y, int argb) {
        buffer.vertex(matrix, x, y, 0.0F).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }

    /** A filled circle. */
    public static void disc(GuiGraphics graphics, float cx, float cy, float radius, int argb) {
        ring(graphics, cx, cy, 0.0F, radius, argb);
    }

    /** A filled annulus between two radii. */
    public static void ring(GuiGraphics graphics, float cx, float cy, float inner, float outer, int argb) {
        if (outer <= 0.0F || ((argb >>> 24) & 0xFF) == 0) {
            return;
        }
        int segments = Math.max(16, Math.min(192, (int) (outer * 1.2F)));
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = begin(graphics, false);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            if (inner <= 0.0F) {
                colored(buffer, matrix, cx, cy, argb);
                colored(buffer, matrix, cx + c1 * outer, cy + s1 * outer, argb);
                colored(buffer, matrix, cx + c0 * outer, cy + s0 * outer, argb);
            } else {
                colored(buffer, matrix, cx + c0 * inner, cy + s0 * inner, argb);
                colored(buffer, matrix, cx + c1 * outer, cy + s1 * outer, argb);
                colored(buffer, matrix, cx + c0 * outer, cy + s0 * outer, argb);
                colored(buffer, matrix, cx + c0 * inner, cy + s0 * inner, argb);
                colored(buffer, matrix, cx + c1 * inner, cy + s1 * inner, argb);
                colored(buffer, matrix, cx + c1 * outer, cy + s1 * outer, argb);
            }
        }
        end(buffer);
    }

    /**
     * Many small dots in one draw call: each centre ({x, y} pairs, {@code count} of them) gets a dark disc of
     * {@code outline} radius under a {@code fill} disc of {@code radius}.
     */
    public static void dots(GuiGraphics graphics, float[] centres, int count, float radius, float outline, int fill,
                            int outlineArgb) {
        if (count <= 0) {
            return;
        }
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = begin(graphics, false);
        int segments = 8;
        for (int pass = 0; pass < 2; pass++) {
            float r = pass == 0 ? outline : radius;
            int argb = pass == 0 ? outlineArgb : fill;
            for (int n = 0; n < count; n++) {
                float cx = centres[n * 2];
                float cy = centres[n * 2 + 1];
                for (int i = 0; i < segments; i++) {
                    double a0 = Math.PI * 2.0 * i / segments;
                    double a1 = Math.PI * 2.0 * (i + 1) / segments;
                    colored(buffer, matrix, cx, cy, argb);
                    colored(buffer, matrix, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, argb);
                    colored(buffer, matrix, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, argb);
                }
            }
        }
        end(buffer);
    }

    /** A filled triangle. */
    public static void triangle(GuiGraphics graphics, float x1, float y1, float x2, float y2, float x3, float y3,
                                int argb) {
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = begin(graphics, false);
        colored(buffer, matrix, x1, y1, argb);
        colored(buffer, matrix, x2, y2, argb);
        colored(buffer, matrix, x3, y3, argb);
        colored(buffer, matrix, x1, y1, argb);
        colored(buffer, matrix, x3, y3, argb);
        colored(buffer, matrix, x2, y2, argb);
        end(buffer);
    }

    /** A filled axis-aligned rectangle at float positions. */
    public static void rect(GuiGraphics graphics, float x0, float y0, float x1, float y1, int argb) {
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = begin(graphics, false);
        colored(buffer, matrix, x0, y0, argb);
        colored(buffer, matrix, x0, y1, argb);
        colored(buffer, matrix, x1, y1, argb);
        colored(buffer, matrix, x0, y0, argb);
        colored(buffer, matrix, x1, y1, argb);
        colored(buffer, matrix, x1, y0, argb);
        end(buffer);
    }

    /** The outline of a rectangle, {@code width} thick, inside its bounds. */
    public static void frame(GuiGraphics graphics, float x0, float y0, float x1, float y1, float width, int argb) {
        rect(graphics, x0, y0, x1, y0 + width, argb);
        rect(graphics, x0, y1 - width, x1, y1, argb);
        rect(graphics, x0, y0 + width, x0 + width, y1 - width, argb);
        rect(graphics, x1 - width, y0 + width, x1, y1 - width, argb);
    }

    /**
     * An arrow (a notched triangle) pointing at {@code angle} (radians, 0 = up, clockwise), centred on (cx, cy).
     */
    public static void arrow(GuiGraphics graphics, float cx, float cy, float size, double angle, int fill, int outline) {
        float[] tip = rotate(0.0F, -size, angle);
        float[] left = rotate(-size * 0.72F, size * 0.8F, angle);
        float[] right = rotate(size * 0.72F, size * 0.8F, angle);
        float[] notch = rotate(0.0F, size * 0.35F, angle);
        float grow = 1.0F + 1.6F / Math.max(1.0F, size);
        triangle(graphics, cx + tip[0] * grow, cy + tip[1] * grow, cx + left[0] * grow, cy + left[1] * grow,
                cx + notch[0] * grow, cy + notch[1] * grow, outline);
        triangle(graphics, cx + tip[0] * grow, cy + tip[1] * grow, cx + notch[0] * grow, cy + notch[1] * grow,
                cx + right[0] * grow, cy + right[1] * grow, outline);
        triangle(graphics, cx + tip[0], cy + tip[1], cx + left[0], cy + left[1], cx + notch[0], cy + notch[1], fill);
        triangle(graphics, cx + tip[0], cy + tip[1], cx + notch[0], cy + notch[1], cx + right[0], cy + right[1], fill);
    }

    /** A diamond (rotated square) marker. */
    /**
     * A waystone (転移碑): a small obelisk with a crystal above it; glowing blue once the player has recorded it,
     * grey before. {@code (cx, cy)} is the foot of the stone's middle.
     */
    public static void waystone(GuiGraphics graphics, float cx, float cy, float size, boolean recorded) {
        float half = size * 0.38F;
        float top = cy - size * 0.55F;
        float bottom = cy + size * 0.5F;
        rect(graphics, cx - half - 1.0F, top - 1.0F, cx + half + 1.0F, bottom + 1.0F, 0xFF000000);
        rect(graphics, cx - half, top, cx + half, bottom, recorded ? 0xFF9AB0D0 : 0xFF808080);
        rect(graphics, cx - half * 0.35F, top + size * 0.15F, cx + half * 0.35F, bottom - size * 0.15F,
                recorded ? 0xFF5FE0FF : 0xFF5A5A5A);
        float crystal = cy - size * 0.95F;
        diamond(graphics, cx, crystal, size * 0.36F + 1.0F, 0xFF000000);
        diamond(graphics, cx, crystal, size * 0.36F, recorded ? 0xFF8FF4FF : 0xFFB0B0B0);
    }

    public static void diamond(GuiGraphics graphics, float cx, float cy, float radius, int argb) {
        triangle(graphics, cx, cy - radius, cx - radius, cy, cx + radius, cy, argb);
        triangle(graphics, cx, cy + radius, cx - radius, cy, cx + radius, cy, argb);
    }

    private static float[] rotate(float x, float y, double angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        return new float[] {x * cos - y * sin, x * sin + y * cos};
    }

    /**
     * A disc filled with a texture: the texture point ({@code u}, {@code v}) (0..1) lies at the centre, and
     * {@code uvRadius} of texture space spans the disc's radius; the texture is turned by {@code rotation} (radians,
     * clockwise on screen).
     */
    public static void texturedDisc(GuiGraphics graphics, ResourceLocation texture, float cx, float cy, float radius,
                                    float u, float v, float uvRadius, double rotation) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = begin(graphics, true);
        RenderSystem.setShaderTexture(0, texture);
        int segments = Math.max(24, Math.min(128, (int) radius));
        double cos = Math.cos(-rotation);
        double sin = Math.sin(-rotation);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            textured(buffer, matrix, cx, cy, u, v);
            corner(buffer, matrix, cx, cy, radius, u, v, uvRadius, a1, cos, sin);
            corner(buffer, matrix, cx, cy, radius, u, v, uvRadius, a0, cos, sin);
        }
        end(buffer);
    }

    private static void corner(BufferBuilder buffer, Matrix4f matrix, float cx, float cy, float radius, float u, float v,
                               float uvRadius, double angle, double cos, double sin) {
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        // the screen offset turned back by the map rotation gives the texture offset
        double tu = dx * cos - dy * sin;
        double tv = dx * sin + dy * cos;
        textured(buffer, matrix, (float) (cx + dx * radius), (float) (cy + dy * radius), (float) (u + tu * uvRadius),
                (float) (v + tv * uvRadius));
    }

    private static void textured(BufferBuilder buffer, Matrix4f matrix, float x, float y, float u, float v) {
        buffer.vertex(matrix, x, y, 0.0F).uv(u, v).endVertex();
    }

    /** A textured rectangle with free texture coordinates (they may lie outside 0..1 where the texture clamps). */
    public static void texturedRect(GuiGraphics graphics, ResourceLocation texture, float x0, float y0, float x1,
                                    float y1, float u0, float v0, float u1, float v1) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = begin(graphics, true);
        RenderSystem.setShaderTexture(0, texture);
        textured(buffer, matrix, x0, y0, u0, v0);
        textured(buffer, matrix, x0, y1, u0, v1);
        textured(buffer, matrix, x1, y1, u1, v1);
        textured(buffer, matrix, x0, y0, u0, v0);
        textured(buffer, matrix, x1, y1, u1, v1);
        textured(buffer, matrix, x1, y0, u1, v0);
        end(buffer);
    }
}
