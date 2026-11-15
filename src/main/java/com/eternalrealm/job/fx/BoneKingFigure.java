package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;

/**
 * The body of 骸骨の騎士王, the skeleton knight-king the necromancer's finale raises (twice a man's height): bones of old
 * ivory under a black iron breastplate and pauldrons trimmed with gold, a tattered royal cape, a crown on the skull,
 * violet fire in the eye sockets and the ribcage, and a greatsword of black iron with a glowing edge. It rises out of
 * the ground, breathes, walks, cleaves overhead or sweeps wide, and crumbles back into mana. Solid shapes and light like
 * the finales' props: the entity renderer draws the solid body (lit by the world, shown with shader packs too), the
 * skill light adds the glow. Pure; the moments come from the entity ({@code job.forge.BoneKing}).
 */
public final class BoneKingFigure {
    /** Ticks it takes to rise out of the ground (it does nothing else meanwhile). */
    public static final int RISE_TICKS = 40;
    /** Length of one swing of the greatsword. */
    public static final int SWING_TICKS = 26;
    /** When in a swing the blade lands. */
    public static final int IMPACT_TICK = 12;
    /** Ticks from one swing's start to the next one's at the earliest. */
    public static final int SWING_INTERVAL = 46;
    /** Ticks it takes to crumble away. */
    public static final int FADE_TICKS = 30;
    /** How tall it stands (blocks). */
    public static final double HEIGHT = 4.4;

    private static final int BONE = 0xD8CDB2;
    private static final int BONE_DARK = 0xA89A7E;
    private static final int IRON = 0x2A2A34;
    private static final int IRON_EDGE = 0x3E3E4C;
    private static final int GOLD = 0xC89A34;
    private static final int CAPE = 0x3A1450;
    private static final int CAPE_DARK = 0x220A30;
    private static final int BLADE = 0x22222A;

    private BoneKingFigure() {
    }

    /**
     * One moment of the king.
     *
     * @param feet       where it stands
     * @param yawDegrees its facing (Minecraft yaw)
     * @param age        ticks since it was raised
     * @param swingAge   ticks into its current swing, or below 0
     * @param swingKind  0 an overhead cleave, 1 a wide sweep
     * @param fadeAge    ticks since it began to crumble, or below 0
     * @param walk       the walk cycle's position (Minecraft's limb swing)
     * @param walkAmount how much it walks (0 standing, 1 striding)
     */
    public record State(V3 feet, double yawDegrees, double age, double swingAge, int swingKind, double fadeAge,
                        double walk, double walkAmount, long seed) {
    }

    /** How solid it is at {@code s}: rising in, then crumbling away. */
    public static double presence(State s) {
        double in = Ctx.smooth(s.age() / 14.0);
        double out = s.fadeAge() < 0.0 ? 1.0 : 1.0 - Ctx.clamp(s.fadeAge() / FADE_TICKS);
        return Math.min(in, out);
    }

    /** Draws the king (solids into the brush's solid sink, its glow as light). */
    public static void paint(Brush b, State s) {
        double yaw = Math.toRadians(s.yawDegrees());
        V3 f = new V3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        V3 r = new V3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        V3 up = V3.UP;
        double al = presence(s);
        if (al <= 0.004) {
            return;
        }
        // rising out of the ground; crumbling, it sags and sinks
        double rise = Ctx.easeOut(Ctx.clamp(s.age() / RISE_TICKS));
        double sink = s.fadeAge() < 0.0 ? 0.0 : Ctx.easeIn(Ctx.clamp(s.fadeAge() / FADE_TICKS));
        V3 feet = s.feet().add(0.0, -HEIGHT * 0.95 * (1.0 - rise) - 1.2 * sink, 0.0);
        double breathe = Math.sin(s.age() * 0.09) * 0.035;
        double stride = Math.sin(s.walk() * 0.6) * Math.min(1.0, s.walkAmount()) * 0.5;
        // the swing: how the torso turns and where the sword is
        Swing sw = swing(s);
        V3 fwd = f.rotate(up, sw.turn).unit(f);
        V3 right = r.rotate(up, sw.turn).unit(r);
        V3 pelvis = feet.add(0.0, 1.72 + breathe * 0.5, 0.0);
        // a heavy, hunched frame: the chest a little forward of the hips
        V3 chest = pelvis.add(0.0, 1.02 + breathe, 0.0).plus(fwd, 0.12 + sw.lean);
        V3 neck = chest.add(0.0, 0.5, 0.0).plus(fwd, 0.1 + sw.lean * 0.5);
        V3 skull = neck.add(0.0, 0.46, 0.0).plus(fwd, 0.1);
        // ---- legs: thick bones in black iron greaves, set wide
        for (int side = -1; side <= 1; side += 2) {
            double swingLeg = stride * side;
            V3 hip = pelvis.plus(r, 0.4 * side);
            V3 knee = hip.add(0.0, -0.84, 0.0).plus(f, 0.2 + swingLeg * 0.6).plus(r, 0.06 * side);
            V3 ankle = knee.add(0.0, -0.8, 0.0).plus(f, -0.05 + swingLeg * 0.3);
            ankle = new V3(ankle.x(), Math.max(feet.y() + 0.14, ankle.y()), ankle.z());
            Solid.prism(b, hip, knee, 0.16, 0.12, 6, f, BONE, al);
            Solid.prism(b, knee, ankle, 0.13, 0.1, 6, f, BONE_DARK, al);
            // greaves, the knee guard and the sabatons of black iron
            Solid.bar(b, knee.lerp(ankle, 0.1), knee.lerp(ankle, 0.92), r, 0.36, 0.34, IRON, al);
            Solid.bar(b, knee.lerp(ankle, 0.1).plus(f, 0.02), knee.lerp(ankle, 0.92).plus(f, 0.02), r, 0.2, 0.36,
                    IRON_EDGE, al);
            Solid.box(b, knee.plus(f, 0.08), r, up, f, 0.2, 0.17, 0.16, IRON_EDGE, al);
            Solid.box(b, knee.plus(f, 0.14).add(0.0, 0.02, 0.0), r, up, f, 0.12, 0.05, 0.12, GOLD, al);
            Solid.box(b, ankle.add(0.0, -0.08, 0.0).plus(f, 0.14), r, up, f, 0.19, 0.09, 0.32, IRON_EDGE, al);
            Solid.prism(b, hip.add(0.0, 0.02, 0.0), hip.lerp(knee, 0.55), 0.24, 0.2, 6, f, IRON, al); // cuisse
        }
        // ---- pelvis, spine, ribs, breastplate, belt and faulds
        Solid.box(b, pelvis, right, up, fwd, 0.56, 0.19, 0.3, BONE_DARK, al);
        for (int i = 0; i < 6; i++) {
            V3 at = pelvis.lerp(chest, (i + 0.5) / 6.0).plus(fwd, -0.17);
            Solid.box(b, at, right, up, fwd, 0.13, 0.09, 0.13, BONE, al);
        }
        for (int i = 0; i < 4; i++) {
            V3 ring = pelvis.lerp(chest, 0.42 + 0.14 * i).plus(fwd, -0.02);
            double w = 0.6 - 0.05 * Math.abs(i - 1.5);
            for (int side = -1; side <= 1; side += 2) {
                V3 back = ring.plus(fwd, -0.18).plus(right, 0.14 * side);
                V3 out = ring.plus(right, w * side).add(0.0, -0.06, 0.0);
                V3 front = ring.plus(fwd, 0.34).plus(right, 0.16 * side).add(0.0, -0.12, 0.0);
                Solid.bar(b, back, out, up, 0.1, 0.085, BONE, al);
                Solid.bar(b, out, front, up, 0.1, 0.085, BONE, al);
            }
        }
        // the breastplate over the upper chest (the ribs and their fire show below it), a gorget, a gold rim
        V3 plate = chest.plus(fwd, 0.26).add(0.0, 0.12, 0.0);
        Solid.box(b, plate, right, up, fwd, 0.58, 0.34, 0.1, IRON, al);
        Solid.box(b, plate.plus(fwd, 0.06).add(0.0, -0.04, 0.0), right, up, fwd, 0.3, 0.26, 0.08, IRON_EDGE, al);
        Solid.box(b, plate.add(0.0, 0.33, 0.0), right, up, fwd, 0.6, 0.05, 0.12, GOLD, al);
        Solid.box(b, plate.add(0.0, -0.33, 0.0), right, up, fwd, 0.6, 0.04, 0.12, GOLD, al);
        Solid.box(b, chest.plus(fwd, -0.22).add(0.0, 0.12, 0.0), right, up, fwd, 0.56, 0.32, 0.08, IRON, al); // back
        Solid.prism(b, chest.add(0.0, 0.4, 0.0).plus(fwd, 0.04), neck.add(0.0, 0.1, 0.0), 0.36, 0.24, 8, fwd, IRON_EDGE,
                al);
        Solid.box(b, pelvis.add(0.0, 0.22, 0.0), right, up, fwd, 0.62, 0.08, 0.34, 0x4A3A2A, al);
        Solid.box(b, pelvis.add(0.0, 0.22, 0.0).plus(fwd, 0.35), right, up, fwd, 0.12, 0.1, 0.03, GOLD, al);
        for (int k = -2; k <= 2; k++) {
            // a skirt of iron plates (faulds) all round, longer at the sides
            double a = k * 0.62;
            V3 dir = fwd.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
            V3 at = pelvis.plus(dir, 0.42).add(0.0, -0.12 - 0.06 * Math.abs(k), 0.0);
            V3 across = up.cross(dir).unit(right);
            Solid.box(b, at, across, up, dir, 0.2, 0.26 + 0.05 * Math.abs(k), 0.04, k % 2 == 0 ? IRON : IRON_EDGE, al);
        }
        // ---- the cape, swaying behind, ragged at the hem
        int strips = 7;
        double sway = Math.sin(s.age() * 0.07) * 0.12 + stride * 0.2;
        for (int i = 0; i < strips; i++) {
            double u0 = (double) i / strips - 0.5;
            double u1 = (double) (i + 1) / strips - 0.5;
            V3 top0 = neck.plus(fwd, -0.36).plus(right, u0 * 1.6).add(0.0, -0.1, 0.0);
            V3 top1 = neck.plus(fwd, -0.36).plus(right, u1 * 1.6).add(0.0, -0.1, 0.0);
            double ragged0 = 0.32 * Brush.unit(s.seed(), i);
            double ragged1 = 0.32 * Brush.unit(s.seed(), i + 1);
            double flutter0 = Math.sin(s.age() * 0.11 + i * 0.9) * 0.08;
            double flutter1 = Math.sin(s.age() * 0.11 + (i + 1) * 0.9) * 0.08;
            V3 low0 = feet.add(0.0, 0.45 + ragged0, 0.0).plus(fwd, -0.85 - sway - flutter0).plus(right, u0 * 2.2);
            V3 low1 = feet.add(0.0, 0.45 + ragged1, 0.0).plus(fwd, -0.85 - sway - flutter1).plus(right, u1 * 2.2);
            Solid.face(b, top0, top1, low1, low0, fwd.scale(-1.0), i % 2 == 0 ? CAPE : CAPE_DARK, CAPE_DARK, al);
        }
        // ---- shoulders, arms and the greatsword in both hands
        V3 hands = sw.hands(pelvis, chest, fwd, right);
        V3 blade = sw.blade;
        for (int side = -1; side <= 1; side += 2) {
            V3 shoulder = chest.add(0.0, 0.32, 0.0).plus(right, 0.8 * side);
            V3 grip = hands.plus(blade, side > 0 ? -0.22 : 0.16).plus(right, 0.08 * side);
            V3 elbow = shoulder.lerp(grip, 0.5).plus(right, 0.36 * side).add(0.0, -0.3, 0.0).plus(fwd, -0.12);
            Solid.prism(b, shoulder, elbow, 0.14, 0.12, 6, fwd, BONE, al);
            Solid.prism(b, elbow, grip, 0.12, 0.1, 6, fwd, BONE_DARK, al);
            Solid.box(b, elbow, right, up, fwd, 0.15, 0.15, 0.15, IRON_EDGE, al); // couter
            Solid.prism(b, elbow.lerp(grip, 0.35), grip, 0.17, 0.15, 6, fwd, IRON, al); // vambrace
            Solid.box(b, grip, right, up, fwd, 0.16, 0.15, 0.16, IRON, al); // gauntlet
            // pauldrons of black iron, gold trimmed, three layers and a spike
            Solid.box(b, shoulder.add(0.0, 0.14, 0.0).plus(right, 0.04 * side), right, up, fwd, 0.44, 0.17, 0.42,
                    IRON, al);
            Solid.box(b, shoulder.add(0.0, -0.08, 0.0).plus(right, 0.16 * side), right, up, fwd, 0.36, 0.08, 0.38,
                    IRON_EDGE, al);
            Solid.box(b, shoulder.add(0.0, -0.22, 0.0).plus(right, 0.22 * side), right, up, fwd, 0.28, 0.07, 0.33,
                    IRON, al);
            Solid.box(b, shoulder.add(0.0, 0.32, 0.0).plus(right, 0.04 * side), right, up, fwd, 0.45, 0.04, 0.43,
                    GOLD, al);
            V3 spike = shoulder.add(0.0, 0.34, 0.0).plus(right, 0.18 * side);
            Solid.prism(b, spike, spike.add(0.0, 0.5, 0.0).plus(right, 0.22 * side), 0.1, 0.0, 5, fwd, IRON_EDGE, al);
        }
        V3 guardAt = hands.plus(blade, 0.36);
        V3 tip = guardAt.plus(blade, 3.4);
        V3 flat = across(right.rotate(blade, 0.0), blade);
        V3 width = flat.cross(blade).unit();
        Solid.prism(b, hands.plus(blade, -0.62), guardAt, 0.08, 0.08, 6, width, 0x3A2A20, al);
        Solid.prism(b, hands.plus(blade, -0.8), hands.plus(blade, -0.62), 0.15, 0.1, 6, width, GOLD, al);
        Solid.bar(b, guardAt.plus(width, -0.62), guardAt.plus(width, 0.62), blade, 0.22, 0.16, GOLD, al);
        Solid.box(b, guardAt, width, blade, flat, 0.16, 0.12, 0.1, IRON, al);
        int n = 6;
        V3[] edgeL = new V3[n];
        V3[] edgeR = new V3[n];
        V3[] mid = new V3[n];
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            double w = u < 0.85 ? 0.27 * (1.0 - 0.2 * u) : 0.27 * 0.8 * (1.0 - u) / 0.15;
            V3 at = guardAt.lerp(tip, u);
            mid[i] = at;
            edgeL[i] = at.plus(width, w);
            edgeR[i] = at.plus(width, -w);
        }
        Solid.blade(b, edgeL, mid, flat, 0.11, BLADE, 0x5A4A6A, al);
        Solid.blade(b, edgeR, mid, flat, 0.11, BLADE, 0x5A4A6A, al);
        // ---- the skull, its jaw and the crown
        V3 face = fwd;
        Solid.prism(b, neck, skull.add(0.0, -0.2, 0.0), 0.12, 0.11, 6, f, BONE_DARK, al);
        Solid.box(b, skull, right, up, face, 0.3, 0.31, 0.32, BONE, al);
        Solid.box(b, skull.add(0.0, 0.12, 0.0).plus(face, 0.02), right, up, face, 0.32, 0.12, 0.33, BONE, al);
        double jaw = sw.roar;
        Solid.box(b, skull.add(0.0, -0.37 - 0.08 * jaw, 0.0).plus(face, 0.08), right, up, face, 0.25, 0.09, 0.24,
                BONE_DARK, al);
        for (int side = -1; side <= 1; side += 2) {
            Solid.box(b, skull.plus(face, 0.31).plus(right, 0.13 * side).add(0.0, 0.02, 0.0), right, up, face, 0.09,
                    0.085, 0.02, 0x14101A, al);
        }
        Solid.box(b, skull.plus(face, 0.31).add(0.0, -0.13, 0.0), right, up, face, 0.04, 0.06, 0.02, 0x14101A, al);
        V3 crownBase = skull.add(0.0, 0.3, 0.0);
        Solid.prism(b, crownBase, crownBase.add(0.0, 0.14, 0.0), 0.4, 0.42, 8, face, GOLD, al);
        for (int k = 0; k < 7; k++) {
            double a = Math.PI * 2.0 * k / 7.0;
            V3 base = crownBase.add(0.0, 0.14, 0.0).plus(face, Math.cos(a) * 0.36).plus(right, Math.sin(a) * 0.36);
            Solid.prism(b, base, base.add(0.0, k == 0 ? 0.42 : 0.3, 0.0), 0.07, 0.0, 4, face, GOLD, al);
        }
        if (!b.drawsLight()) {
            return;
        }
        // ---- its glow: eyes, the fire in the ribs, the crown's gem, the blade's edge, the mana it is made of
        Palette soul = Palette.of(SkillTheme.SHADOW);
        Palette eye = Palette.of(SkillTheme.ARCANE);
        double lit = al * (0.75 + 0.25 * Math.sin(s.age() * 0.21));
        for (int side = -1; side <= 1; side += 2) {
            V3 socket = skull.plus(face, 0.33).plus(right, 0.13 * side).add(0.0, 0.02, 0.0);
            b.glowPoint(socket, 0.18 + 0.06 * jaw, eye, lit);
            b.glow(socket, socket.plus(face, 0.35).add(0.0, 0.07, 0.0).plus(right, 0.2 * side), 0.04, 0.0, eye,
                    lit * 0.7, 0.0);
        }
        b.glowPoint(crownBase.add(0.0, 0.08, 0.0).plus(face, 0.42), 0.12, eye, lit);
        V3 heart = pelvis.lerp(chest, 0.45).plus(fwd, 0.05);
        b.glowPoint(heart, 0.6, soul, lit * 0.85);
        for (int i = 0; i < 9; i++) {
            double k = (s.age() * 0.05 + Brush.unit(s.seed(), i + 30)) % 1.0;
            V3 flame = heart.add((Brush.unit(s.seed(), i + 40) - 0.5) * 0.6, k * 1.1,
                    (Brush.unit(s.seed(), i + 50) - 0.5) * 0.5);
            b.mote(flame, 0.22 * (1.0 - k), soul.glow(), lit * (1.0 - k));
        }
        // wisps of mana rising off the shoulders and the crown
        for (int i = 0; i < 8; i++) {
            double k = (s.age() * 0.035 + Brush.unit(s.seed(), i + 110)) % 1.0;
            int side = i % 2 == 0 ? 1 : -1;
            V3 from = (i < 6 ? chest.add(0.0, 0.5, 0.0).plus(right, 0.8 * side) : crownBase.add(0.0, 0.3, 0.0))
                    .add((Brush.unit(s.seed(), i + 120) - 0.5) * 0.3, k * 1.6, (Brush.unit(s.seed(), i + 130) - 0.5) * 0.3);
            b.mote(from, 0.2 * (1.0 - k) + 0.05, soul.glow(), al * 0.6 * (1.0 - k));
        }
        double[] w = new double[n];
        double[] a = new double[n];
        double hot = sw.hot;
        for (int i = 0; i < n; i++) {
            w[i] = 0.06 + 0.1 * hot;
            a[i] = al * (0.45 + 0.55 * hot);
        }
        b.glowStrip(edgeL, w, a, soul);
        b.glowStrip(edgeR, w, a, soul);
        b.glow(guardAt, guardAt.lerp(tip, 0.85), 0.04, soul, al * 0.6);
        b.glowRing(feet.add(0.0, 0.06, 0.0), new V3(1, 0, 0), new V3(0, 0, 1), 1.6 + 0.1 * Math.sin(s.age() * 0.1),
                0.09, soul, al * 0.5);
        b.disc(feet.add(0.0, 0.05, 0.0), new V3(1, 0, 0), new V3(0, 0, 1), 1.5, soul.glow(), al * 0.18, 0.0);
        if (s.age() < RISE_TICKS + 6) {
            double k = 1.0 - Ctx.clamp(s.age() / (RISE_TICKS + 6.0));
            b.glowColumn(s.feet(), HEIGHT * 1.3, 1.3 * k + 0.2, soul, k, k * 0.3);
        }
        if (s.fadeAge() >= 0.0) {
            double k = Ctx.clamp(s.fadeAge() / FADE_TICKS);
            for (int i = 0; i < 20; i++) {
                V3 mote = feet.add((Brush.unit(s.seed(), i + 60) - 0.5) * 2.0, HEIGHT * Brush.unit(s.seed(), i + 70)
                        + k * 2.5, (Brush.unit(s.seed(), i + 80) - 0.5) * 2.0);
                b.mote(mote, 0.2, soul.glow(), 1.0 - k);
            }
        }
    }

    /** {@code v} with its part along {@code axis} removed (unit). */
    private static V3 across(V3 v, V3 axis) {
        V3 out = v.sub(axis.scale(v.dot(axis)));
        return out.lengthSqr() < 1.0E-9 ? V3.basis(axis)[0] : out.unit();
    }

    /** The king's swing at a moment: the torso's turn and lean, where the blade points, how hot it glows. */
    private record Swing(double turn, double lean, V3 blade, double hot, double roar, int kind, double phase) {
        /** Where the hands hold the hilt. */
        V3 hands(V3 pelvis, V3 chest, V3 fwd, V3 right) {
            return switch (kind) {
                case 0 -> { // overhead: hands go up over the head, then down before the knees
                    double up = phase < 0.4 ? Ctx.smooth(phase / 0.4) : 1.0 - Ctx.easeIn((phase - 0.4) / 0.1);
                    yield chest.add(0.0, -0.3 + 1.1 * Math.max(0.0, up), 0.0).plus(fwd, 0.6 + 0.25 * (1.0 - up));
                }
                case 1 -> chest.add(0.0, -0.35, 0.0).plus(fwd, 0.65).plus(right, 0.25);
                default -> pelvis.add(0.0, 0.38, 0.0).plus(fwd, 0.62).plus(right, 0.18);
            };
        }
    }

    private static Swing swing(State s) {
        double yaw = Math.toRadians(s.yawDegrees());
        V3 f = new V3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        V3 r = new V3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        double t = s.swingAge();
        if (t < 0.0 || t > SWING_TICKS) {
            // resting: the blade held point down before it, a little to the side
            V3 rest = f.scale(0.35).add(0.0, -0.9, 0.0).plus(r, 0.1).unit();
            double roar = s.age() < RISE_TICKS ? Ctx.bump(Ctx.clamp((s.age() - RISE_TICKS * 0.55) / (RISE_TICKS * 0.45)))
                    : 0.0;
            return new Swing(0.0, 0.0, rest, 0.0, roar, 2, 0.0);
        }
        double phase = t / SWING_TICKS;
        double impact = (double) IMPACT_TICK / SWING_TICKS;
        double hot = Math.exp(-Math.abs(t - IMPACT_TICK) / 4.0);
        if (s.swingKind() == 0) {
            // raised overhead (point back), brought down to the ground ahead at the impact
            double angle;
            if (phase < 0.38) {
                angle = 100.0 + 50.0 * Ctx.smooth(phase / 0.38);
            } else if (phase < impact) {
                angle = 150.0 - 175.0 * Ctx.easeIn((phase - 0.38) / (impact - 0.38));
            } else {
                angle = -25.0 + 15.0 * Ctx.smooth((phase - impact) / (1.0 - impact));
            }
            double rad = Math.toRadians(angle);
            V3 blade = f.scale(Math.cos(rad)).add(0.0, Math.sin(rad), 0.0).unit();
            double lean = phase < impact ? 0.0 : 0.35 * (1.0 - Ctx.smooth((phase - impact) / (1.0 - impact)));
            return new Swing(0.0, lean, blade, hot, phase < impact ? 0.6 : 0.2, 0, phase);
        }
        // pulled back to the right, swept across to the left at the waist
        double turn;
        if (phase < 0.38) {
            turn = Math.toRadians(55.0) * Ctx.smooth(phase / 0.38);
        } else if (phase < impact + 0.12) {
            turn = Math.toRadians(55.0 - 140.0 * Ctx.easeIn((phase - 0.38) / (impact + 0.12 - 0.38)));
        } else {
            turn = Math.toRadians(-85.0 + 85.0 * Ctx.smooth((phase - impact - 0.12) / (1.0 - impact - 0.12)));
        }
        V3 fwd = f.rotate(V3.UP, turn).unit(f);
        V3 side = r.rotate(V3.UP, turn).unit(r);
        V3 blade = fwd.scale(0.55).add(side.scale(-0.82)).add(0.0, -0.12, 0.0).unit();
        return new Swing(turn, 0.1, blade, hot, 0.5, 1, phase);
    }
}
