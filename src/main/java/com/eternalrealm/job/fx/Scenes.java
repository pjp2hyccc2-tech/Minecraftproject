package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;

/**
 * The scenes of light of the finales (秘奥義): the shinobi's eight cuts and after-images, pale fire, mana streaming in,
 * the rifle's tracer, the seven spirits and their great bow, the holy rift, the scales of judgment and the sentences,
 * and the cathedral of light.
 */
final class Scenes {
    private static final V3 GX = new V3(1.0, 0.0, 0.0);
    private static final V3 GZ = new V3(0.0, 0.0, 1.0);
    /** The elements of the seven spirits, in the order they stand around the archer. */
    static final SkillTheme[] SPIRIT_THEMES = {SkillTheme.FIRE, SkillTheme.FROST, SkillTheme.THUNDER, SkillTheme.WIND,
            SkillTheme.EARTH, SkillTheme.HOLY, SkillTheme.SHADOW};

    private Scenes() {
    }

    // ------------------------------------------------------------------ the eight cuts

    /** Where cut {@code i} of a run of flashes crosses the area: its two ends (1 block over the ground). */
    static V3[] cut(Ctx x, int i, double radius) {
        V3 g = x.groundAt(x.pose.feet());
        double angle = i * Math.toRadians(67.5) + (x.rnd(i) - 0.5) * 0.35;
        V3 dir = new V3(Math.cos(angle), 0.0, Math.sin(angle));
        V3 side = new V3(-dir.z(), 0.0, dir.x());
        V3 mid = g.plus(side, (x.rnd(i + 20) - 0.5) * radius * 0.5).add(0.0, 0.9 + 0.5 * x.rnd(i + 40), 0.0);
        double tilt = (x.rnd(i + 60) - 0.5) * 0.5;
        V3 a = mid.plus(dir, -radius).add(0.0, -tilt, 0.0);
        V3 b = mid.plus(dir, radius).add(0.0, tilt, 0.0);
        return i % 2 == 0 ? new V3[]{a, b} : new V3[]{b, a};
    }

    /**
     * The shinobi's flashes: at each hit frame a cut streaks across the area and the caster's after-image stands where
     * it ends; the cuts hang in the air as hairlines, and at the peak every one of them flares at once.
     */
    static void flashCuts(Ctx x) {
        double radius = Math.max(2.0, x.c.a());
        int[] at = Props.beats(x, x.c.variant(), Math.max(1, x.c.n()));
        double burst = x.before() ? 0.0 : 1.0 - Ctx.clamp(x.since() / Math.max(1.0, x.c.end() - x.c.peak()));
        Palette steel = x.p;
        for (int i = 0; i < at.length; i++) {
            double since = x.t - at[i];
            if (since < 0.0) {
                continue;
            }
            V3[] ends = cut(x, i, radius);
            V3 a = ends[0];
            V3 b = ends[1];
            if (x.before()) {
                // the stroke: a bright head racing across in two ticks, then the hairline it leaves
                double head = Ctx.clamp(since / 2.0);
                V3 tip = a.lerp(b, head);
                double fresh = Math.exp(-since / 3.0);
                x.b.glow(a.lerp(b, Math.max(0.0, head - 0.5)), tip, 0.09 + 0.25 * fresh, 0.02, steel,
                        0.35 + 0.65 * fresh, 0.9);
                x.b.glow(a, b, 0.012, steel, 0.4);
                if (head < 1.0) {
                    x.b.flare(tip, 1.4, steel.core(), 0.9);
                }
                // the after-image where the flash ends, fading
                double ghost = Math.exp(-Math.max(0.0, since - 2.0) / 4.0) * Ctx.clamp(since / 1.5);
                if (ghost > 0.03) {
                    V3 feet = new V3(b.x(), x.groundAt(b).y(), b.z());
                    figure(x, feet, b.sub(a).flat().unit(GX), ghost * 0.85, steel, i);
                }
            } else {
                // all of them burst at once
                double w = 0.08 + 0.55 * burst;
                x.b.glow(a.lerp(b, -0.1), b.lerp(a, -0.1), w, steel, burst);
                for (int k = 0; k < 3; k++) {
                    V3 spark = a.lerp(b, x.rnd(i * 7 + k));
                    x.b.star(spark, 1.8 * burst + 0.3, 6, x.rnd(i + k), steel.core(), burst);
                }
            }
        }
    }

    /**
     * An after-image of the caster: a figure of light at {@code feet} running along {@code facing}, leaning in, its
     * blade swept back.
     */
    static void figure(Ctx x, V3 feet, V3 facing, double alpha, Palette p, int seed) {
        V3 f = facing.flat().unit(GX);
        V3 r = new V3(-f.z(), 0.0, f.x());
        double lean = 0.45;
        V3 up = V3.UP.plus(f, lean).unit();
        V3 hip = feet.add(0.0, 0.85, 0.0);
        V3 neck = hip.plus(up, 0.75);
        V3 head = neck.plus(up, 0.3);
        double w = 0.13;
        // a soft body of light around the figure, and a scarf streaming from the neck
        x.b.flare(hip.plus(up, 0.35), 1.5, p.glow(), alpha * 0.22);
        x.b.glow(neck, neck.plus(f, -1.1).add(0.0, 0.15 + 0.1 * Math.sin(x.t * 0.8 + seed), 0.0), 0.09, 0.03, p,
                alpha * 0.7, 0.0);
        // legs mid-stride, arms: one forward, the sword hand far back
        x.b.glow(hip.plus(r, 0.12), feet.plus(f, 0.55).plus(r, 0.15).add(0.0, 0.1, 0.0), w, p, alpha * 0.8);
        x.b.glow(hip.plus(r, -0.12), feet.plus(f, -0.6).plus(r, -0.12).add(0.0, 0.25, 0.0), w, p, alpha * 0.8);
        x.b.glow(hip, neck, w * 2.1, p, alpha);
        x.b.glow(neck.plus(r, 0.2), neck.plus(f, 0.55).plus(r, 0.3).add(0.0, -0.25, 0.0), w * 0.8, p, alpha * 0.8);
        V3 hand = neck.plus(f, -0.55).plus(r, -0.35).add(0.0, -0.35, 0.0);
        x.b.glow(neck.plus(r, -0.2), hand, w * 0.8, p, alpha * 0.8);
        x.b.glow(hand, hand.plus(f, -1.3).add(0.0, 0.25, 0.0), 0.035, p, alpha);
        x.b.glowPoint(head, 0.3, p, alpha);
        // speed lines trailing it
        for (int k = 0; k < 3; k++) {
            double y = 0.5 + 0.45 * k + 0.1 * Brush.unit(seed, k);
            V3 from = feet.add(0.0, y, 0.0).plus(f, -0.4);
            x.b.glow(from, from.plus(f, -1.6 - k * 0.4), 0.03, 0.0, p, alpha * 0.6, 0.0);
        }
    }

    // ------------------------------------------------------------------ fire on the ground

    /** Tongues of fire over a disc of {@code a} blocks (the sage's pale blue flames where the star fell). */
    static void flames(Ctx x) {
        double radius = Math.max(1.0, x.c.a());
        int n = Math.max(8, x.c.n());
        double al = x.env();
        if (al <= 0.01) {
            return;
        }
        V3 g = x.groundAt(x.pose.feet());
        x.b.disc(g.add(0.0, 0.04, 0.0), GX, GZ, radius, x.p.deep(), 0.35 * al, 0.0);
        x.b.glowRing(g.add(0.0, 0.05, 0.0), GX, GZ, radius, 0.12, x.p, al * 0.6);
        for (int i = 0; i < n; i++) {
            double ang = x.rnd(i) * Math.PI * 2.0;
            double d = Math.sqrt(x.rnd(i + 300)) * radius * 0.95;
            V3 base = g.add(Math.cos(ang) * d, 0.0, Math.sin(ang) * d);
            double phase = x.t * (0.28 + 0.2 * x.rnd(i + 600)) + x.rnd(i + 900) * 6.28;
            double h = (0.7 + 1.6 * x.rnd(i + 1200)) * (0.75 + 0.25 * Math.sin(phase)) * (1.0 - 0.5 * d / radius);
            V3 sway = new V3(Math.sin(phase * 1.3) * 0.15, 0.0, Math.cos(phase * 0.9) * 0.15);
            V3 mid = base.add(0.0, h * 0.5, 0.0).add(sway.scale(0.5));
            V3 top = base.add(0.0, h, 0.0).add(sway);
            x.b.softRibbon(base, mid, 0.55 * h, 0.4 * h, x.p.deep(), al * 0.55, al * 0.45);
            x.b.softRibbon(mid, top, 0.4 * h, 0.02, x.p.glow(), al * 0.75, 0.0);
            x.b.ribbon(base.add(0.0, 0.05, 0.0), mid, 0.12 * h, 0.05 * h, x.p.core(), al * 0.5, al * 0.3);
            double rise = (x.t * 0.06 + x.rnd(i + 1500)) % 1.0;
            x.b.mote(base.add(sway.x() * 2.0, h + rise * 2.0, sway.z() * 2.0), 0.08, x.p.accent(), al * (1.0 - rise));
        }
    }

    // ------------------------------------------------------------------ mana streaming in

    /**
     * Mana drawn in from every side to one point (the necromancer's finale): {@code n} streams spiralling in from up
     * to {@code a} blocks away, quickening towards the peak, and a heart of condensed mana growing where they meet.
     */
    static void manaStreams(Ctx x) {
        double radius = Math.max(4.0, x.c.a());
        int n = Math.max(6, x.c.n());
        V3 heart = x.pose.feet().plus(x.pose.fwd(), x.c.b()).add(0.0, x.c.c(), 0.0);
        double al = x.fade();
        double gather = Ctx.easeIn(x.rise());
        for (int i = 0; i < n; i++) {
            double ang = x.rnd(i) * Math.PI * 2.0;
            double dist = radius * (0.6 + 0.4 * x.rnd(i + 100));
            double lift = (x.rnd(i + 200) - 0.3) * 6.0;
            double speed = 0.018 + 0.03 * gather + 0.01 * x.rnd(i + 300);
            double k = (x.t * speed + x.rnd(i + 400)) % 1.0;
            int segs = 7;
            V3[] pts = new V3[segs];
            double[] w = new double[segs];
            double[] a = new double[segs];
            for (int s = 0; s < segs; s++) {
                double u = Math.max(0.0, k - s * 0.035);
                double reach = 1.0 - u;
                double swirl = ang + u * 2.4;
                pts[s] = heart.add(Math.cos(swirl) * dist * reach, lift * reach * reach, Math.sin(swirl) * dist * reach);
                w[s] = (0.22 - 0.025 * s) * (0.6 + 0.6 * gather);
                a[s] = al * (1.0 - (double) s / segs) * Ctx.clamp(u * 6.0) * Ctx.clamp((1.0 - u) * 8.0);
            }
            x.b.glowStrip(pts, w, a, x.p);
        }
        double size = 0.3 + 1.6 * gather;
        x.b.glowPoint(heart, size, x.p, al);
        x.b.ring(heart, V3.basis(heart.sub(x.b.camera()))[0], V3.basis(heart.sub(x.b.camera()))[1], size * 0.9,
                0.08, x.p.accent(), al * 0.8);
        x.b.star(heart, size * 2.2, 8, x.t * 0.08, x.p.core(), al * (0.3 + 0.5 * gather));
        if (!x.before()) {
            double u = Ctx.clamp(x.since() / 8.0);
            x.b.flare(heart, 6.0 * (1.0 - u) + 1.0, x.p.core(), 1.0 - u);
        }
    }

    // ------------------------------------------------------------------ the rifle's shot

    /** A shot of the heavy rifle: the muzzle blast and a tracer {@code a} blocks along the hit frame's look. */
    static void tracer(Ctx x) {
        Pose pose = x.pose;
        V3 look = pose.look().unit(pose.fwd());
        V3 u = Props.across(V3.UP, look, V3.UP);
        V3 r = look.cross(u).unit().scale(pose.side());
        V3 muzzle = x.b.firstPerson() ? pose.eye().plus(look, 0.5 + 1.75).plus(r, 0.25).plus(u, -0.27)
                : pose.at(0.28, 1.36, 0.2).plus(look, 2.1);
        double since = x.since();
        if (since < 0.0) {
            return;
        }
        double length = Math.max(8.0, x.c.a());
        double fade = Math.exp(-since / 3.0);
        Palette fire = Palette.of(SkillTheme.FIRE);
        // the blast: a star, a cone of fire, a ring of pressure
        if (since < 4.0) {
            double k = 1.0 - since / 4.0;
            x.b.star(muzzle, 1.6 * k + 0.4, 8, x.rnd(1) * 3.0, fire.core(), k);
            x.b.glow(muzzle, muzzle.plus(look, 1.2 * k + 0.3), 0.4 * k, 0.05, fire, k, 0.0);
            V3[] uv = V3.basis(look);
            x.b.ring(muzzle.plus(look, 0.2), uv[0], uv[1], 0.15 + since * 0.3, 0.025, fire.core(), k * 0.8);
        }
        // the tracer: a thin white-hot line that thins and fades
        V3 end = muzzle.plus(look, length * Ctx.clamp(since / 1.2 + 0.3));
        x.b.glow(muzzle.plus(look, 0.3), end, 0.05 * fade + 0.01, x.p, fade);
        x.b.ribbon(muzzle, end, 0.02, 0.01, 0xFFFFFF, fade, fade * 0.6);
        // smoke-light drifting from the muzzle
        for (int i = 0; i < 4; i++) {
            V3 puff = muzzle.plus(look, 0.2 + i * 0.25).add(0.0, since * 0.03 * (i + 1), 0.0);
            x.b.mote(puff, 0.18 + since * 0.03, x.p.deep(), Math.exp(-since / 6.0) * 0.4);
        }
    }

    // ------------------------------------------------------------------ the seven spirits and their bow

    /** Where the great bow's arrow is nocked (in front of the eyes in first person, at the shoulder otherwise). */
    static V3 nock(Ctx x) {
        Pose pose = x.pose;
        V3 look = pose.look().unit(pose.fwd());
        return x.b.firstPerson() ? pose.eye().plus(look, 0.9).add(0.0, -0.12, 0.0)
                : pose.eye().plus(look, 0.55).add(0.0, -0.15, 0.0);
    }

    /**
     * The seven spirits: small figures of fire, ice, thunder, wind, earth, light and darkness rising around the archer,
     * circling, and flying into the arrow just before the peak.
     */
    static void spirits(Ctx x) {
        double radius = Math.max(1.0, x.c.a());
        int n = Math.min(SPIRIT_THEMES.length, Math.max(1, x.c.n()));
        V3 centre = x.pose.feet().add(0.0, 1.3, 0.0);
        double appear = Ctx.easeOut(Ctx.clamp((x.t - x.c.start()) / 8.0));
        double gone = x.before() ? 0.0 : 1.0;
        if (gone > 0.0) {
            return;
        }
        double fly = Ctx.easeIn(Ctx.clamp((x.t - (x.c.peak() - 22.0)) / 22.0));
        V3 arrow = nock(x).plus(x.pose.look().unit(x.pose.fwd()), 1.2);
        for (int i = 0; i < n; i++) {
            Palette p = Palette.of(SPIRIT_THEMES[i]);
            double ang = Math.PI * 2.0 * i / n + x.t * 0.05;
            double y = 0.4 + 0.6 * Math.sin(x.t * 0.12 + i * 1.7);
            V3 home = centre.add(Math.cos(ang) * radius, y - (1.0 - appear) * 1.6, Math.sin(ang) * radius);
            V3 at = home.lerp(arrow, fly);
            double size = (0.5 + 0.12 * Math.sin(x.t * 0.3 + i)) * (1.0 - 0.6 * fly) * appear;
            double al = appear * (1.0 - 0.3 * fly);
            x.b.glowPoint(at, size, p, al);
            spiritMark(x, at, size, p, al, i);
            if (fly > 0.0) {
                x.b.glow(at, home.lerp(arrow, Math.max(0.0, fly - 0.25)), size * 0.5, 0.02, p, al * 0.8, 0.0);
            } else {
                // a short wake along the orbit
                V3 back = centre.add(Math.cos(ang - 0.25) * radius, y, Math.sin(ang - 0.25) * radius);
                x.b.glow(at, back, size * 0.3, 0.0, p, al * 0.5, 0.0);
            }
        }
    }

    /** The sign of one spirit's element around its light. */
    private static void spiritMark(Ctx x, V3 at, double size, Palette p, double al, int element) {
        V3[] uv = V3.basis(at.sub(x.b.camera()));
        switch (element) {
            case 0 -> x.b.spike(at.add(0.0, -size * 0.4, 0.0), V3.UP, size * 2.2, size * 0.8, p.glow(), al, 0.0);
            case 1 -> {
                for (int k = 0; k < 3; k++) {
                    V3 d = uv[0].rotate(uv[0].cross(uv[1]).unit(), Math.PI * k / 3.0);
                    x.b.glow(at.plus(d, -size * 1.2), at.plus(d, size * 1.2), size * 0.12, p, al);
                }
            }
            case 2 -> x.b.bolt(at.add(0.0, size * 1.5, 0.0), at.add(0.0, -size * 1.5, 0.0), x.c.seed() + element,
                    4, size * 0.4, size * 0.15, p, al);
            case 3 -> x.b.ring(at, uv[0], uv[1], size * 1.3, size * 0.08, p.glow(), al);
            case 4 -> x.b.crystal(at.add(0.0, -size * 0.6, 0.0), V3.UP, size * 1.6, size * 0.6, p, al);
            case 5 -> x.b.star(at, size * 3.0, 8, x.t * 0.1, p.core(), al);
            default -> x.b.arc(at, uv[0], uv[1], size * 1.1, size * 0.25, 0.6, Math.PI * 2.0 - 0.6, p.glow(), al, al,
                    true);
        }
    }

    /**
     * The great bow of light: its limbs grow out from the archer's hand, the string is drawn back as the spirits
     * pour in, and at the peak it is loosed: the string snaps, and an arrow of every colour flies off along the look.
     */
    static void greatBow(Ctx x) {
        Pose pose = x.pose;
        V3 look = pose.look().unit(pose.fwd());
        V3 up = Props.across(V3.UP, look, V3.UP);
        double size = Math.max(1.0, x.c.a());
        V3 nockAt = nock(x);
        V3 grip = nockAt.plus(look, size * 0.42);
        double form = Ctx.easeOut(Ctx.clamp((x.t - x.c.start()) / 12.0));
        double draw = x.before() ? Ctx.smooth(Ctx.clamp((x.t - (x.c.start() + 12.0)) / Math.max(1.0,
                x.c.peak() - x.c.start() - 14.0))) : 0.0;
        double after = x.before() ? 0.0 : x.since();
        double al = x.fade() * form;
        if (al <= 0.01) {
            return;
        }
        // the limbs: two arcs bending back towards the archer
        int n = 9;
        V3[] limb = new V3[n];
        double[] w = new double[n];
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            double v = (double) i / (n - 1) * 2.0 - 1.0;
            double bend = (0.18 + 0.22 * draw) * size * v * v;
            limb[i] = grip.plus(up, v * size * 0.5 * form).plus(look, -bend);
            w[i] = 0.17 * (1.0 - 0.5 * Math.abs(v));
            a[i] = al;
        }
        x.b.glowStrip(limb, w, a, x.p);
        x.b.flare(grip, size * 1.2, x.p.glow(), al * 0.25);
        V3 top = limb[n - 1];
        V3 bottom = limb[0];
        V3 pulled = grip.plus(look, -size * (0.3 + 0.45 * draw));
        if (after <= 0.0) {
            x.b.glow(top, pulled, 0.02, x.p, al);
            x.b.glow(pulled, bottom, 0.02, x.p, al);
            // the arrow being made of every colour
            V3 head = pulled.plus(look, size * 1.1);
            for (int i = 0; i < SPIRIT_THEMES.length; i++) {
                Palette p = Palette.of(SPIRIT_THEMES[i]);
                double off = (i - 3) * 0.035;
                x.b.glow(pulled.plus(up, off), head.plus(up, off * 0.3), 0.02, p, al * draw);
            }
            x.b.glowPoint(head, 0.2 + 0.6 * draw, x.p, al * (0.4 + 0.6 * draw));
        } else {
            // loosed: the string snaps, the arrow flies with seven streams of colour behind it
            double snap = Math.exp(-after / 2.0);
            V3 string = grip.plus(look, -size * 0.3 + Math.sin(after * 3.0) * 0.1 * snap);
            x.b.glow(top, string, 0.02, x.p, al);
            x.b.glow(string, bottom, 0.02, x.p, al);
            double flown = Math.min(48.0, after * 5.5);
            V3 head = grip.plus(look, size * 0.7 + flown);
            for (int i = 0; i < SPIRIT_THEMES.length; i++) {
                Palette p = Palette.of(SPIRIT_THEMES[i]);
                V3 off = up.rotate(look, Math.PI * 2.0 * i / SPIRIT_THEMES.length + after * 0.6).scale(0.35);
                x.b.glow(grip.plus(look, size * 0.7).add(off.scale(0.2)), head.add(off), 0.12, 0.05, p,
                        al * 0.2, al * 0.9);
            }
            x.b.glowPoint(head, 1.0, Palette.of(SkillTheme.HOLY), al);
            x.b.star(head, 3.0, 8, after * 0.2, 0xFFFFFF, al);
            if (after < 4.0) {
                x.b.flare(grip, 3.0 * (1.0 - after / 4.0), x.p.core(), 1.0 - after / 4.0);
            }
        }
    }

    // ------------------------------------------------------------------ the holy rift

    /**
     * The rift the giant blade cuts: from {@code d} ahead to {@code d + a}, opening outward from the caster at the peak,
     * light welling out of it in columns and motes for as long as the holy field lasts.
     */
    static void fissure(Ctx x) {
        if (x.before()) {
            return;
        }
        Pose pose = x.pose;
        V3 f = pose.fwd();
        V3 r = new V3(-f.z(), 0.0, f.x());
        double length = Math.max(2.0, x.c.a());
        double width = Math.max(0.2, x.c.b());
        double open = Ctx.easeOut(Ctx.clamp(x.since() / 5.0));
        double al = x.fade();
        V3 start = x.groundAt(pose.feet().plus(f, x.c.d()));
        V3 end = start.plus(f, length * open);
        V3[] crack = Brush.jagged(start, end, x.c.seed(), 12, width * 0.6);
        for (int i = 0; i < crack.length; i++) {
            crack[i] = new V3(crack[i].x(), start.y() + 0.04, crack[i].z());
        }
        double[] w = new double[crack.length];
        double[] a = new double[crack.length];
        double pulse = 0.8 + 0.2 * Math.sin(x.t * 0.35);
        for (int i = 0; i < crack.length; i++) {
            double u = (double) i / (crack.length - 1);
            w[i] = width * (1.0 - 0.6 * Math.abs(u - 0.35));
            a[i] = al * pulse;
        }
        x.b.glowStrip(crack, w, a, x.p, V3.UP);
        // side cracks branching off
        for (int k = 0; k < 6; k++) {
            int at = 2 + (int) (x.rnd(k) * (crack.length - 4));
            V3 from = crack[at];
            V3 dir = r.scale(k % 2 == 0 ? 1.0 : -1.0).plus(f, (x.rnd(k + 10) - 0.3)).unit();
            V3[] branch = Brush.jagged(from, from.plus(dir, (1.0 + 2.0 * x.rnd(k + 20)) * open), x.c.seed() + k, 4,
                    0.3);
            double[] bw = new double[branch.length];
            double[] ba = new double[branch.length];
            for (int i = 0; i < branch.length; i++) {
                double u = (double) i / (branch.length - 1);
                bw[i] = width * 0.45 * (1.0 - u);
                ba[i] = al * pulse * (1.0 - 0.7 * u);
            }
            x.b.glowStrip(branch, bw, ba, x.p, V3.UP);
        }
        // light welling up: columns that rise and sink along it, motes drifting up
        for (int i = 0; i < 9; i++) {
            double u = (i + 0.5) / 9.0;
            if (u > open) {
                break;
            }
            V3 base = start.lerp(end, u);
            double h = (1.0 + 2.5 * Math.abs(Math.sin(x.t * 0.12 + i * 1.3))) * (x.since() < 8.0 ? 2.0 : 1.0);
            x.b.glowColumn(base, h, 0.22, x.p, al * 0.6, 0.0);
            double rise = (x.t * 0.05 + x.rnd(i + 40)) % 1.0;
            x.b.mote(base.add(0.0, rise * 3.0, 0.0), 0.1, x.p.core(), al * (1.0 - rise));
        }
    }

    // ------------------------------------------------------------------ judgment

    /**
     * The scales of judgment over the inquisitor: they come down out of the light, sway while the foes are weighed,
     * tip hard at the peak (the verdict) and burst into light when the sentence is carried out (the next hit frame).
     */
    static void scales(Ctx x) {
        double size = Math.max(1.0, x.c.a());
        V3 top = x.pose.feet().plus(x.pose.fwd(), x.c.b()).add(0.0, Math.max(3.0, x.c.c()), 0.0);
        double appear = Ctx.easeOut(Ctx.clamp((x.t - x.c.start()) / 10.0));
        top = top.add(0.0, 6.0 * (1.0 - appear), 0.0);
        int[] beat = Props.beats(x, x.c.variant(), 2);
        double execute = beat.length > 1 ? beat[1] : x.c.peak() + 20.0;
        double burst = x.t >= execute ? Ctx.clamp((x.t - execute) / 8.0) : 0.0;
        double al = appear * (1.0 - burst) * x.fade();
        double tilt = x.before() ? 0.25 * Math.sin(x.t * 0.22) * (1.0 - Ctx.clamp((x.c.peak() - x.t) / 30.0) * 0.3)
                : 0.42 * Ctx.easeOut(Ctx.clamp(x.since() / 3.0));
        V3 f = x.pose.fwd();
        V3 r = new V3(-f.z(), 0.0, f.x());
        // the pillar, the beam and the two pans on their chains, a sun behind
        x.b.glow(top.add(0.0, -size * 1.6, 0.0), top.add(0.0, size * 0.4, 0.0), 0.08, x.p, al);
        V3 beamL = top.plus(r, -size * Math.cos(tilt)).add(0.0, -size * Math.sin(tilt), 0.0);
        V3 beamR = top.plus(r, size * Math.cos(tilt)).add(0.0, size * Math.sin(tilt), 0.0);
        x.b.glow(beamL, beamR, 0.07, x.p, al);
        for (V3 end : new V3[]{beamL, beamR}) {
            V3 pan = end.add(0.0, -size * 0.9, 0.0);
            x.b.glow(end, pan.plus(r, -size * 0.3), 0.02, x.p, al * 0.8);
            x.b.glow(end, pan.plus(r, size * 0.3), 0.02, x.p, al * 0.8);
            x.b.arc(pan, r, V3.UP, size * 0.32, 0.06, Math.PI, Math.PI * 2.0, x.p.glow(), al, al, false);
            x.b.glowRing(pan, r, f, size * 0.32, 0.05, x.p, al * 0.8);
        }
        x.b.glowRing(top, r, V3.UP, size * 0.45, 0.06, x.p, al);
        x.b.flare(top, size * 3.0, x.p.glow(), al * 0.35);
        x.b.star(top, size * 2.0, 12, x.t * 0.02, x.p.core(), al * 0.6);
        if (burst > 0.0 && burst < 1.0) {
            x.b.flare(top, size * 8.0 * burst + 1.0, x.p.core(), 1.0 - burst);
            x.b.star(top, size * 10.0 * burst, 16, 0.3, 0xFFFFFF, 1.0 - burst);
        }
    }

    /**
     * A sentence of the 神判 over a foe's head (the show of a sentence packet): a pillar of judgment comes down on it,
     * the sign of its sentence hangs over it, and 22 ticks later the sentence is carried out in a second strike.
     */
    static void sentence(Ctx x) {
        double size = Math.max(0.4, x.c.a());
        V3 at = x.pose.feet();
        double since = x.t - x.c.start();
        double execute = 22.0;
        double al = x.env();
        int kind = x.c.variant();
        SkillTheme theme = switch (kind) {
            case 0 -> SkillTheme.STEEL;
            case 1, 3 -> SkillTheme.FIRE;
            case 2 -> SkillTheme.SHADOW;
            case 4 -> SkillTheme.HOLY;
            default -> SkillTheme.FROST;
        };
        Palette p = Palette.of(theme);
        Palette holy = Palette.of(SkillTheme.HOLY);
        // the pillar of judgment
        if (since < 8.0) {
            double k = 1.0 - since / 8.0;
            V3 ground = at.add(0.0, -x.c.b() - 0.6, 0.0);
            x.b.glowColumn(ground, 18.0, 0.5 * k + 0.1, holy, k, k * 0.3);
            x.b.flare(at, 2.5 * k, holy.core(), k);
        }
        if (since >= execute && since < execute + 10.0) {
            double k = 1.0 - (since - execute) / 10.0;
            V3 ground = at.add(0.0, -x.c.b() - 0.6, 0.0);
            x.b.glowColumn(ground, 22.0, 0.9 * k + 0.1, holy, k, k * 0.2);
            x.b.star(at.add(0.0, -x.c.b() * 0.5, 0.0), 4.0 * k + 1.0, 12, 0.4, 0xFFFFFF, k);
        }
        // the sign, bobbing over the head (gone once the sentence is carried out)
        double sign = al * (since < execute ? 1.0 : Math.max(0.0, 1.0 - (since - execute) / 4.0));
        if (sign <= 0.01) {
            return;
        }
        V3 c = at.add(0.0, 0.4 + Math.sin(x.t * 0.25) * 0.08, 0.0);
        V3[] uv = V3.basis(c.sub(x.b.camera()));
        V3 right = uv[0];
        V3 up = uv[1];
        x.b.flare(c, size * 2.4, p.glow(), sign * 0.3);
        x.b.glowRing(c, right, up, size * 0.75, 0.05, p, sign);
        switch (kind) {
            case 0 -> { // chains: links around a lock
                for (int k = 0; k < 6; k++) {
                    double a = Math.PI * 2.0 * k / 6.0 + x.t * 0.03;
                    V3 link = c.plus(right, Math.cos(a) * size * 0.75).plus(up, Math.sin(a) * size * 0.75);
                    x.b.ring(link, right, up, size * 0.14, 0.03, p.core(), sign);
                }
                x.b.arc(c.plus(up, size * 0.12), right, up, size * 0.2, 0.05, 0.0, Math.PI, p.core(), sign, sign,
                        false);
                x.b.disc(c.plus(up, -size * 0.12), right, up, size * 0.22, p.glow(), sign, sign * 0.6);
            }
            case 1 -> { // the brand: a burning cross-mark
                x.b.glow(c.plus(right, -size * 0.45).plus(up, -size * 0.45), c.plus(right, size * 0.45)
                        .plus(up, size * 0.45), size * 0.1, p, sign);
                x.b.glow(c.plus(right, -size * 0.45).plus(up, size * 0.45), c.plus(right, size * 0.45)
                        .plus(up, -size * 0.45), size * 0.1, p, sign);
                x.b.glowRing(c, right, up, size * 0.5, 0.04, p, sign);
            }
            case 2 -> { // withering: a broken blade pointing down
                x.b.glow(c.plus(up, size * 0.55), c.plus(up, 0.05), size * 0.08, p, sign);
                x.b.glow(c.plus(up, -0.05).plus(right, size * 0.06), c.plus(up, -size * 0.5).plus(right, size * 0.1),
                        size * 0.08, p, sign * 0.8);
                x.b.spike(c.plus(up, -size * 0.3), up.scale(-1.0), size * 0.5, size * 0.35, p.glow(), sign, 0.0);
            }
            case 3 -> { // hellfire: a flame
                x.b.spike(c.plus(up, -size * 0.5), up, size * 1.2, size * 0.7, p.glow(), sign, 0.0);
                x.b.spike(c.plus(up, -size * 0.45).plus(right, size * 0.2), up.plus(right, 0.3).unit(), size * 0.8,
                        size * 0.4, p.accent(), sign, 0.0);
                x.b.spike(c.plus(up, -size * 0.45).plus(right, -size * 0.2), up.plus(right, -0.3).unit(),
                        size * 0.8, size * 0.4, p.accent(), sign, 0.0);
            }
            case 4 -> { // blindness: an eye struck through
                x.b.arc(c, right, up, size * 0.55, size * 0.05, 0.5, Math.PI - 0.5, p.core(), sign, sign, false);
                x.b.arc(c, right, up, size * 0.55, size * 0.05, Math.PI + 0.5, Math.PI * 2.0 - 0.5, p.core(), sign,
                        sign, false);
                x.b.glowPoint(c, size * 0.25, p, sign);
                x.b.glow(c.plus(right, -size * 0.6).plus(up, size * 0.4), c.plus(right, size * 0.6)
                        .plus(up, -size * 0.4), size * 0.07, Palette.of(SkillTheme.BLOOD), sign);
            }
            default -> { // the frozen prison: a crystal of ice
                for (int k = 0; k < 3; k++) {
                    V3 d = up.rotate(right.cross(up).unit(), Math.PI * k / 3.0);
                    x.b.glow(c.plus(d, -size * 0.6), c.plus(d, size * 0.6), size * 0.07, p, sign);
                }
                x.b.crystal(c.plus(up, -size * 0.2), up, size * 0.6, size * 0.25, p, sign);
            }
        }
    }

    // ------------------------------------------------------------------ the cathedral

    /** A rose window of stained light in the plane of {@code a} and {@code b}. */
    private static void rose(Ctx x, V3 centre, V3 a, V3 b, double rr, double al, double bright) {
        SkillTheme[] glass = {SkillTheme.BLOOD, SkillTheme.FROST, SkillTheme.HOLY, SkillTheme.LIFE, SkillTheme.ARCANE};
        Palette p = x.p;
        x.b.glowRing(centre, a, b, rr, 0.1, p, al);
        x.b.glowRing(centre, a, b, rr * 0.55, 0.07, p, al);
        for (int k = 0; k < 10; k++) {
            Palette gp = Palette.of(glass[k % glass.length]);
            double ang = Math.PI * 2.0 * k / 10.0 + x.t * 0.01;
            V3 petal = centre.plus(a, Math.cos(ang) * rr * 0.78).plus(b, Math.sin(ang) * rr * 0.78);
            x.b.disc(petal, a, b, rr * 0.2, gp.glow(), al * 0.55 * bright, al * 0.15);
            x.b.glow(centre, petal, 0.03, gp, al * 0.6);
        }
        x.b.polygram(centre, a, b, rr * 0.5, 8, 3, 0.05, x.t * 0.01, p.core(), al, 1.0);
        x.b.flare(centre, rr * 1.4, p.glow(), al * 0.2 * bright);
    }

    /**
     * A cathedral of light rising around the bishop: {@code n} pillars on a ring of {@code a}, pointed arches between
     * them, ribs vaulting to a crown {@code b} high and a rose window of stained light at its heart; a great bell
     * swings under the crown and rings at the hit frames from {@code variant} on (three bells), and at the peak the
     * window pours its light down onto everyone inside.
     */
    static void cathedral(Ctx x) {
        double radius = Math.max(3.0, x.c.a());
        double height = Math.max(4.0, x.c.b());
        int n = Math.max(6, x.c.n());
        V3 g = x.groundAt(x.pose.feet());
        double rise = Ctx.easeOut(Ctx.clamp((x.t - x.c.start()) / 16.0));
        double dissolve = x.before() ? 0.0 : Ctx.clamp(x.since() / Math.max(1.0, x.c.end() - x.c.peak()));
        double al = rise * (1.0 - dissolve);
        if (al <= 0.01) {
            return;
        }
        Palette p = x.p;
        double h = height * rise;
        V3 crown = g.add(0.0, h * 1.35, 0.0);
        V3[] tops = new V3[n];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2.0 * i / n;
            V3 base = g.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
            tops[i] = base.add(0.0, h * 0.72, 0.0);
            x.b.glowColumn(base, h * 0.72, 0.18, p, al * 0.85, al * 0.65);
            x.b.glowRing(base.add(0.0, 0.1, 0.0), GX, GZ, 0.45, 0.06, p, al * 0.6);
            x.b.glowPoint(tops[i], 0.3, p, al * 0.7);
        }
        for (int i = 0; i < n; i++) {
            V3 a = tops[i];
            V3 b = tops[(i + 1) % n];
            V3 apex = a.lerp(b, 0.5).add(0.0, radius * Math.PI / n * 0.9, 0.0);
            // a pointed arch: two arcs meeting at the apex
            x.b.glow(a, a.lerp(apex, 0.5).add(0.0, radius * 0.08, 0.0), 0.05, p, al * 0.75);
            x.b.glow(a.lerp(apex, 0.5).add(0.0, radius * 0.08, 0.0), apex, 0.05, p, al * 0.75);
            x.b.glow(b, b.lerp(apex, 0.5).add(0.0, radius * 0.08, 0.0), 0.05, p, al * 0.75);
            x.b.glow(b.lerp(apex, 0.5).add(0.0, radius * 0.08, 0.0), apex, 0.05, p, al * 0.75);
            // the rib vaulting up to the crown
            V3 mid = a.lerp(crown, 0.5).add(0.0, height * 0.18, 0.0);
            x.b.glow(a, mid, 0.04, p, al * 0.55);
            x.b.glow(mid, crown, 0.04, p, al * 0.55);
        }
        // the rose windows: one in the vault, the great one standing in the wall before the bishop (where the
        // light will come from), rings and petals of stained light turning slowly
        V3 rose = crown.add(0.0, -0.2, 0.0);
        double rr = radius * 0.42;
        V3 f = x.pose.fwd();
        V3 side = new V3(-f.z(), 0.0, f.x());
        V3 front = g.plus(f, radius * 0.97).add(0.0, h * 0.62, 0.0);
        rose(x, rose, GX, GZ, rr, al, 1.0);
        rose(x, front, side, V3.UP, radius * 0.36, al, 1.4);
        // the great bell under the crown, swinging; each ring sends a wave out across the floor
        int[] bells = Props.beats(x, x.c.variant(), 3);
        double swing = 0.0;
        for (int b : bells) {
            double since = x.t - b;
            if (since >= -6.0 && since < 18.0) {
                swing += Math.sin((since + 6.0) * 0.45) * Math.exp(-Math.max(0.0, since) / 9.0) * 0.5;
            }
            if (since >= 0.0 && since < 20.0) {
                double u = since / 20.0;
                x.b.glowRing(g.add(0.0, 0.08, 0.0), GX, GZ, radius * u * 1.05 + 0.5, 0.2 * (1.0 - u) + 0.04, p,
                        al * (1.0 - u));
                x.b.ring(crown.add(0.0, -height * 0.32, 0.0), GX, GZ, radius * 0.5 * u + 0.6, 0.05, p.core(),
                        al * (1.0 - u));
            }
        }
        V3 hang = crown.add(0.0, -0.4, 0.0);
        V3 down = V3.UP.scale(-1.0).rotate(x.pose.right(), swing).unit();
        V3 mouth = hang.plus(down, height * 0.3);
        x.b.glow(hang, hang.plus(down, height * 0.12), 0.03, p, al);
        V3[] uv = V3.basis(down);
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2.0 * k / 6.0;
            V3 dir = uv[0].scale(Math.cos(a)).add(uv[1].scale(Math.sin(a)));
            x.b.glow(hang.plus(down, height * 0.12).plus(dir, height * 0.05), mouth.plus(dir, height * 0.12), 0.04, p,
                    al * 0.9);
        }
        x.b.glowRing(mouth, uv[0], uv[1], height * 0.12, 0.06, p, al);
        // the windows' light pouring down and in at the peak
        if (!x.before()) {
            double k = 1.0 - dissolve;
            x.b.glowColumn(g, h * 1.35, rr * (0.7 + 0.3 * Math.exp(-x.since() / 6.0)), p, k * 0.8, k * 0.5);
            for (int i = 0; i < 7; i++) {
                V3 from = front.plus(side, (i - 3) * radius * 0.08).add(0.0, (x.rnd(i + 90) - 0.5) * 1.5, 0.0);
                V3 to = g.plus(side, (i - 3) * radius * 0.16).plus(f, -radius * 0.1).add(0.0, 0.2, 0.0);
                x.b.glow(from, to, 0.25, 0.9, p, k * 0.3, k * 0.15);
            }
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2.0 * i / 16.0;
                V3 foot = g.add(Math.cos(a) * radius * 0.7 * x.rnd(i + 70), 0.05,
                        Math.sin(a) * radius * 0.7 * x.rnd(i + 70));
                x.b.glow(rose, foot, 0.08, 0.2, p, k * 0.35, k * 0.6);
            }
        }
    }
}
