package com.eternalrealm.job.fx;

import java.awt.image.BufferedImage;

/**
 * A tiny software renderer for previews of the skill light: projects the engine's quads through a pinhole camera and
 * adds them into an image the way the game does (additive light, clamped), over a night or a day backdrop with a
 * ground grid and a dark figure for scale. Not a test; used by {@link SkillFxPreview}.
 */
final class FxRaster implements Light {
    final int w;
    final int h;
    final float[] r;
    final float[] g;
    final float[] b;
    private final V3 eye;
    private final V3 fwd;
    private final V3 right;
    private final V3 up;
    private final double focal;
    private final double[][] quad = new double[4][3];
    private final int[] colours = new int[4];
    private int n;

    FxRaster(int w, int h, V3 eye, V3 target, double fovDegrees) {
        this.w = w;
        this.h = h;
        this.r = new float[w * h];
        this.g = new float[w * h];
        this.b = new float[w * h];
        this.eye = eye;
        this.fwd = target.sub(eye).unit();
        this.right = fwd.cross(V3.UP).unit();
        this.up = right.cross(fwd).unit();
        this.focal = (h * 0.5) / Math.tan(Math.toRadians(fovDegrees) * 0.5);
    }

    V3 eye() {
        return eye;
    }

    /** A backdrop: a sky gradient and a ground with a grid, and a dark figure standing at {@code figure}. */
    void backdrop(boolean day, V3 figure) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] ray = ray(x + 0.5, y + 0.5);
                int i = y * w + x;
                if (ray[1] < -1.0E-4) {
                    double t = -eye.y() / ray[1];
                    double gx = eye.x() + ray[0] * t;
                    double gz = eye.z() + ray[2] * t;
                    boolean line = Math.abs(gx - Math.rint(gx)) < 0.03 * Math.max(1.0, t * 0.08)
                            || Math.abs(gz - Math.rint(gz)) < 0.03 * Math.max(1.0, t * 0.08);
                    float fog = (float) Math.min(1.0, t / 60.0);
                    if (day) {
                        set(i, mixf(0.36F, 0.62F, fog), mixf(0.55F, 0.78F, fog), mixf(0.28F, 0.92F, fog));
                    } else {
                        set(i, mixf(0.05F, 0.08F, fog), mixf(0.07F, 0.09F, fog), mixf(0.08F, 0.14F, fog));
                    }
                    if (line) {
                        float k = day ? -0.05F : 0.05F;
                        r[i] += k;
                        g[i] += k;
                        b[i] += k;
                    }
                } else {
                    float k = (float) Math.min(1.0, ray[1] * 2.0);
                    if (day) {
                        set(i, mixf(0.74F, 0.42F, k), mixf(0.86F, 0.62F, k), mixf(0.98F, 0.96F, k));
                    } else {
                        set(i, mixf(0.06F, 0.01F, k), mixf(0.07F, 0.02F, k), mixf(0.14F, 0.06F, k));
                    }
                }
            }
        }
        // a figure for scale: a dark column 1.8 blocks tall
        if (figure != null) {
            for (double yy = 0.0; yy < 1.8; yy += 0.02) {
                for (double a = 0.0; a < Math.PI * 2.0; a += 0.08) {
                    V3 p = figure.add(Math.cos(a) * (yy > 1.45 ? 0.22 : 0.3), yy, Math.sin(a) * (yy > 1.45 ? 0.22 : 0.3));
                    int[] s = project(p);
                    if (s != null) {
                        int i = s[1] * w + s[0];
                        set(i, day ? 0.18F : 0.16F, day ? 0.16F : 0.15F, day ? 0.2F : 0.2F);
                    }
                }
            }
        }
    }

    private static float mixf(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private void set(int i, float rr, float gg, float bb) {
        r[i] = rr;
        g[i] = gg;
        b[i] = bb;
    }

    private double[] ray(double px, double py) {
        double cx = (px - w * 0.5) / focal;
        double cy = -(py - h * 0.5) / focal;
        V3 d = fwd.add(right.scale(cx)).add(up.scale(cy)).unit();
        return new double[]{d.x(), d.y(), d.z()};
    }

    private int[] project(V3 p) {
        V3 d = p.sub(eye);
        double z = d.dot(fwd);
        if (z < 0.1) {
            return null;
        }
        int x = (int) Math.round(w * 0.5 + d.dot(right) / z * focal);
        int y = (int) Math.round(h * 0.5 - d.dot(up) / z * focal);
        return x < 0 || y < 0 || x >= w || y >= h ? null : new int[]{x, y};
    }

    @Override
    public void vertex(double x, double y, double z, int argb) {
        quad[n][0] = x;
        quad[n][1] = y;
        quad[n][2] = z;
        colours[n] = argb;
        n++;
        if (n == 4) {
            n = 0;
            tri(0, 1, 2);
            tri(0, 2, 3);
        }
    }

    private final double[] sx = new double[3];
    private final double[] sy = new double[3];
    private final float[][] sc = new float[3][4];

    private void tri(int i0, int i1, int i2) {
        int[] idx = {i0, i1, i2};
        for (int k = 0; k < 3; k++) {
            double dx = quad[idx[k]][0] - eye.x();
            double dy = quad[idx[k]][1] - eye.y();
            double dz = quad[idx[k]][2] - eye.z();
            double z = dx * fwd.x() + dy * fwd.y() + dz * fwd.z();
            if (z < 0.15) {
                return;
            }
            sx[k] = w * 0.5 + (dx * right.x() + dy * right.y() + dz * right.z()) / z * focal;
            sy[k] = h * 0.5 - (dx * up.x() + dy * up.y() + dz * up.z()) / z * focal;
            int c = colours[idx[k]];
            float a = ((c >>> 24) & 0xFF) / 255.0F;
            sc[k][0] = ((c >> 16) & 0xFF) / 255.0F * a;
            sc[k][1] = ((c >> 8) & 0xFF) / 255.0F * a;
            sc[k][2] = (c & 0xFF) / 255.0F * a;
        }
        double area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
        if (Math.abs(area) < 1.0E-9) {
            return;
        }
        int minX = (int) Math.max(0, Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
        int maxX = (int) Math.min(w - 1, Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
        int maxY = (int) Math.min(h - 1, Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
        if (maxX - minX > w * 3 || maxY - minY > h * 3) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            double py = y + 0.5;
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double w0 = ((sx[1] - px) * (sy[2] - py) - (sx[2] - px) * (sy[1] - py)) / area;
                double w1 = ((sx[2] - px) * (sy[0] - py) - (sx[0] - px) * (sy[2] - py)) / area;
                double w2 = 1.0 - w0 - w1;
                if (w0 < 0.0 || w1 < 0.0 || w2 < 0.0) {
                    continue;
                }
                int i = y * w + x;
                r[i] += (float) (sc[0][0] * w0 + sc[1][0] * w1 + sc[2][0] * w2);
                g[i] += (float) (sc[0][1] * w0 + sc[1][1] * w1 + sc[2][1] * w2);
                b[i] += (float) (sc[0][2] * w0 + sc[1][2] * w1 + sc[2][2] * w2);
            }
        }
    }

    BufferedImage image() {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                img.setRGB(x, y, (clamp(r[i]) << 16) | (clamp(g[i]) << 8) | clamp(b[i]));
            }
        }
        return img;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0F)));
    }
}
