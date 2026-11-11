package com.eternalrealm.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Writes entity-format vertices with the pose applied by hand, so renderers drawn hundreds of times a frame (bunting)
 * allocate nothing per vertex: the pose and normal matrices are copied into fields once per draw and every vertex goes
 * out through the single bulk {@link VertexConsumer#vertex(float, float, float, float, float, float, float, float,
 * float, int, int, float, float, float)} call.
 */
final class QuadWriter {
    private VertexConsumer out;
    private float m00;
    private float m01;
    private float m02;
    private float m10;
    private float m11;
    private float m12;
    private float m20;
    private float m21;
    private float m22;
    private float m30;
    private float m31;
    private float m32;
    private float n00;
    private float n01;
    private float n02;
    private float n10;
    private float n11;
    private float n12;
    private float n20;
    private float n21;
    private float n22;
    private int light;
    private float red = 1.0F;
    private float green = 1.0F;
    private float blue = 1.0F;
    private float alpha = 1.0F;

    /** Starts writing into {@code consumer} with the current pose. */
    QuadWriter begin(PoseStack.Pose pose, VertexConsumer consumer, int packedLight) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        m00 = m.m00();
        m01 = m.m01();
        m02 = m.m02();
        m10 = m.m10();
        m11 = m.m11();
        m12 = m.m12();
        m20 = m.m20();
        m21 = m.m21();
        m22 = m.m22();
        m30 = m.m30();
        m31 = m.m31();
        m32 = m.m32();
        n00 = n.m00();
        n01 = n.m01();
        n02 = n.m02();
        n10 = n.m10();
        n11 = n.m11();
        n12 = n.m12();
        n20 = n.m20();
        n21 = n.m21();
        n22 = n.m22();
        out = consumer;
        light = packedLight;
        red = 1.0F;
        green = 1.0F;
        blue = 1.0F;
        alpha = 1.0F;
        return this;
    }

    /** Continues with the same pose in another buffer. */
    QuadWriter target(VertexConsumer consumer) {
        out = consumer;
        return this;
    }

    QuadWriter light(int packedLight) {
        light = packedLight;
        return this;
    }

    QuadWriter color(float r, float g, float b, float a) {
        red = r;
        green = g;
        blue = b;
        alpha = a;
        return this;
    }

    /** One vertex at a position relative to the block (same space as the pose stack's block origin). */
    void vertex(double x, double y, double z, float u, float v, float nx, float ny, float nz) {
        float px = (float) x;
        float py = (float) y;
        float pz = (float) z;
        out.vertex(m00 * px + m10 * py + m20 * pz + m30, m01 * px + m11 * py + m21 * pz + m31,
                m02 * px + m12 * py + m22 * pz + m32, red, green, blue, alpha, u, v, OverlayTexture.NO_OVERLAY, light,
                n00 * nx + n10 * ny + n20 * nz, n01 * nx + n11 * ny + n21 * nz, n02 * nx + n12 * ny + n22 * nz);
    }
}
