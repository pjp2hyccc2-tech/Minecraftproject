package com.eternalrealm.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Small immediate-mode helper for block entity renderers that build their geometry by hand (entity vertex format:
 * position, colour, uv, overlay, light, normal). Boxes are given in an arbitrary orthonormal frame so rotating parts
 * (sails, wheels, clock hands) need no pose stack juggling.
 */
final class Mesh {
    private final VertexConsumer out;
    private final Matrix4f pose;
    private final Matrix3f normal;
    private int light;
    private float r = 1.0F;
    private float g = 1.0F;
    private float b = 1.0F;
    private float a = 1.0F;

    Mesh(PoseStack.Pose pose, VertexConsumer out, int light) {
        this.out = out;
        this.pose = pose.pose();
        this.normal = pose.normal();
        this.light = light;
    }

    Mesh light(int packedLight) {
        this.light = packedLight;
        return this;
    }

    Mesh color(float red, float green, float blue, float alpha) {
        this.r = red;
        this.g = green;
        this.b = blue;
        this.a = alpha;
        return this;
    }

    void vertex(double x, double y, double z, float u, float v, float nx, float ny, float nz) {
        out.vertex(pose, (float) x, (float) y, (float) z).color(r, g, b, a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, nx, ny, nz).endVertex();
    }

    void vertex(double[] p, float u, float v, double[] n) {
        vertex(p[0], p[1], p[2], u, v, (float) n[0], (float) n[1], (float) n[2]);
    }

    /** A quad p0..p3 (front side: counter-clockwise as seen from where its normal points). */
    void quad(double[] p0, double[] p1, double[] p2, double[] p3, float u0, float v0, float u1, float v1) {
        double[] n = normalOf(p0, p1, p2);
        vertex(p0, u0, v0, n);
        vertex(p1, u0, v1, n);
        vertex(p2, u1, v1, n);
        vertex(p3, u1, v0, n);
    }

    /** The same quad seen from both sides. */
    void quadTwoSided(double[] p0, double[] p1, double[] p2, double[] p3, float u0, float v0, float u1, float v1) {
        quad(p0, p1, p2, p3, u0, v0, u1, v1);
        double[] n = normalOf(p0, p1, p2);
        double[] m = {-n[0], -n[1], -n[2]};
        vertex(p0, u0, v0, m);
        vertex(p3, u1, v0, m);
        vertex(p2, u1, v1, m);
        vertex(p1, u0, v1, m);
    }

    /**
     * A box centred at {@code c} with half extents {@code h} along the orthonormal axes {@code ax, ay, az} (right-handed:
     * ax x ay = az). Every face shows the texture rectangle (u0, v0)-(u1, v1); the long side of a face follows u.
     */
    void box(double[] c, double[] ax, double[] ay, double[] az, double hx, double hy, double hz,
             float u0, float v0, float u1, float v1) {
        double[] handed = cross(ax, ay);
        if (handed[0] * az[0] + handed[1] * az[1] + handed[2] * az[2] < 0.0) {
            az = new double[]{-az[0], -az[1], -az[2]};          // keep the faces pointing outwards
        }
        double[][] corner = new double[8][];
        for (int i = 0; i < 8; i++) {
            double sx = (i & 1) == 0 ? -hx : hx;
            double sy = (i & 2) == 0 ? -hy : hy;
            double sz = (i & 4) == 0 ? -hz : hz;
            corner[i] = new double[]{c[0] + ax[0] * sx + ay[0] * sy + az[0] * sz,
                    c[1] + ax[1] * sx + ay[1] * sy + az[1] * sz, c[2] + ax[2] * sx + ay[2] * sy + az[2] * sz};
        }
        // faces as corner index quads, counter-clockwise seen from outside
        face(corner, 4, 5, 7, 6, hx >= hy, u0, v0, u1, v1);   // +z
        face(corner, 1, 0, 2, 3, hx >= hy, u0, v0, u1, v1);   // -z
        face(corner, 5, 1, 3, 7, hz >= hy, u0, v0, u1, v1);   // +x
        face(corner, 0, 4, 6, 2, hz >= hy, u0, v0, u1, v1);   // -x
        face(corner, 6, 7, 3, 2, hx >= hz, u0, v0, u1, v1);   // +y
        face(corner, 0, 1, 5, 4, hx >= hz, u0, v0, u1, v1);   // -y
    }

    private void face(double[][] c, int i0, int i1, int i2, int i3, boolean widthAlongU,
                      float u0, float v0, float u1, float v1) {
        double[] p0 = c[i0];
        double[] p1 = c[i1];
        double[] p2 = c[i2];
        double[] p3 = c[i3];
        double[] n = normalOf(p0, p1, p2);
        if (widthAlongU) {
            vertex(p0, u0, v1, n);
            vertex(p1, u1, v1, n);
            vertex(p2, u1, v0, n);
            vertex(p3, u0, v0, n);
        } else {
            vertex(p0, u0, v0, n);
            vertex(p1, u0, v1, n);
            vertex(p2, u1, v1, n);
            vertex(p3, u1, v0, n);
        }
    }

    static double[] normalOf(double[] p0, double[] p1, double[] p2) {
        double ax = p1[0] - p0[0];
        double ay = p1[1] - p0[1];
        double az = p1[2] - p0[2];
        double bx = p2[0] - p0[0];
        double by = p2[1] - p0[1];
        double bz = p2[2] - p0[2];
        double nx = ay * bz - az * by;
        double ny = az * bx - ax * bz;
        double nz = ax * by - ay * bx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-9) {
            return new double[]{0.0, 1.0, 0.0};
        }
        return new double[]{nx / len, ny / len, nz / len};
    }

    static double[] add(double[] a, double[] b, double s) {
        return new double[]{a[0] + b[0] * s, a[1] + b[1] * s, a[2] + b[2] * s};
    }

    static double[] combo(double[] origin, double[] a, double sa, double[] b, double sb) {
        return new double[]{origin[0] + a[0] * sa + b[0] * sb, origin[1] + a[1] * sa + b[1] * sb,
                origin[2] + a[2] * sa + b[2] * sb};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
