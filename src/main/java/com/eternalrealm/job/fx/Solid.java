package com.eternalrealm.job.fx;

/**
 * Solid things in the finales (a rifle, a scythe, a hammer and its nail, a rock from the sky, a giant shield and
 * blade, the bones of the skeleton king): boxes, prisms and blades of flat quads, each face shaded by a fixed light from
 * above (a key light, a soft fill and a cool rim), so plain colours read as shapes. Drawn into the brush's solid sink.
 */
public final class Solid {
    /** Where the light comes from (upper left behind the viewer, like Minecraft's entity light). */
    static final V3 KEY = new V3(0.32, 0.86, 0.4).unit();
    private static final V3 FILL = new V3(-0.5, 0.2, -0.6).unit();

    private Solid() {
    }

    /** {@code rgb} lit for a face whose outward normal is {@code n}, with {@code alpha} (ARGB). */
    public static int shade(int rgb, V3 n, double alpha) {
        double key = Math.max(0.0, n.dot(KEY));
        double fill = Math.max(0.0, n.dot(FILL));
        double up = 0.5 + 0.5 * n.y();
        double k = 0.42 + 0.5 * key + 0.12 * fill + 0.1 * up;
        int r = (int) Math.min(255.0, ((rgb >> 16) & 0xFF) * k);
        int g = (int) Math.min(255.0, ((rgb >> 8) & 0xFF) * k);
        int b = (int) Math.min(255.0, (rgb & 0xFF) * k);
        int a = (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, alpha)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** One face (corners in order around it), shaded by its own normal. */
    public static void face(Brush b, V3 p0, V3 p1, V3 p2, V3 p3, int rgb, double alpha) {
        V3 n = p1.sub(p0).cross(p3.sub(p0));
        if (n.lengthSqr() < 1.0E-12) {
            n = p2.sub(p1).cross(p3.sub(p1));
        }
        int c = shade(rgb, n.lengthSqr() < 1.0E-12 ? V3.UP : n.unit(), alpha);
        b.solidQuad(p0, c, p1, c, p2, c, p3, c);
    }

    /** A face whose two ends have their own colours (a blade darkening towards its back, a gradient on a shield). */
    public static void face(Brush b, V3 p0, V3 p1, V3 p2, V3 p3, int rgb01, int rgb23, double alpha) {
        V3 n = p1.sub(p0).cross(p3.sub(p0));
        V3 unit = n.lengthSqr() < 1.0E-12 ? V3.UP : n.unit();
        int c01 = shade(rgb01, unit, alpha);
        int c23 = shade(rgb23, unit, alpha);
        b.solidQuad(p0, c01, p1, c01, p2, c23, p3, c23);
    }

    /**
     * A box centred on {@code c} with half sizes {@code hx, hy, hz} along the (unit, perpendicular) axes
     * {@code ax, ay, az}.
     */
    public static void box(Brush b, V3 c, V3 ax, V3 ay, V3 az, double hx, double hy, double hz, int rgb,
                           double alpha) {
        if (!b.drawsSolids() || alpha <= 0.004) {
            return;
        }
        V3 x = ax.scale(hx);
        V3 y = ay.scale(hy);
        V3 z = az.scale(hz);
        V3 p000 = c.sub(x).sub(y).sub(z);
        V3 p100 = c.add(x).sub(y).sub(z);
        V3 p010 = c.sub(x).add(y).sub(z);
        V3 p110 = c.add(x).add(y).sub(z);
        V3 p001 = c.sub(x).sub(y).add(z);
        V3 p101 = c.add(x).sub(y).add(z);
        V3 p011 = c.sub(x).add(y).add(z);
        V3 p111 = c.add(x).add(y).add(z);
        boolean flip = ax.cross(ay).dot(az) < 0.0;
        quad(b, flip, p100, p110, p111, p101, rgb, alpha); // +x
        quad(b, flip, p000, p001, p011, p010, rgb, alpha); // -x
        quad(b, flip, p010, p011, p111, p110, rgb, alpha); // +y
        quad(b, flip, p000, p100, p101, p001, rgb, alpha); // -y
        quad(b, flip, p001, p101, p111, p011, rgb, alpha); // +z
        quad(b, flip, p000, p010, p110, p100, rgb, alpha); // -z
    }

    private static void quad(Brush b, boolean flip, V3 p0, V3 p1, V3 p2, V3 p3, int rgb, double alpha) {
        if (flip) {
            face(b, p0, p3, p2, p1, rgb, alpha);
        } else {
            face(b, p0, p1, p2, p3, rgb, alpha);
        }
    }

    /**
     * A box from {@code a} to {@code e} (its long axis), {@code w} wide along {@code side} and {@code h} thick across
     * both.
     */
    public static void bar(Brush b, V3 a, V3 e, V3 side, double w, double h, int rgb, double alpha) {
        V3 along = e.sub(a);
        double length = along.length();
        if (length < 1.0E-6) {
            return;
        }
        V3 ax = along.scale(1.0 / length);
        V3 az = side.sub(ax.scale(side.dot(ax)));
        az = az.lengthSqr() < 1.0E-9 ? V3.basis(ax)[0] : az.unit();
        V3 ay = az.cross(ax).unit();
        box(b, a.lerp(e, 0.5), ax, ay, az, length * 0.5, h * 0.5, w * 0.5, rgb, alpha);
    }

    /** One face with its outward normal given (corners in any order around it). */
    public static void face(Brush b, V3 p0, V3 p1, V3 p2, V3 p3, V3 normal, int rgb01, int rgb23, double alpha) {
        int c01 = shade(rgb01, normal, alpha);
        int c23 = shade(rgb23, normal, alpha);
        b.solidQuad(p0, c01, p1, c01, p2, c23, p3, c23);
    }

    /**
     * A prism along {@code a}..{@code e} with {@code sides} faces, radius {@code ra} at {@code a} and {@code rb} at
     * {@code e} (a cone when one is 0), its first edge towards {@code ref}; capped at both ends.
     */
    public static void prism(Brush b, V3 a, V3 e, double ra, double rb, int sides, V3 ref, int rgb, double alpha) {
        prism(b, a, e, ra, rb, sides, ref, rgb, rgb, alpha);
    }

    /** A prism whose colour runs from {@code rgbA} at {@code a} to {@code rgbE} at {@code e}. */
    public static void prism(Brush b, V3 a, V3 e, double ra, double rb, int sides, V3 ref, int rgbA, int rgbE,
                             double alpha) {
        if (!b.drawsSolids() || alpha <= 0.004) {
            return;
        }
        V3 along = e.sub(a);
        if (along.lengthSqr() < 1.0E-10) {
            return;
        }
        V3 ax = along.unit();
        V3 u = ref.sub(ax.scale(ref.dot(ax)));
        u = u.lengthSqr() < 1.0E-9 ? V3.basis(ax)[0] : u.unit();
        V3 v = ax.cross(u).unit();
        int n = Math.max(3, sides);
        V3[] ringA = new V3[n];
        V3[] ringB = new V3[n];
        V3[] out = new V3[n];
        for (int i = 0; i < n; i++) {
            double t = Math.PI * 2.0 * (i + 0.5) / n;
            out[i] = u.scale(Math.cos(t)).add(v.scale(Math.sin(t)));
            double tc = Math.PI * 2.0 * i / n;
            V3 d = u.scale(Math.cos(tc)).add(v.scale(Math.sin(tc)));
            ringA[i] = a.plus(d, ra);
            ringB[i] = e.plus(d, rb);
        }
        // the sides lean with the taper
        double lean = (ra - rb) / along.length();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            V3 normal = out[i].add(ax.scale(lean)).unit();
            face(b, ringA[i], ringA[j], ringB[j], ringB[i], normal, rgbA, rgbE, alpha);
        }
        V3 back = ax.scale(-1.0);
        for (int i = 1; i < n - 1 && ra > 1.0E-4; i++) {
            face(b, ringA[0], ringA[i + 1], ringA[i], ringA[i], back, rgbA, rgbA, alpha);
        }
        for (int i = 1; i < n - 1 && rb > 1.0E-4; i++) {
            face(b, ringB[0], ringB[i], ringB[i + 1], ringB[i + 1], ax, rgbE, rgbE, alpha);
        }
    }

    /**
     * A flat blade with a thickness: its two edges are point lists of the same length ({@code edgeA[i]} across from
     * {@code edgeB[i]}), {@code normal} the side one face looks to. The faces narrow to a sharp edge along
     * {@code edgeA} (coloured {@code edgeRgb}, blending to {@code rgb} at the back); the back along {@code edgeB} and
     * the two ends are closed.
     */
    public static void blade(Brush b, V3[] edgeA, V3[] edgeB, V3 normal, double thickness, int rgb, int edgeRgb,
                             double alpha) {
        if (!b.drawsSolids() || alpha <= 0.004 || edgeA.length < 2 || edgeA.length != edgeB.length) {
            return;
        }
        V3 nu = normal.unit();
        V3 n = nu.scale(thickness * 0.5);
        V3 down = nu.scale(-1.0);
        int count = edgeA.length;
        for (int i = 0; i < count - 1; i++) {
            V3 a0 = edgeA[i];
            V3 a1 = edgeA[i + 1];
            V3 b0 = edgeB[i];
            V3 b1 = edgeB[i + 1];
            V3 across = b0.sub(a0).add(b1.sub(a1));
            V3 backOut = across.lengthSqr() < 1.0E-12 ? nu : across.unit();
            face(b, a0, a1, b1.add(n), b0.add(n), nu, edgeRgb, rgb, alpha);
            face(b, a0, a1, b1.sub(n), b0.sub(n), down, edgeRgb, rgb, alpha);
            face(b, b0.add(n), b1.add(n), b1.sub(n), b0.sub(n), backOut, rgb, rgb, alpha);
        }
        V3 startOut = edgeA[0].sub(edgeA[1]).unit(nu);
        V3 endOut = edgeA[count - 1].sub(edgeA[count - 2]).unit(nu);
        face(b, edgeA[0], edgeB[0].add(n), edgeB[0].sub(n), edgeA[0], startOut, rgb, rgb, alpha);
        face(b, edgeA[count - 1], edgeB[count - 1].add(n), edgeB[count - 1].sub(n), edgeA[count - 1], endOut, rgb,
                rgb, alpha);
    }
}
