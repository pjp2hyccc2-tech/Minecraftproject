package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;

/**
 * Draws a playing skill show at a moment: every layer whose time has come, in the caster's pose of its anchor, in its
 * theme's colours. The shapes of ultimates live in {@link Grand}.
 */
public final class Painter {
    private static final V3 GX = new V3(1.0, 0.0, 0.0);
    private static final V3 GZ = new V3(0.0, 0.0, 1.0);

    private Painter() {
    }

    /** Draws {@code state}'s show at show time {@code t} (ticks) for an onlooker; returns the quads drawn. */
    public static int paint(ShowState state, double t, Brush brush) {
        return paint(state, t, brush, false);
    }

    /**
     * Draws {@code state}'s show at show time {@code t} (ticks); returns the quads drawn. {@code ownEyes}: the camera
     * is the caster's own eyes (first person), so the light hugging their body thins out around the camera instead
     * of covering the screen.
     */
    public static int paint(ShowState state, double t, Brush brush, boolean ownEyes) {
        int before = brush.quads();
        SkillShow show = state.show();
        for (Cue cue : show.cues()) {
            if (t < cue.start() || t > cue.end()) {
                continue;
            }
            Pose pose = switch (cue.anchor()) {
                case CASTER -> state.now();
                case ORIGIN -> state.origin();
                case FRAME -> state.frame(cue.frame());
                case AIM -> state.origin().movedTo(state.aim());
            };
            SkillTheme theme = cue.palette() != null ? cue.palette() : show.theme();
            boolean hug = ownEyes && hugs(cue.kind()) && pose.eye().distance(brush.camera()) < 1.5;
            if (hug) {
                brush.nearFade(HUG_GONE, HUG_CLEAR);
            }
            draw(new Ctx(cue, t, pose, state, theme, brush));
            if (hug) {
                brush.nearFade();
            }
        }
        return brush.quads() - before;
    }

    /** Seen through the caster's own eyes, their body's light is gone within this many blocks... */
    static final double HUG_GONE = 0.6;
    /** ...and whole from here on. */
    static final double HUG_CLEAR = 2.4;

    /** The layers wrapped around the caster's body (seen from inside when the camera is their eyes). */
    static boolean hugs(Cue.Kind kind) {
        return switch (kind) {
            case AURA, HALO, HELIX, ORBIT, WINGS, CHARGE, CAST, WEAPON, HEAL, CLEANSE, MOTES, TAUNT, SIPHON, AFFLICT,
                    BLADES, DOME, STREAK, LEAP, FLASH -> true;
            default -> false;
        };
    }

    static void draw(Ctx x) {
        switch (x.c.kind()) {
            case SLASH -> slash(x);
            case CLAW -> claw(x);
            case SPIN -> spin(x);
            case THRUST -> thrust(x);
            case BEAM -> beam(x);
            case SHOCKWAVE -> shockwave(x);
            case NOVA -> nova(x);
            case QUAKE -> quake(x);
            case SPIKES -> spikes(x);
            case PILLAR -> pillar(x);
            case SIGIL -> sigil(x);
            case SKY_SIGIL -> skySigil(x);
            case BOLTS -> bolts(x);
            case RAIN -> rain(x);
            case BURST -> burst(x);
            case IMPACT -> impact(x);
            case VORTEX -> vortex(x);
            case FOG -> fog(x);
            case CHAINS -> chains(x);
            case BREATH -> breath(x);
            case ROAR -> roar(x);
            case DOME -> dome(x);
            case WALLS -> Grand.walls(x);
            case FIELD -> Fields.field(x);
            case CAST -> cast(x);
            case CHARGE -> charge(x);
            case HELIX -> helix(x);
            case AURA -> aura(x);
            case HALO -> halo(x);
            case WINGS -> Grand.wings(x);
            case STREAK -> streak(x);
            case LEAP -> leap(x);
            case GLYPH -> Grand.glyph(x);
            case WEAPON -> weapon(x);
            case ORBIT -> orbit(x);
            case HEAL -> heal(x);
            case CLEANSE -> cleanse(x);
            case MOTES -> motes(x);
            case SIPHON -> siphon(x);
            case TAUNT -> taunt(x);
            case AFFLICT -> afflict(x);
            case FLASH -> flash(x);
            case CLOCK -> Grand.clock(x);
            case RETICLE -> Grand.reticle(x);
            case GIANT_SWORD -> Grand.giantSword(x);
            case CROSS -> Grand.cross(x);
            case BLADES -> Grand.blades(x);
            case RIFT -> Grand.rift(x);
            case ECLIPSE -> Grand.eclipse(x);
            case HYDRA -> Grand.hydra(x);
            case METEOR -> Grand.meteor(x);
        }
    }

    // ------------------------------------------------------------------ strikes

    /** The weapon side's horizontal direction tilted up by {@code tiltDeg} around the forward axis. */
    static V3 tilted(Pose pose, double tiltDeg) {
        double tilt = Math.toRadians(tiltDeg);
        return pose.right().scale(pose.side() * Math.cos(tilt)).add(V3.UP.scale(Math.sin(tilt))).unit();
    }

    /**
     * The plane a cut is drawn in: its centre, the axis across the cut and the axis through its middle; {@code near}
     * is 1 when the viewer is the caster's own eyes (0 for a far onlooker).
     */
    record Plane(V3 center, V3 a, V3 bb, double near) {
        V3 normal() {
            return a.cross(bb).unit();
        }

        /** Bands drawn a little bolder for the caster's own eyes, who see them from just above. */
        double widen() {
            return 1.0 + 0.35 * near;
        }
    }

    /** A cut is never seen flatter than this... */
    static final double FACE_MIN = Math.toRadians(28.0);
    /** ...turning towards the viewer in steps of this... */
    static final double FACE_STEP = Math.toRadians(6.0);
    /** ...at most this many times (an upright cut seen from straight behind needs about 46°). */
    static final int FACE_STEPS = 8;
    /** How far the caster's own eye is kept off the plane of an upright cut... */
    static final double CLEAR_EYE = 0.9;
    /** ...and how much further off a level one (which their eye looks down on from just above). */
    static final double CLEAR_LEVEL = 0.4;

    /**
     * The plane of a cut (radius {@code r} around {@code center}) as this viewer should see it. A cut is a band of
     * light in a plane through the caster; a viewer in or near that plane sees only a thin line: the caster's own eyes
     * (first person) lie a hand's breadth above every cut, and a camera straight behind lies in the plane of every
     * upright one. So the plane is pushed off the caster's own eye, then turned a few degrees at a time (about an
     * axis through the centre, so the cut stays on the body) until it shows the viewer its face. Every client draws
     * its own light, so each viewer gets the cut that reads best from where it stands.
     */
    static Plane facing(V3 camera, V3 center, V3 a, V3 bb, double r) {
        V3 n = a.cross(bb).unit();
        double near = Ctx.clamp(1.0 - (camera.distance(center) - 1.5) / 3.0);
        if (near > 0.0) {
            double s = camera.sub(center).dot(n);
            double clear = (CLEAR_EYE + CLEAR_LEVEL * Math.abs(n.y())) * near;
            if (Math.abs(s) < clear) {
                center = center.plus(n, s - Math.copySign(clear, s));
            }
        }
        double best = elevation(camera, center, a, bb, r);
        if (best >= FACE_MIN || near > 0.5) {
            // turning about the caster cannot help the eyes right above the cut: it only lifts the far end to them
            return new Plane(center, a, bb, near);
        }
        V3 v = center.plus(bb, r).sub(camera).unit();
        V3 axis = (n.dot(v) < 0.0 ? n.scale(-1.0) : n).cross(v);
        if (axis.lengthSqr() < 1.0E-10) {
            return new Plane(center, a, bb, near);
        }
        axis = axis.unit();
        V3 bestA = a;
        V3 bestB = bb;
        for (int i = 1; i <= FACE_STEPS && best < FACE_MIN; i++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                double turn = sign * i * FACE_STEP;
                V3 ra = a.rotate(axis, turn);
                V3 rb = bb.rotate(axis, turn);
                double e = elevation(camera, center, ra, rb, r);
                if (e > best + 1.0E-4) {
                    best = e;
                    bestA = ra;
                    bestB = rb;
                }
            }
        }
        return new Plane(center, bestA, bestB, near);
    }

    /** The angle (radians) under which the camera sees the plane at the middle of the cut. */
    static double elevation(V3 camera, V3 center, V3 a, V3 bb, double r) {
        V3 n = a.cross(bb).unit();
        V3 v = center.plus(bb, r).sub(camera);
        double len = v.length();
        if (len < 1.0E-6) {
            return Math.PI / 2.0;
        }
        return Math.asin(Math.min(1.0, Math.abs(v.dot(n)) / len));
    }

    static void slash(Ctx x) {
        Cue c = x.c;
        Pose pose = x.pose;
        double reach = Math.max(1.2, c.a());
        double r = reach * 0.82;
        double half = Math.toRadians(Math.max(25.0, Math.min(175.0, c.b())));
        double width = c.d() > 0.0 ? c.d() : 0.5 + r * 0.14;
        boolean reversed = (c.variant() & 1) != 0;
        boolean heavy = (c.variant() & 2) != 0;
        boolean emphasis = (c.variant() & 4) != 0;
        Plane plane = facing(x.b.camera(), pose.at(0.1, 1.05, 0.0), tilted(pose, c.c()), pose.fwd(), r);
        V3 a = plane.a();
        V3 bb = plane.bb();
        V3 center = plane.center();
        width *= plane.widen();
        double from = Math.PI / 2.0 - half;
        double to = Math.PI / 2.0 + half;
        if (reversed) {
            double k = from;
            from = to;
            to = k;
        }
        double sweep = x.rise() >= 1.0 ? 1.0 : Ctx.easeOut(x.rise());
        double alpha = x.fade() * (emphasis ? 1.0 : 0.92);
        double grow = 1.0 + 0.1 * x.fall();
        if (heavy) {
            width *= 1.25;
        }
        x.b.crescent(center, a, bb, r * grow, width, from, to, sweep, x.p, alpha);
        x.b.sweepStroke(center, a, bb, r * grow + width * 0.15, width * 0.32, from, to, sweep, x.p, alpha);
        // an echo just inside, a breath behind
        x.b.crescent(center, a, bb, r * 0.8 * grow, width * 0.45, from, to, Math.max(0.0, sweep - 0.08), x.p,
                alpha * 0.4);
        if (heavy || emphasis) {
            x.b.crescent(center, a, bb, r * 1.16 * grow, width * 0.5, from, to, Math.max(0.0, sweep - 0.15), x.p,
                    alpha * 0.45);
        }
        double head = from + (to - from) * sweep;
        if (x.t <= c.peak() + 1.5) {
            V3 tip = Brush.onCircle(center, a, bb, r, head);
            double flash = x.before() ? 0.85 : 1.0 - Ctx.clamp(x.since() / 1.5);
            x.b.glowPoint(tip, width * (heavy ? 1.6 : 1.1), x.p, flash);
            x.b.star(tip, width * 3.0, 4, x.t * 0.4, x.p.core(), flash * 0.8);
        }
        if (!x.before()) {
            decorate(x, center, a, bb, r, from, to, alpha, heavy ? 9 : 6);
        }
    }

    /** The theme's touch along a cut: flames, ice, sparks, runes... */
    static void decorate(Ctx x, V3 center, V3 a, V3 bb, double r, double from, double to, double alpha, int count) {
        double s = x.since();
        V3 normal = a.cross(bb).unit();
        for (int i = 0; i < count; i++) {
            double u = (i + 0.5) / count;
            double ang = from + (to - from) * u;
            V3 at = Brush.onCircle(center, a, bb, r, ang);
            V3 radial = at.sub(center).unit();
            double k = x.rnd(i);
            double al = alpha * (0.6 + 0.4 * k);
            switch (x.theme) {
                case FIRE -> {
                    double h = (0.35 + 0.5 * k) * x.flicker(0.5, i) * (1.0 + s * 0.08);
                    x.b.crystal(at, V3.UP.plus(radial, 0.25), h, 0.18, x.p, al * 0.8);
                    x.b.mote(at.add(0.0, s * (0.08 + 0.05 * k), 0.0), 0.12, x.p.accent(), al);
                }
                case FROST -> {
                    double h = 0.35 + 0.4 * k;
                    x.b.crystal(at, radial.plus(normal, (k - 0.5) * 0.8), h, 0.16, x.p, al);
                    x.b.mote(at.plus(radial, s * 0.05).add(0.0, -s * 0.03, 0.0), 0.1, x.p.core(), al * 0.8);
                }
                case THUNDER -> {
                    if (i + 1 < count) {
                        V3 next = Brush.onCircle(center, a, bb, r, from + (to - from) * (i + 1.5) / count);
                        x.b.bolt(at.plus(radial, 0.15), next.plus(radial, 0.15), c(x, i), 3, 0.18, 0.06, x.p,
                                al * 0.8);
                    }
                }
                case HOLY -> {
                    x.b.sparkle(at.add(0.0, s * 0.06, 0.0), 0.3 + 0.2 * k, x.t * 0.2 + k * 3.0, x.p, al);
                }
                case SHADOW -> {
                    V3 out = at.plus(radial, 0.2 + s * 0.06).add(0.0, s * 0.04, 0.0);
                    x.b.glow(at, out, 0.09, x.p, al * 0.7);
                    x.b.mote(out, 0.14, x.p.accent(), al * 0.7);
                }
                case ARCANE -> {
                    x.b.mote(at.plus(radial, 0.15 + s * 0.04), 0.16, x.p.accent(), al);
                    x.b.star(at, 0.3, 6, k * 3.0 + x.t * 0.2, x.p.glow(), al * 0.8);
                }
                case VENOM -> {
                    x.b.mote(at.add(0.0, -s * (0.07 + 0.05 * k), 0.0), 0.16, x.p.glow(), al);
                    x.b.flare(at.add(0.0, -s * 0.05, 0.0), 0.3, x.p.accent(), al * 0.4);
                }
                case BLOOD -> {
                    V3 drop = at.plus(radial, s * (0.08 + 0.06 * k)).add(0.0, -s * s * 0.01, 0.0);
                    x.b.ribbon(drop, drop.plus(radial, -0.18), 0.08, 0.02, x.p.glow(), al, al * 0.2);
                }
                case WIND -> {
                    if (i == 0) {
                        x.b.crescent(center, a, bb, r * 1.22, 0.08, from, to, 1.0, x.p, alpha * 0.45);
                        x.b.crescent(center, a, bb, r * 0.62, 0.06, from, to, 1.0, x.p, alpha * 0.35);
                    }
                    x.b.mote(at.plus(radial, s * 0.1), 0.1, x.p.core(), al * 0.6);
                }
                case EARTH -> {
                    V3 dust = at.add(0.0, -s * 0.05, 0.0).plus(radial, s * 0.05);
                    x.b.mote(dust, 0.16, x.p.glow(), al * 0.8);
                    x.b.sparkle(at, 0.25, k * 3.0, x.p, al * 0.6);
                }
                case LIFE -> {
                    V3 leaf = at.add(Math.sin(s * 0.5 + k * 6.0) * 0.15, s * 0.05, Math.cos(s * 0.5 + k * 6.0) * 0.15);
                    x.b.mote(leaf, 0.18, i % 2 == 0 ? x.p.glow() : x.p.accent(), al);
                }
                default -> {
                    V3 spark = at.plus(radial, s * (0.12 + 0.1 * k)).add(0.0, -s * s * 0.004, 0.0);
                    x.b.ribbon(spark, spark.plus(radial, -0.25), 0.05, 0.01, x.p.core(), al, al * 0.2);
                }
            }
        }
    }

    private static long c(Ctx x, int i) {
        return x.c.seed() * 31 + i * 7L + (long) (x.t * 0.75);
    }

    static void claw(Ctx x) {
        // three parallel cuts, slightly staggered: a beast's claws
        Pose pose = x.pose;
        Cue c = x.c;
        double reach = Math.max(1.2, c.a());
        Plane plane = facing(x.b.camera(), pose.at(0.1, 1.05, 0.0), tilted(pose, c.c()), pose.fwd(), reach * 0.78);
        V3 a = plane.a();
        V3 bb = plane.bb();
        V3 normal = plane.normal();
        for (int k = -1; k <= 1; k++) {
            V3 center = plane.center().plus(normal, k * 0.32);
            double half = Math.toRadians(55.0);
            double from = Math.PI / 2.0 - half;
            double to = Math.PI / 2.0 + half;
            double rise = Ctx.clamp(x.rise() * 1.25 - (k + 1) * 0.12);
            double sweep = Ctx.easeOut(rise);
            double w = plane.widen();
            x.b.crescent(center, a, bb, reach * (0.78 + 0.05 * k), 0.5 * w, from, to, sweep, x.p, x.fade());
            x.b.sweepStroke(center, a, bb, reach * (0.78 + 0.05 * k), 0.2 * w, from, to, sweep, x.p, x.fade());
        }
    }

    static void spin(Ctx x) {
        Cue c = x.c;
        Pose pose = x.pose;
        double r = Math.max(1.0, c.a()) * 0.85;
        double h = c.b() > 0.0 ? c.b() : 0.95;
        double width = c.d() > 0.0 ? c.d() : 0.45 + r * 0.08;
        int dir = (c.variant() & 1) != 0 ? -1 : 1;
        V3 tilt = Math.abs(c.c()) < 1.0E-6 ? pose.right() : tilted(pose, c.c());
        Plane plane = facing(x.b.camera(), pose.feet().add(0.0, h, 0.0), tilt, pose.fwd(), r);
        V3 a2 = plane.a();
        V3 bb = plane.bb();
        V3 center = plane.center();
        width *= plane.widen();
        double from = Math.PI / 2.0;
        double to = from + dir * Math.PI * 2.1;
        double sweep = x.rise() >= 1.0 ? 1.0 : Ctx.easeOut(x.rise());
        double alpha = x.fade();
        double grow = 1.0 + 0.12 * x.fall();
        x.b.crescent(center, a2, bb, r * grow, width, from, to, sweep, x.p, alpha);
        x.b.sweepStroke(center, a2, bb, r * grow, width * 0.3, from, to, sweep, x.p, alpha * 0.9);
        x.b.crescent(center, a2, bb, r * 0.7 * grow, width * 0.4, from + dir * 0.4, to + dir * 0.4,
                Math.max(0.0, sweep - 0.1), x.p, alpha * 0.4);
        if (!x.before()) {
            double u = x.fall();
            V3 g = pose.feet().add(0.0, 0.06, 0.0);
            x.b.glowRing(g, GX, GZ, r * (0.6 + 0.6 * Ctx.easeOut(u)), 0.12, x.p, (1.0 - u) * 0.7);
            decorate(x, center, a2, bb, r, from, from + dir * Math.PI * 2.0, alpha * 0.7, 10);
        }
    }

    static void thrust(Ctx x) {
        Cue c = x.c;
        Pose pose = x.pose;
        double length = Math.max(1.5, c.a());
        double width = c.b() > 0.0 ? c.b() * 0.55 : 0.5;
        boolean look3d = (c.variant() & 1) != 0;
        V3 dir = look3d ? pose.look() : pose.fwd();
        V3 base = pose.hand().plus(dir, -0.2);
        double extend = x.before() ? Ctx.easeOut(x.rise()) * 0.35 : 0.35 + 0.65 * Ctx.easeOut(Ctx.clamp(x.since() / 2.0));
        double alpha = x.fade();
        V3 tip = base.plus(dir, length * extend);
        int n = 8;
        V3[] pts = new V3[n];
        double[] w = new double[n];
        double[] al = new double[n];
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            pts[i] = base.lerp(tip, u);
            w[i] = width * (0.25 + 0.95 * Math.sin(Math.PI * Math.min(1.0, u * 0.62 + 0.05))) * (u > 0.9 ? (1.0 - u) * 10.0 : 1.0);
            al[i] = alpha * (0.3 + 0.7 * u);
        }
        x.b.glowStrip(pts, w, al, x.p);
        x.b.glowPoint(tip, width * 1.6, x.p, alpha);
        x.b.star(tip, width * 4.0, 6, x.t * 0.3, x.p.core(), alpha * 0.7);
        if (!x.before()) {
            double u = Ctx.clamp(x.since() / Math.max(2.0, x.c.end() - x.c.peak()));
            V3[] uv = V3.basis(dir);
            for (int k = 0; k < 3; k++) {
                double at = 0.35 + 0.25 * k;
                double rr = (0.25 + 0.6 * Ctx.easeOut(u)) * (1.0 - 0.2 * k);
                x.b.glowRing(base.plus(dir, length * at), uv[0], uv[1], rr, 0.06, x.p, (1.0 - u) * 0.8);
            }
        }
    }

    static void beam(Ctx x) {
        Cue c = x.c;
        Pose pose = x.pose;
        double length = Math.max(2.0, c.a());
        double width = c.b() > 0.0 ? c.b() * 0.6 : 0.7;
        V3 dir = pose.look();
        V3 from = pose.hand().plus(dir, 0.3);
        if (x.before()) {
            double k = Ctx.easeOut(x.rise());
            x.b.glowPoint(from, width * (0.6 + 1.4 * k), x.p, 0.4 + 0.6 * k);
            V3[] uv = V3.basis(dir);
            x.b.glowRing(from, uv[0], uv[1], width * (1.6 - k), 0.05, x.p, k * 0.8);
            return;
        }
        double s = x.since();
        double extend = Ctx.easeOut(Ctx.clamp(s / 2.0));
        double alpha = x.fade();
        double wNow = width * (1.0 + 0.3 * Math.exp(-s * 0.6)) * x.flicker(0.15, 3);
        V3 to = from.plus(dir, length * extend);
        x.b.glow(from, to, wNow, wNow * 0.8, x.p, alpha, alpha * 0.85);
        // beads of light running along it
        for (int i = 0; i < 6; i++) {
            double u = ((s * 0.22 + i / 6.0) % 1.0) * extend;
            x.b.glowPoint(from.plus(dir, length * u), wNow * 1.1, x.p, alpha * 0.7);
        }
        // two ribbons wound around
        V3[] uv = V3.basis(dir);
        for (int strand = 0; strand < 2; strand++) {
            int n = 18;
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] al = new double[n];
            for (int i = 0; i < n; i++) {
                double u = (double) i / (n - 1);
                double ang = u * length * 1.4 - s * 0.8 + strand * Math.PI;
                double rr = wNow * 0.9;
                pts[i] = from.plus(dir, length * u * extend).plus(uv[0], Math.cos(ang) * rr).plus(uv[1], Math.sin(ang) * rr);
                w[i] = 0.07;
                al[i] = alpha * 0.6 * (1.0 - u * 0.5);
            }
            x.b.strip(pts, w, al, x.p.accent(), true);
        }
        x.b.glowPoint(to, wNow * 2.4, x.p, alpha);
        x.b.star(to, wNow * 6.0, 8, s * 0.2, x.p.core(), alpha * 0.6);
        x.b.glowRing(from, uv[0], uv[1], wNow * 1.6, 0.05, x.p, alpha * 0.6);
    }

    // ------------------------------------------------------------------ areas

    static void shockwave(Ctx x) {
        V3 g = x.groundAt(x.pose.feet().plus(x.pose.fwd(), x.c.d()));
        double radius = Math.max(1.0, x.c.a());
        int rings = Math.max(1, x.c.n());
        double span = Math.max(4.0, x.c.end() - x.c.peak());
        for (int k = 0; k < rings; k++) {
            double s = x.since() - k * 1.6;
            if (s < 0.0) {
                continue;
            }
            double u = Ctx.clamp(s / (span - k * 1.6));
            double r = radius * (0.15 + 0.85 * Ctx.easeOut(u));
            double al = Math.pow(1.0 - u, 1.2) * (1.0 - 0.2 * k);
            x.b.glowRing(g, GX, GZ, r, 0.16 + 0.22 * (1.0 - u), x.p, al);
            if (x.c.b() > 0.0) {
                x.b.wall(g, r, 0.0, x.c.b() * (1.0 - 0.5 * u), 0.0, Math.PI * 2.0, x.p.glow(), al * 0.45, 0.0);
            }
        }
        double u0 = Ctx.clamp(x.since() / span);
        // a floor of light, kept small: wide additive light washes the ground out by day
        x.b.disc(g, GX, GZ, Math.min(radius * 0.6, 3.2), x.p.glow(), 0.32 * (1.0 - u0), 0.0);
    }

    static void nova(Ctx x) {
        V3 g = x.groundAt(x.pose.feet().plus(x.pose.fwd(), x.c.d()));
        V3 mid = g.add(0.0, 1.0, 0.0);
        double radius = Math.max(1.5, x.c.a());
        double s = x.since();
        if (x.before()) {
            // gathering: a tightening ring and a growing core
            double k = Ctx.easeOut(x.rise());
            x.b.glowRing(g, GX, GZ, radius * (1.0 - 0.7 * k), 0.08, x.p, k * 0.8);
            x.b.glowPoint(mid, 0.5 + 1.2 * k, x.p, k);
            return;
        }
        double span = Math.max(4.0, x.c.end() - x.c.peak());
        double u = Ctx.clamp(s / span);
        double e = Ctx.easeOut(u);
        double al = Math.pow(1.0 - u, 1.1);
        x.b.glowPoint(mid, (2.0 + radius * 0.8) * (1.0 - 0.6 * u), x.p, al * Math.exp(-s * 0.35) * 1.2);
        x.b.glowRing(g, GX, GZ, radius * e, 0.3 * (1.0 - u) + 0.08, x.p, al);
        x.b.glowRing(g.add(0.0, 0.3, 0.0), GX, GZ, radius * e * 0.72, 0.12, x.p, al * 0.6);
        // tilted rings: a sphere of light opening
        V3 t1 = new V3(0.0, Math.sin(0.45), Math.cos(0.45));
        V3 t2 = new V3(0.0, -Math.sin(0.45), Math.cos(0.45));
        x.b.glowRing(mid, GX, t1, radius * e * 0.6, 0.1, x.p, al * 0.7);
        x.b.glowRing(mid, GX, t2, radius * e * 0.6, 0.1, x.p, al * 0.7);
        int rays = Math.max(8, x.c.n() > 0 ? x.c.n() : 12);
        for (int i = 0; i < rays; i++) {
            double ang = Math.PI * 2.0 * i / rays + x.rnd(i) * 0.2;
            V3 d = new V3(Math.cos(ang), 0.0, Math.sin(ang));
            double len = radius * (0.5 + 0.5 * x.rnd(i + 50)) * e;
            x.b.flatGlow(g.plus(d, len * 0.25), g.plus(d, len), 0.22 * (1.0 - u), 0.02, V3.UP, x.p, al, 0.0);
            if (i % 2 == 0) {
                V3 up = d.scale(0.45).add(0.0, 1.0, 0.0).unit();
                x.b.glow(mid, mid.plus(up, len * 0.55), 0.12 * (1.0 - u), 0.01, x.p, al * 0.7, 0.0);
            }
        }
        x.b.disc(g, GX, GZ, radius * e, x.p.glow(), 0.0, 0.0);
        x.b.disc(g, GX, GZ, radius * 0.7, x.p.deep(), 0.35 * al, 0.0);
    }

    static void quake(Ctx x) {
        V3 g = x.groundAt(x.pose.feet().plus(x.pose.fwd(), x.c.b()));
        double radius = Math.max(1.5, x.c.a());
        int cracks = Math.max(5, x.c.n() > 0 ? x.c.n() : 9);
        if (x.before()) {
            return;
        }
        double s = x.since();
        double span = Math.max(5.0, x.c.end() - x.c.peak());
        double grow = Ctx.easeOut(Ctx.clamp(s / 3.0));
        double al = Math.pow(1.0 - Ctx.clamp(s / span), 1.2);
        for (int i = 0; i < cracks; i++) {
            double ang = Math.PI * 2.0 * i / cracks + (x.rnd(i) - 0.5) * 0.5;
            double len = radius * (0.6 + 0.4 * x.rnd(i + 20)) * grow;
            int n = 6;
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            for (int k = 0; k < n; k++) {
                double u = (double) k / (n - 1);
                double wob = (Brush.unit(x.c.seed() + i, k) - 0.5) * 0.6 * u;
                double aa = ang + wob;
                pts[k] = g.add(Math.cos(aa) * len * u, 0.0, Math.sin(aa) * len * u);
                w[k] = 0.3 * (1.0 - u * 0.85);
                a[k] = al * (1.0 - 0.6 * u);
            }
            x.b.glowStrip(pts, w, a, x.p, V3.UP);
            // shards thrown up along the crack
            for (int k = 1; k < 4; k++) {
                double u = k / 4.0;
                V3 base = g.add(Math.cos(ang) * len * u, 0.0, Math.sin(ang) * len * u);
                double h = (0.3 + 0.5 * x.rnd(i * 7 + k)) * (1.0 - Ctx.clamp(s / span)) * (1.0 + (1.0 - u));
                V3 tilt = new V3(Math.cos(ang) * 0.4, 1.0, Math.sin(ang) * 0.4);
                x.b.crystal(base.add(0.0, s * 0.02, 0.0), tilt, h, 0.2, x.p, al * 0.9);
            }
        }
        x.b.glowRing(g, GX, GZ, radius * (0.3 + 0.9 * Ctx.easeOut(Ctx.clamp(s / span))), 0.25, x.p, al * 0.7);
        x.b.disc(g, GX, GZ, Math.min(radius * 0.5, 3.0), x.p.glow(), al * 0.4, 0.0);
        for (int i = 0; i < 10; i++) {
            double ang = x.rnd(i + 100) * Math.PI * 2.0;
            double d = radius * x.rnd(i + 200) * grow;
            V3 dust = g.add(Math.cos(ang) * d, 0.2 + s * 0.08 * x.rnd(i + 300), Math.sin(ang) * d);
            x.b.mote(dust, 0.2, x.p.accent(), al * 0.6);
        }
    }

    static void spikes(Ctx x) {
        V3 g = x.groundAt(x.pose.feet().plus(x.pose.fwd(), x.c.d()));
        double radius = Math.max(0.6, x.c.a());
        double height = x.c.b() > 0.0 ? x.c.b() : 1.4;
        int count = Math.max(4, x.c.n() > 0 ? x.c.n() : 10);
        if (x.before()) {
            double k = Ctx.easeOut(x.rise());
            x.b.glowRing(g, GX, GZ, radius, 0.06, x.p, k * 0.5);
            return;
        }
        double s = x.since();
        double span = Math.max(4.0, x.c.end() - x.c.peak());
        for (int i = 0; i < count; i++) {
            double delay = x.rnd(i) * 2.5;
            double ss = s - delay;
            if (ss < 0.0) {
                continue;
            }
            double grow = Ctx.easeOut(Ctx.clamp(ss / 2.0));
            double al = Math.pow(1.0 - Ctx.clamp((s - span * 0.6) / (span * 0.4)), 1.2);
            double ang = Math.PI * 2.0 * i / count + x.rnd(i + 40) * 0.5;
            double rr = radius * (0.55 + 0.45 * x.rnd(i + 80));
            V3 base = g.add(Math.cos(ang) * rr, 0.0, Math.sin(ang) * rr);
            V3 dir = new V3(Math.cos(ang) * 0.35, 1.0, Math.sin(ang) * 0.35);
            double h = height * (0.6 + 0.5 * x.rnd(i + 120)) * grow;
            x.b.crystal(base, dir, h, 0.32 * (0.7 + 0.5 * x.rnd(i + 7)), x.p, al);
            if (i % 3 == 0) {
                x.b.crystal(base.add(0.12, 0.0, 0.08), dir.add(0.3, 0.0, -0.2), h * 0.6, 0.22, x.p, al * 0.8);
            }
        }
        double al = 1.0 - Ctx.clamp(s / span);
        x.b.disc(g, GX, GZ, radius * 1.1, x.p.deep(), al * 0.35, 0.0);
        x.b.glowRing(g, GX, GZ, radius * (0.8 + 0.4 * Ctx.easeOut(Ctx.clamp(s / 4.0))), 0.1, x.p, al * 0.6);
    }

    static void pillar(Ctx x) {
        int count = Math.max(1, x.c.n());
        double radius = Math.max(0.3, x.c.a());
        double height = x.c.b() > 0.0 ? x.c.b() : 6.0;
        double ring = x.c.c();
        V3 center = x.pose.feet().plus(x.pose.fwd(), x.c.d());
        for (int i = 0; i < count; i++) {
            V3 at = center;
            if (count > 1 || ring > 0.0) {
                double ang = Math.PI * 2.0 * i / count + x.c.seed() % 7;
                at = center.add(Math.cos(ang) * ring, 0.0, Math.sin(ang) * ring);
            }
            column(x, x.groundAt(at), radius, height, i * 0.8);
        }
    }

    /** One pillar of light: it shoots up towards the peak, flares, then thins away. */
    static void column(Ctx x, V3 g, double radius, double height, double delay) {
        double rise = Ctx.clamp((x.t - x.c.start() - delay) / Math.max(1.0, x.c.peak() - x.c.start()));
        if (rise <= 0.0) {
            return;
        }
        double grown = x.before() ? Ctx.easeOut(rise) : 1.0;
        double al = x.fade();
        double w = radius * (x.before() ? 0.5 + 0.5 * grown : 1.0 + 0.3 * Math.exp(-x.since() * 0.5)) * (1.0 - 0.6 * x.fall());
        x.b.glowColumn(g, height * grown, w, x.p, al, al * 0.05);
        x.b.disc(g, GX, GZ, w * 2.6, x.p.glow(), al * 0.6, 0.0);
        x.b.glowRing(g, GX, GZ, w * 1.8, 0.08, x.p, al * 0.7);
        for (int k = 0; k < 3; k++) {
            double u = ((x.t * 0.06 + k / 3.0) % 1.0);
            x.b.glowRing(g.add(0.0, height * grown * u, 0.0), GX, GZ, w * (1.3 - 0.6 * u), 0.04, x.p, al * (1.0 - u) * 0.8);
        }
        if (!x.before()) {
            x.b.glowPoint(g.add(0.0, 0.3, 0.0), w * 3.5 * Math.exp(-x.since() * 0.4), x.p, al);
        }
    }

    /** The shape of a job's magic circle. */
    static final int STYLE_KNIGHT = 1;
    static final int STYLE_ASSASSIN = 2;
    static final int STYLE_MAGE = 3;
    static final int STYLE_RANGER = 4;
    static final int STYLE_PRIEST = 5;
    static final int STYLE_SPELLBLADE = 6;

    static void sigil(Ctx x) {
        Cue c = x.c;
        Pose pose = x.pose;
        double radius = Math.max(0.6, c.a());
        boolean upright = c.d() >= 45.0;
        V3 center;
        V3 a;
        V3 bb;
        if (upright) {
            center = pose.at(Math.max(0.6, c.c()), 1.25, 0.0);
            a = pose.right();
            bb = V3.UP;
        } else {
            center = x.groundAt(pose.feet()).add(0.0, c.c(), 0.0).plus(pose.fwd(), c.b());
            a = pose.right();
            bb = pose.fwd();
        }
        double drawIn = Ctx.easeOut(x.rise());
        double al = x.fade() * (0.85 + 0.15 * Math.sin(x.t * 0.5));
        double flash = x.before() ? 0.0 : Math.exp(-x.since() * 0.45);
        circle(x, center, a, bb, radius * (1.0 + 0.06 * flash), drawIn, al + flash * 0.5, c.n(), c.variant());
        if (!upright) {
            x.b.disc(center, a, bb, radius * 1.05, x.p.deep(), al * 0.25, 0.0);
        }
    }

    /**
     * The magic circle itself: double outer ring drawn in as {@code drawIn} grows, a band of runes, the polygram of
     * {@code corners}, small circles on its corners and the job's mark at the centre.
     */
    static void circle(Ctx x, V3 center, V3 a, V3 bb, double radius, double drawIn, double al, int corners, int style) {
        double rot = x.t * 0.035 + x.c.seed() % 11;
        double sweep = Math.PI * 2.0 * Ctx.clamp(drawIn * 1.15);
        x.b.arc(center, a, bb, radius, 0.09, rot, rot + sweep, x.p.glow(), al, al, true);
        x.b.arc(center, a, bb, radius, 0.03, rot, rot + sweep, x.p.core(), al, al, false);
        x.b.arc(center, a, bb, radius * 0.9, 0.025, -rot, -rot - sweep, x.p.core(), al * 0.9, al * 0.9, false);
        if (drawIn < 0.3) {
            return;
        }
        double inner = Ctx.clamp((drawIn - 0.3) / 0.7);
        int runeCount = 12 + (int) (Math.abs(x.c.seed()) % 3) * 4;
        x.b.runes(center, a, bb, radius * 0.95, radius * 0.1, runeCount, -rot * 1.4, x.c.seed(), x.p.accent(),
                al * inner);
        x.b.ring(center, a, bb, radius * 0.74, 0.025, x.p.glow(), al * inner);
        int n = corners >= 3 ? corners : 6;
        int step = n == 4 ? 1 : (n == 5 || n == 6 ? 2 : 3);
        double prot = rot * 1.6;
        int s = style;
        switch (s) {
            case STYLE_KNIGHT -> {
                x.b.polygram(center, a, bb, radius * 0.74, 4, 1, 0.05, prot, x.p.glow(), al * inner, inner);
                x.b.polygram(center, a, bb, radius * 0.74, 4, 1, 0.05, prot + Math.PI / 4.0, x.p.glow(), al * inner,
                        inner);
                x.b.spokes(center, a, bb, radius * 0.3, radius * 0.74, 8, prot, 0.04, x.p.accent(), al * inner, 0.0);
            }
            case STYLE_ASSASSIN -> {
                x.b.polygram(center, a, bb, radius * 0.74, 5, 2, 0.04, prot, x.p.glow(), al * inner, inner);
                x.b.spokes(center, a, bb, radius * 0.74, radius * 0.56, 15, -prot, 0.05, x.p.accent(), al * inner,
                        0.0);
                for (int i = 0; i < 3; i++) {
                    double ang = prot + i * Math.PI * 2.0 / 3.0;
                    V3 at = Brush.onCircle(center, a, bb, radius * 0.45, ang);
                    x.b.arc(at, a, bb, radius * 0.12, 0.03, ang + 0.6, ang + 3.6, x.p.accent(), al * inner, al * inner,
                            false);
                }
            }
            case STYLE_RANGER -> {
                x.b.spokes(center, a, bb, radius * 0.15, radius * 0.9, 4, prot * 0.3, 0.06, x.p.glow(), al * inner, 0.1);
                x.b.spokes(center, a, bb, radius * 0.2, radius * 0.62, 4, prot * 0.3 + Math.PI / 4.0, 0.04,
                        x.p.accent(), al * inner, 0.1);
                x.b.ring(center, a, bb, radius * 0.4, 0.02, x.p.glow(), al * inner);
            }
            case STYLE_PRIEST -> {
                for (int i = 0; i < 8; i++) {
                    double ang = prot * 0.5 + i * Math.PI / 4.0;
                    V3 at = Brush.onCircle(center, a, bb, radius * 0.48, ang);
                    x.b.ring(at, a, bb, radius * 0.24, 0.025, x.p.glow(), al * inner * 0.8);
                }
                x.b.spokes(center, a, bb, 0.0, radius * 0.7, 4, prot * 0.5, 0.07, x.p.core(), al * inner, al * inner * 0.3);
            }
            case STYLE_SPELLBLADE -> {
                x.b.polygram(center, a, bb, radius * 0.74, 5, 2, 0.045, prot, x.p.glow(), al * inner, inner);
                x.b.spokes(center, a, bb, radius * 0.2, radius * 0.95, 6, -prot, 0.06, x.p.accent(), al * inner, 0.0);
            }
            default -> {
                x.b.polygram(center, a, bb, radius * 0.74, n, step, 0.045, prot, x.p.glow(), al * inner, inner);
                x.b.ring(center, a, bb, radius * 0.38, 0.02, x.p.accent(), al * inner);
            }
        }
        // small circles on the corners
        if (s == STYLE_MAGE || s == 0) {
            for (int i = 0; i < n; i++) {
                V3 at = Brush.onCircle(center, a, bb, radius * 0.74, prot + Math.PI * 2.0 * i / n);
                x.b.ring(at, a, bb, radius * 0.08, 0.02, x.p.accent(), al * inner);
            }
        }
        x.b.ring(center, a, bb, radius * 0.18, 0.03, x.p.core(), al * inner);
        x.b.glowPoint(center.plus(a.cross(bb).unit(), 0.02), radius * 0.25, x.p, al * inner * 0.6);
    }

    static void skySigil(Ctx x) {
        Cue c = x.c;
        double radius = Math.max(1.5, c.a());
        double height = c.c() > 0.0 ? c.c() : 9.0;
        V3 g = x.groundAt(x.pose.feet().plus(x.pose.fwd(), c.d()));
        V3 center = g.add(0.0, height, 0.0);
        double drawIn = Ctx.easeOut(x.rise());
        double al = x.fade();
        double flash = x.before() ? 0.0 : Math.exp(-x.since() * 0.35);
        // the circle faces down: drawn in the horizontal plane
        int style = c.variant();
        circle(x, center, x.pose.right(), x.pose.fwd(), radius, drawIn, al * 0.95 + flash * 0.4,
                c.n() > 0 ? c.n() : 7, style);
        x.b.glowRing(center, GX, GZ, radius * 1.18, 0.12, x.p, al * 0.5 * drawIn);
        x.b.disc(center, GX, GZ, radius, x.p.deep(), al * 0.3 * drawIn, 0.0);
        // shafts of light at the peak
        if (!x.before() || drawIn > 0.8) {
            double k = x.before() ? (drawIn - 0.8) * 2.0 : 1.0;
            double sh = al * k * (0.35 + 0.65 * flash);
            for (int i = 0; i < 6; i++) {
                double ang = x.t * 0.03 + i * Math.PI / 3.0;
                V3 top = center.add(Math.cos(ang) * radius * 0.6, 0.0, Math.sin(ang) * radius * 0.6);
                V3 foot = g.add(Math.cos(ang) * radius * 0.35, 0.0, Math.sin(ang) * radius * 0.35);
                x.b.softRibbon(top, foot, 0.5, 0.25, x.p.glow(), sh * 0.5, sh * 0.15);
            }
            x.b.softRibbon(center, g, radius * 0.5, radius * 0.25, x.p.glow(), sh * 0.35, sh * 0.1);
            x.b.disc(g, GX, GZ, Math.min(radius * 0.7, 3.5), x.p.glow(), sh * 0.3, 0.0);
        }
    }

    static void bolts(Ctx x) {
        double radius = Math.max(0.0, x.c.a());
        int count = Math.max(1, x.c.n());
        double height = x.c.c() > 0.0 ? x.c.c() : 12.0;
        V3 center = x.pose.feet().plus(x.pose.fwd(), x.c.b());
        double window = Math.max(0.0, x.c.peak() - x.c.start());
        for (int i = 0; i < count; i++) {
            double when = x.c.start() + window * (i + 0.5) / count;
            double s = x.t - when;
            if (s < 0.0 || s > 6.0) {
                continue;
            }
            double ang = x.rnd(i) * Math.PI * 2.0;
            double d = radius * Math.sqrt(x.rnd(i + 30));
            V3 g = x.groundAt(center.add(Math.cos(ang) * d, 0.0, Math.sin(ang) * d));
            V3 sky = g.add((x.rnd(i + 60) - 0.5) * 3.0, height, (x.rnd(i + 90) - 0.5) * 3.0);
            double al = s < 1.0 ? 1.0 : Math.pow(1.0 - (s - 1.0) / 5.0, 1.5);
            long shape = x.c.seed() * 13 + i * 101L + (long) (x.t * 1.5);
            x.b.bolt(sky, g, shape, 9, 0.55, 0.22, x.p, al * x.flicker(0.3, i));
            x.b.glowPoint(g.add(0.0, 0.3, 0.0), 2.2 * (1.0 - s / 6.0), x.p, al);
            x.b.glowRing(g, GX, GZ, 0.4 + s * 0.35, 0.1, x.p, al * 0.8);
        }
    }

    /** Falling light: streaks, arrows, icicles, embers, needles, feathers, petals or stars. */
    static void rain(Ctx x) {
        double radius = Math.max(0.5, x.c.a());
        int perTick = Math.max(1, x.c.n());
        double height = x.c.c() > 0.0 ? x.c.c() : 9.0;
        int shape = x.c.variant();
        double fall = shape == 5 || shape == 6 ? 14.0 : 5.0;
        V3 center = x.groundAt(x.pose.feet().plus(x.pose.fwd(), x.c.b()));
        double lastSpawn = x.c.end() - fall - 1.0;
        int first = (int) Math.floor(Math.max(x.c.start(), x.t - fall - 3.0));
        int last = (int) Math.floor(Math.min(lastSpawn, x.t));
        for (int tick = first; tick <= last; tick++) {
            for (int k = 0; k < perTick; k++) {
                long id = tick * 64L + k;
                double born = tick + Brush.unit(x.c.seed(), id);
                double age = x.t - born;
                if (age < 0.0 || age > fall + 3.0) {
                    continue;
                }
                double ang = Brush.unit(x.c.seed(), id + 7) * Math.PI * 2.0;
                double d = radius * Math.sqrt(Brush.unit(x.c.seed(), id + 13));
                V3 g = center.add(Math.cos(ang) * d, 0.0, Math.sin(ang) * d);
                if (age <= fall) {
                    double u = age / fall;
                    double y = height * (1.0 - (shape == 5 || shape == 6 ? u : u * u * 0.4 + u * 0.6));
                    V3 at = g.add(0.0, y, 0.0);
                    drop(x, shape, at, id, u);
                } else {
                    double v = (age - fall) / 3.0;
                    double al = 1.0 - v;
                    if (shape != 5 && shape != 6) {
                        x.b.glowRing(g, GX, GZ, 0.15 + v * 0.5, 0.05, x.p, al * 0.8);
                        x.b.glowPoint(g.add(0.0, 0.1, 0.0), 0.5 * al, x.p, al);
                    }
                }
            }
        }
    }

    private static void drop(Ctx x, int shape, V3 at, long id, double u) {
        double al = Math.min(1.0, u * 5.0);
        switch (shape) {
            case 1 -> { // an arrow of light
                V3 tail = at.add(0.0, 1.3, 0.0);
                x.b.glow(tail, at, 0.07, x.p, al * 0.6);
                x.b.spike(at.add(0.0, 0.25, 0.0), new V3(0.0, -1.0, 0.0), 0.35, 0.22, x.p.core(), al, al);
                x.b.ribbon(tail, tail.add(0.0, 0.35, 0.0), 0.2, 0.05, x.p.accent(), al * 0.7, 0.0);
            }
            case 2 -> x.b.crystal(at.add(0.0, 0.8, 0.0), new V3(0.0, -1.0, 0.0), 0.9, 0.2, x.p, al);
            case 3 -> {
                x.b.glowPoint(at, 0.45, x.p, al);
                x.b.glow(at.add(0.3, 1.6, 0.2), at, 0.08, 0.2, x.p, 0.0, al * 0.7);
            }
            case 4 -> {
                x.b.glow(at.add(0.0, 1.2, 0.0), at, 0.02, 0.07, x.p, 0.0, al);
                x.b.flare(at, 0.3, x.p.glow(), al * 0.6);
            }
            case 5 -> {
                double sway = Math.sin(u * 9.0 + id) * 0.4;
                V3 f = at.add(sway, 0.0, Math.cos(u * 7.0 + id) * 0.3);
                x.b.softRibbon(f.add(-0.12, 0.08, 0.0), f.add(0.12, -0.08, 0.0), 0.12, 0.04, x.p.core(), al, al * 0.6);
                x.b.flare(f, 0.3, x.p.glow(), al * 0.4);
            }
            case 6 -> {
                double sway = Math.sin(u * 8.0 + id) * 0.5;
                V3 f = at.add(sway, 0.0, Math.cos(u * 6.0 + id) * 0.5);
                x.b.mote(f, 0.2, id % 2 == 0 ? x.p.glow() : x.p.accent(), al);
            }
            case 7 -> x.b.sparkle(at, 0.5, u * 4.0 + id, x.p, al);
            default -> {
                x.b.glow(at.add(0.0, 1.1, 0.0), at, 0.03, 0.08, x.p, 0.0, al);
                x.b.flare(at, 0.25, x.p.core(), al * 0.8);
            }
        }
    }

    static void burst(Ctx x) {
        V3 center = x.pose.feet().plus(x.pose.fwd(), x.c.b()).add(0.0, 1.0, 0.0);
        if (x.before()) {
            double k = Ctx.easeOut(x.rise());
            x.b.glowPoint(center, 0.4 + 1.2 * k, x.p, k);
            return;
        }
        double radius = Math.max(1.0, x.c.a());
        double s = x.since();
        double span = Math.max(3.0, x.c.end() - x.c.peak());
        double u = Ctx.clamp(s / span);
        double e = Ctx.easeOut(u);
        double al = Math.pow(1.0 - u, 1.2);
        int rays = Math.max(10, x.c.n() > 0 ? x.c.n() : 16);
        for (int i = 0; i < rays; i++) {
            double yy = 1.0 - 2.0 * (i + 0.5) / rays;
            double rr = Math.sqrt(1.0 - yy * yy);
            double ang = i * 2.399963 + x.c.seed();
            V3 d = new V3(Math.cos(ang) * rr, yy * 0.8, Math.sin(ang) * rr);
            double len = radius * (0.6 + 0.4 * x.rnd(i)) * e;
            x.b.glow(center.plus(d, len * 0.3), center.plus(d, len), 0.14 * (1.0 - u), 0.02, x.p, al, 0.0);
        }
        x.b.glowPoint(center, radius * 1.4 * (1.0 - 0.5 * u), x.p, al * Math.exp(-s * 0.3));
        x.b.star(center, radius * 2.2 * (1.0 - 0.3 * u), 8, s * 0.1, x.p.core(), al * 0.7);
        V3[] uv = V3.basis(center.sub(x.b.camera()));
        x.b.glowRing(center, uv[0], uv[1], radius * e, 0.1, x.p, al * 0.6);
    }

    static void impact(Ctx x) {
        V3 at = x.pose.at(Math.max(0.8, x.c.b()), 1.1, 0.0);
        if (x.before()) {
            return;
        }
        double s = x.since();
        double span = Math.max(3.0, x.c.end() - x.c.peak());
        double u = Ctx.clamp(s / span);
        double al = Math.pow(1.0 - u, 1.3);
        double size = Math.max(0.6, x.c.a());
        x.b.glowPoint(at, size * (1.4 - 0.6 * u), x.p, al);
        x.b.star(at, size * 3.4 * (0.7 + 0.5 * u), 8, x.c.seed() + s * 0.05, x.p.core(), al * 0.85);
        V3[] uv = V3.basis(x.pose.fwd());
        x.b.glowRing(at, uv[0], uv[1], size * (0.3 + 1.3 * Ctx.easeOut(u)), 0.07, x.p, al * 0.8);
        for (int i = 0; i < 6; i++) {
            double ang = x.rnd(i) * Math.PI * 2.0;
            V3 d = uv[0].scale(Math.cos(ang)).add(uv[1].scale(Math.sin(ang))).plus(x.pose.fwd(), 0.4).unit();
            V3 p = at.plus(d, size * (0.3 + 1.6 * Ctx.easeOut(u)));
            x.b.ribbon(p, p.plus(d, -0.35), 0.06, 0.01, x.p.core(), al, 0.0);
        }
    }

    static void vortex(Ctx x) {
        V3 g = x.groundAt(x.pose.feet().plus(x.pose.fwd(), x.c.d()));
        double radius = Math.max(1.0, x.c.a());
        double height = x.c.b() > 0.0 ? x.c.b() : 3.0;
        double al = x.env();
        double rot = x.t * 0.18;
        int arms = 4;
        for (int arm = 0; arm < arms; arm++) {
            int n = 14;
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            for (int i = 0; i < n; i++) {
                double u = (double) i / (n - 1);
                double ang = rot + arm * Math.PI * 2.0 / arms + u * 3.4;
                double rr = radius * (1.0 - u * 0.92);
                pts[i] = g.add(Math.cos(ang) * rr, 0.02, Math.sin(ang) * rr);
                w[i] = 0.12 + 0.25 * u;
                a[i] = al * (0.3 + 0.7 * u);
            }
            x.b.glowStrip(pts, w, a, x.p, V3.UP);
        }
        for (int k = 0; k < 5; k++) {
            double u = ((x.t * 0.05 + k / 5.0) % 1.0);
            double rr = radius * 0.7 * (1.0 - u) + 0.2;
            x.b.glowRing(g.add(0.0, height * u, 0.0), GX, GZ, rr, 0.06, x.p, al * (1.0 - u) * 0.8);
        }
        for (int i = 0; i < 16; i++) {
            double u = ((x.t * 0.04 + x.rnd(i)) % 1.0);
            double ang = x.rnd(i + 20) * Math.PI * 2.0 + u * 5.0 + rot;
            double rr = radius * (1.0 - u);
            x.b.mote(g.add(Math.cos(ang) * rr, 0.2 + u * height * 0.5, Math.sin(ang) * rr), 0.16, x.p.accent(), al * u);
        }
        x.b.glowPoint(g.add(0.0, 0.5, 0.0), 0.8 + 0.3 * Math.sin(x.t * 0.6), x.p, al);
        x.b.disc(g, GX, GZ, radius, x.p.deep(), al * 0.35, 0.0);
    }

    static void fog(Ctx x) {
        V3 g = x.groundAt(x.pose.feet().plus(x.pose.fwd(), x.c.b()));
        double radius = Math.max(1.0, x.c.a());
        int n = Math.max(4, x.c.n() > 0 ? x.c.n() : 10);
        double al = x.env();
        for (int i = 0; i < n; i++) {
            double ang = x.rnd(i) * Math.PI * 2.0 + x.t * 0.01 * (i % 2 == 0 ? 1 : -1);
            double d = radius * Math.sqrt(x.rnd(i + 10)) * (0.6 + 0.4 * x.life());
            V3 at = g.add(Math.cos(ang) * d, 0.5 + x.rnd(i + 20) * 1.2 + x.life() * 0.6, Math.sin(ang) * d);
            double size = 1.6 + 1.6 * x.rnd(i + 30);
            x.b.flare(at, size, i % 3 == 0 ? x.p.accent() : x.p.deep(), al * 0.22);
            x.b.flare(at, size * 0.5, x.p.glow(), al * 0.12);
        }
    }

    static void chains(Ctx x) {
        double radius = Math.max(2.0, x.c.a());
        int n = Math.max(1, x.c.n() > 0 ? x.c.n() : 4);
        V3 from = x.pose.chest();
        double reach = Ctx.easeOut(x.rise());
        double al = x.fade();
        for (int i = 0; i < n; i++) {
            double ang = (i - (n - 1) / 2.0) * 0.45 + (x.rnd(i) - 0.5) * 0.2;
            V3 dir = x.pose.fwd().yaw(ang);
            V3 to = x.pose.feet().plus(dir, radius * (0.55 + 0.4 * x.rnd(i + 5))).add(0.0, 0.9, 0.0);
            V3 end = from.lerp(to, reach);
            double len = from.distance(end);
            int links = Math.max(2, (int) (len / 0.32));
            V3 along = end.sub(from).unit();
            V3[] uv = V3.basis(along);
            double shake = x.before() ? 0.0 : Math.sin(x.t * 2.0 + i) * 0.03 * (1.0 - x.fall());
            for (int k = 0; k < links; k++) {
                V3 at = from.lerp(end, (k + 0.5) / links).plus(uv[0], shake);
                V3 sa = k % 2 == 0 ? uv[0] : uv[1];
                x.b.ring(at, along, sa, 0.13, 0.035, x.p.core(), al);
                x.b.softRing(at, along, sa, 0.13, 0.12, x.p.glow(), al * 0.6);
            }
            if (reach >= 1.0) {
                x.b.glowPoint(end, 0.6, x.p, al);
                V3[] g = V3.basis(V3.UP);
                x.b.glowRing(end.add(0.0, -0.4, 0.0), g[0], g[1], 0.5, 0.05, x.p, al * 0.8);
            }
        }
    }

    static void breath(Ctx x) {
        double length = Math.max(2.0, x.c.a());
        double half = Math.toRadians(Math.max(10.0, x.c.b() > 0.0 ? x.c.b() : 25.0));
        int tongues = Math.max(5, x.c.n() > 0 ? x.c.n() : 9);
        V3 mouth = x.pose.at(0.5, 1.45, 0.0);
        double al = x.env();
        double reach = Ctx.easeOut(Ctx.clamp((x.t - x.c.start()) / 3.0));
        for (int i = 0; i < tongues; i++) {
            double off = (i / (double) (tongues - 1) - 0.5) * 2.0 * half;
            V3 dir = x.pose.fwd().yaw(off).add(0.0, -0.12 + 0.1 * Math.sin(i * 1.7), 0.0).unit();
            int n = 10;
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            V3[] uv = V3.basis(dir);
            for (int k = 0; k < n; k++) {
                double u = (double) k / (n - 1);
                double wob = Math.sin(u * 6.0 - x.t * 1.3 + i * 2.1) * 0.25 * u;
                pts[k] = mouth.plus(dir, length * u * reach).plus(uv[0], wob).plus(uv[1], wob * 0.6);
                w[k] = 0.15 + 0.9 * u;
                a[k] = al * (1.0 - u) * x.flicker(0.3, i);
            }
            x.b.glowStrip(pts, w, a, x.p);
        }
        for (int i = 0; i < 10; i++) {
            double u = ((x.t * 0.12 + x.rnd(i)) % 1.0);
            double off = (x.rnd(i + 9) - 0.5) * 2.0 * half;
            V3 ember = mouth.plus(x.pose.fwd().yaw(off), length * u * reach).add(0.0, u * 0.6, 0.0);
            x.b.mote(ember, 0.14, x.p.accent(), al * (1.0 - u));
        }
        x.b.glowPoint(mouth, 0.9, x.p, al);
    }

    static void roar(Ctx x) {
        double length = Math.max(2.0, x.c.a());
        int rings = Math.max(2, x.c.n() > 0 ? x.c.n() : 4);
        boolean muzzle = x.c.variant() == 1;
        V3 head = muzzle ? x.pose.hand() : x.pose.at(0.4, 1.5, 0.0);
        V3 dir = muzzle ? x.pose.look() : x.pose.fwd();
        V3[] uv = V3.basis(dir);
        double span = Math.max(4.0, x.c.end() - x.c.start());
        double step = muzzle ? 1.0 : 1.8;
        for (int k = 0; k < rings; k++) {
            double s = x.t - x.c.start() - k * step;
            if (s < 0.0) {
                continue;
            }
            double u = Ctx.clamp(s / (span - k * step));
            V3 at = head.plus(dir, length * Ctx.easeOut(u));
            double r = muzzle ? 0.2 + 0.7 * Ctx.easeOut(u) : 0.5 + 2.2 * Ctx.easeOut(u);
            x.b.glowRing(at, uv[0], uv[1], r, (muzzle ? 0.05 : 0.1) * (1.0 - u) + 0.03, x.p, Math.pow(1.0 - u, 1.2));
        }
        if (!muzzle) {
            double u0 = x.life();
            x.b.glowRing(x.groundAt(x.pose.feet()), GX, GZ, 0.5 + 4.0 * Ctx.easeOut(u0), 0.12, x.p, (1.0 - u0) * 0.6);
        }
    }

    static void dome(Ctx x) {
        double radius = Math.max(0.8, x.c.a());
        V3 center = x.pose.feet().add(0.0, Math.min(1.0, radius * 0.6), 0.0);
        double grow = x.before() ? Ctx.easeOut(x.rise()) : 1.0;
        double pulse = x.before() ? 0.0 : Math.exp(-x.since() * 0.5);
        double r = radius * (0.5 + 0.5 * grow) * (1.0 + 0.06 * pulse);
        double al = x.fade() * grow;
        int lat = 6;
        int lon = 12;
        V3 cam = x.b.camera();
        for (int i = 0; i < lat; i++) {
            double ph = Math.PI / 2.0 * (i + 0.5) / lat;
            double y = Math.sin(ph) * r;
            double rr = Math.cos(ph) * r;
            fresnelRing(x, center.add(0.0, y - (radius > 2.5 ? r * 0.35 : 0.0), 0.0), rr, cam, center, al * 0.85);
        }
        for (int j = 0; j < lon; j++) {
            double th = Math.PI * 2.0 * j / lon + x.t * 0.01;
            V3 dir = new V3(Math.cos(th), 0.0, Math.sin(th));
            V3 base = center.add(0.0, radius > 2.5 ? -r * 0.35 : 0.0, 0.0);
            x.b.arc(base, dir, V3.UP, r, 0.03, 0.0, Math.PI / 2.0, x.p.glow(), al * 0.5, al * 0.15, false);
        }
        // hex glints sliding over it
        for (int k = 0; k < 6; k++) {
            double th = x.rnd(k) * Math.PI * 2.0 + x.t * 0.02;
            double ph = 0.3 + x.rnd(k + 9) * 1.0;
            V3 n = new V3(Math.cos(th) * Math.cos(ph), Math.sin(ph), Math.sin(th) * Math.cos(ph));
            V3 at = center.plus(n, r);
            V3[] uv = V3.basis(n);
            double tw = 0.5 + 0.5 * Math.sin(x.t * 0.4 + k * 1.3);
            x.b.polygram(at, uv[0], uv[1], 0.22 + radius * 0.04, 6, 1, 0.03, 0.0, x.p.core(), al * tw, 1.0);
        }
        if (pulse > 0.01) {
            x.b.glowRing(x.groundAt(x.pose.feet()), GX, GZ, r * (1.0 + 0.3 * (1.0 - pulse)), 0.1, x.p, pulse * al);
        }
    }

    /** A horizontal ring whose light is strongest where it is seen edge-on (the rim of a sphere). */
    private static void fresnelRing(Ctx x, V3 c, double radius, V3 cam, V3 sphereCenter, double alpha) {
        int steps = Math.max(16, Math.min(48, (int) (radius * 10)));
        for (int i = 0; i < steps; i++) {
            double t0 = Math.PI * 2.0 * i / steps;
            double t1 = Math.PI * 2.0 * (i + 1) / steps;
            V3 p0 = c.add(Math.cos(t0) * radius, 0.0, Math.sin(t0) * radius);
            V3 p1 = c.add(Math.cos(t1) * radius, 0.0, Math.sin(t1) * radius);
            double f0 = rim(p0, sphereCenter, cam);
            double f1 = rim(p1, sphereCenter, cam);
            V3 up = new V3(0.0, 0.05, 0.0);
            int c0 = Palette.argb(x.p.glow(), alpha * f0);
            int c1 = Palette.argb(x.p.glow(), alpha * f1);
            x.b.quad(p0.sub(up), c0, p0.add(up), c0, p1.add(up), c1, p1.sub(up), c1);
        }
    }

    private static double rim(V3 p, V3 center, V3 cam) {
        V3 n = p.sub(center).unit();
        V3 v = cam.sub(p).unit();
        double d = Math.abs(n.dot(v));
        return 0.2 + 0.8 * (1.0 - d) * (1.0 - d);
    }

    // ------------------------------------------------------------------ on the caster

    static void cast(Ctx x) {
        Pose pose = x.pose;
        double size = Math.max(0.5, x.c.a());
        V3 hand = pose.hand();
        double k = Ctx.easeOut(x.rise());
        double al = x.fade();
        double flash = x.before() ? 0.0 : Math.exp(-x.since() * 0.6);
        x.b.glowPoint(hand, size * (0.4 + 0.6 * k + 0.6 * flash), x.p, al * (0.5 + 0.5 * k));
        // the casting circle stands upright an arm's length ahead and spins up as the spell gathers; on release it
        // flashes and swells as it fades
        V3 front = pose.at(1.45, 1.3, 0.1);
        double r = size * 0.85 * (0.3 + 0.7 * k) * (1.0 + 0.25 * (1.0 - flash) * (x.before() ? 0.0 : 1.0));
        circle(x, front, pose.right(), V3.UP, r, k, al * 0.9 + flash * 0.4, 6, x.c.variant());
        x.b.glowRing(front, pose.right(), V3.UP, r * 1.18, 0.03, x.p, al * k * 0.7);
        if (flash > 0.02) {
            x.b.glowPoint(front, r * 1.4 * flash + 0.3, x.p, flash);
            x.b.star(front, r * 3.2, 8, x.t * 0.05, x.p.core(), flash * 0.7);
        }
        for (int i = 0; i < 8; i++) {
            double u = ((x.t * 0.1 + x.rnd(i)) % 1.0);
            double ang = x.rnd(i + 3) * Math.PI * 2.0;
            V3 from = hand.add(Math.cos(ang) * 0.9, Math.sin(ang * 1.3) * 0.7, Math.sin(ang) * 0.9);
            x.b.mote(from.lerp(hand, u), 0.1, x.p.accent(), al * u * (x.before() ? 1.0 : 1.0 - x.fall()));
        }
    }

    static void charge(Ctx x) {
        Pose pose = x.pose;
        double size = Math.max(0.5, x.c.a());
        V3 hand = pose.hand();
        double k = Ctx.easeOut(x.rise());
        double al = x.fade();
        x.b.glowPoint(hand, size * (0.3 + 1.0 * k), x.p, al * (0.4 + 0.6 * k));
        V3[] uv = V3.basis(pose.fwd());
        x.b.glowRing(hand, uv[0], uv[1].rotate(uv[0], x.t * 0.3), size * 0.5, 0.03, x.p, al * k);
        x.b.glowRing(hand, uv[1], pose.fwd().rotate(uv[1], -x.t * 0.25), size * 0.42, 0.03, x.p, al * k);
        for (int i = 0; i < 12; i++) {
            double u = ((x.t * 0.09 + x.rnd(i)) % 1.0);
            double th = x.rnd(i + 5) * Math.PI * 2.0;
            double ph = (x.rnd(i + 11) - 0.5) * 2.4;
            V3 from = hand.add(Math.cos(th) * Math.cos(ph) * 1.6, Math.sin(ph) * 1.2, Math.sin(th) * Math.cos(ph) * 1.6);
            V3 at = from.lerp(hand, u * u);
            x.b.ribbon(at, at.lerp(hand, 0.15), 0.07, 0.02, x.p.accent(), al * u, al * u * 0.3);
        }
    }

    static void helix(Ctx x) {
        double radius = Math.max(0.4, x.c.a());
        double height = x.c.b() > 0.0 ? x.c.b() : 2.4;
        int strands = Math.max(1, x.c.n() > 0 ? x.c.n() : 2);
        V3 g = x.pose.feet();
        double grown = Ctx.easeOut(Ctx.clamp((x.t - x.c.start()) / Math.max(1.0, (x.c.end() - x.c.start()) * 0.45)));
        double al = x.fade() * (x.before() ? 1.0 : 1.0);
        for (int s = 0; s < strands; s++) {
            int n = 20;
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            for (int i = 0; i < n; i++) {
                double u = (double) i / (n - 1) * grown;
                double ang = x.t * 0.25 + s * Math.PI * 2.0 / strands + u * Math.PI * 3.0;
                double rr = radius * (1.0 - 0.35 * u);
                pts[i] = g.add(Math.cos(ang) * rr, height * u, Math.sin(ang) * rr);
                w[i] = 0.1 * (1.0 - 0.6 * u);
                a[i] = al * (0.2 + 0.8 * Math.sin(Math.PI * Math.min(1.0, u / Math.max(0.05, grown))));
            }
            x.b.glowStrip(pts, w, a, x.p);
            x.b.glowPoint(pts[n - 1], 0.3, x.p, al);
        }
    }

    static void aura(Ctx x) {
        V3 g = x.groundAt(x.pose.feet());
        double al = x.env();
        for (int k = 0; k < 3; k++) {
            double u = ((x.life() * 1.5 + k / 3.0) % 1.0);
            x.b.glowRing(g.add(0.0, 0.1 + 2.0 * u, 0.0), GX, GZ, 0.75 - 0.2 * u, 0.06, x.p, al * (1.0 - u) * 0.9);
        }
        x.b.disc(g, GX, GZ, 1.1, x.p.glow(), al * 0.4, 0.0);
        double area = x.c.a();
        if (area > 0.5) {
            double u = Ctx.easeOut(x.life() * 1.4);
            x.b.glowRing(g, GX, GZ, area * u, 0.12, x.p, al * (1.0 - x.life()) * 0.9);
        }
        int kind = x.c.variant();
        for (int i = 0; i < 8; i++) {
            double u = ((x.t * 0.06 + x.rnd(i)) % 1.0);
            double ang = x.rnd(i + 40) * Math.PI * 2.0 + x.t * 0.05;
            V3 at = g.add(Math.cos(ang) * 0.6, 0.2 + u * 2.2, Math.sin(ang) * 0.6);
            double a = al * Math.sin(Math.PI * u);
            switch (kind) {
                case 1 -> x.b.spike(at, V3.UP, 0.35, 0.18, x.p.accent(), a, 0.0); // attack: chevrons of power
                case 2 -> x.b.polygram(at, x.pose.right(), V3.UP, 0.16, 6, 1, 0.025, 0.0, x.p.core(), a, 1.0);
                case 3 -> x.b.ribbon(at, at.plus(x.pose.fwd().yaw(ang), -0.5), 0.05, 0.0, x.p.core(), a, 0.0);
                case 4, 11 -> x.b.mote(at, 0.16, i % 2 == 0 ? x.p.glow() : x.p.accent(), a);
                case 10 -> x.b.sparkle(at, 0.35, u * 5.0, x.p, a);
                case 13 -> x.b.spike(at.add(0.0, -0.1, 0.0), new V3(Math.cos(ang), 0.2, Math.sin(ang)), 0.35, 0.12,
                        x.p.glow(), a, 0.0);
                default -> x.b.mote(at, 0.13, x.p.core(), a);
            }
        }
    }

    static void halo(Ctx x) {
        double radius = Math.max(0.25, x.c.a() > 0.0 ? x.c.a() : 0.4);
        V3 at = x.pose.head().add(0.0, 0.45, 0.0);
        double al = x.env();
        V3 a = new V3(1.0, 0.0, 0.0);
        V3 bb = new V3(0.0, Math.sin(0.25), Math.cos(0.25));
        x.b.glowRing(at, a, bb, radius, 0.06, x.p, al);
        x.b.flare(at, radius * 3.0, x.p.glow(), al * 0.25);
    }

    static void streak(Ctx x) {
        Pose now = x.st.now();
        V3 from = x.st.origin().feet();
        V3 to = now.feet();
        if (from.distance(to) < 0.5) {
            to = from.plus(x.st.origin().fwd(), Math.max(1.5, x.c.a()) * Ctx.easeOut(x.rise()));
        }
        double al = x.fade();
        // afterimages: soft figures of light, fainter the older they are
        int ghosts = 4;
        for (int i = 0; i < ghosts; i++) {
            double u = (i + 1.0) / (ghosts + 1.0);
            V3 at = from.lerp(to, u);
            double a = al * (0.1 + 0.35 * u * u);
            x.b.softRibbon(at.add(0.0, 0.25, 0.0), at.add(0.0, 1.35, 0.0), 0.55, 0.62, x.p.glow(), a * 0.45, a * 0.5);
            x.b.flare(at.add(0.0, 1.6, 0.0), 0.55, x.p.glow(), a * 0.7);
        }
        // the wake: a bright stroke at chest height and a wider veil behind it
        x.b.glow(from.add(0.0, 1.0, 0.0), to.add(0.0, 1.0, 0.0), 0.12, 0.42, x.p, 0.0, al);
        x.b.softRibbon(from.add(0.0, 0.9, 0.0), to.add(0.0, 0.9, 0.0), 0.3, 1.6, x.p.deep(), 0.0, al * 0.35);
        x.b.glowPoint(to.add(0.0, 1.0, 0.0), 0.9, x.p, al * (x.before() ? 1.0 : 0.6));
        for (int k = 0; k < 4; k++) {
            double side = (k - 1.5) * 0.25;
            V3 off = x.st.origin().right().scale(side);
            x.b.flatGlow(from.add(off).add(0.0, 0.05, 0.0), to.add(off).add(0.0, 0.05, 0.0), 0.02, 0.06, V3.UP, x.p,
                    0.0, al * 0.6);
        }
    }

    static void leap(Ctx x) {
        V3 g = x.st.origin().feet();
        double height = Math.max(1.0, x.c.a() > 0.0 ? x.c.a() : 2.5);
        double al = x.fade();
        double grown = Ctx.easeOut(x.rise());
        for (int s = 0; s < 3; s++) {
            int n = 14;
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            for (int i = 0; i < n; i++) {
                double u = (double) i / (n - 1) * grown;
                double ang = s * Math.PI * 2.0 / 3.0 + u * Math.PI * 2.5;
                double rr = 0.9 * (1.0 - 0.5 * u);
                pts[i] = g.add(Math.cos(ang) * rr, height * u, Math.sin(ang) * rr);
                w[i] = 0.12;
                a[i] = al * (1.0 - u * 0.6);
            }
            x.b.glowStrip(pts, w, a, x.p);
        }
        x.b.glowRing(g.add(0.0, 0.06, 0.0), GX, GZ, 0.6 + 1.6 * grown, 0.1, x.p, al * (1.0 - grown * 0.5));
    }

    static void weapon(Ctx x) {
        Pose pose = x.pose;
        V3 hand = pose.hand();
        V3 tip = hand.add(0.0, 0.9, 0.0).plus(pose.fwd(), 0.4);
        double al = x.env();
        x.b.glow(hand, tip, 0.12, 0.05, x.p, al, al * 0.6);
        for (int k = 0; k < 2; k++) {
            int n = 12;
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            V3 along = tip.sub(hand).unit();
            V3[] uv = V3.basis(along);
            for (int i = 0; i < n; i++) {
                double u = (double) i / (n - 1);
                double ang = u * 9.0 - x.t * 0.6 + k * Math.PI;
                pts[i] = hand.lerp(tip, u).plus(uv[0], Math.cos(ang) * 0.18).plus(uv[1], Math.sin(ang) * 0.18);
                w[i] = 0.06;
                a[i] = al * (1.0 - u * 0.7);
            }
            x.b.strip(pts, w, a, x.p.accent(), true);
        }
        x.b.glowPoint(hand, 0.8, x.p, al);
        for (int i = 0; i < 6; i++) {
            double u = ((x.t * 0.1 + x.rnd(i)) % 1.0);
            x.b.mote(hand.lerp(tip, x.rnd(i + 4)).add(0.0, u * 0.6, 0.0), 0.12, x.p.accent(), al * (1.0 - u));
        }
    }

    static void orbit(Ctx x) {
        double radius = Math.max(0.6, x.c.a());
        int n = Math.max(2, x.c.n() > 0 ? x.c.n() : 4);
        V3 c = x.pose.chest();
        double al = x.env();
        for (int i = 0; i < n; i++) {
            double base = i * Math.PI * 2.0 / n;
            int trail = 8;
            V3[] pts = new V3[trail];
            double[] w = new double[trail];
            double[] a = new double[trail];
            for (int k = 0; k < trail; k++) {
                double tt = x.t - k * 0.6;
                double ang = base + tt * 0.3;
                pts[k] = c.add(Math.cos(ang) * radius, Math.sin(tt * 0.2 + i) * 0.3, Math.sin(ang) * radius);
                w[k] = 0.12 * (1.0 - (double) k / trail);
                a[k] = al * (1.0 - (double) k / trail);
            }
            x.b.glowStrip(pts, w, a, x.p);
            x.b.glowPoint(pts[0], 0.35, x.p, al);
        }
    }

    static void heal(Ctx x) {
        V3 g = x.groundAt(x.pose.feet());
        double radius = Math.max(1.0, x.c.a());
        double al = x.env();
        x.b.disc(g, GX, GZ, Math.min(radius * 0.6, 3.5), x.p.glow(), al * 0.25, 0.0);
        double u0 = Ctx.easeOut(x.life() * 1.3);
        x.b.glowRing(g, GX, GZ, radius * u0, 0.12, x.p, al * (1.0 - x.life()));
        for (int i = 0; i < 14; i++) {
            double u = ((x.t * 0.05 + x.rnd(i)) % 1.0);
            double ang = x.rnd(i + 20) * Math.PI * 2.0;
            double d = Math.min(radius, 4.0) * Math.sqrt(x.rnd(i + 40));
            V3 at = g.add(Math.cos(ang) * d, 0.3 + u * 2.4, Math.sin(ang) * d);
            double a = al * Math.sin(Math.PI * u);
            if (i % 3 == 0) {
                Grand.crossMark(x, at, 0.28, a);
            } else {
                x.b.mote(at, 0.14, i % 2 == 0 ? x.p.glow() : x.p.accent(), a);
            }
        }
        x.b.glowColumn(g, 2.6, 0.35, x.p, al * 0.5, 0.0);
    }

    static void cleanse(Ctx x) {
        // waist-high, so the caster's own eyes look down on the wave as it runs out (not along it)
        V3 c = x.pose.feet().add(0.0, 0.8, 0.0);
        double radius = Math.max(1.0, x.c.a());
        double u = Ctx.easeOut(x.life());
        double al = 1.0 - x.life();
        Palette white = new Palette(0xFFFFFF, 0xEAF6FF, 0xFFFFFF, 0x9AC8FF);
        x.b.glowRing(c, GX, GZ, radius * u, 0.15, white, al);
        x.b.glowRing(c.add(0.0, -0.6, 0.0), GX, GZ, radius * u * 0.8, 0.08, white, al * 0.6);
        for (int i = 0; i < 10; i++) {
            double ang = x.rnd(i) * Math.PI * 2.0;
            V3 at = c.add(Math.cos(ang) * radius * u * 0.8, x.life() * 1.5 * x.rnd(i + 3), Math.sin(ang) * radius * u * 0.8);
            x.b.sparkle(at, 0.35, ang, white, al);
        }
    }

    static void motes(Ctx x) {
        double radius = Math.max(0.5, x.c.a());
        int n = Math.max(4, x.c.n());
        V3 c = x.pose.chest();
        double al = x.env();
        for (int i = 0; i < n; i++) {
            double u = ((x.t * 0.05 + x.rnd(i)) % 1.0);
            double ang = x.rnd(i + 3) * Math.PI * 2.0;
            double d = radius * Math.sqrt(x.rnd(i + 6));
            V3 at = switch (x.c.variant()) {
                case 1 -> c.add(Math.cos(ang) * d * (1.0 - u), (x.rnd(i + 9) - 0.5) * (1.0 - u), Math.sin(ang) * d * (1.0 - u));
                case 2 -> c.add(Math.cos(ang + x.t * 0.1) * d, Math.sin(x.t * 0.1 + i) * 0.4, Math.sin(ang + x.t * 0.1) * d);
                case 3 -> c.add(Math.cos(ang) * d, 2.0 - u * 3.0, Math.sin(ang) * d);
                default -> x.groundAt(c).add(Math.cos(ang) * d, u * 2.5, Math.sin(ang) * d);
            };
            x.b.mote(at, 0.13, i % 2 == 0 ? x.p.glow() : x.p.accent(), al * Math.sin(Math.PI * u));
        }
    }

    static void siphon(Ctx x) {
        V3 to = x.pose.chest();
        double dist = Math.max(1.5, x.c.a());
        double al = x.env();
        for (int k = 0; k < 4; k++) {
            double ang = (k - 1.5) * 0.35;
            V3 from = x.pose.feet().plus(x.pose.fwd().yaw(ang), dist).add(0.0, 1.0, 0.0);
            V3 ctrl = from.lerp(to, 0.5).add(0.0, 1.0 + 0.3 * k, 0.0);
            int n = 12;
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            for (int i = 0; i < n; i++) {
                double u = (double) i / (n - 1);
                pts[i] = bezier(from, ctrl, to, u);
                w[i] = 0.08;
                a[i] = al * 0.5;
            }
            x.b.glowStrip(pts, w, a, x.p);
            for (int j = 0; j < 3; j++) {
                double u = ((x.t * 0.12 + j / 3.0 + k * 0.1) % 1.0);
                x.b.glowPoint(bezier(from, ctrl, to, u), 0.3, x.p, al);
            }
        }
    }

    static V3 bezier(V3 a, V3 c, V3 b, double u) {
        return a.lerp(c, u).lerp(c.lerp(b, u), u);
    }

    static void taunt(Ctx x) {
        V3 c = x.pose.chest();
        double radius = Math.max(3.0, x.c.a());
        Palette red = Palette.of(SkillTheme.BLOOD);
        for (int k = 0; k < 3; k++) {
            double u = Ctx.clamp((x.t - x.c.start() - k * 2.0) / Math.max(3.0, x.c.end() - x.c.start() - 4.0));
            if (u <= 0.0) {
                continue;
            }
            x.b.glowRing(x.groundAt(c), GX, GZ, radius * Ctx.easeOut(u), 0.15, red, (1.0 - u) * 0.9);
            x.b.glowRing(c, GX, GZ, 0.8 + 1.4 * Ctx.easeOut(u), 0.06, red, (1.0 - u) * 0.7);
        }
        double al = x.env();
        for (int i = 0; i < 3; i++) {
            V3 top = x.pose.head().add(0.0, 0.5 + 0.3 * Math.sin(x.t * 0.8 + i), 0.0).plus(x.pose.right(), (i - 1) * 0.35);
            x.b.spike(top.add(0.0, 0.45, 0.0), new V3(0.0, -1.0, 0.0), 0.45, 0.16, red.glow(), al, al * 0.4);
            x.b.glowPoint(top.add(0.0, -0.1, 0.0), 0.15, red, al);
        }
    }

    static void afflict(Ctx x) {
        V3 at = x.pose.at(Math.max(1.0, x.c.b()), 1.0, 0.0);
        double radius = Math.max(0.6, x.c.a());
        if (x.before()) {
            return;
        }
        double s = x.since();
        double span = Math.max(4.0, x.c.end() - x.c.peak());
        double u = Ctx.clamp(s / span);
        double al = Math.pow(1.0 - u, 1.1);
        int kind = x.c.variant();
        switch (kind) {
            case 0 -> { // slow: heavy rings sinking
                Palette p = Palette.of(SkillTheme.FROST);
                for (int k = 0; k < 2; k++) {
                    x.b.glowRing(at.add(0.0, 0.6 - u * 1.4 - k * 0.3, 0.0), GX, GZ, radius * (0.7 + 0.2 * k), 0.07, p, al * 0.7);
                }
            }
            case 1 -> { // freeze: ice crystals
                Palette p = Palette.of(SkillTheme.FROST);
                for (int i = 0; i < 7; i++) {
                    double ang = x.rnd(i) * Math.PI * 2.0;
                    V3 dir = new V3(Math.cos(ang), 0.6 + x.rnd(i + 3), Math.sin(ang));
                    x.b.crystal(at.add(0.0, -0.7, 0.0), dir, 0.6 + 0.6 * x.rnd(i + 7) * Ctx.easeOut(Ctx.clamp(s / 2.0)), 0.2, p, al);
                }
            }
            case 2 -> { // burn: flames licking up
                Palette p = Palette.of(SkillTheme.FIRE);
                for (int i = 0; i < 6; i++) {
                    double ang = x.rnd(i) * Math.PI * 2.0;
                    V3 base = at.add(Math.cos(ang) * radius * 0.4, -0.8, Math.sin(ang) * radius * 0.4);
                    x.b.crystal(base, V3.UP, (0.5 + 0.6 * x.rnd(i + 5)) * x.flicker(0.5, i), 0.25, p, al);
                }
            }
            case 3 -> { // bleed: drops flung
                Palette p = Palette.of(SkillTheme.BLOOD);
                for (int i = 0; i < 8; i++) {
                    double ang = x.rnd(i) * Math.PI * 2.0;
                    V3 d = new V3(Math.cos(ang), 0.5, Math.sin(ang));
                    V3 drop = at.plus(d, s * 0.12).add(0.0, -s * s * 0.012, 0.0);
                    x.b.ribbon(drop, drop.plus(d, -0.2), 0.08, 0.02, p.glow(), al, al * 0.2);
                }
            }
            case 4 -> { // blind: a white flash
                Palette p = Palette.of(SkillTheme.HOLY);
                x.b.glowPoint(at, 1.8 * (1.0 - u), p, al);
                x.b.star(at, 3.0, 8, s * 0.1, 0xFFFFFF, al * 0.8);
            }
            case 5 -> { // stun: stars circling above
                Palette p = Palette.of(SkillTheme.THUNDER);
                V3 top = at.add(0.0, 1.0, 0.0);
                for (int i = 0; i < 4; i++) {
                    double ang = x.t * 0.35 + i * Math.PI / 2.0;
                    x.b.sparkle(top.add(Math.cos(ang) * 0.45, 0.0, Math.sin(ang) * 0.45), 0.35, ang, p, al);
                }
                x.b.ring(top, GX, GZ, 0.45, 0.02, p.glow(), al * 0.5);
            }
            case 6 -> { // poison: bubbles rising
                Palette p = Palette.of(SkillTheme.VENOM);
                for (int i = 0; i < 8; i++) {
                    double uu = ((s * 0.08 + x.rnd(i)) % 1.0);
                    double ang = x.rnd(i + 3) * Math.PI * 2.0;
                    V3 bub = at.add(Math.cos(ang) * radius * 0.4, -0.6 + uu * 1.6, Math.sin(ang) * radius * 0.4);
                    V3[] uv = V3.basis(bub.sub(x.b.camera()));
                    x.b.ring(bub, uv[0], uv[1], 0.08 + 0.05 * x.rnd(i + 8), 0.02, p.glow(), al * (1.0 - uu));
                }
                x.b.flare(at, 1.4, p.deep(), al * 0.3);
            }
            case 7 -> { // weaken: arrows sinking
                Palette p = Palette.of(SkillTheme.SHADOW);
                for (int i = 0; i < 3; i++) {
                    V3 top = at.add((i - 1) * 0.35, 0.6 - u * 0.8, 0.0);
                    x.b.spike(top, new V3(0.0, -1.0, 0.0), 0.4, 0.2, p.glow(), al, al * 0.2);
                }
            }
            case 8 -> { // vulnerable: a cracked ring
                Palette p = Palette.of(SkillTheme.BLOOD);
                V3[] uv = V3.basis(x.pose.fwd());
                for (int k = 0; k < 4; k++) {
                    double t0 = k * Math.PI / 2.0 + 0.2;
                    x.b.arc(at, uv[0], uv[1], 0.6 + u * 0.3, 0.06, t0, t0 + 1.1, p.glow(), al, al, false);
                }
            }
            case 9 -> { // pull: an inward spiral
                for (int k = 0; k < 3; k++) {
                    double rr = radius * (1.0 - ((s * 0.15 + k / 3.0) % 1.0));
                    x.b.glowRing(at.add(0.0, -0.8, 0.0), GX, GZ, rr, 0.05, x.p, al * 0.8);
                }
            }
            default -> { // launch: streaks shooting up
                for (int i = 0; i < 5; i++) {
                    double ang = x.rnd(i) * Math.PI * 2.0;
                    V3 base = at.add(Math.cos(ang) * 0.4, -0.8 + s * 0.25, Math.sin(ang) * 0.4);
                    x.b.glow(base, base.add(0.0, 0.8, 0.0), 0.06, 0.01, x.p, al, 0.0);
                }
            }
        }
    }

    static void flash(Ctx x) {
        V3 at = x.pose.feet().plus(x.pose.fwd(), x.c.b()).add(0.0, 1.2, 0.0);
        double size = Math.max(1.0, x.c.a());
        double s = x.since();
        double al = x.before() ? Ctx.easeIn(x.rise()) * 0.6 : Math.exp(-s * 0.5);
        x.b.glowPoint(at, size, x.p, al);
        x.b.star(at, size * 2.4, 12, x.c.seed() + x.t * 0.03, x.p.core(), al * 0.6);
    }
}
