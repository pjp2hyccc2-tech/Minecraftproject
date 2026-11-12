package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * The light of what a skill leaves behind, drawn where it lands rather than on the caster: a ground area for its whole
 * life ({@link #zone}: burning ground, a frost field, a healing circle, a storm field, a delayed blast) and the burst
 * of an exploding spell or arrow ({@link #blast}). Both are ordinary {@link SkillShow}s anchored on a pose standing at
 * the spot, so the client, the tests and the preview draw them with the {@link Painter} like any skill.
 */
public final class Fields {
    /** The longest ground area drawn (ticks; the server's areas live at most 30 seconds). */
    public static final int MAX_LIFE = 600;
    private static final V3 GX = new V3(1.0, 0.0, 0.0);
    private static final V3 GZ = new V3(0.0, 0.0, 1.0);

    private Fields() {
    }

    /**
     * The show of a ground area of {@code radius} that pulses {@code pulses} times, the first after {@code delay}
     * ticks and then every {@code interval}: the field itself for its whole life, a ring running out on every pulse
     * and, for a delayed blast, a ring closing in until it bursts.
     */
    public static SkillShow zone(SkillTheme theme, double radius, int delay, int pulses, int interval, boolean heals,
                                 boolean harms, long seed) {
        SkillTheme t = theme == null ? SkillTheme.ARCANE : theme;
        double r = clamp(radius, 0.5, 16.0);
        int d = Math.max(0, Math.min(MAX_LIFE, delay));
        int n = Math.max(1, Math.min(64, pulses));
        int every = Math.max(1, Math.min(200, interval));
        int lastPulse = Math.min(MAX_LIFE, d + (n - 1) * every);
        int life = Math.min(MAX_LIFE + 20, lastPulse + Math.max(10, Math.min(every, 24)));
        List<Cue> cues = new ArrayList<>();
        int variant = (heals ? 1 : 0) | (harms ? 2 : 0);
        cues.add(Cue.of(Cue.Kind.FIELD, 0, Math.min(6, Math.max(2, d)), life).at(Cue.Anchor.ORIGIN).a(r).b(d)
                .c(every).n(n).variant(variant).seed(Brush.mixSeed(seed, 1)).build());
        boolean delayedBlast = harms && d >= 10 && n <= 2;
        for (int k = 0; k < n; k++) {
            int at = d + k * every;
            if (at > MAX_LIFE) {
                break;
            }
            cues.add(Cue.of(Cue.Kind.SHOCKWAVE, at, at, at + 10).at(Cue.Anchor.ORIGIN).a(r).n(1)
                    .b(delayedBlast ? 0.8 : 0.0).seed(Brush.mixSeed(seed, 10 + k)).build());
            if (delayedBlast) {
                cues.add(Cue.of(Cue.Kind.BURST, Math.max(0, at - 2), at, at + 12).at(Cue.Anchor.ORIGIN)
                        .a(r * 0.9).n(20).seed(Brush.mixSeed(seed, 100 + k)).build());
                cues.add(Cue.of(Cue.Kind.PILLAR, Math.max(0, at - 1), at, at + 10).at(Cue.Anchor.ORIGIN)
                        .a(Math.min(1.6, r * 0.3)).b(5.0 + r).seed(Brush.mixSeed(seed, 200 + k)).build());
            }
        }
        List<SkillShow.Shake> shakes = delayedBlast
                ? List.of(new SkillShow.Shake(d, (float) Math.min(1.6, 0.4 + r * 0.15), 7, 0.6F)) : List.of();
        return new SkillShow("zone:" + t.name().toLowerCase(java.util.Locale.ROOT), null, t, false, life, 4,
                new int[]{d}, cues, shakes);
    }

    /** The show of a spell or arrow exploding with {@code radius}: a flash, a burst, rings and the theme's mark. */
    public static SkillShow blast(SkillTheme theme, double radius, long seed) {
        SkillTheme t = theme == null ? SkillTheme.FIRE : theme;
        double r = clamp(radius, 0.5, 12.0);
        List<Cue> cues = new ArrayList<>();
        int k = 0;
        cues.add(Cue.of(Cue.Kind.FLASH, 0, 0, 7).at(Cue.Anchor.ORIGIN).a(1.0 + r * 0.45).seed(Brush.mixSeed(seed, ++k))
                .build());
        cues.add(Cue.of(Cue.Kind.BURST, 0, 0, 12).at(Cue.Anchor.ORIGIN).a(r).n(14 + (int) (r * 3))
                .seed(Brush.mixSeed(seed, ++k)).build());
        cues.add(Cue.of(Cue.Kind.SHOCKWAVE, 0, 0, 12).at(Cue.Anchor.ORIGIN).a(r * 1.15).n(2).b(0.6)
                .seed(Brush.mixSeed(seed, ++k)).build());
        Cue.Builder mark = switch (t) {
            case FIRE -> Cue.of(Cue.Kind.NOVA, 0, 0, 12).a(r).n(12);
            case FROST -> Cue.of(Cue.Kind.SPIKES, 0, 0, 16).a(r * 0.8).b(1.0 + r * 0.15).n(10);
            case THUNDER -> Cue.of(Cue.Kind.BOLTS, 0, 0, 6).a(r * 0.5).n(2).c(9.0);
            case HOLY -> Cue.of(Cue.Kind.PILLAR, 0, 0, 10).a(Math.min(1.2, 0.4 + r * 0.15)).b(6.0);
            case EARTH -> Cue.of(Cue.Kind.QUAKE, 0, 0, 16).a(r).n(7);
            case SHADOW, ARCANE -> Cue.of(Cue.Kind.VORTEX, 0, 2, 14).a(r * 0.7).b(2.0);
            case VENOM -> Cue.of(Cue.Kind.FOG, 0, 3, 24).a(r).n(8);
            case BLOOD -> Cue.of(Cue.Kind.SPIKES, 0, 0, 12).a(r * 0.75).b(0.9).n(9);
            case LIFE -> Cue.of(Cue.Kind.HEAL, 0, 3, 18).a(r);
            case WIND -> Cue.of(Cue.Kind.SHOCKWAVE, 2, 2, 12).a(r * 1.4).n(1);
            case STEEL -> Cue.of(Cue.Kind.IMPACT, 0, 0, 8).a(1.0 + r * 0.3);
        };
        cues.add(mark.at(Cue.Anchor.ORIGIN).seed(Brush.mixSeed(seed, ++k)).build());
        List<SkillShow.Shake> shakes = r >= 2.5
                ? List.of(new SkillShow.Shake(0, (float) Math.min(1.4, 0.3 + r * 0.12), 6, 0.4F)) : List.of();
        return new SkillShow("blast:" + t.name().toLowerCase(java.util.Locale.ROOT), null, t, false, 4, 18,
                new int[]{0}, cues, shakes);
    }

    /**
     * The pose a field or a blast is drawn on: standing at {@code ground} (the area's centre on the ground, or one
     * block under the heart of a blast) facing {@code yawDegrees}.
     */
    public static Pose at(V3 ground, double yawDegrees) {
        return Pose.of(ground, yawDegrees, 0.0, 1.62, 1);
    }

    // ------------------------------------------------------------------ the field itself

    /** Draws a {@link Cue.Kind#FIELD}. */
    static void field(Ctx x) {
        Cue c = x.c;
        V3 g = x.groundAt(x.pose.feet());
        double r = Math.max(0.5, c.a());
        double delay = Math.max(0.0, c.b());
        double every = Math.max(1.0, c.c());
        boolean heals = (c.variant() & 1) != 0;
        boolean harms = (c.variant() & 2) != 0;
        double t = x.t - c.start();
        double life = c.end() - c.start();
        double al = Ctx.clamp(t / 4.0) * Ctx.clamp((life - t) / 8.0);
        if (al <= 0.004) {
            return;
        }
        // the last pulse that went off and how bright it still is
        double sincePulse = t < delay ? 99.0 : (t - delay) % every;
        double pulse = Math.exp(-sincePulse * 0.35);
        double rot = x.t * 0.02;
        // rim, floor and runes
        x.b.glowRing(g, GX, GZ, r, 0.08 + 0.1 * pulse, x.p, al * (0.55 + 0.45 * pulse));
        x.b.softRing(g, GX, GZ, r * 0.94, 0.25, x.p.glow(), al * 0.25);
        x.b.disc(g, GX, GZ, r, x.p.deep(), al * (0.22 + 0.12 * pulse), al * 0.05);
        switch (x.theme) {
            case ARCANE, HOLY, SHADOW, THUNDER, LIFE, FROST -> x.b.runes(g.add(0.0, 0.01, 0.0), GX, GZ, r * 0.9,
                    Math.min(0.5, 0.12 + r * 0.04), 10 + (int) (r * 3), -rot, c.seed(), x.p.accent(), al * 0.55);
            default -> {
            }
        }
        if (harms && delay >= 10.0 && t < delay) {
            // a delayed blast: a ring closes in on the centre and the light gathers until it goes off
            double u = Ctx.clamp(t / delay);
            x.b.glowRing(g.add(0.0, 0.02, 0.0), GX, GZ, r * (1.0 - u) + 0.15, 0.1, x.p, al * (0.4 + 0.6 * u));
            x.b.glowPoint(g.add(0.0, 0.4, 0.0), 0.3 + 1.2 * u, x.p, al * u);
            x.b.spokes(g.add(0.0, 0.02, 0.0), GX, GZ, r * (1.0 - u) * 0.6, r, 8, rot * 3.0, 0.05, x.p.glow(),
                    al * 0.5 * u, 0.0);
        }
        int m = (int) Math.max(6.0, Math.min(36.0, r * r * 1.1));
        switch (x.theme) {
            case FIRE -> fire(x, g, r, m, al);
            case FROST -> frost(x, g, r, m, al, t);
            case THUNDER -> storm(x, g, r, m, al);
            case HOLY -> holy(x, g, r, m, al);
            case LIFE -> life(x, g, r, m, al);
            case SHADOW -> shadow(x, g, r, m, al);
            case ARCANE -> arcane(x, g, r, m, al);
            case VENOM -> venom(x, g, r, m, al);
            case BLOOD -> blood(x, g, r, m, al);
            case WIND -> wind(x, g, r, m, al);
            case EARTH -> earth(x, g, r, m, al, pulse);
            case STEEL -> steel(x, g, r, m, al, pulse);
        }
        if (heals && x.theme != SkillTheme.LIFE && x.theme != SkillTheme.HOLY) {
            life(x, g, r, Math.max(4, m / 2), al * 0.8);
        }
    }

    /** A point {@code i} spread evenly over the disc (golden angle), at ground height. */
    private static V3 spot(Ctx x, V3 g, double r, int i, int m) {
        double ang = i * 2.399963 + x.rnd(i) * 0.6;
        double d = r * Math.sqrt((i + 0.5) / m) * (0.92 + 0.08 * x.rnd(i + 500));
        return g.add(Math.cos(ang) * d, 0.0, Math.sin(ang) * d);
    }

    /** Repeating 0..1 of detail {@code i} running at {@code speed} per tick. */
    private static double cycle(Ctx x, int i, double speed) {
        double v = x.t * speed + x.rnd(i + 900);
        return v - Math.floor(v);
    }

    private static void fire(Ctx x, V3 g, double r, int m, double al) {
        int flames = (int) (m * 1.4);
        for (int i = 0; i < flames; i++) {
            V3 at = spot(x, g, r, i, flames);
            double h = (0.7 + 0.95 * x.rnd(i + 40)) * x.flicker(0.55, i);
            V3 sway = new V3(Math.sin(x.t * 0.3 + i) * 0.15, 1.0, Math.cos(x.t * 0.27 + i) * 0.15);
            x.b.crystal(at, sway, h, 0.3, x.p, al * 0.9);
            if (i >= m) {
                continue;
            }
            double u = cycle(x, i, 0.045);
            V3 ember = at.add(Math.sin(u * 6.0 + i) * 0.25, 0.2 + u * 2.6, Math.cos(u * 5.0 + i) * 0.25);
            x.b.mote(ember, 0.11, i % 2 == 0 ? x.p.accent() : x.p.core(), al * Ctx.bump(u));
        }
        x.b.flare(g.add(0.0, 0.5, 0.0), r * 1.6, x.p.deep(), al * 0.3);
        for (int i = 0; i < 3; i++) {
            V3 at = spot(x, g, r * 0.6, i * 5 + 1, 16);
            x.b.glowColumn(at, 1.8 + x.rnd(i + 45), 0.22, x.p, al * 0.45 * x.flicker(0.4, i + 7), 0.0);
        }
    }

    private static void frost(Ctx x, V3 g, double r, int m, double al, double t) {
        double grow = Ctx.easeOut(Ctx.clamp(t / 6.0));
        for (int i = 0; i < m; i++) {
            V3 at = spot(x, g, r, i, m);
            V3 lean = new V3((x.rnd(i + 60) - 0.5) * 0.9, 1.0, (x.rnd(i + 61) - 0.5) * 0.9);
            x.b.crystal(at, lean, (0.5 + 0.9 * x.rnd(i + 62)) * grow, 0.22, x.p, al);
            double u = cycle(x, i, 0.02);
            V3 flake = at.add(Math.sin(u * 7.0 + i) * 0.4, 2.6 * (1.0 - u), Math.cos(u * 6.0 + i) * 0.4);
            x.b.mote(flake, 0.1, x.p.core(), al * Ctx.bump(u) * 0.9);
        }
        for (int i = 0; i < Math.max(3, m / 4); i++) {
            double ang = x.rnd(i + 70) * Math.PI * 2.0 + x.t * 0.008;
            double d = r * 0.6 * Math.sqrt(x.rnd(i + 71));
            x.b.flare(g.add(Math.cos(ang) * d, 0.35, Math.sin(ang) * d), 1.4 + x.rnd(i + 72), x.p.glow(), al * 0.12);
        }
    }

    private static void storm(Ctx x, V3 g, double r, int m, double al) {
        long beat = (long) Math.floor(x.t / 2.5);
        int arcs = Math.max(3, m / 3);
        for (int i = 0; i < arcs; i++) {
            long k = Brush.mixSeed(x.c.seed(), beat * 31 + i);
            V3 a = spot(x, g, r, Math.floorMod(k, m), m).add(0.0, 0.15, 0.0);
            V3 b = spot(x, g, r, Math.floorMod(k >> 8, m), m).add(0.0, 0.15 + Brush.unit(k, 3) * 0.6, 0.0);
            if (a.distance(b) < 0.4) {
                continue;
            }
            x.b.bolt(a, b, k, 5, 0.3, 0.09, x.p, al * x.flicker(0.5, i));
            if (i % 2 == 0) {
                // sparks leaping up off the ground
                x.b.bolt(a, a.add(0.0, 0.8 + Brush.unit(k, 5) * 0.8, 0.0), k + 7, 3, 0.25, 0.06, x.p, al * 0.8);
            }
        }
        for (int i = 0; i < m; i++) {
            if (Brush.unit(x.c.seed(), beat * 97 + i) < 0.7) {
                continue;
            }
            x.b.sparkle(spot(x, g, r, i, m).add(0.0, 0.2, 0.0), 0.35, x.t * 0.2 + i, x.p, al);
        }
    }

    private static void holy(Ctx x, V3 g, double r, int m, double al) {
        for (int i = 0; i < m; i++) {
            double u = cycle(x, i, 0.03);
            V3 at = spot(x, g, r, i, m).add(0.0, 0.2 + u * 2.8, 0.0);
            if (i % 4 == 0) {
                Grand.crossMark(x, at, 0.22, al * Ctx.bump(u));
            } else {
                x.b.mote(at, 0.12, i % 2 == 0 ? x.p.glow() : x.p.accent(), al * Ctx.bump(u));
            }
        }
        for (int i = 0; i < 3; i++) {
            V3 at = spot(x, g, r * 0.7, i * 7 + 3, m + 20);
            double breathe = 0.5 + 0.5 * Math.sin(x.t * 0.15 + i * 2.1);
            x.b.glowColumn(at, 3.5, 0.18, x.p, al * 0.35 * breathe, 0.0);
        }
    }

    private static void life(Ctx x, V3 g, double r, int m, double al) {
        Palette leaf = Palette.of(SkillTheme.LIFE);
        for (int i = 0; i < m; i++) {
            double u = cycle(x, i, 0.035);
            double ang = x.rnd(i + 80) * Math.PI * 2.0 + u * 3.0;
            double d = r * 0.85 * Math.sqrt(x.rnd(i + 81)) * (1.0 - 0.3 * u);
            V3 at = g.add(Math.cos(ang) * d, 0.15 + u * 2.4, Math.sin(ang) * d);
            x.b.mote(at, 0.13, i % 3 == 0 ? leaf.accent() : leaf.glow(), al * Ctx.bump(u));
            if (i % 5 == 0) {
                Grand.crossMark(x, at.add(0.0, 0.2, 0.0), 0.18, al * Ctx.bump(u) * 0.8);
            }
        }
        x.b.glowColumn(g, 2.2, 0.3, leaf, al * 0.3, 0.0);
    }

    private static void shadow(Ctx x, V3 g, double r, int m, double al) {
        for (int i = 0; i < Math.max(4, m / 2); i++) {
            double ang = x.rnd(i + 90) * Math.PI * 2.0 + x.t * 0.012 * (i % 2 == 0 ? 1 : -1);
            double d = r * Math.sqrt(x.rnd(i + 91)) * 0.85;
            V3 at = g.add(Math.cos(ang) * d, 0.4 + 0.5 * x.rnd(i + 92), Math.sin(ang) * d);
            x.b.flare(at, 1.2 + 1.3 * x.rnd(i + 93), i % 3 == 0 ? x.p.accent() : x.p.deep(), al * 0.2);
        }
        for (int i = 0; i < m; i++) {
            double u = cycle(x, i, 0.04);
            V3 base = spot(x, g, r, i, m);
            V3 top = base.add(Math.sin(u * 4.0 + i) * 0.3, 0.2 + u * 2.0, Math.cos(u * 4.0 + i) * 0.3);
            x.b.glow(top.add(0.0, -0.35, 0.0), top, 0.06, 0.0, x.p, al * Ctx.bump(u), 0.0);
        }
    }

    private static void arcane(Ctx x, V3 g, double r, int m, double al) {
        Painter.circle(x, g.add(0.0, 0.03, 0.0), GX, GZ, r * 0.86, 1.0, al * 0.75, 6, Painter.STYLE_MAGE);
        for (int i = 0; i < Math.max(4, m / 2); i++) {
            double u = cycle(x, i, 0.025);
            double ang = x.rnd(i + 110) * Math.PI * 2.0 + x.t * 0.03;
            double d = r * 0.7 * Math.sqrt(x.rnd(i + 111));
            V3 at = g.add(Math.cos(ang) * d, 0.3 + u * 1.8, Math.sin(ang) * d);
            x.b.star(at, 0.28, 6, x.t * 0.1 + i, x.p.accent(), al * Ctx.bump(u));
        }
    }

    private static void venom(Ctx x, V3 g, double r, int m, double al) {
        for (int i = 0; i < m; i++) {
            double u = cycle(x, i, 0.05);
            V3 at = spot(x, g, r, i, m).add(0.0, 0.1 + u * 1.2, 0.0);
            double size = 0.08 + 0.12 * u;
            V3[] uv = V3.basis(at.sub(x.b.camera()));
            if (u < 0.85) {
                x.b.ring(at, uv[0], uv[1], size, 0.03, x.p.glow(), al * 0.8);
                x.b.flare(at, size * 2.0, x.p.accent(), al * 0.25);
            } else {
                // the bubble bursts
                double k = (u - 0.85) / 0.15;
                x.b.ring(at, uv[0], uv[1], size * (1.0 + 2.0 * k), 0.025, x.p.accent(), al * (1.0 - k));
            }
        }
        for (int i = 0; i < Math.max(3, m / 3); i++) {
            double ang = x.rnd(i + 120) * Math.PI * 2.0 + x.t * 0.01;
            double d = r * 0.7 * Math.sqrt(x.rnd(i + 121));
            x.b.flare(g.add(Math.cos(ang) * d, 0.5, Math.sin(ang) * d), 1.6 + x.rnd(i + 122), x.p.deep(), al * 0.22);
        }
    }

    private static void blood(Ctx x, V3 g, double r, int m, double al) {
        for (int i = 0; i < Math.max(3, m / 3); i++) {
            double ang = x.rnd(i + 130) * Math.PI * 2.0 - x.t * 0.01;
            double d = r * 0.75 * Math.sqrt(x.rnd(i + 131));
            x.b.flare(g.add(Math.cos(ang) * d, 0.45, Math.sin(ang) * d), 1.5 + x.rnd(i + 132), x.p.deep(), al * 0.25);
        }
        for (int i = 0; i < m; i++) {
            double u = cycle(x, i, 0.06);
            V3 top = spot(x, g, r, i, m).add(0.0, 2.2 * (1.0 - u * u), 0.0);
            x.b.ribbon(top, top.add(0.0, 0.25, 0.0), 0.06, 0.0, x.p.glow(), al * Ctx.bump(u), 0.0);
        }
    }

    private static void wind(Ctx x, V3 g, double r, int m, double al) {
        double spin = x.t * 0.22;
        for (int k = 0; k < 4; k++) {
            double rr = r * (0.3 + 0.18 * k);
            double from = spin * (1.0 + 0.2 * k) + k * 1.7;
            x.b.arc(g.add(0.0, 0.25 + 0.35 * k, 0.0), GX, GZ, rr, 0.09, from, from + 2.2, x.p.glow(), 0.0,
                    al * 0.8, true);
            x.b.arc(g.add(0.0, 0.25 + 0.35 * k, 0.0), GX, GZ, rr, 0.03, from + 1.0, from + 2.2, x.p.core(), 0.0,
                    al, false);
        }
        for (int i = 0; i < m; i++) {
            double u = cycle(x, i, 0.05);
            double ang = x.rnd(i + 140) * Math.PI * 2.0 + spin * 1.5 + u * 2.0;
            double d = r * (0.3 + 0.7 * x.rnd(i + 141));
            x.b.mote(g.add(Math.cos(ang) * d, 0.2 + u * 1.6, Math.sin(ang) * d), 0.1, x.p.core(), al * Ctx.bump(u));
        }
    }

    private static void earth(Ctx x, V3 g, double r, int m, double al, double pulse) {
        int cracks = 5 + (int) (r * 1.5);
        for (int i = 0; i < cracks; i++) {
            double ang = Math.PI * 2.0 * i / cracks + x.rnd(i + 150) * 0.5;
            int n = 5;
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            for (int k = 0; k < n; k++) {
                double u = (double) k / (n - 1);
                double wobble = (x.rnd(i * 13 + k + 151) - 0.5) * 0.35;
                pts[k] = g.add(Math.cos(ang + wobble) * r * u * 0.95, 0.02, Math.sin(ang + wobble) * r * u * 0.95);
                w[k] = 0.16 * (1.0 - 0.7 * u) * (1.0 + pulse);
                a[k] = al * (0.5 + 0.5 * pulse) * (1.0 - 0.5 * u);
            }
            x.b.glowStrip(pts, w, a, x.p, V3.UP);
        }
        for (int i = 0; i < m; i++) {
            double u = cycle(x, i, 0.03);
            V3 at = spot(x, g, r, i, m).add(0.0, 0.1 + u * 0.9, 0.0);
            x.b.mote(at, 0.15, x.p.glow(), al * Ctx.bump(u) * 0.7);
            if (pulse > 0.05 && i % 3 == 0) {
                x.b.crystal(spot(x, g, r, i, m), new V3(0.0, 1.0, 0.0), 0.6 * pulse, 0.2, x.p, al * pulse);
            }
        }
    }

    private static void steel(Ctx x, V3 g, double r, int m, double al, double pulse) {
        for (int i = 0; i < m; i++) {
            V3 at = spot(x, g, r, i, m).add(0.0, 0.3 + 0.8 * x.rnd(i + 160), 0.0);
            double glint = Math.max(0.0, Math.sin(x.t * 0.4 + i * 1.7));
            x.b.sparkle(at, 0.3 + 0.2 * glint, x.t * 0.1 + i, x.p, al * glint * glint);
        }
        if (pulse > 0.05) {
            for (int k = 0; k < 3; k++) {
                double ang = x.rnd(k + 170 + (long) (x.t / Math.max(1.0, x.c.c()))) * Math.PI * 2.0;
                V3 a = GX.yaw(ang);
                x.b.crescent(g.add(0.0, 0.8 + 0.3 * k, 0.0), a, V3.UP.cross(a).unit(), r * (0.5 + 0.15 * k), 0.35,
                        0.0, 2.4, 1.0, x.p, al * pulse);
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo;
    }
}
