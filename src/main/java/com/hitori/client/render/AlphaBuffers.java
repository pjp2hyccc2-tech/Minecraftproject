package com.hitori.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import javax.annotation.Nullable;

/**
 * Wraps a buffer source so everything drawn through it is faded: alpha is scaled (translucent layers) and, for the
 * additive glow layer, the colour itself is scaled (an additive glow has no alpha to fade).
 */
public final class AlphaBuffers implements MultiBufferSource {
    private final MultiBufferSource inner;
    private final float alpha;
    private final float glow;
    @Nullable private final RenderType additive;

    public AlphaBuffers(MultiBufferSource inner, float alpha, float glow, @Nullable RenderType additive) {
        this.inner = inner;
        this.alpha = alpha;
        this.glow = glow;
        this.additive = additive;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        VertexConsumer v = inner.getBuffer(type);
        if (type == additive) return glow >= 0.999f ? v : new Faded(v, glow, true);
        if (alpha >= 0.999f) return v;
        return new Faded(v, alpha, false);
    }

    private static final class Faded implements VertexConsumer {
        private final VertexConsumer v;
        private final float a;
        private final boolean glow;

        Faded(VertexConsumer v, float a, boolean glow) {
            this.v = v;
            this.a = a;
            this.glow = glow;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            v.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int alpha) {
            if (glow) v.color((int) (r * a), (int) (g * a), (int) (b * a), alpha);
            else v.color(r, g, b, (int) (alpha * a));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float vv) {
            v.uv(u, vv);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int vv) {
            v.overlayCoords(u, vv);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int vv) {
            v.uv2(u, vv);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            v.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            v.endVertex();
        }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float alpha, float u, float vv, int overlay, int light,
                           float nx, float ny, float nz) {
            if (glow) v.vertex(x, y, z, r * a, g * a, b * a, alpha, u, vv, overlay, light, nx, ny, nz);
            else v.vertex(x, y, z, r, g, b, alpha * a, u, vv, overlay, light, nx, ny, nz);
        }

        @Override
        public void defaultColor(int r, int g, int b, int alpha) {
            v.defaultColor(r, g, b, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            v.unsetDefaultColor();
        }
    }
}
