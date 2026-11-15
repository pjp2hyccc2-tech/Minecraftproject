package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;

/**
 * The things the finales (秘奥義) hold or raise: the sniper's heavy rifle, the reaper's scythe, a sword grown huge or a
 * blade of light, the hexblade's cursed nail and hammer, the sage's rock from the sky and the guardian's giant shield.
 * Each is a solid shape ({@link Solid}, drawn in the solid pass) with light on it (drawn in the light pass); both
 * passes call the same code and the brush keeps what its pass draws.
 */
final class Props {
    private static final int GUNMETAL = 0x4A505C;
    private static final int GUNMETAL_LIGHT = 0x626A7A;
    private static final int STOCK_WOOD = 0x4A3424;
    private static final int IRON = 0x4A4A58;
    private static final int IRON_DARK = 0x30303A;
    private static final int OLD_WOOD = 0x3A2618;
    private static final int STEEL = 0xB8C0CC;
    private static final int GOLD = 0xD8A030;
    private static final int SILVER = 0xC8CCD8;
    private static final int BASALT = 0x2C2C3A;

    private Props() {
    }

    // ------------------------------------------------------------------ helpers

    /** The times of {@code n} hit frames from frame {@code first} on (missing ones repeat the last). */
    static int[] beats(Ctx x, int first, int n) {
        int[] all = x.st.show().frameTimes();
        int[] out = new int[Math.max(1, n)];
        for (int i = 0; i < out.length; i++) {
            int k = Math.max(0, Math.min(all.length - 1, first + i));
            out[i] = all.length == 0 ? (int) x.c.peak() : all[k];
        }
        return out;
    }

    /** {@code v} with its part along {@code axis} removed, as a unit vector ({@code fallback} if nothing is left). */
    static V3 across(V3 v, V3 axis, V3 fallback) {
        V3 r = v.sub(axis.scale(v.dot(axis)));
        return r.lengthSqr() < 1.0E-9 ? fallback : r.unit();
    }

    /** A unit vector between {@code a} and {@code b} ({@code u} 0..1). */
    static V3 blend(V3 a, V3 b, double u) {
        return a.lerp(b, Ctx.clamp(u)).unit(a);
    }

    /** How present a prop is: grows in over {@code in} ticks from its start, fades over {@code out} before its end. */
    static double presence(Ctx x, double in, double out) {
        double a = Ctx.clamp((x.t - x.c.start()) / Math.max(1.0, in));
        double b = Ctx.clamp((x.c.end() - x.t) / Math.max(1.0, out));
        return Ctx.smooth(Math.min(a, b));
    }

    // ------------------------------------------------------------------ the rifle

    /**
     * The heavy rifle the sniper's bow becomes: held along the look (in front of the eyes in first person, at the
     * shoulder for everyone else), assembling out of light, kicking back at every shot and folding away at the end.
     */
    static void rifle(Ctx x) {
        Pose pose = x.pose;
        V3 f = pose.look().unit(pose.fwd());
        V3 u = across(V3.UP, f, V3.UP);
        V3 r = f.cross(u).unit().scale(pose.side());
        double show = presence(x, 8.0, 8.0);
        if (show <= 0.004) {
            return;
        }
        // recoil: back along the barrel and the muzzle up, then settling
        double kick = 0.0;
        for (int t : beats(x, x.c.variant(), Math.max(1, x.c.n()))) {
            double since = x.t - t;
            if (since >= 0.0 && since < 12.0) {
                kick = Math.max(kick, Math.exp(-since / 2.6));
            }
        }
        V3 aim = f.rotate(f.cross(u).unit(), Math.toRadians(7.0 * kick)).unit(f);
        V3 up = across(V3.UP, aim, u);
        V3 right = aim.cross(up).unit().scale(pose.side());
        V3 grip = x.b.firstPerson()
                ? pose.eye().plus(f, 0.5).plus(r, 0.25).plus(u, -0.27)
                : pose.at(0.28, 1.36, 0.2);
        grip = grip.plus(aim, -0.16 * kick);
        // heavier at the shoulder than in front of the eyes (where it is that much closer)
        double scale = (0.35 + 0.65 * show) * (x.b.firstPerson() ? 1.0 : 1.25);
        double len = Math.max(1.2, x.c.a()) * scale;
        double al = show;
        // the long barrel, its brake, the handguard with arcane runes, receiver, scope, magazine, grip and stock
        V3 muzzle = grip.plus(aim, len * 0.86);
        Solid.prism(x.b, grip.plus(aim, len * 0.18), muzzle, 0.036 * scale, 0.032 * scale, 8, up, GUNMETAL, al);
        Solid.bar(x.b, muzzle.plus(aim, -0.02), muzzle.plus(aim, 0.1 * scale), right, 0.085 * scale, 0.08 * scale,
                IRON_DARK, al);
        Solid.bar(x.b, grip.plus(aim, 0.02 * scale), grip.plus(aim, len * 0.42), right, 0.11 * scale,
                0.13 * scale, GUNMETAL_LIGHT, al);
        Solid.bar(x.b, grip.plus(aim, -0.2 * scale), grip.plus(aim, 0.08 * scale), right, 0.12 * scale,
                0.16 * scale, GUNMETAL, al);
        V3 scopeAxis = grip.plus(up, 0.13 * scale);
        Solid.prism(x.b, scopeAxis.plus(aim, -0.14 * scale), scopeAxis.plus(aim, 0.3 * scale), 0.034 * scale,
                0.04 * scale, 8, up, IRON_DARK, al);
        Solid.bar(x.b, grip.plus(up, 0.06 * scale).plus(aim, -0.06 * scale),
                grip.plus(up, 0.06 * scale).plus(aim, 0.02 * scale), right, 0.05 * scale, 0.07 * scale, IRON, al);
        Solid.bar(x.b, grip.plus(up, 0.06 * scale).plus(aim, 0.16 * scale),
                grip.plus(up, 0.06 * scale).plus(aim, 0.24 * scale), right, 0.05 * scale, 0.07 * scale, IRON, al);
        V3 mag = grip.plus(aim, -0.02 * scale).plus(up, -0.1 * scale);
        Solid.bar(x.b, mag, mag.plus(up, -0.16 * scale).plus(aim, 0.03 * scale), right, 0.06 * scale,
                0.09 * scale, IRON, al);
        V3 handle = grip.plus(aim, -0.26 * scale).plus(up, -0.06 * scale);
        Solid.bar(x.b, handle, handle.plus(up, -0.15 * scale).plus(aim, -0.06 * scale), right, 0.06 * scale,
                0.07 * scale, STOCK_WOOD, al);
        Solid.bar(x.b, grip.plus(aim, -0.72 * scale).plus(up, -0.02 * scale), grip.plus(aim, -0.18 * scale),
                right, 0.065 * scale, 0.15 * scale, STOCK_WOOD, al);
        Solid.bar(x.b, grip.plus(aim, -0.62 * scale).plus(up, 0.065 * scale),
                grip.plus(aim, -0.36 * scale).plus(up, 0.06 * scale), right, 0.05 * scale, 0.04 * scale,
                0x5A4030, al);
        // the folded bipod under the handguard
        for (int s = -1; s <= 1; s += 2) {
            Solid.bar(x.b, grip.plus(aim, 0.36 * scale).plus(up, -0.07 * scale).plus(right, s * 0.03 * scale),
                    grip.plus(aim, 0.12 * scale).plus(up, -0.08 * scale).plus(right, s * 0.03 * scale), right,
                    0.018 * scale, 0.018 * scale, IRON_DARK, al);
        }
        if (!x.b.drawsLight()) {
            return;
        }
        Palette rune = Palette.of(SkillTheme.FROST);
        // runes along the handguard and the barrel, glowing hot after each shot
        for (int s = -1; s <= 1; s += 2) {
            V3 side = right.scale(s * 0.058 * scale);
            x.b.glow(grip.plus(aim, 0.06 * scale).add(side), grip.plus(aim, len * 0.4).add(side), 0.012 * scale,
                    rune, al * (0.55 + 0.45 * kick));
        }
        x.b.glow(grip.plus(aim, len * 0.45).plus(up, 0.04 * scale), muzzle.plus(up, 0.035 * scale), 0.01 * scale,
                rune, al * 0.6);
        x.b.glowPoint(scopeAxis.plus(aim, 0.31 * scale), 0.06 * scale, rune, al * 0.9);
        x.b.glowPoint(scopeAxis.plus(aim, -0.15 * scale), 0.035 * scale, rune, al * 0.6);
        if (kick > 0.05) {
            Palette heat = Palette.of(SkillTheme.FIRE);
            x.b.glow(muzzle.plus(aim, -len * 0.3), muzzle, 0.05 * scale, heat, kick * 0.7);
        }
        // assembling and folding away: light along the outline and sparks
        double edge = Ctx.bump(Ctx.clamp((x.t - x.c.start()) / 10.0)) + Ctx.bump(Ctx.clamp((x.c.end() - x.t) / 10.0));
        if (edge > 0.02) {
            x.b.glow(grip.plus(aim, -0.72 * scale), muzzle, 0.05, rune, edge * 0.8);
            for (int i = 0; i < 10; i++) {
                V3 at = grip.plus(aim, (x.rnd(i) * 1.6 - 0.6) * scale).plus(up, (x.rnd(i + 20) - 0.5) * 0.4)
                        .plus(right, (x.rnd(i + 40) - 0.5) * 0.4);
                x.b.mote(at, 0.05, rune.core(), edge);
            }
        }
    }

    // ------------------------------------------------------------------ the scythe

    /** The pose of the scythe at a moment: the grip, the shaft's direction (towards the blade) and the blade's. */
    private record Hold(V3 grip, V3 shaft, V3 blade) {
        Hold lerp(Hold o, double u) {
            double k = Ctx.smooth(u);
            V3 s = blend(shaft, o.shaft, k);
            return new Hold(grip.lerp(o.grip, k), s, across(blade.lerp(o.blade, k), s, o.blade));
        }
    }

    /**
     * The reaper's scythe: risen out of the dark, raised high over the head (振りかぶって), swung back once to hook the
     * souls in (hit frame {@code variant}), raised again and brought round in one great reaping turn at the peak, then
     * planted and gone in a breath of souls.
     */
    static void scythe(Ctx x) {
        Pose pose = x.pose;
        int[] beat = beats(x, x.c.variant(), 2);
        double hook = beat[0];
        double reap = x.c.peak();
        V3 f = pose.fwd();
        V3 r = pose.right();
        V3 chest = pose.feet().add(0.0, 1.25, 0.0);
        double length = Math.max(3.0, x.c.a());
        double show = presence(x, 10.0, 10.0);
        if (show <= 0.004) {
            return;
        }
        // key holds (shaft from the grip towards the blade end)
        Hold low = new Hold(chest.plus(f, 0.3).add(0.0, -0.6, 0.0), V3.UP.plus(f, 0.2).unit(), f);
        // raised over the head, leaning a little forward so the blade hangs over the reaper's sight
        Hold raised = new Hold(chest.add(0.0, 0.75, 0.0).plus(r, 0.2), V3.UP.scale(0.92).plus(f, 0.15).unit(),
                f.add(0.0, -0.45, 0.0).unit());
        Hold hooked = new Hold(chest.plus(r, 0.5), r.scale(0.95).add(0.0, 0.3, 0.0).unit(),
                f.scale(0.6).plus(r, -0.2).unit());
        Hold wound = new Hold(chest.add(0.0, 0.6, 0.0).plus(r, -0.4), V3.UP.scale(0.6).plus(r, -0.75).plus(f, -0.3)
                .unit(), f.plus(r, 0.3).unit());
        Hold hold;
        double t = x.t;
        double spin = -1.0;
        if (t < x.c.start() + 10.0) {
            hold = low.lerp(raised, (t - x.c.start()) / 10.0);
        } else if (t < hook - 5.0) {
            double tremble = Math.sin(t * 2.3) * 0.02 * Ctx.clamp((t - x.c.start() - 10.0) / 6.0);
            hold = new Hold(raised.grip.add(0.0, tremble, 0.0), raised.shaft, raised.blade);
        } else if (t < hook + 1.0) {
            hold = raised.lerp(hooked, (t - (hook - 5.0)) / 6.0);
        } else if (t < reap - 5.0) {
            hold = hooked.lerp(wound, (t - (hook + 1.0)) / Math.max(1.0, reap - 5.0 - hook - 1.0));
        } else if (t < reap + 3.0) {
            // one great turn all around the reaper
            spin = Ctx.easeOut((t - (reap - 5.0)) / 8.0);
            double angle = Math.PI * 2.0 * spin + Math.PI * 0.5;
            V3 out = f.scale(Math.cos(angle)).add(r.scale(Math.sin(angle)));
            V3 ahead = f.scale(-Math.sin(angle)).add(r.scale(Math.cos(angle)));
            hold = new Hold(chest.plus(out, 0.35), out.add(0.0, 0.18, 0.0).unit(), ahead);
        } else {
            double u = Ctx.clamp((t - reap - 3.0) / 6.0);
            V3 out = f.scale(Math.cos(Math.PI * 2.5)).add(r.scale(Math.sin(Math.PI * 2.5)));
            Hold done = new Hold(chest.plus(out, 0.35), out.add(0.0, 0.18, 0.0).unit(), f.scale(-1.0));
            Hold planted = new Hold(chest.plus(r, 0.5).add(0.0, -0.3, 0.0), V3.UP.plus(r, 0.25).unit(), f);
            hold = done.lerp(planted, u);
        }
        drawScythe(x, hold, length, show, spin);
    }

    private static void drawScythe(Ctx x, Hold hold, double length, double al, double spin) {
        V3 s = hold.shaft.unit(V3.UP);
        V3 bdir = across(hold.blade, s, x.pose.fwd());
        V3 butt = hold.grip.plus(s, -length * 0.28);
        V3 top = hold.grip.plus(s, length * 0.72);
        // the shaft: old wood bound with iron, a socket and a spike at the butt
        Solid.prism(x.b, butt, top, 0.075, 0.065, 8, bdir, OLD_WOOD, al);
        for (double k : new double[]{0.1, 0.42, 0.78}) {
            V3 at = butt.lerp(top, k);
            Solid.prism(x.b, at.plus(s, -0.07), at.plus(s, 0.07), 0.09, 0.09, 8, bdir, IRON, al);
        }
        Solid.prism(x.b, butt, butt.plus(s, -0.35), 0.06, 0.0, 6, bdir, IRON_DARK, al);
        Solid.prism(x.b, top.plus(s, -0.25), top.plus(s, 0.12), 0.11, 0.09, 8, bdir, IRON_DARK, al);
        // the blade: a long crescent from the top, its cutting edge on the inside facing the reaper
        double radius = length * 0.42;
        double sweep = Math.toRadians(112.0);
        V3 centre = top.plus(s, -radius);
        V3 normal = bdir.cross(s).unit();
        int n = 14;
        V3[] edge = new V3[n];
        V3[] back = new V3[n];
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            double a = sweep * u;
            V3 dir = s.scale(Math.cos(a)).add(bdir.scale(Math.sin(a)));
            double width = (0.62 * Math.pow(1.0 - u, 0.7) + 0.03) * (length / 6.5);
            edge[i] = centre.plus(dir, radius - width * 0.15);
            back[i] = centre.plus(dir, radius + width * 0.85);
        }
        Solid.blade(x.b, edge, back, normal, 0.06, 0x24242C, 0x6A5A6A, al);
        if (!x.b.drawsLight()) {
            return;
        }
        Palette blood = Palette.of(SkillTheme.BLOOD);
        Palette shade = Palette.of(SkillTheme.SHADOW);
        double[] w = new double[n];
        double[] a = new double[n];
        double hot = spin >= 0.0 ? 1.0 : 0.8 + 0.2 * Math.sin(x.t * 0.7);
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            w[i] = 0.09 * (1.0 - 0.6 * u);
            a[i] = al * hot * (1.0 - 0.3 * u);
        }
        x.b.glowStrip(edge, w, a, blood);
        // a cold shadow-light along the back of the blade
        double[] wb = new double[n];
        double[] ab = new double[n];
        for (int i = 0; i < n; i++) {
            wb[i] = 0.05 * (1.0 - 0.5 * i / (n - 1.0));
            ab[i] = al * 0.45;
        }
        x.b.glowStrip(back, wb, ab, shade);
        // souls clinging to the blade, a ghostly wake while it reaps
        for (int i = 0; i < 6; i++) {
            V3 at = back[(int) (x.rnd(i) * (n - 1))].plus(normal, (x.rnd(i + 9) - 0.5) * 0.3)
                    .add(0.0, Math.sin(x.t * 0.3 + i) * 0.15, 0.0);
            x.b.mote(at, 0.14, shade.glow(), al * 0.7);
        }
        x.b.glowPoint(top, 0.22, shade, al * 0.8);
        if (spin >= 0.0 && spin < 1.0) {
            for (int k = 1; k <= 4; k++) {
                double lag = spin - k * 0.05;
                if (lag <= 0.0) {
                    break;
                }
                double angle = Math.PI * 2.0 * lag + Math.PI * 0.5;
                V3 out = x.pose.fwd().scale(Math.cos(angle)).add(x.pose.right().scale(Math.sin(angle)));
                V3 tip = hold.grip.plus(out, length * 0.95).add(0.0, 0.4, 0.0);
                x.b.flare(tip, 1.2, blood.glow(), al * (0.5 - k * 0.1));
            }
        }
    }

    // ------------------------------------------------------------------ the giant blade

    /**
     * A blade grown huge (variant 0: the paladin's own sword, steel and gold with holy light running up it; variant 1:
     * the sword saint's blade of pure light come down from the sky), raised high, brought down ahead with all the
     * caster's strength at the peak and left standing in the rift a moment.
     */
    static void greatblade(Ctx x) {
        Pose pose = x.pose;
        V3 f = pose.fwd();
        V3 r = pose.right();
        double length = Math.max(3.0, x.c.a());
        boolean light = x.c.variant() == 1;
        double show = presence(x, light ? 12.0 : 6.0, 10.0);
        if (show <= 0.004) {
            return;
        }
        double peak = x.c.peak();
        double t = x.t;
        double strike = 6.0;
        // how long the blade is: it grows while raised (from the sky for the blade of light)
        double grow = Ctx.easeOut(Ctx.clamp((t - x.c.start()) / Math.max(4.0, (peak - strike - x.c.start()) * 0.6)));
        double len = length * (light ? 1.0 : 0.12 + 0.88 * grow);
        V3 hands;
        V3 dir;
        if (t < peak - strike) {
            double tremble = Math.sin(t * 2.7) * 0.015 * grow;
            hands = pose.feet().add(0.0, 2.15 + tremble, 0.0).plus(f, -0.1);
            dir = V3.UP.scale(0.94).plus(f, -0.34).unit();
        } else if (t < peak) {
            double k = Ctx.easeIn((t - (peak - strike)) / strike);
            double angle = Math.toRadians(110.0 - 128.0 * k);
            dir = f.scale(Math.cos(angle)).add(V3.UP.scale(Math.sin(angle))).unit();
            hands = pose.feet().add(0.0, 2.15 - 0.95 * k, 0.0).plus(f, -0.1 + 0.55 * k);
        } else {
            double angle = Math.toRadians(-18.0);
            dir = f.scale(Math.cos(angle)).add(V3.UP.scale(Math.sin(angle))).unit();
            hands = pose.feet().add(0.0, 1.2, 0.0).plus(f, 0.45);
        }
        if (light && t < x.c.start() + 12.0) {
            // the blade of light comes down out of the sky into the raised hands
            double fall = Ctx.easeOut(Ctx.clamp((t - x.c.start()) / 12.0));
            hands = hands.add(0.0, 18.0 * (1.0 - fall), 0.0);
        }
        V3 flat = r;
        V3 width = flat.cross(dir).unit();
        drawBlade(x, hands, dir, width, flat, len, show, light, t >= peak - strike && t < peak + 2.0);
    }

    private static void drawBlade(Ctx x, V3 hands, V3 dir, V3 width, V3 flat, double len, double al, boolean light,
                                  boolean swinging) {
        double base = 0.1 + 0.055 * len;
        V3 hilt = hands.plus(dir, 0.18);
        V3 tip = hilt.plus(dir, len);
        int n = 8;
        V3[] left = new V3[n];
        V3[] right = new V3[n];
        V3[] mid = new V3[n];
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            double w = base * (u < 0.82 ? 1.0 - 0.25 * u : (1.0 - 0.205) * (1.0 - u) / 0.18);
            V3 at = hilt.plus(dir, len * u);
            mid[i] = at;
            left[i] = at.plus(width, w);
            right[i] = at.plus(width, -w);
        }
        double thick = 0.05 + 0.012 * len;
        if (!light) {
            // steel blade (two halves, each sharp on its outer edge), a golden guard, a wrapped grip and a pommel
            Solid.blade(x.b, left, mid, flat, thick, STEEL, 0xEEF2F8, al);
            Solid.blade(x.b, right, mid, flat, thick, STEEL, 0xEEF2F8, al);
            double guard = base * 2.6 + 0.2;
            Solid.bar(x.b, hilt.plus(width, -guard), hilt.plus(width, guard), dir, 0.16, 0.12 + 0.01 * len, GOLD, al);
            Solid.prism(x.b, hands.plus(dir, 0.18), hands.plus(dir, -0.42), 0.05, 0.05, 6, width, 0x5A2A1A, al);
            Solid.prism(x.b, hands.plus(dir, -0.42), hands.plus(dir, -0.56), 0.085, 0.06, 6, width, GOLD, al);
        } else {
            Solid.blade(x.b, left, mid, flat, thick * 0.6, 0xFFF4D0, 0xFFFFFF, al * 0.45);
            Solid.blade(x.b, right, mid, flat, thick * 0.6, 0xFFF4D0, 0xFFFFFF, al * 0.45);
        }
        if (!x.b.drawsLight()) {
            return;
        }
        Palette holy = Palette.of(SkillTheme.HOLY);
        double pulse = 0.75 + 0.25 * Math.sin(x.t * 0.6);
        double[] w = new double[n];
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            w[i] = (light ? 0.22 : 0.05) * (1.0 - 0.5 * i / (n - 1.0)) * (1.0 + 0.02 * len);
            a[i] = al * pulse;
        }
        x.b.glowStrip(left, w, a, holy);
        x.b.glowStrip(right, w, a, holy);
        if (light) {
            double[] wc = new double[n];
            for (int i = 0; i < n; i++) {
                wc[i] = 0.18 * (1.0 + 0.04 * len) * (1.0 - 0.6 * i / (n - 1.0));
            }
            x.b.glowStrip(mid, wc, a, holy);
            x.b.glowPoint(tip, 0.6, holy, al);
            x.b.star(hilt, 2.2, 8, x.t * 0.05, holy.core(), al * 0.6);
        } else {
            // light runs up the fuller and gathers at the guard
            x.b.glow(hilt, hilt.plus(dir, len * 0.8), 0.05 * (1.0 + 0.03 * len), holy, al * pulse * 0.8);
            x.b.glowPoint(hilt, 0.35, holy, al * 0.8);
        }
        if (swinging) {
            // the arc the tip draws
            for (int k = 1; k <= 3; k++) {
                x.b.flare(tip.add(0.0, k * 0.8, 0.0).plus(x.pose.fwd(), -k * 0.9), 2.0, holy.glow(), al * 0.25);
            }
        }
    }

    // ------------------------------------------------------------------ the nail and the hammer

    /** Where the nail stands: {@code b} blocks ahead of where the caster stood. */
    private static V3 nailSpot(Ctx x) {
        return x.groundAt(x.pose.feet().plus(x.pose.fwd(), x.c.b()));
    }

    /** How deep the nail is driven: 0 before the first blow, 1 after the last. */
    private static double driven(Ctx x, int[] blows) {
        double depth = 0.0;
        for (int i = 0; i < blows.length; i++) {
            double since = x.t - blows[i];
            if (since >= 0.0) {
                double share = i == blows.length - 1 ? 0.6 : 0.4 / Math.max(1, blows.length - 1);
                depth += share * Ctx.easeOut(Ctx.clamp(since / 2.0));
            }
        }
        return depth;
    }

    /**
     * The cursed nail of 丑の刻参り: it comes down out of the dark and stands over the foe, point first; each blow of the
     * hammer drives it deeper, the last right through into the ground.
     */
    static void nail(Ctx x) {
        double length = Math.max(1.5, x.c.a());
        int[] blows = beats(x, x.c.variant(), Math.max(1, x.c.n()));
        double show = presence(x, 6.0, 10.0);
        if (show <= 0.004) {
            return;
        }
        V3 ground = nailSpot(x);
        double arrive = Ctx.easeOut(Ctx.clamp((x.t - x.c.start()) / 10.0));
        double depth = driven(x, blows);
        // the point hovers over the foe's head, then goes down through it into the ground
        double tipY = 2.2 + 6.0 * (1.0 - arrive) - (2.2 + length * 0.35) * depth;
        V3 tipAt = ground.add(0.0, tipY, 0.0);
        V3 headAt = tipAt.add(0.0, length, 0.0);
        V3 ref = x.pose.fwd();
        double k = length / 3.0;
        Solid.prism(x.b, tipAt.add(0.0, length * 0.18, 0.0), tipAt, 0.17 * k, 0.0, 4, ref, IRON_DARK, show);
        Solid.prism(x.b, headAt.add(0.0, -0.08, 0.0), tipAt.add(0.0, length * 0.18, 0.0), 0.24 * k, 0.17 * k, 4, ref,
                IRON, show);
        Solid.prism(x.b, headAt.add(0.0, -0.14, 0.0), headAt.add(0.0, 0.12, 0.0), 0.5 * k, 0.45 * k, 8, ref,
                IRON_DARK, show);
        if (!x.b.drawsLight()) {
            return;
        }
        Palette curse = Palette.of(SkillTheme.SHADOW);
        double flash = 0.0;
        for (int b : blows) {
            double since = x.t - b;
            if (since >= 0.0 && since < 10.0) {
                flash = Math.max(flash, Math.exp(-since / 2.5));
            }
        }
        // runes crawling down the shank, sparks at the head on each blow
        for (int s = 0; s < 4; s++) {
            V3 side = V3.UP.cross(ref).unit().rotate(V3.UP, Math.PI * 0.5 * s).scale(0.15 * length / 3.0);
            x.b.glow(headAt.add(0.0, -0.3, 0.0).add(side), tipAt.add(0.0, length * 0.25, 0.0).add(side.scale(0.7)),
                    0.025, curse, show * (0.45 + 0.55 * flash));
        }
        x.b.glowPoint(headAt.add(0.0, 0.12, 0.0), 0.35 + 0.8 * flash, curse, show * (0.3 + 0.7 * flash));
        if (flash > 0.05) {
            x.b.star(headAt.add(0.0, 0.15, 0.0), 2.6 * flash + 0.4, 8, x.rnd(3) * 3.0, 0xFFFFFF, flash);
        }
    }

    /** The cursed hammer: raised behind the nail, brought down onto its head at every blow. */
    static void hammer(Ctx x) {
        double size = Math.max(0.6, x.c.a());
        int[] blows = beats(x, x.c.variant(), Math.max(1, x.c.n()));
        double show = presence(x, 8.0, 8.0);
        if (show <= 0.004) {
            return;
        }
        V3 f = x.pose.fwd();
        V3 r = x.pose.right();
        V3 ground = nailSpot(x);
        double nailLength = Math.max(1.5, x.c.c() > 0.0 ? x.c.c() : 3.2);
        double depth = driven(x, blows);
        double arrive = Ctx.easeOut(Ctx.clamp((x.t - x.c.start() + 2.0) / 10.0));
        double headY = 2.2 + 6.0 * (1.0 - arrive) + nailLength - (2.2 + nailLength * 0.35) * depth;
        V3 nailHead = ground.add(0.0, headY + 0.12, 0.0);
        // the swing: the hammer turns about a point behind and above the nail head (towards the caster)
        double angle = Math.toRadians(70.0);
        double windup = 9.0;
        double stroke = 3.0;
        for (int i = 0; i < blows.length; i++) {
            double b = blows[i];
            double start = i == 0 ? x.c.start() + 6.0 : blows[i - 1] + 3.0;
            if (x.t >= start && x.t < b - stroke) {
                double k = Ctx.smooth((x.t - start) / Math.max(1.0, b - stroke - start));
                angle = Math.toRadians(25.0 + (i == blows.length - 1 ? 95.0 : 70.0) * k);
            } else if (x.t >= b - stroke && x.t < b) {
                double k = Ctx.easeIn((x.t - (b - stroke)) / stroke);
                double from = i == blows.length - 1 ? 120.0 : 95.0;
                angle = Math.toRadians(from * (1.0 - k));
            } else if (x.t >= b && x.t < b + 3.0) {
                angle = Math.toRadians(4.0 * Math.sin((x.t - b) * 2.0));
            }
        }
        if (x.t < x.c.start() + 6.0) {
            angle = Math.toRadians(25.0);
        }
        double handle = size * 2.2;
        // the hammer is swung from beside the nail (on the caster's weapon side) across the line of sight, so it is
        // seen whole from behind the caster and through its eyes: at angle 0 the handle lies from the pivot to the
        // nail and the head sits on it; raising turns it up about the forward axis
        V3 side = r.scale(x.pose.side());
        V3 pivot = nailHead.plus(side, handle * 0.95).add(0.0, 0.3, 0.0);
        V3 along = side.scale(-1.0).rotate(f, angle * x.pose.side()).unit();
        V3 headDown = V3.UP.scale(-1.0).rotate(f, angle * x.pose.side()).unit();
        V3 headCentre = pivot.plus(along, handle).plus(headDown, -size * 0.4);
        r = f;
        Solid.prism(x.b, pivot.plus(along, -0.2), pivot.plus(along, handle), 0.07 * size, 0.08 * size, 6, r,
                OLD_WOOD, show);
        Solid.box(x.b, headCentre, r, headDown, along, size * 0.38, size * 0.48, size * 0.3, IRON_DARK, show);
        Solid.box(x.b, headCentre.plus(headDown, size * 0.5), r, headDown, along, size * 0.4, size * 0.04,
                size * 0.32, IRON, show);
        Solid.box(x.b, headCentre.plus(headDown, -size * 0.5), r, headDown, along, size * 0.4, size * 0.04,
                size * 0.32, IRON, show);
        if (!x.b.drawsLight()) {
            return;
        }
        Palette curse = Palette.of(SkillTheme.SHADOW);
        x.b.glow(headCentre.plus(r, -size * 0.39).plus(headDown, -size * 0.3),
                headCentre.plus(r, -size * 0.39).plus(headDown, size * 0.3), 0.04 * size, curse, show * 0.8);
        x.b.glow(headCentre.plus(r, size * 0.39).plus(headDown, -size * 0.3),
                headCentre.plus(r, size * 0.39).plus(headDown, size * 0.3), 0.04 * size, curse, show * 0.8);
        x.b.glowPoint(headCentre, 0.5 * size, curse, show * 0.35);
    }

    // ------------------------------------------------------------------ the rock from the sky

    /**
     * The sage's star: a huge rock wreathed in pale blue fire, coming down slowly out of the sky circle onto the anchor
     * at the peak (the blast is the other layers').
     */
    static void starRock(Ctx x) {
        if (!x.before()) {
            return;
        }
        double size = Math.max(1.0, x.c.a());
        double height = x.c.c() > 0.0 ? x.c.c() : 36.0;
        V3 ground = x.groundAt(x.pose.feet());
        // it comes down from beyond the spot it falls on, so the caster watches it coming
        V3 sky = ground.add(x.pose.fwd().x() * height * 0.4, height, x.pose.fwd().z() * height * 0.4);
        // a slow fall that quickens only at the end (it is in sight for most of it)
        double u = Math.pow(x.rise(), 1.7);
        double appear = Ctx.smooth(Ctx.clamp((x.t - x.c.start()) / 8.0));
        V3 at = sky.lerp(ground.add(0.0, size * 0.5, 0.0), u);
        V3 dir = ground.sub(sky).unit();
        double scale = size * (0.25 + 0.75 * appear);
        // the rock: a lumpy ball of basalt turning slowly
        int rings = 6;
        int segs = 9;
        V3[][] pts = new V3[rings + 1][segs];
        double spin = x.t * 0.05;
        for (int i = 0; i <= rings; i++) {
            double lat = Math.PI * i / rings - Math.PI / 2.0;
            for (int j = 0; j < segs; j++) {
                double lon = Math.PI * 2.0 * j / segs + spin;
                double bump = 0.78 + 0.32 * Brush.unit(x.c.seed(), i * 31L + j);
                if (i == 0 || i == rings) {
                    bump = 0.85;
                }
                pts[i][j] = at.add(Math.cos(lat) * Math.cos(lon) * scale * bump, Math.sin(lat) * scale * bump,
                        Math.cos(lat) * Math.sin(lon) * scale * bump);
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < segs; j++) {
                int k = (j + 1) % segs;
                int shade = (i + j) % 3 == 0 ? 0x3A4258 : 0x48546E;
                Solid.face(x.b, pts[i][j], pts[i + 1][j], pts[i + 1][k], pts[i][k], shade, appear);
            }
        }
        if (!x.b.drawsLight()) {
            return;
        }
        Palette fire = Palette.of(x.theme);
        // glowing cracks over the rock, the fire wrapped around it and streaming back along its path
        for (int i = 0; i < 10; i++) {
            V3 a0 = pts[1 + (int) (x.rnd(i) * (rings - 1))][(int) (x.rnd(i + 7) * segs)];
            V3 a1 = pts[1 + (int) (x.rnd(i + 14) * (rings - 1))][(int) (x.rnd(i + 21) * segs)];
            x.b.glow(a0.lerp(at, -0.04), a1.lerp(at, -0.04), 0.07 * scale, fire, appear);
        }
        x.b.glowPoint(at, scale * 2.6, fire, appear * 0.8);
        x.b.flare(at, scale * 5.0, fire.deep(), appear * 0.35);
        // tongues of pale fire licking back off the rock against its fall
        for (int i = 0; i < 22; i++) {
            V3 out = new V3(x.rnd(i + 90) - 0.5, x.rnd(i + 100) - 0.5, x.rnd(i + 110) - 0.5).unit(V3.UP);
            V3 root = at.plus(out, scale * 0.85);
            double flick = 0.6 + 0.4 * Math.sin(x.t * 0.9 + i * 1.7);
            V3 tip = root.plus(dir, -scale * (1.4 + 1.6 * x.rnd(i + 120)) * flick).plus(out, scale * 0.5);
            x.b.softRibbon(root, tip, scale * 0.95, 0.05, fire.glow(), appear * 0.85, 0.0);
            x.b.ribbon(root, root.lerp(tip, 0.5), scale * 0.18, 0.02, fire.core(), appear * 0.6, 0.0);
        }
        int n = 12;
        V3[] trail = new V3[n];
        double[] w = new double[n];
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            double k = (double) i / (n - 1);
            trail[i] = at.plus(dir, -k * (6.0 + 14.0 * u) * (0.5 + 0.5 * appear)).add(
                    Math.sin(x.t * 0.5 + i) * 0.2 * k * scale, 0.0, Math.cos(x.t * 0.4 + i) * 0.2 * k * scale);
            w[i] = scale * 2.4 * (1.0 - 0.85 * k);
            a[i] = appear * (1.0 - k) * x.flicker(0.25, i);
        }
        x.b.glowStrip(trail, w, a, fire);
        for (int i = 0; i < 18; i++) {
            double k = (x.rnd(i + 50) + x.t * 0.07) % 1.0;
            V3 tongue = at.plus(dir, -k * scale * 4.0).add((x.rnd(i + 60) - 0.5) * scale * 2.2,
                    (x.rnd(i + 70) - 0.5) * scale * 2.2, (x.rnd(i + 80) - 0.5) * scale * 2.2);
            x.b.mote(tongue, 0.25 * scale * (1.0 - k), fire.core(), appear * (1.0 - k));
        }
        // the ring of fire where it will land, tightening
        V3 g = ground.add(0.0, 0.05, 0.0);
        x.b.glowRing(g, new V3(1, 0, 0), new V3(0, 0, 1), size * 3.0 * (1.0 - u) + size * 1.2, 0.18, fire,
                appear * (0.3 + 0.7 * u));
    }

    // ------------------------------------------------------------------ the giant shield

    /**
     * The guardian's giant shield: it rises golden out of the ground ahead, is planted (the ward goes out at hit frame
     * {@code variant}), held, and thrust forward with everything at the peak before it fades.
     */
    static void aegis(Ctx x) {
        Pose pose = x.pose;
        V3 f = pose.fwd();
        V3 r = pose.right();
        double height = Math.max(1.5, x.c.a());
        double width = height * 0.72;
        double show = presence(x, 6.0, 10.0);
        if (show <= 0.004) {
            return;
        }
        // a shield of light made solid: the caster can see the foes through it
        double body = show * 0.78;
        double planted = beats(x, x.c.variant(), 1)[0];
        double peak = x.c.peak();
        double rise = Ctx.easeOut(Ctx.clamp((x.t - x.c.start()) / Math.max(1.0, planted - x.c.start())));
        double thrust = 0.0;
        if (x.t >= peak - 4.0) {
            thrust = x.t < peak ? Ctx.easeIn((x.t - (peak - 4.0)) / 4.0) : 1.0 - 0.35 * Ctx.clamp((x.t - peak) / 8.0);
        }
        V3 base = pose.feet().plus(f, 1.6 + 2.6 * thrust).add(0.0, -height * (1.0 - rise) - 0.1, 0.0);
        // a heater shield: straight at the top, curving to a point at the bottom, slightly bowed
        int n = 9;
        V3[] left = new V3[n];
        V3[] right = new V3[n];
        for (int i = 0; i < n; i++) {
            double v = (double) i / (n - 1);
            double half = width * 0.5 * (v > 0.45 ? 1.0 : Math.sqrt(Math.max(0.0, v / 0.45)) * 0.96 + 0.04);
            double y = height * v;
            double bow = 0.18 * height * 0.25;
            left[i] = base.add(0.0, y, 0.0).plus(r, -half).plus(f, -bow * (half / (width * 0.5)));
            right[i] = base.add(0.0, y, 0.0).plus(r, half).plus(f, -bow * (half / (width * 0.5)));
        }
        double thick = 0.22;
        V3 back = f.scale(-thick);
        for (int i = 0; i < n - 1; i++) {
            Solid.face(x.b, left[i], right[i], right[i + 1], left[i + 1], f, GOLD, GOLD, body);
            Solid.face(x.b, left[i].add(back), left[i + 1].add(back), right[i + 1].add(back), right[i].add(back),
                    f.scale(-1.0), 0xC89A40, 0xB08A3A, body);
            Solid.face(x.b, left[i], left[i + 1], left[i + 1].add(back), left[i].add(back), r.scale(-1.0), SILVER,
                    SILVER, show);
            Solid.face(x.b, right[i], right[i].add(back), right[i + 1].add(back), right[i + 1], r, SILVER, SILVER,
                    show);
        }
        Solid.face(x.b, left[n - 1], left[n - 1].add(back), right[n - 1].add(back), right[n - 1], V3.UP, SILVER,
                SILVER, show);
        if (!x.b.drawsLight()) {
            return;
        }
        Palette holy = Palette.of(SkillTheme.HOLY);
        double ward = x.t >= planted ? Math.exp(-(x.t - planted) / 6.0) : 0.0;
        double glow = show * (0.55 + 0.3 * Math.sin(x.t * 0.4) + 0.6 * ward + 0.6 * thrust);
        // the rim and the cross, on both faces (the caster sees the back)
        double[] w = new double[n];
        double[] a = new double[n];
        for (double out : new double[]{0.05, -thick - 0.05}) {
            V3 face = base.plus(f, out);
            V3[] rimL = new V3[n];
            V3[] rimR = new V3[n];
            for (int i = 0; i < n; i++) {
                rimL[i] = left[i].plus(f, out);
                rimR[i] = right[i].plus(f, out);
                w[i] = 0.07;
                a[i] = glow;
            }
            x.b.glowStrip(rimL, w, a, holy);
            x.b.glowStrip(rimR, w, a, holy);
            V3 centre = face.add(0.0, height * 0.58, 0.0);
            x.b.glow(centre.add(0.0, -height * 0.32, 0.0), centre.add(0.0, height * 0.3, 0.0), 0.14, holy, glow);
            x.b.glow(centre.plus(r, -width * 0.3).add(0.0, height * 0.08, 0.0),
                    centre.plus(r, width * 0.3).add(0.0, height * 0.08, 0.0), 0.14, holy, glow);
            x.b.glowPoint(centre.add(0.0, height * 0.08, 0.0), 0.6 + ward * 2.0, holy, glow * 0.7);
        }
        V3 centre = base.add(0.0, height * 0.58, 0.0);
        if (thrust > 0.0) {
            x.b.flare(centre.plus(f, 0.6), height * 1.4 * thrust, holy.glow(), thrust * 0.5);
        }
    }
}
