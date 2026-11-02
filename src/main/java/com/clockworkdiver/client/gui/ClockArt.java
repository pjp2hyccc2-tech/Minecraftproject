package com.clockworkdiver.client.gui;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.ClockType;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.clock.DefectType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

/**
 * Procedural drawings of the customers' clocks. Two layers: the movement (gears, springs and the faulty
 * parts, normally hidden) and the exterior (case, dial, decorations) that the spectacles see through.
 * Coordinates: centre (cx, cy), {@code s} pixels per normalised unit (the dial radius is about 0.9).
 */
public final class ClockArt {
    private static final float PI = (float) Math.PI;
    private static final float TAU = PI * 2.0f;

    public static final int BRASS = 0xFFD9AE55;
    public static final int BRASS_DARK = 0xFF8C6424;
    public static final int GOLD = 0xFFF0C860;
    public static final int STEEL = 0xFFB4BCC6;
    public static final int BLUED = 0xFF3550A8;
    public static final int RUST = 0xFF8E3D1E;
    public static final int CREAM = 0xFFF3E8CF;
    public static final int INK = 0xFF231A12;

    private ClockArt() {}

    public static float defectX(Defect d, float cx, float s) {
        return cx + d.x * s;
    }

    public static float defectY(Defect d, float cy, float s) {
        return cy + d.y * s;
    }

    // ================================================================== movement

    /** The inside of the clock. Faulty parts glow red; repaired ones are bright brass. */
    public static void movement(@Nullable ClockData clock, ClockType type, float cx, float cy, float s, float time) {
        boolean running = clock == null || clock.allRepaired();
        float tick = running ? (float) (Math.floor(time / 10.0f) + easeTick((time % 10.0f) / 10.0f)) : 0.0f;
        float jitter = running ? 0.0f : (float) Math.sin(time * 0.9f) * 0.012f;

        // Main plate with perlage.
        Gfx.circle(cx, cy, s * 0.95f, 0xFF4A3218);
        Gfx.circleGradient(cx, cy, s * 0.92f, 0xFF7A5528, 0xFF5A3C1C);
        for (int i = 0; i < 18; i++) {
            float a = i * 2.4f;
            float r = s * (0.2f + (i % 6) * 0.12f);
            Gfx.ring(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, s * 0.04f, s * 0.05f, 0x22FFE0A0);
        }

        // Generic train of wheels.
        Gfx.gear(cx, cy, s * 0.26f, s * 0.3f, 40, tick * 0.05f + jitter, BRASS, 5, 0);
        Gfx.gear(cx + s * 0.05f, cy - s * 0.05f, s * 0.08f, s * 0.1f, 10, -tick * 0.2f, STEEL, 0, 0);
        // Balance wheel with hairspring, oscillating.
        float bx = cx - s * 0.05f, by = cy - s * 0.52f;
        float swing = running ? (float) Math.sin(time * PI / 10.0f) * 2.2f : jitter * 10.0f;
        Gfx.ring(bx, by, s * 0.17f, s * 0.2f, GOLD);
        for (int k = 0; k < 3; k++) {
            float a = swing + k * TAU / 3.0f;
            Gfx.line(bx, by, bx + (float) Math.cos(a) * s * 0.18f, by + (float) Math.sin(a) * s * 0.18f, s * 0.025f, GOLD);
        }
        Gfx.spiral(bx, by, s * 0.02f, s * 0.13f, 4.0f, Math.max(0.7f, s * 0.008f), swing * 0.3f, 0xFF9DB4D8);
        // Bridges.
        Gfx.line(cx - s * 0.75f, cy + s * 0.55f, cx + s * 0.1f, cy - s * 0.05f, s * 0.09f, 0xCC6E4C24);
        Gfx.line(cx + s * 0.55f, cy - s * 0.6f, cx - s * 0.25f, cy - s * 0.52f, s * 0.07f, 0xCC6E4C24);

        // Parts at every anchor of this clock kind.
        float[][] anchors = type.anchors;
        for (int i = 0; i < anchors.length; i++) {
            float ax = cx + anchors[i][0] * s, ay = cy + anchors[i][1] * s;
            Defect d = clock == null ? null : defectAt(clock, anchors[i][0], anchors[i][1]);
            float r = s * (0.13f + 0.05f * ((i * 7) % 3));
            int teeth = Math.max(10, (int) (r / s * 120));
            float dir = (i % 2 == 0) ? 1.0f : -1.0f;
            if (d != null && d.type == DefectType.CLUE) {
                // A clue is not a broken part: an ordinary wheel with something odd glinting on it.
                Gfx.gear(ax, ay, r, r * 1.14f, teeth, dir * tick * (0.3f / (r / s * 6.0f)) + i, BRASS, r > s * 0.15f ? 4 : 0, 0);
                float tw = 0.5f + 0.5f * (float) Math.sin(time * 0.2f + i);
                int glint = d.found ? 0xFFC890FF : Gfx.withAlpha(0xD8A8FF, (int) (120 + 120 * tw));
                Gfx.circleGradient(ax, ay, r * 0.8f, Gfx.withAlpha(0xB070FF, (int) (60 + 60 * tw)), 0x00B070FF);
                Gfx.quad(ax, ay - r * 0.55f, ax + r * 0.18f, ay, ax, ay + r * 0.55f, ax - r * 0.18f, ay, glint);
                Gfx.quad(ax - r * 0.55f, ay, ax, ay - r * 0.18f, ax + r * 0.55f, ay, ax, ay + r * 0.18f, glint);
                continue;
            }
            if (d == null || d.repaired) {
                Gfx.gear(ax, ay, r, r * 1.14f, teeth, dir * tick * (0.3f / (r / s * 6.0f)) + i, d != null ? GOLD : BRASS, r > s * 0.15f ? 4 : 0, 0);
                if (d != null) Gfx.ring(ax, ay, r * 1.2f, r * 1.32f, Gfx.withAlpha(0x7CFF9A, 90 + (int) (60 * Math.sin(time * 0.2f))));
            } else {
                drawDefect(d, ax, ay, r, teeth, time, i);
            }
        }
        // Screws holding the bridges.
        for (float[] p : new float[][]{{-0.62f, 0.48f}, {0.48f, -0.58f}, {0.06f, -0.08f}, {-0.3f, -0.6f}}) {
            screw(cx + p[0] * s, cy + p[1] * s, s * 0.045f, 0.6f, BLUED);
        }
    }

    private static float easeTick(float f) {
        if (f >= 0.25f) return 1.0f;
        float t = f / 0.25f;
        return 1.0f + 2.2f * (float) Math.pow(t - 1.0f, 3) + 1.2f * (float) Math.pow(t - 1.0f, 2);
    }

    @Nullable
    private static Defect defectAt(ClockData clock, float x, float y) {
        for (Defect d : clock.defects) {
            if (Math.abs(d.x - x) < 0.02f && Math.abs(d.y - y) < 0.02f) return d;
        }
        return null;
    }

    private static void drawDefect(Defect d, float x, float y, float r, int teeth, float time, int seed) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.25f + seed);
        Gfx.circleGradient(x, y, r * 1.7f, Gfx.withAlpha(0xFF2A1A, (int) (90 + 70 * pulse)), 0x00FF2A1A);
        switch (d.type) {
            case RUST -> {
                Gfx.gear(x, y, r, r * 1.14f, teeth, seed + (float) Math.sin(time) * 0.01f, RUST, 4, 0);
                for (int k = 0; k < 9; k++) {
                    float a = k * 2.3f + seed;
                    float rr = r * (0.3f + (k % 4) * 0.2f);
                    Gfx.circle(x + (float) Math.cos(a) * rr, y + (float) Math.sin(a) * rr, r * (0.12f + (k % 3) * 0.05f),
                            k % 2 == 0 ? 0xFFB5582A : 0xFF6E2A12);
                }
            }
            case TEETH -> {
                Gfx.gear(x, y, r, r * 1.16f, Math.max(12, teeth), seed, STEEL, 0, 3);
                float step = TAU / Math.max(12, teeth);
                for (int i = 0; i < Math.max(12, teeth); i += 3) {
                    float a = seed + i * step;
                    Gfx.circle(x + (float) Math.cos(a) * r * 1.08f, y + (float) Math.sin(a) * r * 1.08f, r * 0.07f,
                            Gfx.withAlpha(0xFF3030, (int) (150 + 100 * pulse)));
                }
            }
            case SCREW -> {
                Gfx.gear(x, y, r * 0.9f, r, teeth, seed, BRASS_DARK, 0, 0);
                float wob = (float) Math.sin(time * 1.3f) * 0.15f;
                screw(x + r * 0.12f, y - r * 0.1f, r * 0.55f, 0.7f + wob, BLUED);
                Gfx.ring(x + r * 0.12f, y - r * 0.1f, r * 0.58f, r * 0.68f, Gfx.withAlpha(0xFF3030, (int) (160 * pulse)));
            }
            case SPRING -> {
                Gfx.ring(x, y, r * 0.95f, r * 1.12f, BRASS_DARK);
                float wob = (float) Math.sin(time * 0.7f) * 0.2f;
                Gfx.spiral(x, y, r * 0.15f, r * 0.92f, 3.2f + wob, Math.max(1.0f, r * 0.06f), seed + wob, 0xFFC9CED6);
                Gfx.spiral(x + r * 0.2f, y + r * 0.1f, r * 0.3f, r * 0.7f, 1.4f, Math.max(1.0f, r * 0.05f), seed * 2.0f, 0xFFE05050);
            }
            case DUST -> {
                Gfx.gear(x, y, r, r * 1.14f, teeth, seed, BRASS_DARK, 4, 0);
                for (int k = 0; k < 11; k++) {
                    float a = k * 2.1f + seed;
                    float rr = r * (0.2f + (k % 5) * 0.2f);
                    float wob = (float) Math.sin(time * 0.05f + k) * r * 0.03f;
                    Gfx.circle(x + (float) Math.cos(a) * rr + wob, y + (float) Math.sin(a) * rr, r * (0.16f + (k % 3) * 0.06f),
                            k % 2 == 0 ? 0xFFB8B4AC : 0xFF8C8880);
                }
                float mx = x + (float) Math.cos(time * 0.08f + seed) * r * 0.9f, my = y + (float) Math.sin(time * 0.11f) * r * 0.6f;
                Gfx.tri(mx, my, mx - r * 0.2f, my - r * 0.12f, mx - r * 0.2f, my + r * 0.12f, 0xFF6A6460);
                Gfx.tri(mx, my, mx + r * 0.2f, my - r * 0.12f, mx + r * 0.2f, my + r * 0.12f, 0xFF6A6460);
            }
            case TUNING -> {
                Gfx.gear(x, y, r, r * 1.14f, teeth, seed, BRASS, 0, 0);
                float ring = (float) Math.sin(time * 0.6f + seed) * r * 0.08f;
                Gfx.quad(x - r * 0.3f + ring, y - r * 0.35f, x + r * 0.3f + ring, y - r * 0.35f, x + r * 0.5f, y + r * 0.35f, x - r * 0.5f, y + r * 0.35f, GOLD);
                Gfx.circle(x + ring * 0.5f, y + r * 0.45f, r * 0.1f, BRASS_DARK);
                for (int k = 1; k <= 2; k++) {
                    Gfx.arc(x, y, r * (0.65f + k * 0.18f), r * (0.7f + k * 0.18f), -0.6f, 0.6f, Gfx.withAlpha(0xFF3030, (int) (200 * pulse)));
                    Gfx.arc(x, y, r * (0.65f + k * 0.18f), r * (0.7f + k * 0.18f), PI - 0.6f, PI + 0.6f, Gfx.withAlpha(0xFF3030, (int) (200 * pulse)));
                }
            }
            case CLUE -> {
            }
            case NEST -> {
                Gfx.circleGradient(x, y, r * 1.1f, 0xFF120818, 0xFF3A1650);
                for (int k = 0; k < 6; k++) {
                    float a = time * 0.05f + k * TAU / 6.0f;
                    Gfx.line(x, y, x + (float) Math.cos(a) * r * 1.2f, y + (float) Math.sin(a) * r * 1.2f, r * 0.08f, 0xAA5A2A7A);
                }
                float blink = (time % 60.0f) < 3.0f ? 0.2f : 1.0f;
                Gfx.circle(x - r * 0.3f, y - r * 0.1f, r * 0.13f * blink, 0xFFFFE040);
                Gfx.circle(x + r * 0.3f, y - r * 0.1f, r * 0.13f * blink, 0xFFFFE040);
            }
        }
    }

    public static void screw(float x, float y, float r, float slot, int color) {
        Gfx.circle(x, y, r * 1.08f, 0xFF1A1A2A);
        Gfx.circleGradient(x, y, r, Gfx.shade(color, 1.3f), color);
        float c = (float) Math.cos(slot), sn = (float) Math.sin(slot);
        Gfx.line(x - c * r * 0.8f, y - sn * r * 0.8f, x + c * r * 0.8f, y + sn * r * 0.8f, Math.max(0.8f, r * 0.25f), 0xFF0E0E18);
    }

    // ================================================================== exterior

    /** Case, dial and decorations, drawn with the current lens mask. */
    public static void exterior(ClockType type, float cx, float cy, float s, float time, boolean running) {
        switch (type) {
            case POCKET_WATCH -> pocketWatch(cx, cy, s, time, running);
            case CUCKOO -> cuckoo(cx, cy, s, time, running);
            case ASTRONOMICAL -> astronomical(cx, cy, s, time, running);
            case BIRDCAGE -> birdcage(cx, cy, s, time, running);
            case STEAM_TOWER -> steamTower(cx, cy, s, time, running);
            case ORRERY -> orrery(cx, cy, s, time, running);
            case MUSIC_BOX -> musicBox(cx, cy, s, time, running);
            case GRANDFATHER -> grandfather(cx, cy, s, time, running);
            case GREAT_CLOCK -> greatClock(cx, cy, s, time, running);
        }
    }

    private static void musicBox(float cx, float cy, float s, float time, boolean running) {
        // Open lid behind, with a mirror.
        Gfx.quad(cx - s * 0.92f, cy - s * 0.22f, cx + s * 0.92f, cy - s * 0.22f, cx + s * 0.8f, cy - s * 0.95f, cx - s * 0.8f, cy - s * 0.95f, 0xFF5A2E18);
        Gfx.quad(cx - s * 0.8f, cy - s * 0.28f, cx + s * 0.8f, cy - s * 0.28f, cx + s * 0.7f, cy - s * 0.88f, cx - s * 0.7f, cy - s * 0.88f, 0xFF9AB4C8);
        Gfx.quad(cx - s * 0.5f, cy - s * 0.3f, cx - s * 0.3f, cy - s * 0.3f, cx - s * 0.05f, cy - s * 0.86f, cx - s * 0.25f, cy - s * 0.86f, 0x60FFFFFF);
        // The box.
        Gfx.rectV(cx - s * 0.98f, cy - s * 0.25f, cx + s * 0.98f, cy + s * 0.88f, 0xFF8A4A26, 0xFF5A2A14);
        Gfx.rect(cx - s * 0.98f, cy - s * 0.25f, cx + s * 0.98f, cy - s * 0.17f, 0xFFB86A36);
        Gfx.rect(cx - s * 0.98f, cy + s * 0.8f, cx + s * 0.98f, cy + s * 0.88f, 0xFF3A1A0A);
        for (float[] c : new float[][]{{-0.9f, -0.17f}, {0.9f, -0.17f}, {-0.9f, 0.8f}, {0.9f, 0.8f}}) {
            Gfx.circle(cx + c[0] * s, cy + c[1] * s, s * 0.07f, GOLD);
        }
        for (int i = 0; i < 5; i++) {
            float x = cx - s * 0.75f + i * s * 0.375f;
            Gfx.circle(x, cy + s * 0.7f, s * 0.035f, 0xFFE8C46A);
        }
        // Winding key.
        float kx = cx + s * 1.02f, ky = cy + s * 0.3f, ka = running ? time * 0.05f : 0.4f;
        Gfx.rect(cx + s * 0.98f, ky - s * 0.03f, kx + s * 0.06f, ky + s * 0.03f, GOLD);
        Gfx.line(kx + s * 0.06f, ky - (float) Math.cos(ka) * s * 0.14f, kx + s * 0.06f, ky + (float) Math.cos(ka) * s * 0.14f, s * 0.07f, GOLD);
        // Turntable and dancer.
        float dx = cx, dy = cy - s * 0.32f;
        Gfx.circleGradient(dx, dy, s * 0.22f, 0xFFF0D080, 0xFFB08030);
        float spin = running ? (float) Math.cos(time * 0.15f) : 0.8f;
        float skirt = s * 0.17f * (0.35f + 0.65f * Math.abs(spin));
        Gfx.tri(dx, dy - s * 0.42f, dx - skirt, dy - s * 0.08f, dx + skirt, dy - s * 0.08f, 0xFFF4B8C8);
        Gfx.line(dx, dy - s * 0.42f, dx, dy - s * 0.6f, s * 0.035f, 0xFFF4D8C0);
        Gfx.circle(dx, dy - s * 0.66f, s * 0.06f, 0xFFF4D8C0);
        Gfx.line(dx - s * 0.12f * spin, dy - s * 0.66f, dx + s * 0.12f * spin, dy - s * 0.74f, s * 0.02f, 0xFFF4D8C0);
        Gfx.line(dx, dy - s * 0.08f, dx, dy, s * 0.03f, 0xFFF4D8C0);
        // Dial on the front.
        Gfx.metalRing(cx, cy + s * 0.3f, s * 0.36f, s * 0.42f, GOLD);
        dial(cx, cy + s * 0.3f, s * 0.36f, CREAM, INK, time, running, false);
        if (running) {
            for (int i = 0; i < 3; i++) {
                float t = ((time * 0.015f) + i / 3.0f) % 1.0f;
                float nx = cx + s * (0.55f + t * 0.4f), ny = cy - s * (0.3f + t * 0.6f);
                Gfx.circle(nx, ny, s * 0.035f, Gfx.withAlpha(0x3A2410, (int) (220 * (1.0f - t))));
                Gfx.line(nx + s * 0.03f, ny, nx + s * 0.03f, ny - s * 0.12f, s * 0.012f, Gfx.withAlpha(0x3A2410, (int) (220 * (1.0f - t))));
            }
        }
    }

    private static void grandfather(float cx, float cy, float s, float time, boolean running) {
        int wood = 0xFF5A2E16, woodLight = 0xFF7A4224, woodDark = 0xFF3A1A0A;
        // Trunk with a glass door, pendulum and weights.
        Gfx.rectV(cx - s * 0.42f, cy - s * 0.15f, cx + s * 0.42f, cy + s * 1.2f, woodLight, wood);
        Gfx.rect(cx - s * 0.5f, cy + s * 1.12f, cx + s * 0.5f, cy + s * 1.25f, woodDark);
        Gfx.rect(cx - s * 0.3f, cy, cx + s * 0.3f, cy + s * 1.02f, 0xFF20140C);
        float swing = running ? (float) Math.sin(time * PI / 20.0f) * 0.22f : 0.02f;
        float px = cx, py = cy + s * 0.02f;
        float bobX = px + (float) Math.sin(swing) * s * 0.78f, bobY = py + (float) Math.cos(swing) * s * 0.78f;
        Gfx.line(px, py, bobX, bobY, Math.max(1.0f, s * 0.02f), 0xFFC8A040);
        Gfx.circleGradient(bobX, bobY, s * 0.11f, 0xFFFFF0A0, 0xFFB08020);
        for (int side : new int[]{-1, 1}) {
            float wx = cx + side * s * 0.18f, wy = cy + s * (side < 0 ? 0.55f : 0.4f);
            Gfx.line(wx, cy, wx, wy, Math.max(0.6f, s * 0.008f), 0xFF9A9AA0);
            Gfx.rectV(wx - s * 0.045f, wy, wx + s * 0.045f, wy + s * 0.22f, 0xFFE8C46A, 0xFF9A7020);
        }
        Gfx.rect(cx - s * 0.3f, cy, cx + s * 0.3f, cy + s * 1.02f, 0x2088C8FF);
        Gfx.line(cx - s * 0.2f, cy + s * 0.1f, cx - s * 0.05f, cy + s * 0.5f, Math.max(0.6f, s * 0.01f), 0x40FFFFFF);
        // Hood with an arched top and finials.
        Gfx.rectV(cx - s * 0.56f, cy - s * 1.08f, cx + s * 0.56f, cy - s * 0.12f, woodLight, wood);
        Gfx.disc(cx, cy - s * 1.08f, s * 0.56f, woodLight);
        Gfx.rect(cx - s * 0.6f, cy - s * 0.18f, cx + s * 0.6f, cy - s * 0.1f, woodDark);
        for (int side : new int[]{-1, 0, 1}) {
            float fx = cx + side * s * 0.45f, fy = cy - s * (side == 0 ? 1.68f : 1.22f);
            Gfx.circle(fx, fy, s * 0.06f, GOLD);
            Gfx.rect(fx - s * 0.02f, fy, fx + s * 0.02f, fy + s * 0.1f, GOLD);
        }
        for (int side : new int[]{-1, 1}) Gfx.rect(cx + side * s * 0.5f - s * 0.03f, cy - s * 1.0f, cx + side * s * 0.5f + s * 0.03f, cy - s * 0.2f, GOLD);
        // Moon phase arch above the dial.
        Gfx.arc(cx, cy - s * 1.0f, s * 0.2f, s * 0.36f, PI, TAU, 0xFF1B2A55);
        Gfx.circle(cx + (float) Math.cos(PI + (running ? time * 0.002f : 1.2f) % PI) * s * 0.28f,
                cy - s * 1.0f + (float) Math.sin(PI + (running ? time * 0.002f : 1.2f) % PI) * s * 0.28f, s * 0.06f, 0xFFF0F0E0);
        Gfx.metalRing(cx, cy - s * 0.6f, s * 0.4f, s * 0.46f, BRASS);
        dial(cx, cy - s * 0.6f, s * 0.4f, CREAM, INK, time, running, true);
    }

    private static void greatClock(float cx, float cy, float s, float time, boolean running) {
        // Stone surround of the tower face.
        Gfx.rectFine(cx - s * 1.05f, cy - s * 1.05f, cx + s * 1.05f, cy + s * 1.05f, 0xFF4A4648, Math.max(4.0f, s * 0.1f));
        Gfx.rectFine(cx - s * 1.0f, cy - s * 1.0f, cx + s * 1.0f, cy + s * 1.0f, 0xFF6A6466, Math.max(4.0f, s * 0.1f));
        for (int i = 0; i < 8; i++) {
            float y = cy - s * 0.95f + i * s * 0.27f;
            Gfx.rect(cx - s * 1.0f, y, cx + s * 1.0f, y + s * 0.012f, 0x30000000);
        }
        for (float[] c : new float[][]{{-0.86f, -0.86f}, {0.86f, -0.86f}, {-0.86f, 0.86f}, {0.86f, 0.86f}}) {
            Gfx.gear(cx + c[0] * s, cy + c[1] * s, s * 0.09f, s * 0.12f, 10, time * 0.01f * (c[0] * c[1] > 0 ? 1 : -1), GOLD, 0, 0);
        }
        if (!running) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.08f);
            Gfx.circleGradient(cx, cy, s * 1.1f, Gfx.withAlpha(0x8A30C0, (int) (60 + 50 * pulse)), 0x008A30C0);
        }
        Gfx.metalRing(cx, cy, s * 0.88f, s * 0.97f, GOLD);
        Gfx.ring(cx, cy, s * 0.84f, s * 0.88f, 0xFF2A2420);
        Gfx.disc(cx, cy, s * 0.84f, 0xFFF0E6CE);
        Gfx.ring(cx, cy, s * 0.58f, s * 0.6f, 0xFF2A2420);
        for (int i = 0; i < 60; i++) {
            float a = i * TAU / 60.0f - PI / 2.0f;
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            float r0 = i % 5 == 0 ? s * 0.62f : s * 0.66f;
            Gfx.line(cx + c * r0, cy + sn * r0, cx + c * s * 0.69f, cy + sn * s * 0.69f, i % 5 == 0 ? Math.max(1.2f, s * 0.02f) : Math.max(0.6f, s * 0.008f), 0xFF2A2420);
        }
        // Iron filigree at the centre.
        for (int k = 0; k < 8; k++) {
            float a = k * TAU / 8.0f + PI / 8.0f;
            Gfx.line(cx, cy, cx + (float) Math.cos(a) * s * 0.4f, cy + (float) Math.sin(a) * s * 0.4f, Math.max(0.8f, s * 0.012f), 0x902A2420);
            Gfx.circle(cx + (float) Math.cos(a) * s * 0.4f, cy + (float) Math.sin(a) * s * 0.4f, s * 0.03f, 0xA02A2420);
        }
        Gfx.ring(cx, cy, s * 0.22f, s * 0.24f, 0xA02A2420);
        hands(cx, cy, s * 0.72f, 0xFF1E1A18, time, running);
    }

    private static void dial(float cx, float cy, float r, int face, int ink, float time, boolean running, boolean minuteTrack) {
        Gfx.disc(cx, cy, r, face);
        Gfx.ring(cx, cy, r * 0.9f, r * 0.92f, Gfx.alpha(ink, 0.5f));
        if (minuteTrack) {
            for (int i = 0; i < 60; i++) {
                float a = i * TAU / 60.0f - PI / 2.0f;
                float r0 = i % 5 == 0 ? r * 0.8f : r * 0.86f;
                float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                Gfx.line(cx + c * r0, cy + sn * r0, cx + c * r * 0.9f, cy + sn * r * 0.9f, i % 5 == 0 ? Math.max(1.0f, r * 0.025f) : Math.max(0.6f, r * 0.01f), ink);
            }
        }
        hands(cx, cy, r, ink, time, running);
    }

    public static void hands(float cx, float cy, float r, int ink, float time, boolean running) {
        float sec, min, hour;
        if (running) {
            float seconds = time / 20.0f + 37200.0f;
            sec = (float) Math.floor(seconds % 60.0f) / 60.0f;
            min = (seconds / 60.0f % 60.0f) / 60.0f;
            hour = (seconds / 3600.0f % 12.0f) / 12.0f;
        } else {
            hour = 10.1f / 12.0f;
            min = 8.0f / 60.0f;
            sec = 0.55f + (float) Math.sin(time * 1.7f) * 0.004f;
        }
        float ah = hour * TAU - PI / 2.0f, am = min * TAU - PI / 2.0f, as = sec * TAU - PI / 2.0f;
        Gfx.hand(cx, cy, cx + (float) Math.cos(ah) * r * 0.5f, cy + (float) Math.sin(ah) * r * 0.5f, Math.max(1.5f, r * 0.07f), ink);
        Gfx.hand(cx, cy, cx + (float) Math.cos(am) * r * 0.74f, cy + (float) Math.sin(am) * r * 0.74f, Math.max(1.2f, r * 0.05f), ink);
        Gfx.line(cx - (float) Math.cos(as) * r * 0.15f, cy - (float) Math.sin(as) * r * 0.15f, cx + (float) Math.cos(as) * r * 0.8f,
                cy + (float) Math.sin(as) * r * 0.8f, Math.max(0.6f, r * 0.015f), 0xFFB02020);
        Gfx.circle(cx, cy, Math.max(1.5f, r * 0.05f), GOLD);
    }

    private static void pocketWatch(float cx, float cy, float s, float time, boolean running) {
        Gfx.metalRing(cx, cy - s * 1.2f, s * 0.14f, s * 0.23f, GOLD);
        Gfx.rectV(cx - s * 0.1f, cy - s * 1.1f, cx + s * 0.1f, cy - s * 0.95f, 0xFFF6D98A, 0xFFAA7E2E);
        for (int i = -3; i <= 3; i++) Gfx.line(cx + i * s * 0.025f, cy - s * 1.1f, cx + i * s * 0.025f, cy - s * 0.95f, Math.max(0.5f, s * 0.008f), 0xFF7A5420);
        Gfx.metalRing(cx, cy, s * 0.9f, s * 1.02f, GOLD);
        Gfx.ring(cx, cy, s * 0.87f, s * 0.9f, 0xFF8E6420);
        dial(cx, cy, s * 0.87f, CREAM, INK, time, running, true);
        // Small seconds.
        float sx = cx, sy = cy + s * 0.42f;
        Gfx.ring(sx, sy, s * 0.13f, s * 0.145f, Gfx.alpha(INK, 0.7f));
        float a = (running ? (float) Math.floor(time / 20.0f % 60.0f) / 60.0f : 0.3f) * TAU - PI / 2.0f;
        Gfx.line(sx, sy, sx + (float) Math.cos(a) * s * 0.11f, sy + (float) Math.sin(a) * s * 0.11f, Math.max(0.6f, s * 0.012f), INK);
    }

    private static void cuckoo(float cx, float cy, float s, float time, boolean running) {
        float swing = running ? (float) Math.sin(time * PI / 18.0f) * 0.35f : 0.05f;
        float px = cx, py = cy + s * 0.8f;
        float bobX = px + (float) Math.sin(swing) * s * 0.55f, bobY = py + (float) Math.cos(swing) * s * 0.55f;
        Gfx.line(px, py, bobX, bobY, Math.max(1.0f, s * 0.025f), 0xFF5A3A16);
        Gfx.polygon(bobX, bobY, s * 0.1f, 6, 0.0f, 0xFF3E7A2A);
        Gfx.circle(bobX, bobY, s * 0.05f, GOLD);
        for (int side : new int[]{-1, 1}) {
            float wx = cx + side * s * 0.38f;
            float wy = cy + s * (side < 0 ? 1.15f : 1.0f);
            Gfx.line(wx, cy + s * 0.8f, wx, wy, Math.max(0.6f, s * 0.01f), 0xFF8C8C8C);
            Gfx.quad(wx - s * 0.06f, wy, wx + s * 0.06f, wy, wx + s * 0.04f, wy + s * 0.22f, wx - s * 0.04f, wy + s * 0.22f, 0xFF3A2410);
            for (int k = 0; k < 4; k++) Gfx.line(wx - s * 0.05f, wy + k * s * 0.05f + s * 0.03f, wx + s * 0.05f, wy + k * s * 0.05f + s * 0.03f, Math.max(0.6f, s * 0.008f), 0xFF5E3C1C);
        }
        Gfx.rectFine(cx - s * 0.8f, cy - s * 0.62f, cx + s * 0.8f, cy + s * 0.84f, 0xFF6B3F1F, Math.max(4.0f, s * 0.1f));
        Gfx.rectFine(cx - s * 0.72f, cy - s * 0.55f, cx + s * 0.72f, cy + s * 0.76f, 0xFF7E4C26, Math.max(4.0f, s * 0.1f));
        for (int i = 0; i < 8; i++) {
            float lx = cx - s * 0.7f + i * s * 0.2f;
            Gfx.circle(lx, cy + s * 0.8f, s * 0.06f, 0xFF2F5A22);
        }
        Gfx.quad(cx, cy - s * 1.28f, cx + s * 1.05f, cy - s * 0.52f, cx + s * 0.9f, cy - s * 0.45f, cx, cy - s * 1.1f, 0xFF3E2410);
        Gfx.quad(cx, cy - s * 1.28f, cx, cy - s * 1.1f, cx - s * 0.9f, cy - s * 0.45f, cx - s * 1.05f, cy - s * 0.52f, 0xFF3E2410);
        Gfx.tri(cx, cy - s * 1.1f, cx + s * 0.88f, cy - s * 0.5f, cx - s * 0.88f, cy - s * 0.5f, 0xFF6B3F1F);
        for (int i = 0; i < 7; i++) {
            float t = i / 6.0f;
            Gfx.circle(cx - s * 1.0f * t, cy - s * 1.25f + s * 0.75f * t, s * 0.05f, 0xFF3E7A2A);
            Gfx.circle(cx + s * 1.0f * t, cy - s * 1.25f + s * 0.75f * t, s * 0.05f, 0xFF3E7A2A);
        }
        float doorY = cy - s * 0.8f;
        boolean bird = running && (time % 200.0f) < 40.0f;
        Gfx.circle(cx, doorY, s * 0.13f, 0xFF2A1808);
        if (bird) {
            Gfx.circle(cx, doorY + s * 0.02f, s * 0.08f, 0xFFF2C94C);
            Gfx.tri(cx + s * 0.06f, doorY, cx + s * 0.14f, doorY + s * 0.02f, cx + s * 0.06f, doorY + s * 0.04f, 0xFFE07020);
            Gfx.circle(cx + s * 0.02f, doorY - s * 0.02f, s * 0.015f, INK);
        }
        Gfx.metalRing(cx, cy + s * 0.06f, s * 0.46f, s * 0.52f, BRASS);
        dial(cx, cy + s * 0.06f, s * 0.46f, CREAM, INK, time, running, false);
    }

    private static void astronomical(float cx, float cy, float s, float time, boolean running) {
        Gfx.rectFine(cx - s * 1.02f, cy - s * 1.02f, cx + s * 1.02f, cy + s * 1.02f, 0xFF3A2410, Math.max(4.0f, s * 0.1f));
        Gfx.rectFine(cx - s * 0.98f, cy - s * 0.98f, cx + s * 0.98f, cy + s * 0.98f, 0xFF9C6B26, Math.max(4.0f, s * 0.1f));
        for (float[] c : new float[][]{{-0.88f, -0.88f}, {0.88f, -0.88f}, {-0.88f, 0.88f}, {0.88f, 0.88f}}) {
            Gfx.circle(cx + c[0] * s, cy + c[1] * s, s * 0.08f, GOLD);
            Gfx.circle(cx + c[0] * s, cy + c[1] * s, s * 0.04f, 0xFF3550A8);
        }
        Gfx.metalRing(cx, cy, s * 0.9f, s * 0.97f, GOLD);
        Gfx.disc(cx, cy, s * 0.9f, CREAM);
        Gfx.disc(cx, cy, s * 0.8f, 0xFF1B2A55);
        for (int i = 0; i < 46; i++) {
            float a = i * 2.39996f;
            float r = s * 0.78f * (float) Math.sqrt((i * 0.618f) % 1.0f);
            float tw = 0.6f + 0.4f * (float) Math.sin(time * 0.1f + i);
            Gfx.circle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, Math.max(0.6f, s * 0.008f * (1 + i % 3)), Gfx.alpha(0xFFFFF4C0, tw));
        }
        Gfx.ring(cx, cy, s * 0.62f, s * 0.72f, 0xCCB8913A);
        for (int i = 0; i < 12; i++) {
            float a = i * TAU / 12.0f;
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            Gfx.line(cx + c * s * 0.62f, cy + sn * s * 0.62f, cx + c * s * 0.72f, cy + sn * s * 0.72f, Math.max(0.8f, s * 0.012f), 0xFF3A2410);
            float m = a + TAU / 24.0f;
            Gfx.circle(cx + (float) Math.cos(m) * s * 0.67f, cy + (float) Math.sin(m) * s * 0.67f, s * 0.018f, i % 2 == 0 ? 0xFF1B2A55 : 0xFF8A1E1E);
        }
        float sunA = (running ? time * 0.004f : 0.6f) - PI / 2.0f;
        float moonA = (running ? time * 0.0035f + 2.0f : 2.4f) - PI / 2.0f;
        Gfx.line(cx, cy, cx + (float) Math.cos(sunA) * s * 0.7f, cy + (float) Math.sin(sunA) * s * 0.7f, Math.max(1.0f, s * 0.03f), GOLD);
        Gfx.circleGradient(cx + (float) Math.cos(sunA) * s * 0.7f, cy + (float) Math.sin(sunA) * s * 0.7f, s * 0.08f, 0xFFFFF0A0, 0xFFE09020);
        Gfx.line(cx, cy, cx + (float) Math.cos(moonA) * s * 0.55f, cy + (float) Math.sin(moonA) * s * 0.55f, Math.max(0.8f, s * 0.02f), STEEL);
        float mx = cx + (float) Math.cos(moonA) * s * 0.55f, my = cy + (float) Math.sin(moonA) * s * 0.55f;
        Gfx.circle(mx, my, s * 0.06f, 0xFFE8E8F0);
        Gfx.circle(mx + s * 0.025f, my - s * 0.01f, s * 0.05f, 0xFF1B2A55);
        Gfx.circleGradient(cx, cy, s * 0.12f, 0xFF4A9A6A, 0xFF1E4E8A);
        hands(cx, cy, s * 0.5f, GOLD, time, running);
    }

    private static void birdcage(float cx, float cy, float s, float time, boolean running) {
        Gfx.quad(cx - s * 0.78f, cy + s * 0.72f, cx + s * 0.78f, cy + s * 0.72f, cx + s * 0.92f, cy + s * 1.02f, cx - s * 0.92f, cy + s * 1.02f, 0xFF6E4A1C);
        Gfx.rectV(cx - s * 0.85f, cy + s * 0.68f, cx + s * 0.85f, cy + s * 0.78f, 0xFFF0C860, 0xFF9C6B26);
        // Frosted glass dome behind the bars hides the movement.
        for (int i = 0; i < 24; i++) {
            float t0 = i / 24.0f, t1 = (i + 1) / 24.0f;
            float y0 = cy + s * 0.7f - t0 * s * 1.62f, y1 = cy + s * 0.7f - t1 * s * 1.62f;
            float w0 = s * 0.78f * (float) Math.sqrt(1.0f - t0 * t0);
            Gfx.rectFine(cx - w0, y1, cx + w0, y0, 0xF0D6E0E6, Math.max(4.0f, s * 0.09f));
        }
        for (int i = -6; i <= 6; i++) {
            float bx = i / 6.0f * 0.78f;
            float px = 0, py = 0;
            for (int k = 0; k <= 16; k++) {
                float t = k / 16.0f;
                float x = cx + bx * s * (float) Math.sqrt(Math.max(0.0f, 1.0f - t * t));
                float y = cy + s * 0.7f - t * s * 1.62f;
                if (k > 0) Gfx.line(px, py, x, y, Math.max(0.8f, s * 0.018f), GOLD);
                px = x;
                py = y;
            }
        }
        for (float t : new float[]{0.25f, 0.55f, 0.8f}) {
            float y = cy + s * 0.7f - t * s * 1.62f;
            float w = s * 0.78f * (float) Math.sqrt(1.0f - t * t);
            Gfx.line(cx - w, y, cx + w, y, Math.max(0.8f, s * 0.02f), GOLD);
        }
        Gfx.metalRing(cx, cy - s * 1.02f, s * 0.06f, s * 0.11f, GOLD);
        // The clockwork bird.
        float bob = running ? (float) Math.sin(time * 0.3f) * s * 0.02f : 0.0f;
        Gfx.line(cx - s * 0.45f, cy + s * 0.18f, cx + s * 0.45f, cy + s * 0.18f, Math.max(1.0f, s * 0.03f), 0xFF7A5420);
        float bx = cx + s * 0.05f, by = cy + s * 0.02f + bob;
        Gfx.polygon(bx, by, s * 0.14f, 14, 0.0f, 0xFF2F8F8A);
        Gfx.tri(bx - s * 0.1f, by, bx - s * 0.34f, by + s * 0.1f, bx - s * 0.1f, by + s * 0.08f, 0xFF246E6A);
        Gfx.quad(bx - s * 0.06f, by - s * 0.02f, bx + s * 0.08f, by - s * 0.04f, bx + s * 0.04f, by + s * 0.08f, bx - s * 0.1f, by + s * 0.06f, GOLD);
        Gfx.circle(bx + s * 0.12f, by - s * 0.12f, s * 0.08f, 0xFF2F8F8A);
        Gfx.tri(bx + s * 0.19f, by - s * 0.14f, bx + s * 0.3f, by - s * 0.1f, bx + s * 0.19f, by - s * 0.08f, 0xFFE0A030);
        Gfx.circle(bx + s * 0.14f, by - s * 0.14f, s * 0.018f, INK);
        Gfx.metalRing(cx, cy + s * 0.87f, s * 0.11f, s * 0.14f, GOLD);
        dial(cx, cy + s * 0.87f, s * 0.11f, CREAM, INK, time, running, false);
    }

    private static void steamTower(float cx, float cy, float s, float time, boolean running) {
        for (int i = 0; i < 4; i++) {
            float t = ((time * 0.02f) + i * 0.25f) % 1.0f;
            Gfx.circle(cx + s * 0.05f + t * s * 0.25f, cy - s * 1.2f - t * s * 0.5f, s * (0.06f + t * 0.12f), Gfx.withAlpha(0xFFFFFF, (int) (150 * (1.0f - t))));
        }
        Gfx.rectV(cx - s * 0.14f, cy - s * 1.25f, cx + s * 0.14f, cy - s * 0.9f, 0xFF55524E, 0xFF2E2C2A);
        for (int side : new int[]{-1, 1}) {
            float x0 = cx + side * s * 0.7f;
            Gfx.rectV(x0 - s * 0.06f, cy - s * 0.8f, x0 + s * 0.06f, cy + s * 0.95f, 0xFFD58A4A, 0xFF8A4A22);
            for (float y : new float[]{-0.5f, 0.1f, 0.6f}) {
                Gfx.circle(x0, cy + y * s, s * 0.08f, 0xFFB46A30);
                Gfx.circle(x0, cy + y * s, s * 0.04f, 0xFF5A2A10);
            }
        }
        Gfx.rectFine(cx - s * 0.62f, cy - s * 0.95f, cx + s * 0.62f, cy + s * 1.0f, 0xFF3C3A3E, Math.max(4.0f, s * 0.1f));
        Gfx.rectFine(cx - s * 0.58f, cy - s * 0.92f, cx + s * 0.58f, cy + s * 0.96f, 0xFF4A474C, Math.max(4.0f, s * 0.1f));
        for (float y : new float[]{-0.92f, 0.08f, 0.96f}) Gfx.rect(cx - s * 0.62f, cy + y * s - s * 0.03f, cx + s * 0.62f, cy + y * s + s * 0.03f, 0xFFB46A30);
        for (int i = 0; i < 10; i++) {
            float y = cy - s * 0.88f + i * s * 0.2f;
            Gfx.circle(cx - s * 0.55f, y, s * 0.02f, 0xFF8E8A90);
            Gfx.circle(cx + s * 0.55f, y, s * 0.02f, 0xFF8E8A90);
        }
        Gfx.metalRing(cx, cy - s * 0.45f, s * 0.42f, s * 0.48f, 0xFFC07A3A);
        dial(cx, cy - s * 0.45f, s * 0.42f, CREAM, INK, time, running, true);
        for (int side : new int[]{-1, 1}) {
            float gx = cx + side * s * 0.28f, gy = cy + s * 0.5f;
            Gfx.metalRing(gx, gy, s * 0.15f, s * 0.18f, STEEL);
            Gfx.disc(gx, gy, s * 0.15f, 0xFFF4F0E4);
            Gfx.arc(gx, gy, s * 0.11f, s * 0.14f, PI * 0.15f, PI * 0.45f, 0xFFCC3030);
            float needle = PI * 0.75f + (running ? 0.6f + (float) Math.sin(time * 0.05f + side) * 0.2f : 1.6f + (float) Math.sin(time * 2.0f) * 0.05f);
            Gfx.line(gx, gy, gx + (float) Math.cos(needle) * s * 0.12f, gy + (float) Math.sin(needle) * s * 0.12f, Math.max(0.6f, s * 0.015f), 0xFFB02020);
        }
        for (int i = -2; i <= 2; i++) Gfx.rect(cx + i * s * 0.08f - s * 0.02f, cy + s * 0.72f, cx + i * s * 0.08f + s * 0.02f, cy + s * 0.9f, 0xFF1E1C20);
    }

    private static void orrery(float cx, float cy, float s, float time, boolean running) {
        Gfx.metalRing(cx, cy, s * 0.95f, s * 1.03f, GOLD);
        Gfx.disc(cx, cy, s * 0.95f, 0xFF3A2416);
        Gfx.ring(cx, cy, s * 0.84f, s * 0.95f, 0xFF5A3A22);
        for (int i = 0; i < 48; i++) {
            float a = i * TAU / 48.0f;
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            Gfx.line(cx + c * s * (i % 4 == 0 ? 0.85f : 0.89f), cy + sn * s * (i % 4 == 0 ? 0.85f : 0.89f), cx + c * s * 0.93f,
                    cy + sn * s * 0.93f, Math.max(0.6f, s * 0.008f), GOLD);
        }
        float[] orbits = {0.24f, 0.4f, 0.56f, 0.72f};
        int[] colors = {0xFF9A9A9A, 0xFFE8D2A0, 0xFF3A8ADA, 0xFFC84A2A};
        float[] speeds = {0.02f, 0.012f, 0.008f, 0.005f};
        for (int i = 0; i < orbits.length; i++) Gfx.ring(cx, cy, s * orbits[i] - 0.5f, s * orbits[i] + 0.5f, 0x88F0C860);
        for (int i = 0; i < orbits.length; i++) {
            float a = (running ? time * speeds[i] : 0.0f) + i * 1.7f;
            float px = cx + (float) Math.cos(a) * s * orbits[i], py = cy + (float) Math.sin(a) * s * orbits[i];
            Gfx.line(cx, cy, px, py, Math.max(0.8f, s * 0.015f), GOLD);
            Gfx.circleGradient(px, py, s * (0.05f + i * 0.012f), Gfx.shade(colors[i], 1.3f), colors[i]);
            if (i == 3) Gfx.ring(px, py, s * 0.1f, s * 0.115f, 0xFFE8D2A0);
        }
        Gfx.circleGradient(cx, cy, s * 0.13f, 0xFFFFF4B0, 0xFFE07A10);
        Gfx.ringGradient(cx, cy, s * 0.13f, s * 0.2f, 0x88FFC040, 0x00FFC040);
    }

    // ================================================================== text

    /** Hour numerals for the clock kinds that show them, faded by the lens mask. */
    public static void numerals(GuiGraphics g, Font font, ClockType type, float cx, float cy, float s) {
        float r;
        float ox = cx, oy = cy;
        boolean roman;
        switch (type) {
            case POCKET_WATCH -> {
                r = s * 0.67f;
                roman = true;
            }
            case CUCKOO -> {
                r = s * 0.34f;
                oy = cy + s * 0.06f;
                roman = false;
            }
            case ASTRONOMICAL -> {
                r = s * 0.85f;
                roman = true;
            }
            case STEAM_TOWER -> {
                r = s * 0.3f;
                oy = cy - s * 0.45f;
                roman = false;
            }
            case MUSIC_BOX -> {
                r = s * 0.27f;
                oy = cy + s * 0.3f;
                roman = false;
            }
            case GRANDFATHER -> {
                r = s * 0.3f;
                oy = cy - s * 0.6f;
                roman = true;
            }
            case GREAT_CLOCK -> {
                r = s * 0.76f;
                roman = true;
            }
            default -> {
                return;
            }
        }
        String[] romanNums = {"XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI"};
        float scale = Math.max(0.5f, Math.min(1.6f, r / 70.0f));
        if (type == ClockType.ASTRONOMICAL || type == ClockType.GRANDFATHER) scale *= 0.75f;
        if (type == ClockType.GREAT_CLOCK) scale *= 1.15f;
        for (int i = 0; i < 12; i++) {
            float a = i * TAU / 12.0f - PI / 2.0f;
            float x = ox + (float) Math.cos(a) * r, y = oy + (float) Math.sin(a) * r;
            float alpha = Gfx.maskAt(x, y);
            if (alpha < 0.15f) continue;
            String text = roman ? romanNums[i] : String.valueOf(i == 0 ? 12 : i);
            int color = Gfx.withAlpha(type == ClockType.ASTRONOMICAL ? 0x3A2410 : 0x231A12, (int) (alpha * 255));
            g.pose().pushPose();
            g.pose().translate(x, y, 0.0f);
            g.pose().scale(scale, scale, 1.0f);
            g.drawString(font, text, -font.width(text) / 2, -4, color, false);
            g.pose().popPose();
        }
    }

    public static String defectIcon(DefectType type) {
        return switch (type) {
            case RUST -> "❂";
            case TEETH -> "⚙";
            case SCREW -> "✚";
            case SPRING -> "∞";
            case NEST -> "☠";
            case DUST -> "☁";
            case TUNING -> "♪";
            case CLUE -> "◆";
        };
    }
}
