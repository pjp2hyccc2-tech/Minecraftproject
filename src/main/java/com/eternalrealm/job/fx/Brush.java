package com.eternalrealm.job.fx;

/**
 * The shapes of light the skill effects are made of: crescents, rings, discs, star polygons, rune rings, camera-facing
 * ribbons, flares and sparkles, crystal spikes, columns, walls and lightning. Every shape is drawn as quads of
 * additive light; soft shapes fade to zero at their edges, so overlapping layers (a wide dim glow, the theme colour, a
 * thin white core) read as one glowing stroke.
 */
public final class Brush {
    /** Hard cap of quads per frame (a safety net; real shows stay far below). */
    public static final int MAX_QUADS = 60_000;

    /** Light this close to the camera (blocks) is gone; from here to {@link #NEAR_CLEAR} it fades in. */
    public static final double NEAR_GONE = 0.3;
    public static final double NEAR_CLEAR = 1.4;

    private final Light out;
    private final V3 camera;
    private int quads;
    private double nearGone = NEAR_GONE;
    private double nearClear = NEAR_CLEAR;

    public Brush(Light out, V3 camera) {
        this.out = out;
        this.camera = camera;
    }

    public V3 camera() {
        return camera;
    }

    /**
     * Sets how light near the camera thins out: nothing within {@code gone} blocks, full strength from {@code clear}
     * on. Light that would sit on the viewer's nose (a flare in their own hand, a ring around their own head) would
     * otherwise cover the screen.
     */
    public void nearFade(double gone, double clear) {
        this.nearGone = Math.max(0.0, gone);
        this.nearClear = Math.max(this.nearGone + 0.05, clear);
    }

    /** Back to the default near fade. */
    public void nearFade() {
        nearFade(NEAR_GONE, NEAR_CLEAR);
    }

    /** Quads drawn so far. */
    public int quads() {
        return quads;
    }

    // ------------------------------------------------------------------ raw quads

    /** A quad with {@code p0,p1} at strength {@code a01} and {@code p2,p3} at {@code a23}. */
    public void quad(V3 p0, V3 p1, V3 p2, V3 p3, int rgb, double a01, double a23) {
        if (a01 <= 0.004 && a23 <= 0.004) {
            return;
        }
        int c01 = Palette.argb(rgb, a01);
        int c23 = Palette.argb(rgb, a23);
        quad(p0, c01, p1, c01, p2, c23, p3, c23);
    }

    /** A quad with a colour per vertex (ARGB). */
    public void quad(V3 p0, int c0, V3 p1, int c1, V3 p2, int c2, V3 p3, int c3) {
        if (quads >= MAX_QUADS) {
            return;
        }
        int f0 = near(p0, c0);
        int f1 = near(p1, c1);
        int f2 = near(p2, c2);
        int f3 = near(p3, c3);
        if (((f0 | f1 | f2 | f3) >>> 24) == 0) {
            return;
        }
        quads++;
        out.vertex(p0.x(), p0.y(), p0.z(), f0);
        out.vertex(p1.x(), p1.y(), p1.z(), f1);
        out.vertex(p2.x(), p2.y(), p2.z(), f2);
        out.vertex(p3.x(), p3.y(), p3.z(), f3);
    }

    /** {@code argb} thinned by the vertex's nearness to the camera. */
    private int near(V3 p, int argb) {
        double dx = p.x() - camera.x();
        double dy = p.y() - camera.y();
        double dz = p.z() - camera.z();
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 >= nearClear * nearClear) {
            return argb;
        }
        double u = (Math.sqrt(d2) - nearGone) / (nearClear - nearGone);
        if (u <= 0.0) {
            return argb & 0x00FFFFFF;
        }
        double k = u * u * (3.0 - 2.0 * u);
        int a = (int) Math.round(((argb >>> 24) & 0xFF) * k);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** A triangle (a quad with its first corner doubled). */
    public void tri(V3 p0, int c0, V3 p1, int c1, V3 p2, int c2) {
        quad(p0, c0, p0, c0, p1, c1, p2, c2);
    }

    // ------------------------------------------------------------------ strokes facing the camera

    /** A strip from {@code a} to {@code b} turned to the camera, {@code wa}/{@code wb} wide. */
    public void ribbon(V3 a, V3 b, double wa, double wb, int rgb, double aa, double ab) {
        if (aa <= 0.004 && ab <= 0.004) {
            return;
        }
        V3 along = b.sub(a);
        if (along.lengthSqr() < 1.0E-8) {
            return;
        }
        V3 across = across(a.lerp(b, 0.5), along);
        V3 sa = across.scale(wa * 0.5);
        V3 sb = across.scale(wb * 0.5);
        quad(a.add(sa), a.sub(sa), b.sub(sb), b.add(sb), rgb, aa, ab);
    }

    /** A soft strip: full strength along its middle, fading to nothing at both edges. */
    public void softRibbon(V3 a, V3 b, double wa, double wb, int rgb, double aa, double ab) {
        if (aa <= 0.004 && ab <= 0.004) {
            return;
        }
        V3 along = b.sub(a);
        if (along.lengthSqr() < 1.0E-8) {
            return;
        }
        V3 across = across(a.lerp(b, 0.5), along);
        V3 sa = across.scale(wa * 0.5);
        V3 sb = across.scale(wb * 0.5);
        int ca = Palette.argb(rgb, aa);
        int cb = Palette.argb(rgb, ab);
        int z = Palette.argb(rgb, 0.0);
        quad(a, ca, a.add(sa), z, b.add(sb), z, b, cb);
        quad(a, ca, b, cb, b.sub(sb), z, a.sub(sa), z);
    }

    /** A glowing stroke: a wide dim deep glow, the glow colour and a thin white-hot core. */
    public void glow(V3 a, V3 b, double width, Palette p, double alpha) {
        glow(a, b, width, width, p, alpha, alpha);
    }

    public void glow(V3 a, V3 b, double wa, double wb, Palette p, double aa, double ab) {
        softRibbon(a, b, wa * 3.2, wb * 3.2, p.deep(), aa * 0.32, ab * 0.32);
        softRibbon(a, b, wa * 1.5, wb * 1.5, p.glow(), aa * 0.75, ab * 0.75);
        ribbon(a, b, wa * 0.42, wb * 0.42, p.core(), aa, ab);
    }

    /**
     * A strip through {@code points} turned to the camera, {@code widths[i]} wide and {@code alphas[i]} strong at each
     * point (joints are shared, so the strip bends smoothly).
     */
    public void strip(V3[] points, double[] widths, double[] alphas, int rgb, boolean soft) {
        strip(points, widths, alphas, rgb, soft, null);
    }

    /**
     * A strip through {@code points}; turned to the camera when {@code normal} is {@code null}, else lying flat in the
     * plane across {@code normal} (cracks on the ground, writing on a wall).
     */
    public void strip(V3[] points, double[] widths, double[] alphas, int rgb, boolean soft, V3 normal) {
        int n = points.length;
        if (n < 2) {
            return;
        }
        V3[] side = new V3[n];
        for (int i = 0; i < n; i++) {
            V3 along = points[Math.min(n - 1, i + 1)].sub(points[Math.max(0, i - 1)]);
            if (along.lengthSqr() < 1.0E-10) {
                side[i] = i > 0 ? side[i - 1] : new V3(0.0, 1.0, 0.0);
            } else if (normal == null) {
                side[i] = across(points[i], along);
            } else {
                V3 c = along.cross(normal);
                side[i] = c.lengthSqr() < 1.0E-10 ? (i > 0 ? side[i - 1] : new V3(1.0, 0.0, 0.0)) : c.unit();
            }
        }
        for (int i = 0; i < n - 1; i++) {
            if (alphas[i] <= 0.004 && alphas[i + 1] <= 0.004) {
                continue;
            }
            V3 s0 = side[i].scale(widths[i] * 0.5);
            V3 s1 = side[i + 1].scale(widths[i + 1] * 0.5);
            int c0 = Palette.argb(rgb, alphas[i]);
            int c1 = Palette.argb(rgb, alphas[i + 1]);
            if (soft) {
                int z = Palette.argb(rgb, 0.0);
                quad(points[i], c0, points[i].add(s0), z, points[i + 1].add(s1), z, points[i + 1], c1);
                quad(points[i], c0, points[i + 1], c1, points[i + 1].sub(s1), z, points[i].sub(s0), z);
            } else {
                quad(points[i].add(s0), c0, points[i].sub(s0), c0, points[i + 1].sub(s1), c1, points[i + 1].add(s1),
                        c1);
            }
        }
    }

    /** A glowing strip through points (deep, glow, core), turned to the camera. */
    public void glowStrip(V3[] points, double[] widths, double[] alphas, Palette p) {
        glowStrip(points, widths, alphas, p, null);
    }

    /** A glowing strip through points; lying across {@code normal} unless it is {@code null}. */
    public void glowStrip(V3[] points, double[] widths, double[] alphas, Palette p, V3 normal) {
        int n = points.length;
        double[] w = new double[n];
        double[] al = new double[n];
        for (int i = 0; i < n; i++) {
            w[i] = widths[i] * 3.0;
            al[i] = alphas[i] * 0.3;
        }
        strip(points, w, al, p.deep(), true, normal);
        for (int i = 0; i < n; i++) {
            w[i] = widths[i] * 1.4;
            al[i] = alphas[i] * 0.75;
        }
        strip(points, w, al, p.glow(), true, normal);
        for (int i = 0; i < n; i++) {
            w[i] = widths[i] * 0.4;
            al[i] = alphas[i];
        }
        strip(points, w, al, p.core(), false, normal);
    }

    /** A straight glowing stroke lying across {@code normal}. */
    public void flatGlow(V3 a, V3 b, double wa, double wb, V3 normal, Palette p, double aa, double ab) {
        glowStrip(new V3[]{a, b}, new double[]{wa, wb}, new double[]{aa, ab}, p, normal);
    }

    /** The unit vector across a stroke along {@code along} at {@code at}, as seen from the camera. */
    private V3 across(V3 at, V3 along) {
        V3 view = at.sub(camera);
        V3 c = along.cross(view);
        if (c.lengthSqr() < 1.0E-10) {
            c = along.cross(V3.UP);
            if (c.lengthSqr() < 1.0E-10) {
                c = along.cross(new V3(1.0, 0.0, 0.0));
            }
        }
        return c.unit();
    }

    // ------------------------------------------------------------------ shapes in a plane

    /** The point at {@code angle} (radians) on the circle of {@code radius} in the plane of {@code a} and {@code b}. */
    public static V3 onCircle(V3 center, V3 a, V3 b, double radius, double angle) {
        return center.plus(a, Math.cos(angle) * radius).plus(b, Math.sin(angle) * radius);
    }

    /** One piece of a band between the angles {@code t0} and {@code t1} (radians), {@code w0}/{@code w1} thick. */
    public void arcPiece(V3 center, V3 a, V3 b, double radius, double w0, double w1, double t0, double t1, int rgb,
                         double al0, double al1) {
        V3 in0 = onCircle(center, a, b, radius - w0 * 0.5, t0);
        V3 out0 = onCircle(center, a, b, radius + w0 * 0.5, t0);
        V3 in1 = onCircle(center, a, b, radius - w1 * 0.5, t1);
        V3 out1 = onCircle(center, a, b, radius + w1 * 0.5, t1);
        int c0 = Palette.argb(rgb, al0);
        int c1 = Palette.argb(rgb, al1);
        quad(in0, c0, out0, c0, out1, c1, in1, c1);
    }

    /** A soft band piece: strongest at the radius, fading inwards and outwards. */
    public void softArcPiece(V3 center, V3 a, V3 b, double radius, double w0, double w1, double t0, double t1, int rgb,
                             double al0, double al1) {
        V3 m0 = onCircle(center, a, b, radius, t0);
        V3 m1 = onCircle(center, a, b, radius, t1);
        V3 in0 = onCircle(center, a, b, Math.max(0.0, radius - w0 * 0.5), t0);
        V3 out0 = onCircle(center, a, b, radius + w0 * 0.5, t0);
        V3 in1 = onCircle(center, a, b, Math.max(0.0, radius - w1 * 0.5), t1);
        V3 out1 = onCircle(center, a, b, radius + w1 * 0.5, t1);
        int c0 = Palette.argb(rgb, al0);
        int c1 = Palette.argb(rgb, al1);
        int z = Palette.argb(rgb, 0.0);
        quad(in0, z, m0, c0, m1, c1, in1, z);
        quad(m0, c0, out0, z, out1, z, m1, c1);
    }

    /** A full ring, {@code width} thick, in the plane of {@code a} and {@code b}. */
    public void ring(V3 center, V3 a, V3 b, double radius, double width, int rgb, double alpha) {
        arc(center, a, b, radius, width, 0.0, Math.PI * 2.0, rgb, alpha, alpha, false);
    }

    /** A soft ring (fades to both edges). */
    public void softRing(V3 center, V3 a, V3 b, double radius, double width, int rgb, double alpha) {
        arc(center, a, b, radius, width, 0.0, Math.PI * 2.0, rgb, alpha, alpha, true);
    }

    /** A glowing ring: deep halo, colour and core. */
    public void glowRing(V3 center, V3 a, V3 b, double radius, double width, Palette p, double alpha) {
        softRing(center, a, b, radius, width * 3.4, p.deep(), alpha * 0.3);
        softRing(center, a, b, radius, width * 1.6, p.glow(), alpha * 0.7);
        ring(center, a, b, radius, width * 0.4, p.core(), alpha);
    }

    /** An arc band from {@code t0} to {@code t1} (radians) with strength going from {@code a0} to {@code a1}. */
    public void arc(V3 center, V3 a, V3 b, double radius, double width, double t0, double t1, int rgb, double a0,
                    double a1, boolean soft) {
        if ((a0 <= 0.004 && a1 <= 0.004) || radius <= 0.0) {
            return;
        }
        int steps = Math.max(6, (int) Math.ceil(Math.abs(t1 - t0) * Math.max(2.0, radius) * 2.2));
        steps = Math.min(steps, 96);
        for (int i = 0; i < steps; i++) {
            double u0 = (double) i / steps;
            double u1 = (double) (i + 1) / steps;
            double al0 = a0 + (a1 - a0) * u0;
            double al1 = a0 + (a1 - a0) * u1;
            double s0 = t0 + (t1 - t0) * u0;
            double s1 = t0 + (t1 - t0) * u1;
            if (soft) {
                softArcPiece(center, a, b, radius, width, width, s0, s1, rgb, al0, al1);
            } else {
                arcPiece(center, a, b, radius, width, width, s0, s1, rgb, al0, al1);
            }
        }
    }

    /**
     * A crescent sweeping from {@code from} towards {@code to} (radians) as far as {@code sweep} (0..1): brightest and
     * widest at its head, thinning and fading along its tail; a deep halo, the glow and a white core.
     */
    public void crescent(V3 center, V3 a, V3 b, double radius, double width, double from, double to, double sweep,
                         Palette p, double alpha) {
        if (alpha <= 0.004 || sweep <= 0.0) {
            return;
        }
        double head = from + (to - from) * Math.min(1.0, sweep);
        double span = Math.abs(head - from);
        int steps = Math.max(5, Math.min(64, (int) Math.ceil(span * Math.max(2.0, radius) * 1.8)));
        for (int i = 0; i < steps; i++) {
            double u0 = (double) i / steps;
            double u1 = (double) (i + 1) / steps;
            double t0 = from + (head - from) * u0;
            double t1 = from + (head - from) * u1;
            double al0 = alpha * (0.06 + 0.94 * u0 * u0);
            double al1 = alpha * (0.06 + 0.94 * u1 * u1);
            // the head is the widest part: the shape grows towards the head and narrows only at its very tip
            double w0 = width * (0.12 + 0.88 * Math.pow(u0, 0.7)) * (u0 > 0.92 ? (1.0 - (u0 - 0.92) * 6.0) : 1.0);
            double w1 = width * (0.12 + 0.88 * Math.pow(u1, 0.7)) * (u1 > 0.92 ? (1.0 - (u1 - 0.92) * 6.0) : 1.0);
            softArcPiece(center, a, b, radius, w0 * 2.6, w1 * 2.6, t0, t1, p.deep(), al0 * 0.3, al1 * 0.3);
            softArcPiece(center, a, b, radius, w0 * 1.2, w1 * 1.2, t0, t1, p.glow(), al0 * 0.8, al1 * 0.8);
            arcPiece(center, a, b, radius + w0 * 0.18, w0 * 0.3, w1 * 0.3, t0, t1, p.core(), al0, al1);
        }
    }

    /**
     * The same sweep as {@link #crescent} drawn as a stroke turned to the camera, so a cut stays a bright line from
     * any side (seen edge-on, a crescent alone would vanish into a thin thread).
     */
    public void sweepStroke(V3 center, V3 a, V3 b, double radius, double width, double from, double to, double sweep,
                            Palette p, double alpha) {
        if (alpha <= 0.004 || sweep <= 0.0) {
            return;
        }
        double head = from + (to - from) * Math.min(1.0, sweep);
        int n = Math.max(6, Math.min(40, (int) Math.ceil(Math.abs(head - from) * Math.max(2.0, radius) * 1.4)));
        V3[] pts = new V3[n + 1];
        double[] w = new double[n + 1];
        double[] al = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n;
            pts[i] = onCircle(center, a, b, radius, from + (head - from) * u);
            w[i] = width * (0.08 + 0.92 * Math.pow(u, 0.8)) * (u > 0.94 ? (1.0 - (u - 0.94) * 12.0) : 1.0);
            al[i] = alpha * (0.05 + 0.95 * u * u);
        }
        glowStrip(pts, w, al, p);
    }

    /** A soft disc (a pool of light): {@code centre} strength in the middle, {@code edge} at the rim. */
    public void disc(V3 center, V3 a, V3 b, double radius, int rgb, double centre, double edge) {
        if ((centre <= 0.004 && edge <= 0.004) || radius <= 0.0) {
            return;
        }
        int steps = Math.max(12, Math.min(48, (int) (radius * 6.0)));
        int cc = Palette.argb(rgb, centre);
        int ce = Palette.argb(rgb, edge);
        for (int i = 0; i < steps; i++) {
            double t0 = Math.PI * 2.0 * i / steps;
            double t1 = Math.PI * 2.0 * (i + 1) / steps;
            tri(center, cc, onCircle(center, a, b, radius, t0), ce, onCircle(center, a, b, radius, t1), ce);
        }
    }

    /** A star polygon of {@code points} corners joining every {@code step}-th corner (5/2 = pentagram). */
    public void polygram(V3 center, V3 a, V3 b, double radius, int points, int step, double width, double rotation,
                         int rgb, double alpha, double drawn) {
        if (alpha <= 0.004 || points < 3) {
            return;
        }
        V3 normal = a.cross(b).unit();
        int lines = points;
        for (int i = 0; i < lines; i++) {
            double t0 = rotation + Math.PI * 2.0 * i / points;
            double t1 = rotation + Math.PI * 2.0 * (i + step) / points;
            V3 p0 = onCircle(center, a, b, radius, t0);
            V3 p1 = onCircle(center, a, b, radius, t1);
            V3 p1d = p0.lerp(p1, Math.max(0.0, Math.min(1.0, drawn)));
            V3 side = p1.sub(p0).cross(normal).unit().scale(width * 0.5);
            quad(p0.add(side), p0.sub(side), p1d.sub(side), p1d.add(side), rgb, alpha, alpha);
        }
    }

    /** Radial marks between {@code r0} and {@code r1}: clock ticks, spokes, rays. */
    public void spokes(V3 center, V3 a, V3 b, double r0, double r1, int count, double rotation, double width, int rgb,
                       double alphaIn, double alphaOut) {
        if (count <= 0 || (alphaIn <= 0.004 && alphaOut <= 0.004)) {
            return;
        }
        V3 normal = a.cross(b).unit();
        for (int i = 0; i < count; i++) {
            double t = rotation + Math.PI * 2.0 * i / count;
            V3 p0 = onCircle(center, a, b, r0, t);
            V3 p1 = onCircle(center, a, b, r1, t);
            V3 side = p1.sub(p0).cross(normal).unit().scale(width * 0.5);
            quad(p0.add(side), p0.sub(side), p1.sub(side.scale(0.4)), p1.add(side.scale(0.4)), rgb, alphaIn, alphaOut);
        }
    }

    /**
     * A ring of small rune marks (seeded shapes made of two or three strokes) {@code height} tall around
     * {@code radius}: the writing of magic circles.
     */
    public void runes(V3 center, V3 a, V3 b, double radius, double height, int count, double rotation, long seed,
                      int rgb, double alpha) {
        if (alpha <= 0.004 || count <= 0) {
            return;
        }
        V3 normal = a.cross(b).unit();
        double w = Math.max(0.025, height * 0.16);
        for (int i = 0; i < count; i++) {
            double t = rotation + Math.PI * 2.0 * (i + 0.5) / count;
            V3 radial = a.scale(Math.cos(t)).add(b.scale(Math.sin(t)));
            V3 tangent = normal.cross(radial).unit();
            V3 base = center.plus(radial, radius - height * 0.5);
            long h = mixSeed(seed, i);
            int strokes = 2 + (int) (h & 1);
            for (int s = 0; s < strokes; s++) {
                long k = mixSeed(h, s + 7);
                double x0 = ((k & 0xFF) / 255.0 - 0.5) * height * 0.8;
                double y0 = (((k >> 8) & 0xFF) / 255.0) * height;
                double x1 = (((k >> 16) & 0xFF) / 255.0 - 0.5) * height * 0.8;
                double y1 = (((k >> 24) & 0xFF) / 255.0) * height;
                V3 p0 = base.plus(tangent, x0).plus(radial, y0);
                V3 p1 = base.plus(tangent, x1).plus(radial, y1);
                if (p0.distance(p1) < height * 0.2) {
                    p1 = p0.plus(radial, height * 0.6);
                }
                V3 side = p1.sub(p0).cross(normal).unit().scale(w * 0.5);
                quad(p0.add(side), p0.sub(side), p1.sub(side), p1.add(side), rgb, alpha, alpha);
            }
        }
    }

    // ------------------------------------------------------------------ points of light facing the camera

    /** A round soft glow facing the camera. */
    public void flare(V3 at, double size, int rgb, double alpha) {
        if (alpha <= 0.004 || size <= 0.0) {
            return;
        }
        V3[] uv = V3.basis(at.sub(camera));
        disc(at, uv[0], uv[1], size * 0.5, rgb, alpha, 0.0);
    }

    /** A bright point: deep halo, glow and core flares. */
    public void glowPoint(V3 at, double size, Palette p, double alpha) {
        flare(at, size * 2.6, p.deep(), alpha * 0.35);
        flare(at, size * 1.3, p.glow(), alpha * 0.8);
        flare(at, size * 0.45, p.core(), alpha);
    }

    /** A star of {@code rays} thin rays facing the camera (a glint). */
    public void star(V3 at, double size, int rays, double rotation, int rgb, double alpha) {
        if (alpha <= 0.004 || size <= 0.0) {
            return;
        }
        V3[] uv = V3.basis(at.sub(camera));
        int c = Palette.argb(rgb, alpha);
        int z = Palette.argb(rgb, 0.0);
        for (int i = 0; i < rays; i++) {
            double t = rotation + Math.PI * 2.0 * i / rays;
            double len = size * 0.5 * (i % 2 == 0 ? 1.0 : 0.55);
            V3 dir = uv[0].scale(Math.cos(t)).add(uv[1].scale(Math.sin(t)));
            V3 side = uv[0].scale(-Math.sin(t)).add(uv[1].scale(Math.cos(t))).scale(size * 0.045);
            tri(at.add(side), c, at.sub(side), c, at.plus(dir, len), z);
        }
    }

    /** A four-pointed sparkle with a soft centre. */
    public void sparkle(V3 at, double size, double rotation, Palette p, double alpha) {
        star(at, size, 4, rotation, p.core(), alpha);
        flare(at, size * 0.45, p.glow(), alpha * 0.8);
    }

    /** A small diamond facing the camera (a mote). */
    public void mote(V3 at, double size, int rgb, double alpha) {
        if (alpha <= 0.004 || size <= 0.0) {
            return;
        }
        V3[] uv = V3.basis(at.sub(camera));
        V3 u = uv[0].scale(size * 0.5);
        V3 v = uv[1].scale(size * 0.5);
        int c = Palette.argb(rgb, alpha);
        int z = Palette.argb(rgb, alpha * 0.1);
        quad(at.add(v), c, at.add(u), z, at.sub(v), c, at.sub(u), z);
    }

    // ------------------------------------------------------------------ solids

    /**
     * A crystal spike from {@code base} along {@code dir}: two crossed blades, wide at the base and sharp at the tip,
     * strength {@code aBase} at the base and {@code aTip} at the tip.
     */
    public void spike(V3 base, V3 dir, double length, double width, int rgb, double aBase, double aTip) {
        if (length <= 0.0 || (aBase <= 0.004 && aTip <= 0.004)) {
            return;
        }
        V3 d = dir.unit();
        V3[] uv = V3.basis(d);
        V3 tip = base.plus(d, length);
        int cb = Palette.argb(rgb, aBase);
        int ct = Palette.argb(rgb, aTip);
        for (V3 side : uv) {
            V3 s = side.scale(width * 0.5);
            tri(base.add(s), cb, base.sub(s), cb, tip, ct);
        }
    }

    /** A glowing crystal: halo spike and a bright core spike. */
    public void crystal(V3 base, V3 dir, double length, double width, Palette p, double alpha) {
        spike(base, dir, length * 1.08, width * 2.2, p.deep(), alpha * 0.35, 0.0);
        spike(base, dir, length, width, p.glow(), alpha * 0.85, alpha * 0.3);
        spike(base, dir, length * 0.92, width * 0.35, p.core(), alpha, alpha * 0.6);
    }

    /** A vertical column of light turned to the camera: strength {@code aBottom} at its foot, {@code aTop} above. */
    public void column(V3 base, double height, double radius, int rgb, double aBottom, double aTop) {
        if (height <= 0.0 || radius <= 0.0) {
            return;
        }
        V3 top = base.add(0.0, height, 0.0);
        softRibbon(base, top, radius * 2.0, radius * 2.0, rgb, aBottom, aTop);
    }

    /** A glowing column: deep, glow and core. */
    public void glowColumn(V3 base, double height, double radius, Palette p, double aBottom, double aTop) {
        column(base, height, radius * 2.4, p.deep(), aBottom * 0.3, aTop * 0.3);
        column(base, height, radius, p.glow(), aBottom * 0.8, aTop * 0.8);
        column(base, height, radius * 0.32, p.core(), aBottom, aTop);
    }

    /** A vertical wall along a circle (a cylinder band) from {@code y0} to {@code y1} above {@code base}. */
    public void wall(V3 base, double radius, double y0, double y1, double t0, double t1, int rgb, double aLow,
                     double aHigh) {
        if (radius <= 0.0 || (aLow <= 0.004 && aHigh <= 0.004)) {
            return;
        }
        int steps = Math.max(8, Math.min(72, (int) Math.ceil(Math.abs(t1 - t0) * radius * 2.0)));
        int cl = Palette.argb(rgb, aLow);
        int ch = Palette.argb(rgb, aHigh);
        for (int i = 0; i < steps; i++) {
            double s0 = t0 + (t1 - t0) * i / steps;
            double s1 = t0 + (t1 - t0) * (i + 1) / steps;
            V3 p0 = base.add(Math.cos(s0) * radius, y0, Math.sin(s0) * radius);
            V3 p1 = base.add(Math.cos(s1) * radius, y0, Math.sin(s1) * radius);
            V3 q0 = base.add(Math.cos(s0) * radius, y1, Math.sin(s0) * radius);
            V3 q1 = base.add(Math.cos(s1) * radius, y1, Math.sin(s1) * radius);
            quad(p0, cl, p1, cl, q1, ch, q0, ch);
        }
    }

    /**
     * Lightning from {@code from} to {@code to}: a jagged path (re-shaped whenever {@code seed} changes) with a branch
     * or two, drawn as glowing strips.
     */
    public void bolt(V3 from, V3 to, long seed, int segments, double jag, double width, Palette p, double alpha) {
        if (alpha <= 0.004) {
            return;
        }
        V3[] pts = jagged(from, to, seed, segments, jag);
        double[] w = new double[pts.length];
        double[] al = new double[pts.length];
        for (int i = 0; i < pts.length; i++) {
            double u = (double) i / (pts.length - 1);
            w[i] = width * (1.0 - 0.45 * u);
            al[i] = alpha;
        }
        glowStrip(pts, w, al, p);
        // branches
        long h = mixSeed(seed, 99);
        int branches = 1 + (int) (h & 1);
        for (int k = 0; k < branches; k++) {
            long hk = mixSeed(h, k);
            int at = 1 + (int) ((hk & 0xFFFF) % Math.max(1, pts.length - 2));
            V3 start = pts[at];
            V3 dir = to.sub(from).unit();
            V3[] uv = V3.basis(dir);
            double ang = ((hk >> 16) & 0xFF) / 255.0 * Math.PI * 2.0;
            V3 off = uv[0].scale(Math.cos(ang)).add(uv[1].scale(Math.sin(ang)));
            double len = to.distance(from) * (0.2 + ((hk >> 24) & 0xFF) / 255.0 * 0.25);
            V3 end = start.plus(dir, len * 0.6).plus(off, len * 0.6);
            V3[] bp = jagged(start, end, hk, Math.max(3, segments / 2), jag * 0.7);
            double[] bw = new double[bp.length];
            double[] ba = new double[bp.length];
            for (int i = 0; i < bp.length; i++) {
                double u = (double) i / (bp.length - 1);
                bw[i] = width * 0.5 * (1.0 - 0.6 * u);
                ba[i] = alpha * (1.0 - 0.8 * u);
            }
            glowStrip(bp, bw, ba, p);
        }
    }

    /** The points of a jagged line (both ends fixed). */
    public static V3[] jagged(V3 from, V3 to, long seed, int segments, double jag) {
        int n = Math.max(2, segments) + 1;
        V3[] pts = new V3[n];
        V3 dir = to.sub(from);
        V3[] uv = V3.basis(dir.lengthSqr() < 1.0E-9 ? V3.UP : dir);
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            V3 p = from.lerp(to, u);
            if (i > 0 && i < n - 1) {
                long h = mixSeed(seed, i);
                double ox = ((h & 0xFFFF) / 65535.0 - 0.5) * 2.0 * jag;
                double oy = (((h >> 16) & 0xFFFF) / 65535.0 - 0.5) * 2.0 * jag;
                p = p.plus(uv[0], ox).plus(uv[1], oy);
            }
            pts[i] = p;
        }
        return pts;
    }

    /** A well-mixed 64-bit hash of a seed and an index (stable shapes per seed). */
    public static long mixSeed(long seed, long index) {
        long z = seed + 0x9E3779B97F4A7C15L * (index + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A number in [0, 1) from a seed and an index. */
    public static double unit(long seed, long index) {
        return (mixSeed(seed, index) >>> 11) * 0x1.0p-53;
    }
}
