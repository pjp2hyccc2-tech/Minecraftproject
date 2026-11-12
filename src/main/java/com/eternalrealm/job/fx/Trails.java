package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;

/**
 * The light of a job skill's projectile in flight, in its theme: a glowing head and a tapering wake along where it
 * has been. Spells are orbs of their element (a fireball's tongues of flame, an ice shard, a crackling storm ball,
 * light with a cross of rays, a wisp of shadow, a turning ring of runes, a dripping globe, a crimson lance, a whirl
 * of wind, a rock, a swirl of leaves); arrows are bright streaks with a glowing point, dressed in the same element.
 */
public final class Trails {
    /** Points of the wake used (one per tick). */
    public static final int LENGTH = 10;
    private static final V3 UP = V3.UP;

    private Trails() {
    }

    /**
     * Draws a projectile. {@code path[0]} is where it is now, then where it was (one point per tick, newest first);
     * {@code time} is a running clock in ticks (for flicker and spin), {@code alpha} fades a wake that outlives its
     * projectile.
     */
    public static void draw(Brush b, SkillTheme theme, boolean arrow, V3[] path, double time, long seed, double alpha) {
        if (path == null || path.length == 0 || alpha <= 0.004 || !path[0].finite()) {
            return;
        }
        Palette p = Palette.of(theme == null ? SkillTheme.ARCANE : theme);
        int n = Math.min(path.length, LENGTH);
        V3 head = path[0];
        V3 dir = n > 1 ? head.sub(path[1]).unit(new V3(0.0, 0.0, 1.0)) : new V3(0.0, 0.0, 1.0);
        double headWidth = arrow ? 0.2 : 0.62;
        if (n > 1) {
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            for (int i = 0; i < n; i++) {
                double u = (double) i / (n - 1);
                pts[i] = path[i];
                w[i] = headWidth * (1.0 - u) * (1.0 - u) + 0.01;
                a[i] = alpha * (1.0 - u);
            }
            b.glowStrip(pts, w, a, p);
        }
        V3[] uv = V3.basis(dir);
        if (arrow) {
            b.glowPoint(head, 0.45, p, alpha);
            b.star(head, 1.1, 4, time * 0.35 + seed % 7, p.core(), alpha * 0.7);
            b.spike(head.plus(dir, -0.15), dir, 0.55, 0.14, p.core(), alpha, alpha * 0.4);
        } else {
            b.glowPoint(head, 1.0 + 0.12 * Math.sin(time * 0.9), p, alpha);
            b.star(head, 2.1, 6, time * 0.2 + seed % 11, p.core(), alpha * 0.55);
            b.glowRing(head, uv[0].rotate(dir, time * 0.35), uv[1].rotate(dir, time * 0.35), 0.5, 0.04, p,
                    alpha * 0.7);
        }
        switch (theme == null ? SkillTheme.ARCANE : theme) {
            case FIRE -> fire(b, p, path, n, head, dir, time, alpha, arrow);
            case FROST -> frost(b, p, path, n, head, dir, time, alpha, arrow);
            case THUNDER -> thunder(b, p, path, n, time, seed, alpha);
            case HOLY -> holy(b, p, path, n, head, uv, time, alpha);
            case SHADOW, LIFE -> helix(b, p, path, n, time, alpha, theme == SkillTheme.SHADOW);
            case ARCANE -> arcane(b, p, head, dir, uv, time, seed, alpha, arrow);
            case VENOM, BLOOD -> drips(b, p, path, n, time, seed, alpha);
            case WIND -> wind(b, p, path, n, dir, uv, time, alpha);
            case EARTH -> earth(b, p, path, n, head, dir, time, seed, alpha);
            case STEEL -> steel(b, p, path, n, time, alpha);
        }
    }

    private static void fire(Brush b, Palette p, V3[] path, int n, V3 head, V3 dir, double time, double alpha,
                             boolean arrow) {
        int tongues = arrow ? 3 : 6;
        for (int i = 0; i < tongues; i++) {
            double ang = i * Math.PI * 2.0 / tongues + time * 0.4;
            V3[] uv = V3.basis(dir);
            V3 off = uv[0].scale(Math.cos(ang)).add(uv[1].scale(Math.sin(ang)));
            double len = (arrow ? 0.5 : 1.0) * (0.7 + 0.3 * Math.sin(time * 1.3 + i * 2.1));
            b.crystal(head.plus(off, arrow ? 0.05 : 0.15), dir.scale(-1.0).plus(off, 0.3), len, arrow ? 0.08 : 0.16, p,
                    alpha * 0.8);
        }
        for (int i = 1; i < n; i++) {
            double k = Brush.unit((long) (time) - i, 3);
            V3 ember = path[i].add((k - 0.5) * 0.3, i * 0.06, (Brush.unit((long) time - i, 4) - 0.5) * 0.3);
            b.mote(ember, 0.09, i % 2 == 0 ? p.accent() : p.core(), alpha * (1.0 - (double) i / n));
        }
    }

    private static void frost(Brush b, Palette p, V3[] path, int n, V3 head, V3 dir, double time, double alpha,
                              boolean arrow) {
        b.crystal(head.plus(dir, arrow ? -0.4 : -0.5), dir, arrow ? 0.8 : 1.1, arrow ? 0.1 : 0.2, p, alpha);
        for (int i = 1; i < n; i += 2) {
            V3 flake = path[i].add(0.0, -0.04 * i, 0.0);
            b.sparkle(flake, 0.22, time * 0.2 + i, p, alpha * (1.0 - (double) i / n));
        }
    }

    private static void thunder(Brush b, Palette p, V3[] path, int n, double time, long seed, double alpha) {
        long beat = (long) Math.floor(time / 1.5);
        for (int i = 0; i + 2 < n; i += 2) {
            b.bolt(path[i], path[i + 2], Brush.mixSeed(seed, beat * 17 + i), 4, 0.35, 0.05, p,
                    alpha * (1.0 - (double) i / n));
        }
        b.sparkle(path[0], 0.6, time * 0.5, p, alpha);
    }

    private static void holy(Brush b, Palette p, V3[] path, int n, V3 head, V3[] uv, double time, double alpha) {
        // a cross of light at the head, turning slowly
        V3 h = uv[0].rotate(path.length > 1 ? head.sub(path[1]).unit() : UP, time * 0.05);
        V3 v = uv[1].rotate(path.length > 1 ? head.sub(path[1]).unit() : UP, time * 0.05);
        b.glow(head.plus(h, -0.55), head.plus(h, 0.55), 0.07, p, alpha * 0.9);
        b.glow(head.plus(v, -0.75), head.plus(v, 0.55), 0.07, p, alpha * 0.9);
        for (int i = 1; i < n; i += 2) {
            b.mote(path[i].add(0.0, 0.05 * i, 0.0), 0.1, p.accent(), alpha * (1.0 - (double) i / n));
        }
    }

    private static void helix(Brush b, Palette p, V3[] path, int n, double time, double alpha, boolean shadow) {
        if (n < 3) {
            return;
        }
        for (int strand = 0; strand < 2; strand++) {
            V3[] pts = new V3[n];
            double[] w = new double[n];
            double[] a = new double[n];
            for (int i = 0; i < n; i++) {
                V3 d = (i + 1 < n ? path[i].sub(path[i + 1]) : path[i - 1].sub(path[i])).unit();
                V3[] uv = V3.basis(d);
                double ang = time * 0.5 + i * 0.9 + strand * Math.PI;
                double rr = 0.12 + 0.05 * i;
                pts[i] = path[i].plus(uv[0], Math.cos(ang) * rr).plus(uv[1], Math.sin(ang) * rr);
                double u = (double) i / (n - 1);
                w[i] = 0.1 * (1.0 - u) + 0.02;
                a[i] = alpha * (1.0 - u) * 0.8;
            }
            b.strip(pts, w, a, strand == 0 ? p.accent() : (shadow ? p.deep() : p.glow()), true);
        }
    }

    private static void arcane(Brush b, Palette p, V3 head, V3 dir, V3[] uv, double time, long seed, double alpha,
                               boolean arrow) {
        double r = arrow ? 0.3 : 0.55;
        b.runes(head, uv[0], uv[1], r, r * 0.3, 8, time * 0.25, seed, p.accent(), alpha * 0.85);
        b.ring(head, uv[0], uv[1], r * 1.1, 0.025, p.glow(), alpha * 0.7);
        b.star(head.plus(dir, 0.2), r * 2.0, 4, -time * 0.3, p.accent(), alpha * 0.5);
    }

    private static void drips(Brush b, Palette p, V3[] path, int n, double time, long seed, double alpha) {
        for (int i = 1; i < n; i++) {
            double fall = (i + (time % 1.0)) * 0.05;
            V3 drop = path[i].add((Brush.unit(seed, i) - 0.5) * 0.2, -fall * i * 0.6,
                    (Brush.unit(seed, i + 9) - 0.5) * 0.2);
            b.ribbon(drop, drop.add(0.0, 0.16, 0.0), 0.07, 0.0, p.glow(), alpha * (1.0 - (double) i / n), 0.0);
        }
    }

    private static void wind(Brush b, Palette p, V3[] path, int n, V3 dir, V3[] uv, double time, double alpha) {
        for (int i = 1; i < n; i += 2) {
            double u = (double) i / n;
            double spin = time * 0.6 + i;
            b.arc(path[i], uv[0], uv[1], 0.15 + 0.35 * u, 0.04, spin, spin + 3.6, p.core(), alpha * (1.0 - u), 0.0,
                    false);
        }
    }

    private static void earth(Brush b, Palette p, V3[] path, int n, V3 head, V3 dir, double time, long seed,
                              double alpha) {
        V3 spin = dir.rotate(V3.basis(dir)[0], time * 0.3);
        b.crystal(head.plus(spin, -0.3), spin, 0.6, 0.28, p, alpha);
        for (int i = 1; i < n; i++) {
            V3 dust = path[i].add((Brush.unit(seed, i) - 0.5) * 0.4, -0.03 * i, (Brush.unit(seed, i + 5) - 0.5) * 0.4);
            b.mote(dust, 0.14, p.glow(), alpha * 0.6 * (1.0 - (double) i / n));
        }
    }

    private static void steel(Brush b, Palette p, V3[] path, int n, double time, double alpha) {
        for (int i = 2; i < n; i += 3) {
            b.sparkle(path[i], 0.25, time * 0.3 + i, p, alpha * (1.0 - (double) i / n));
        }
    }
}
