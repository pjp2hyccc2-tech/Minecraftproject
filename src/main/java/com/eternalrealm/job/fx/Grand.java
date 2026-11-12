package com.eternalrealm.job.fx;

/** The big shapes: giant swords, crosses, wings, walls, rifts, eclipses, serpents, meteors, and the floating signs. */
final class Grand {
    private static final V3 GX = new V3(1.0, 0.0, 0.0);
    private static final V3 GZ = new V3(0.0, 0.0, 1.0);

    private Grand() {
    }

    // ------------------------------------------------------------------ signs

    /** A cross of light facing the camera. */
    static void crossMark(Ctx x, V3 at, double size, double alpha) {
        V3[] uv = V3.basis(at.sub(x.b.camera()));
        V3 up = V3.UP;
        V3 side = uv[0].flat().lengthSqr() > 1.0E-6 ? uv[0].flat().unit() : GX;
        x.b.glow(at.plus(up, -size * 0.6), at.plus(up, size * 0.75), size * 0.16, x.p, alpha);
        x.b.glow(at.plus(side, -size * 0.45).plus(up, size * 0.25), at.plus(side, size * 0.45).plus(up, size * 0.25),
                size * 0.16, x.p, alpha);
    }

    static void glyph(Ctx x) {
        double size = Math.max(0.4, x.c.a());
        V3 at = x.pose.head().plus(x.pose.fwd(), x.c.b()).add(0.0, x.c.c() != 0.0 ? x.c.c() : 0.9, 0.0);
        double al = x.env();
        double bob = Math.sin(x.t * 0.25) * 0.08;
        at = at.add(0.0, bob, 0.0);
        V3[] uv = V3.basis(at.sub(x.b.camera()));
        V3 right = uv[0];
        V3 up = uv[1];
        x.b.flare(at, size * 2.4, x.p.glow(), al * 0.25);
        switch (x.c.variant()) {
            case 0 -> crossMark(x, at, size, al);
            case 1 -> { // sword
                x.b.glow(at.plus(up, -size * 0.7), at.plus(up, size * 0.6), size * 0.12, x.p, al);
                x.b.glow(at.plus(right, -size * 0.3).plus(up, -size * 0.3), at.plus(right, size * 0.3).plus(up, -size * 0.3),
                        size * 0.1, x.p, al);
            }
            case 2 -> { // shield
                x.b.polygram(at, right, up, size * 0.55, 6, 1, size * 0.07, Math.PI / 2.0, x.p.core(), al, 1.0);
                x.b.ring(at, right, up, size * 0.3, size * 0.05, x.p.glow(), al);
            }
            case 3 -> { // clock
                x.b.ring(at, right, up, size * 0.55, size * 0.06, x.p.core(), al);
                x.b.spokes(at, right, up, 0.0, size * 0.4, 1, x.t * 0.4, size * 0.07, x.p.glow(), al, al);
                x.b.spokes(at, right, up, 0.0, size * 0.28, 1, x.t * 0.05, size * 0.09, x.p.glow(), al, al);
            }
            case 4 -> { // eye
                x.b.arc(at, right, up, size * 0.6, size * 0.06, 0.5, Math.PI - 0.5, x.p.core(), al, al, false);
                x.b.arc(at, right, up, size * 0.6, size * 0.06, Math.PI + 0.5, Math.PI * 2.0 - 0.5, x.p.core(), al, al,
                        false);
                x.b.glowPoint(at, size * 0.35, x.p, al);
            }
            case 5 -> x.b.star(at, size * 1.6, 8, x.t * 0.05, x.p.core(), al);
            case 6 -> { // crescent moon
                x.b.arc(at, right, up, size * 0.5, size * 0.18, 0.6, Math.PI * 2.0 - 0.6, x.p.glow(), al, al, true);
                x.b.arc(at, right, up, size * 0.5, size * 0.06, 0.6, Math.PI * 2.0 - 0.6, x.p.core(), al, al, false);
            }
            case 7, 8 -> { // arrow up / down
                double dir = x.c.variant() == 7 ? 1.0 : -1.0;
                x.b.spike(at.plus(up, -size * 0.5 * dir), up.scale(dir), size, size * 0.6, x.p.core(), al, al * 0.5);
            }
            case 9 -> { // death mark: a circle crossed out
                x.b.ring(at, right, up, size * 0.55, size * 0.07, x.p.glow(), al);
                V3 d1 = right.add(up).unit();
                V3 d2 = right.sub(up).unit();
                x.b.glow(at.plus(d1, -size * 0.5), at.plus(d1, size * 0.5), size * 0.09, x.p, al);
                x.b.glow(at.plus(d2, -size * 0.5), at.plus(d2, size * 0.5), size * 0.09, x.p, al);
            }
            case 10 -> { // heart (a soft double lobe)
                x.b.flare(at.plus(right, -size * 0.2).plus(up, size * 0.12), size * 0.6, x.p.glow(), al);
                x.b.flare(at.plus(right, size * 0.2).plus(up, size * 0.12), size * 0.6, x.p.glow(), al);
                x.b.spike(at.plus(up, size * 0.1), up.scale(-1.0), size * 0.55, size * 0.7, x.p.glow(), al, al * 0.4);
            }
            default -> { // lightning sign
                V3[] pts = Brush.jagged(at.plus(up, size * 0.6), at.plus(up, -size * 0.6), x.c.seed(), 3, size * 0.25);
                x.b.glowStrip(pts, new double[]{size * 0.1, size * 0.1, size * 0.1, size * 0.1},
                        new double[]{al, al, al, al}, x.p);
            }
        }
    }

    static void clock(Ctx x) {
        double radius = Math.max(0.6, x.c.a());
        V3 center = x.pose.at(1.0, 1.4, 0.0);
        V3 a = x.pose.right();
        V3 bb = V3.UP;
        double al = x.env();
        double draw = Ctx.easeOut(x.rise());
        int dir = x.c.variant() == 1 ? -1 : 1;
        x.b.arc(center, a, bb, radius, 0.08, Math.PI / 2.0, Math.PI / 2.0 - dir * Math.PI * 2.0 * draw, x.p.glow(),
                al, al, true);
        x.b.ring(center, a, bb, radius, 0.025, x.p.core(), al * draw);
        x.b.spokes(center, a, bb, radius * 0.82, radius * 0.97, 12, 0.0, 0.04, x.p.core(), al * draw, al * draw);
        x.b.runes(center, a, bb, radius * 0.72, radius * 0.12, 12, -x.t * 0.02 * dir, x.c.seed(), x.p.accent(),
                al * draw * 0.8);
        double turn = x.t * 0.55 * dir;
        x.b.spokes(center, a, bb, 0.0, radius * 0.75, 1, Math.PI / 2.0 - turn, 0.06, x.p.core(), al, al * 0.6);
        x.b.spokes(center, a, bb, 0.0, radius * 0.5, 1, Math.PI / 2.0 - turn / 12.0, 0.09, x.p.glow(), al, al * 0.6);
        x.b.glowPoint(center, 0.3, x.p, al);
        x.b.disc(center, a, bb, radius, x.p.deep(), al * 0.2, 0.0);
    }

    static void reticle(Ctx x) {
        double radius = Math.max(0.5, x.c.a());
        V3 center = x.pose.eye().plus(x.pose.look(), Math.max(3.0, x.c.b()));
        V3[] uv = V3.basis(x.pose.look());
        double tight = Ctx.easeOut(x.rise());
        double r = radius * (1.6 - 0.6 * tight);
        double al = x.fade();
        double rot = x.t * 0.08;
        x.b.glowRing(center, uv[0], uv[1], r, 0.04, x.p, al);
        x.b.arc(center, uv[0], uv[1], r * 0.62, 0.04, rot, rot + 1.2, x.p.core(), al, al, false);
        x.b.arc(center, uv[0], uv[1], r * 0.62, 0.04, rot + Math.PI, rot + Math.PI + 1.2, x.p.core(), al, al, false);
        x.b.spokes(center, uv[0], uv[1], r * 0.75, r * 1.35, 4, Math.PI / 4.0, 0.05, x.p.glow(), al, al * 0.3);
        x.b.glowPoint(center, 0.18, x.p, al);
    }

    // ------------------------------------------------------------------ the sword of light

    /** A sword from {@code tip} along {@code dir} (towards the hilt), {@code length} long. */
    static void sword(Ctx x, V3 tip, V3 dir, double length, double alpha, Palette p) {
        V3 d = dir.unit();
        V3[] uv = V3.basis(d);
        V3 face = uv[0];
        V3 guardAt = tip.plus(d, length * 0.78);
        double w = length * 0.075;
        // the blade: two crossed planes, a fuller of white light and a soft halo
        for (V3 s : new V3[]{face, uv[1]}) {
            V3 side = s.scale(w);
            x.b.tri(tip, Palette.argb(p.core(), alpha), guardAt.add(side), Palette.argb(p.glow(), alpha * 0.9),
                    guardAt.sub(side), Palette.argb(p.glow(), alpha * 0.9));
        }
        x.b.softRibbon(tip, guardAt, w * 5.0, w * 6.5, p.deep(), alpha * 0.35, alpha * 0.4);
        x.b.ribbon(tip.plus(d, length * 0.08), guardAt, w * 0.25, w * 0.5, p.core(), alpha, alpha);
        // edges
        x.b.glow(tip, guardAt.add(face.scale(w)), w * 0.18, p, alpha * 0.8);
        x.b.glow(tip, guardAt.sub(face.scale(w)), w * 0.18, p, alpha * 0.8);
        // the guard, the grip and the pommel
        V3 g0 = guardAt.plus(face, -length * 0.2);
        V3 g1 = guardAt.plus(face, length * 0.2);
        x.b.glow(g0, g1, w * 0.7, p, alpha);
        x.b.glowPoint(g0, w * 1.4, p, alpha);
        x.b.glowPoint(g1, w * 1.4, p, alpha);
        V3 end = guardAt.plus(d, length * 0.2);
        x.b.glow(guardAt, end, w * 0.45, p, alpha * 0.9);
        x.b.glowPoint(end, w * 2.0, p, alpha);
        x.b.glowPoint(guardAt, w * 3.0, p, alpha * 0.8);
    }

    static void giantSword(Ctx x) {
        double length = Math.max(3.0, x.c.a());
        Pose pose = x.pose;
        double al = x.fade();
        if (x.c.variant() == 1) {
            // raised over the caster, then brought down along the forward direction
            V3 pivot = pose.at(0.3, 1.6, 0.0);
            double swing = x.before() ? 0.0 : Ctx.easeIn(Ctx.clamp(x.since() / 3.0));
            V3 handle = V3.UP.scale(-1.0).lerp(pose.fwd().scale(-1.0), swing).unit();
            double grow = Ctx.easeOut(x.rise());
            V3 tip = pivot.plus(handle, -length * grow);
            sword(x, tip, handle, length * grow, al, x.p);
            if (!x.before()) {
                double u = Ctx.clamp(x.since() / Math.max(3.0, x.c.end() - x.c.peak()));
                V3 g = x.groundAt(pose.feet());
                V3 far = g.plus(pose.fwd(), length * 1.15);
                x.b.flatGlow(g.plus(pose.fwd(), 0.8), far, 1.1 * (1.0 - u), 0.5 * (1.0 - u), V3.UP, x.p, al, al * 0.5);
                for (int i = 0; i < 6; i++) {
                    V3 at = g.plus(pose.fwd(), length * (0.2 + 0.16 * i));
                    x.b.glowColumn(at, 3.0 * (1.0 - u) * (0.6 + 0.4 * x.rnd(i)), 0.25, x.p, al * (1.0 - u), 0.0);
                }
            }
            return;
        }
        // falls point first from the sky onto the anchor
        V3 g = x.groundAt(pose.feet().plus(pose.fwd(), x.c.b()));
        double fall = Ctx.easeIn(x.rise());
        double sky = 22.0;
        V3 tip = x.before() ? g.add(0.0, sky * (1.0 - fall), 0.0)
                : g.add(0.0, -length * 0.22 * Ctx.easeOut(Ctx.clamp(x.since() / 1.5)), 0.0);
        double size = length * (0.75 + 0.25 * fall);
        sword(x, tip, V3.UP, size, al * (0.5 + 0.5 * fall), x.p);
        if (x.before()) {
            // its trail through the sky
            x.b.glow(tip.add(0.0, size, 0.0), tip.add(0.0, size + 6.0 * fall, 0.0), size * 0.05, 0.0, x.p, al * 0.6, 0.0);
            x.b.glowRing(g, GX, GZ, length * 0.5 * (1.0 - fall) + 0.6, 0.12, x.p, 0.5 + 0.5 * fall);
        }
    }

    // ------------------------------------------------------------------ the cross

    static void cross(Ctx x) {
        double length = Math.max(3.0, x.c.a());
        Pose pose = x.pose;
        V3 g = x.groundAt(pose.feet().plus(pose.fwd(), x.c.b()));
        double draw = Ctx.easeOut(x.rise());
        double al = x.fade();
        double flash = x.before() ? 0.0 : Math.exp(-x.since() * 0.4);
        V3 f = pose.fwd();
        V3 r = pose.right();
        double wv = 0.9 + 0.6 * flash;
        x.b.flatGlow(g.plus(f, -length * 0.35 * draw), g.plus(f, length * 0.65 * draw), wv, wv, V3.UP, x.p, al, al);
        V3 mid = g.plus(f, length * 0.25);
        x.b.flatGlow(mid.plus(r, -length * 0.4 * draw), mid.plus(r, length * 0.4 * draw), wv, wv, V3.UP, x.p, al, al);
        if (!x.before()) {
            // the cross rises upright from the ground, and light pours from it
            double up = Ctx.easeOut(Ctx.clamp(x.since() / 4.0));
            V3 base = mid;
            x.b.glow(base, base.add(0.0, length * 0.55 * up, 0.0), 0.5, x.p, al);
            V3 bar = base.add(0.0, length * 0.38 * up, 0.0);
            x.b.glow(bar.plus(r, -length * 0.2 * up), bar.plus(r, length * 0.2 * up), 0.45, x.p, al);
            x.b.glowPoint(bar, 2.5 * flash + 0.6, x.p, al);
            for (int i = 0; i < 12; i++) {
                double ang = Math.PI * 2.0 * i / 12.0 + x.t * 0.02;
                V3 d = new V3(Math.cos(ang), 0.15, Math.sin(ang));
                x.b.glow(bar, bar.plus(d, length * 0.5 * Ctx.easeOut(1.0 - flash + 0.2)), 0.08, 0.0, x.p,
                        al * flash, 0.0);
            }
        }
    }

    // ------------------------------------------------------------------ blades

    static void blades(Ctx x) {
        double radius = Math.max(1.0, x.c.a());
        int count = Math.max(3, x.c.n() > 0 ? x.c.n() : 8);
        V3 c = x.pose.chest();
        double appear = Ctx.easeOut(x.rise());
        double al = x.fade();
        double rot = x.t * 0.12;
        for (int i = 0; i < count; i++) {
            double ang = rot + Math.PI * 2.0 * i / count;
            double rr = radius * (0.6 + 0.4 * appear);
            double height = Math.sin(x.t * 0.2 + i) * 0.25 + 0.4;
            V3 at;
            V3 point;
            if (x.before()) {
                at = c.add(Math.cos(ang) * rr, height, Math.sin(ang) * rr);
                point = new V3(-Math.sin(ang), 0.2, Math.cos(ang));
            } else {
                // at the peak they dive in on the target ahead
                double u = Ctx.easeIn(Ctx.clamp(x.since() / 3.0));
                V3 target = x.pose.feet().plus(x.pose.fwd(), radius * 1.2).add(0.0, 0.2, 0.0);
                V3 start = c.add(Math.cos(ang) * rr, height, Math.sin(ang) * rr);
                at = start.lerp(target, u);
                point = target.sub(start).unit();
            }
            double size = 1.1;
            V3 tip = at.plus(point, size * 0.5);
            sword(x, tip, point.scale(-1.0), size, al * appear, x.p);
        }
    }

    // ------------------------------------------------------------------ a tear in space

    static void rift(Ctx x) {
        double length = Math.max(2.0, x.c.a());
        Pose pose = x.pose;
        V3 center = pose.at(Math.min(4.0, length * 0.35), 1.3, 0.0);
        double tilt = Math.toRadians(x.c.c());
        V3 along = pose.fwd().scale(Math.cos(tilt)).add(V3.UP.scale(Math.sin(tilt))).unit();
        if (x.c.c() == 0.0) {
            along = pose.fwd();
        }
        V3 a = center.plus(along, -length * 0.5);
        V3 bb = center.plus(along, length * 0.5);
        double open = x.before() ? Ctx.easeOut(x.rise()) : 1.0;
        double al = x.fade();
        V3[] pts = Brush.jagged(a, bb, x.c.seed(), 10, 0.35);
        int n = pts.length;
        double[] w = new double[n];
        double[] alphas = new double[n];
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            w[i] = (0.1 + 0.9 * Math.sin(Math.PI * u)) * 0.55 * open;
            alphas[i] = al;
        }
        x.b.glowStrip(pts, w, alphas, x.p);
        double[] core = new double[n];
        for (int i = 0; i < n; i++) {
            core[i] = w[i] * 0.3;
        }
        x.b.strip(pts, core, alphas, 0xFFFFFF, false);
        if (!x.before()) {
            double s = x.since();
            double u = Ctx.clamp(s / Math.max(3.0, x.c.end() - x.c.peak()));
            for (int i = 0; i < 14; i++) {
                V3 from = pts[(int) (x.rnd(i) * (n - 1))];
                double ang = x.rnd(i + 5) * Math.PI * 2.0;
                V3 d = V3.basis(along)[0].scale(Math.cos(ang)).add(V3.basis(along)[1].scale(Math.sin(ang)));
                V3 shard = from.plus(d, s * 0.35);
                x.b.crystal(shard, d, 0.4 * (1.0 - u), 0.18, x.p, (1.0 - u));
            }
            x.b.glowPoint(center, length * 0.4 * Math.exp(-s * 0.5), x.p, al);
        }
    }

    // ------------------------------------------------------------------ the eclipse

    static void eclipse(Ctx x) {
        double radius = Math.max(1.5, x.c.a());
        V3 center = x.pose.feet().add(0.0, x.c.c() > 0.0 ? x.c.c() : 7.0, 0.0).plus(x.pose.fwd(), 2.0);
        V3[] uv = V3.basis(center.sub(x.b.camera()));
        double grow = Ctx.easeOut(x.rise());
        double al = x.fade();
        double r = radius * grow;
        x.b.softRing(center, uv[0], uv[1], r * 1.08, r * 0.5, x.p.deep(), al * 0.55);
        x.b.softRing(center, uv[0], uv[1], r, r * 0.16, x.p.glow(), al * 0.9);
        x.b.ring(center, uv[0], uv[1], r * 0.97, 0.05, x.p.core(), al);
        x.b.spokes(center, uv[0], uv[1], r * 1.05, r * 1.6, 18, x.t * 0.01, 0.12, x.p.accent(), al * 0.5, 0.0);
        x.b.spokes(center, uv[0], uv[1], r * 1.05, r * 2.2, 6, -x.t * 0.015, 0.22, x.p.glow(), al * 0.5, 0.0);
        if (!x.before()) {
            x.b.glowPoint(center, radius * 3.0 * Math.exp(-x.since() * 0.4), x.p, al);
        }
    }

    // ------------------------------------------------------------------ the hydra

    static void hydra(Ctx x) {
        int heads = Math.max(1, x.c.n() > 0 ? x.c.n() : 3);
        double size = x.c.a() > 0.0 ? x.c.a() : 1.0;
        Pose pose = x.pose;
        double al = x.env();
        int[] lunges = x.st.show().frameTimes();
        for (int h = 0; h < heads; h++) {
            double side = (h - (heads - 1) / 2.0) * 1.3;
            V3 root = pose.feet().plus(pose.fwd(), -0.8).plus(pose.right(), side * 0.6);
            double lunge = 0.0;
            for (int lt : lunges) {
                double s = x.t - lt;
                if (s > -2.0 && s < 4.0) {
                    lunge = Math.max(lunge, s < 0.0 ? (s + 2.0) / 2.0 : 1.0 - s / 4.0);
                }
            }
            int n = 16;
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            double rise = Ctx.easeOut(x.rise());
            for (int i = 0; i < n; i++) {
                double u = (double) i / (n - 1);
                double sway = Math.sin(x.t * 0.25 + h * 2.0 + u * 3.0) * 0.35 * u * size;
                double up = Math.sin(u * Math.PI * 0.85) * 2.6 * rise * size;
                double fwd = u * u * (1.5 + 3.0 * lunge) * rise * size;
                pts[i] = root.plus(pose.fwd(), fwd).plus(pose.right(), side * size * (1.0 - u * 0.4) + sway)
                        .add(0.0, up, 0.0);
                w[i] = 0.34 * size * (1.0 - 0.45 * u);
                a[i] = al * (0.3 + 0.7 * u);
            }
            x.b.glowStrip(pts, w, a, x.p);
            V3 head = pts[n - 1];
            V3 jaw = pts[n - 1].sub(pts[n - 3]).unit();
            x.b.glowPoint(head, 0.8 * size, x.p, al);
            x.b.spike(head, jaw, 0.6 * size, 0.35 * size, x.p.glow(), al, al * 0.3);
            x.b.mote(head.plus(pose.right(), 0.14 * size).add(0.0, 0.1 * size, 0.0), 0.16 * size, x.p.accent(), al);
            x.b.mote(head.plus(pose.right(), -0.14 * size).add(0.0, 0.1 * size, 0.0), 0.16 * size, x.p.accent(), al);
            for (int k = 0; k < 3; k++) {
                double uu = ((x.t * 0.1 + k / 3.0 + h * 0.2) % 1.0);
                x.b.mote(head.add(0.0, -uu * 1.5, 0.0), 0.12, x.p.glow(), al * (1.0 - uu));
            }
        }
    }

    // ------------------------------------------------------------------ walls

    static void walls(Ctx x) {
        double radius = Math.max(1.5, x.c.a());
        double height = x.c.b() > 0.0 ? x.c.b() : 2.6;
        int panels = Math.max(4, x.c.n() > 0 ? x.c.n() : 8);
        V3 g = x.groundAt(x.pose.feet());
        double rise = x.before() ? Ctx.easeOut(x.rise()) : 1.0;
        double al = x.fade();
        double gap = 0.12;
        for (int i = 0; i < panels; i++) {
            double t0 = Math.PI * 2.0 * i / panels + gap;
            double t1 = Math.PI * 2.0 * (i + 1) / panels - gap;
            double h = height * rise * (0.85 + 0.15 * x.rnd(i));
            V3 p0 = g.add(Math.cos(t0) * radius, 0.0, Math.sin(t0) * radius);
            V3 p1 = g.add(Math.cos(t1) * radius, 0.0, Math.sin(t1) * radius);
            V3 q0 = p0.add(0.0, h, 0.0);
            V3 q1 = p1.add(0.0, h, 0.0);
            x.b.quad(p0, p1, q1, q0, x.p.glow(), al * 0.35, al * 0.08);
            x.b.glow(p0, q0, 0.06, x.p, al);
            x.b.glow(p1, q1, 0.06, x.p, al);
            x.b.glow(q0, q1, 0.08, x.p, al);
            V3 mid = p0.lerp(q1, 0.5);
            V3 out = mid.sub(g).flat().unit();
            V3 along = q1.sub(q0).unit();
            x.b.polygram(mid, along, V3.UP, 0.32, 6, 1, 0.03, Math.PI / 2.0, x.p.core(), al * 0.8, 1.0);
            x.b.glowPoint(mid.plus(out, 0.02), 0.25, x.p, al * 0.6);
        }
        x.b.glowRing(g, GX, GZ, radius, 0.14, x.p, al);
    }

    // ------------------------------------------------------------------ wings

    static void wings(Ctx x) {
        double span = Math.max(1.5, x.c.a());
        Pose pose = x.pose;
        V3 back = pose.at(-0.25, 1.45, 0.0);
        double open = Ctx.easeOut(x.rise());
        double al = x.fade();
        double flap = Math.sin(x.t * 0.18) * 0.12;
        for (int side = -1; side <= 1; side += 2) {
            V3 out = pose.right().scale(side);
            for (int k = 0; k < 7; k++) {
                double u = k / 6.0;
                double ang = Math.toRadians(-35.0 + 95.0 * u) + flap;
                V3 dir = out.scale(Math.cos(ang)).add(V3.UP.scale(Math.sin(ang))).plus(pose.fwd(), -0.35).unit();
                double len = span * (0.55 + 0.45 * Math.sin(Math.PI * (0.2 + 0.7 * u))) * open;
                V3 tip = back.plus(dir, len);
                V3 mid = back.lerp(tip, 0.5).add(0.0, 0.15, 0.0);
                V3[] pts = {back, mid, tip};
                x.b.glowStrip(pts, new double[]{0.2, 0.28, 0.05}, new double[]{al * 0.8, al, al * 0.5}, x.p);
            }
            x.b.flare(back.plus(out, span * 0.5 * open).add(0.0, span * 0.2, 0.0), span * 1.6, x.p.glow(), al * 0.2);
        }
    }

    // ------------------------------------------------------------------ the meteor

    static void meteor(Ctx x) {
        double size = Math.max(1.0, x.c.a());
        V3 g = x.groundAt(x.pose.feet());
        if (!x.before()) {
            return;
        }
        double u = Ctx.easeIn(x.rise());
        V3 sky = g.add(-x.pose.fwd().x() * 8.0, 26.0, -x.pose.fwd().z() * 8.0);
        V3 at = sky.lerp(g.add(0.0, size * 0.4, 0.0), u);
        V3 dir = g.sub(sky).unit();
        x.b.glowPoint(at, size * 2.2, x.p, 1.0);
        x.b.star(at, size * 4.0, 10, x.t * 0.2, x.p.core(), 0.7);
        int n = 10;
        V3[] pts = new V3[n];
        double[] w = new double[n];
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            double k = (double) i / (n - 1);
            pts[i] = at.plus(dir, -k * 9.0 * (0.4 + 0.6 * u));
            w[i] = size * 1.4 * (1.0 - k);
            a[i] = 1.0 - k;
        }
        x.b.glowStrip(pts, w, a, x.p);
        for (int i = 0; i < 10; i++) {
            double k = x.rnd(i);
            V3 ember = at.plus(dir, -k * 6.0).add((x.rnd(i + 3) - 0.5) * 1.5, (x.rnd(i + 6) - 0.5) * 1.5,
                    (x.rnd(i + 9) - 0.5) * 1.5);
            x.b.mote(ember, 0.25, x.p.accent(), 1.0 - k);
        }
        x.b.glowRing(g, GX, GZ, size * 2.5 * (1.0 - u) + 1.0, 0.15, x.p, 0.3 + 0.7 * u);
        x.b.disc(g, GX, GZ, size * 2.0, x.p.glow(), 0.4 * u, 0.0);
    }
}
