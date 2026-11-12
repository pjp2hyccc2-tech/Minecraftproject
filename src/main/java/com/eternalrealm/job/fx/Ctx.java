package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;

/** One layer being drawn: its cue, the show time, where it stands, its colours and the brush. */
final class Ctx {
    final Cue c;
    final double t;
    final Pose pose;
    final ShowState st;
    final SkillTheme theme;
    final Palette p;
    final Brush b;

    Ctx(Cue c, double t, Pose pose, ShowState st, SkillTheme theme, Brush b) {
        this.c = c;
        this.t = t;
        this.pose = pose;
        this.st = st;
        this.theme = theme;
        this.p = Palette.of(theme);
        this.b = b;
    }

    /** 0..1 from the start to the peak (1 from the peak on). */
    double rise() {
        if (c.peak() <= c.start()) {
            return t >= c.start() ? 1.0 : 0.0;
        }
        return clamp((t - c.start()) / (c.peak() - c.start()));
    }

    /** 0..1 from the peak to the end (0 before the peak). */
    double fall() {
        if (c.end() <= c.peak()) {
            return t >= c.peak() ? 1.0 : 0.0;
        }
        return clamp((t - c.peak()) / (c.end() - c.peak()));
    }

    /** 0..1 over the whole life. */
    double life() {
        return clamp((t - c.start()) / Math.max(1.0E-6, c.end() - c.start()));
    }

    /** Ticks since the peak (negative before it). */
    double since() {
        return t - c.peak();
    }

    boolean before() {
        return t < c.peak();
    }

    /** Full strength until the peak, then fading out. */
    double fade() {
        return before() ? 1.0 : Math.pow(1.0 - fall(), 1.3);
    }

    /** Grows in towards the peak, then fades out. */
    double env() {
        return easeOut(rise()) * fade();
    }

    /** A number in [0, 1) for detail {@code i} of this layer. */
    double rnd(long i) {
        return Brush.unit(c.seed(), i);
    }

    /** A slow shimmer around 1 (fire and lightning flicker, light breathes). */
    double flicker(double amount, long i) {
        return 1.0 - amount + amount * (0.5 + 0.5 * Math.sin(t * 1.9 + rnd(i) * 6.28) * Math.sin(t * 0.73 + i));
    }

    /** The ground under the anchor (the anchor's feet). */
    V3 groundAt(V3 at) {
        return new V3(at.x(), pose.feet().y() + 0.06, at.z());
    }

    static double clamp(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    static double easeOut(double u) {
        double k = 1.0 - clamp(u);
        return 1.0 - k * k * k;
    }

    static double easeIn(double u) {
        double k = clamp(u);
        return k * k * k;
    }

    static double smooth(double u) {
        double k = clamp(u);
        return k * k * (3.0 - 2.0 * k);
    }

    /** A bump that rises and falls back over 0..1. */
    static double bump(double u) {
        return u <= 0.0 || u >= 1.0 ? 0.0 : Math.sin(Math.PI * u);
    }
}
