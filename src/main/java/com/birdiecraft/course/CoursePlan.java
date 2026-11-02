package com.birdiecraft.course;

import com.birdiecraft.block.SlopeDir;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic layout of a generated course. Holes are laid out in parallel lanes that
 * alternate direction (zig-zag), each described in hole-local coordinates: u runs from the tee
 * (u = 0) towards the green, v is lateral (positive = clockwise of the course forward axis).
 */
public final class CoursePlan {
    public static final int LANE_WIDTH = 60;
    public static final int LANE_HALF = LANE_WIDTH / 2;

    public enum Surface { TEE_BOX, TEE_MARKER, FAIRWAY, FAIRWAY_DARK, FRINGE, GREEN, CUP, ROUGH, DEEP, BUNKER, WATER, OB, CHASM }

    public record Cell(Surface surface, int h) {
    }

    public record Ellipse(double u, double v, double ru, double rv) {
        double e(double pu, double pv) {
            double a = (pu - u) / ru;
            double b = (pv - v) / rv;
            return a * a + b * b;
        }
    }

    public record Mound(double u, double v, double amp, double sigma) {
    }

    public final CourseTheme theme;
    public final int baseY;
    public final List<HolePlan> holes = new ArrayList<>();
    public final long seed;

    private CoursePlan(CourseTheme theme, int baseY, long seed) {
        this.theme = theme;
        this.baseY = baseY;
        this.seed = seed;
    }

    /**
     * @param origin  block the builder stands on (its Y becomes the base level)
     * @param forward course forward axis (horizontal)
     */
    public static CoursePlan create(CourseTheme theme, long seed, int[] pars, double lengthScale, BlockPos origin, Direction forward) {
        CoursePlan plan = new CoursePlan(theme, origin.getY(), seed);
        Direction right = forward.getClockWise();
        Random rng = new Random(seed);
        double along = 8;
        for (int i = 0; i < pars.length; i++) {
            int dir = (i % 2 == 0) ? 1 : -1;
            HolePlan hp = new HolePlan(plan, i, pars[i], new Random(rng.nextLong()), lengthScale);
            int ax = forward.getStepX() * dir;
            int az = forward.getStepZ() * dir;
            int laneOffset = i * LANE_WIDTH;
            hp.x0 = origin.getX() + (int) Math.round(forward.getStepX() * along) + right.getStepX() * laneOffset;
            hp.z0 = origin.getZ() + (int) Math.round(forward.getStepZ() * along) + right.getStepZ() * laneOffset;
            hp.ax = ax;
            hp.az = az;
            hp.rx = right.getStepX();
            hp.rz = right.getStepZ();
            plan.holes.add(hp);
            along += dir * hp.length;
        }
        return plan;
    }

    /** Total columns the builder will process (for progress reports). */
    public long columnCount() {
        long n = 0;
        for (HolePlan h : holes) n += (long) (h.uMax() - h.uMin() + 1) * LANE_WIDTH;
        return n;
    }

    public static final class HolePlan {
        public final CoursePlan course;
        public final int index;
        public final int par;
        public final int length;
        public final CourseTheme theme;
        final Random rng;
        // world transform
        public int x0, z0, ax, az, rx, rz;
        // layout
        final double dogleg, dogStart, dogEnd;
        final double fwBase, fwPhase;
        final double greenU, greenV, greenRu, greenRv;
        public final int cupU, cupV;
        public final int teeH;
        public final int greenH;
        final List<Ellipse> bunkers = new ArrayList<>();
        final List<Ellipse> waters = new ArrayList<>();
        final double chasmStart, chasmEnd;
        final double tiltU, tiltV;
        final List<Mound> mounds = new ArrayList<>();
        final double ph1, ph2, ph3;
        public final long decorSeed;

        HolePlan(CoursePlan course, int index, int par, Random rng, double lengthScale) {
            this.course = course;
            this.index = index;
            this.par = par;
            this.theme = course.theme;
            this.rng = rng;
            int base = switch (par) {
                case 3 -> 72 + rng.nextInt(28);
                case 5 -> 236 + rng.nextInt(34);
                default -> 160 + rng.nextInt(44);
            };
            this.length = (int) Math.round(base * lengthScale);
            this.ph1 = rng.nextDouble() * Math.PI * 2;
            this.ph2 = rng.nextDouble() * Math.PI * 2;
            this.ph3 = rng.nextDouble() * Math.PI * 2;
            this.decorSeed = rng.nextLong();

            if (par == 3) {
                this.dogleg = (rng.nextDouble() - 0.5) * 3.0;
            } else if (rng.nextDouble() < 0.7) {
                double mag = (3.0 + rng.nextDouble() * 5.0) * theme.doglegScale;
                this.dogleg = rng.nextBoolean() ? mag : -mag;
            } else {
                this.dogleg = (rng.nextDouble() - 0.5) * 2.0;
            }
            this.dogStart = length * (0.35 + rng.nextDouble() * 0.1);
            this.dogEnd = length * (0.68 + rng.nextDouble() * 0.1);
            this.fwBase = 7.2 + rng.nextDouble() * 1.5;
            this.fwPhase = rng.nextDouble() * Math.PI * 2;

            this.greenU = length;
            this.greenV = center(length);
            this.greenRu = (6.0 + rng.nextDouble() * 1.8) * theme.greenScale;
            this.greenRv = (6.8 + rng.nextDouble() * 2.2) * theme.greenScale;
            this.cupU = (int) Math.round(greenU + (rng.nextDouble() - 0.5) * 0.9 * greenRu);
            this.cupV = (int) Math.round(greenV + (rng.nextDouble() - 0.5) * 0.9 * greenRv);

            // chasm (canyon courses)
            double cs = -1;
            double ce = -1;
            if (theme.elevation == CourseTheme.Elevation.CANYON && rng.nextDouble() < (par == 3 ? 0.6 : 0.75)) {
                double w = 7 + rng.nextDouble() * 5;
                double start = length * (par == 3 ? 0.3 + rng.nextDouble() * 0.2 : 0.28 + rng.nextDouble() * 0.3);
                if (start > 14 && start + w < length - greenRu - 7) {
                    cs = start;
                    ce = start + w;
                }
            }
            this.chasmStart = cs;
            this.chasmEnd = ce;

            // water
            if (theme.elevation != CourseTheme.Elevation.CANYON && rng.nextDouble() < theme.waterChance) {
                planWater();
            }
            planBunkers();

            // green slopes
            double tiltAng = Math.PI + (rng.nextDouble() - 0.5) * 2.1; // roughly towards the front (tee side)
            double tiltMag = 0.35 + rng.nextDouble() * 0.55;
            this.tiltU = Math.cos(tiltAng) * tiltMag;
            this.tiltV = Math.sin(tiltAng) * tiltMag;
            int moundCount = 1 + rng.nextInt(2);
            for (int i = 0; i < moundCount; i++) {
                mounds.add(new Mound(greenU + (rng.nextDouble() - 0.5) * greenRu * 1.4,
                        greenV + (rng.nextDouble() - 0.5) * greenRv * 1.4,
                        0.6 + rng.nextDouble() * 0.8, 2.5 + rng.nextDouble() * 2.0));
            }

            int teeBase = rawHeight(0, 0);
            this.teeH = teeBase + (theme.elevation == CourseTheme.Elevation.CANYON ? 3 : 1);
            this.greenH = rawHeight(greenU, greenV);
        }

        private void planWater() {
            int kind = par == 3 ? (rng.nextDouble() < 0.7 ? 0 : 1) : (rng.nextDouble() < 0.5 ? 1 : 2);
            Ellipse e;
            switch (kind) {
                case 0 -> { // pond in front of the green on a par 3
                    double u = length * (0.38 + rng.nextDouble() * 0.22);
                    e = new Ellipse(u, center(u) + (rng.nextDouble() - 0.5) * 6, 5 + rng.nextDouble() * 4, 9 + rng.nextDouble() * 6);
                }
                case 1 -> { // side pond
                    double u = length * (0.42 + rng.nextDouble() * 0.38);
                    double side = rng.nextBoolean() ? 1 : -1;
                    e = new Ellipse(u, center(u) + side * (fwHalf(u) + 4 + rng.nextDouble() * 4), 8 + rng.nextDouble() * 6, 4 + rng.nextDouble() * 3);
                }
                default -> { // creek crossing the fairway
                    double u = length * (0.55 + rng.nextDouble() * 0.18);
                    e = new Ellipse(u, center(u), 2.3 + rng.nextDouble() * 1.2, 24);
                }
            }
            // keep clear of tee and green
            double gd = Math.hypot((e.u - greenU) / (greenRu + 4 + e.ru), (e.v - greenV) / (greenRv + 4 + e.rv));
            if (gd < 1.0 || e.u - e.ru < 10) return;
            waters.add(e);
        }

        private void planBunkers() {
            int n = theme.greenBunkersMin + rng.nextInt(Math.max(1, theme.greenBunkersMax - theme.greenBunkersMin + 1));
            for (int i = 0; i < n; i++) {
                double ang = rng.nextDouble() * Math.PI * 2;
                double ru = 1.8 + rng.nextDouble() * 1.4;
                double rv = 2.4 + rng.nextDouble() * 2.0;
                double dist = 1.0 + 2.6 / Math.max(greenRu, greenRv);
                double bu = greenU + Math.cos(ang) * (greenRu * dist + ru * 0.9 + 1.5);
                double bv = greenV + Math.sin(ang) * (greenRv * dist + rv * 0.9 + 1.5);
                addBunker(new Ellipse(bu, bv, ru, rv));
            }
            if (par >= 4) {
                int m = theme.fairwayBunkersMin + rng.nextInt(Math.max(1, theme.fairwayBunkersMax - theme.fairwayBunkersMin + 1));
                for (int i = 0; i < m; i++) {
                    double u = length * (0.48 + rng.nextDouble() * 0.22);
                    double side = rng.nextBoolean() ? 1 : -1;
                    double v = center(u) + side * (fwHalf(u) - 0.5 + rng.nextDouble() * 3.0);
                    addBunker(new Ellipse(u, v, 3.0 + rng.nextDouble() * 2.5, 2.0 + rng.nextDouble() * 1.5));
                }
            }
        }

        private void addBunker(Ellipse b) {
            for (Ellipse w : waters) {
                double d = Math.hypot((b.u - w.u) / (b.ru + w.ru + 3), (b.v - w.v) / (b.rv + w.rv + 3));
                if (d < 1.0) return;
            }
            if (chasmStart >= 0 && b.u + b.ru > chasmStart - 3 && b.u - b.ru < chasmEnd + 3) return;
            if (b.u - b.ru < 8) return;
            bunkers.add(b);
        }

        // ------------------------------------------------------------ geometry

        public double center(double u) {
            if (Math.abs(dogleg) < 1.0e-6) return 0;
            double t = (u - dogStart) / Math.max(1.0, dogEnd - dogStart);
            t = Math.max(0, Math.min(1, t));
            t = t * t * (3 - 2 * t);
            return dogleg * t;
        }

        public double fwHalf(double u) {
            return (fwBase + 1.3 * Math.sin(u / 17.0 + fwPhase)) * theme.fairwayScale;
        }

        double fairwayStart() {
            return par == 3 ? length * 0.62 : length * 0.16;
        }

        private int rawHeight(double u, double v) {
            if (theme.elevation != CourseTheme.Elevation.ROLLING) return 0;
            for (Ellipse w : waters) {
                if (w.e(u, v) <= 2.6) return 0;
            }
            double n = Math.sin(u / 21.0 + ph1) * Math.cos(v / 15.0 + ph2) + 0.5 * Math.sin(u / 9.0 + ph3);
            return Math.max(0, Math.min(2, (int) Math.round(n * 0.9 + 0.6)));
        }

        int baseHeight(double u, double v) {
            double eg = ellipseE(u, v, greenU, greenV, greenRu * 1.9, greenRv * 1.9);
            if (eg <= 1.0) return greenH;
            if (Math.abs(u) <= 5 && Math.abs(v) <= 6) return teeH - (theme.elevation == CourseTheme.Elevation.CANYON ? 3 : 1);
            return rawHeight(u, v);
        }

        private static double ellipseE(double u, double v, double cu, double cv, double ru, double rv) {
            double a = (u - cu) / ru;
            double b = (v - cv) / rv;
            return a * a + b * b;
        }

        public int uMin() {
            return -12;
        }

        public int uMax() {
            return (int) Math.ceil(length + greenRu + 18);
        }

        public Cell classify(int u, int v) {
            double pu = u + 0.0;
            double pv = v + 0.0;
            // tee box
            if (Math.abs(u) <= 3 && Math.abs(v) <= 4) {
                return new Cell(u == 0 && v == 0 ? Surface.TEE_MARKER : Surface.TEE_BOX, teeH);
            }
            // green & fringe
            double eg = ellipseE(pu, pv, greenU, greenV, greenRu, greenRv);
            if (eg <= 1.0) {
                return new Cell(u == cupU && v == cupV ? Surface.CUP : Surface.GREEN, greenH);
            }
            double ef = ellipseE(pu, pv, greenU, greenV, greenRu + 1.7, greenRv + 1.7);
            if (ef <= 1.0) return new Cell(Surface.FRINGE, greenH);
            if (u == cupU && v == cupV) return new Cell(Surface.CUP, greenH);
            // hazards
            for (Ellipse w : waters) {
                if (w.e(pu, pv) <= 1.0) return new Cell(Surface.WATER, 0);
            }
            if (chasmStart >= 0 && pu >= chasmStart && pu <= chasmEnd && Math.abs(pv - center(pu)) < LANE_HALF + 1) {
                return new Cell(Surface.CHASM, -14);
            }
            int h = baseHeight(pu, pv);
            for (Ellipse b : bunkers) {
                if (b.e(pu, pv) <= 1.0) return new Cell(Surface.BUNKER, h - 1);
            }
            double dv = Math.abs(pv - center(pu));
            double fw = fwHalf(pu);
            if (pu >= fairwayStart() && pu <= greenU - greenRu * 0.5 && dv <= fw) {
                boolean dark = Math.floorMod(Math.floorDiv(u, 4), 2) == 1;
                return new Cell(dark ? Surface.FAIRWAY_DARK : Surface.FAIRWAY, h);
            }
            if (pu >= -7 && pu <= greenU + greenRu + 8 && dv <= fw + 9) return new Cell(Surface.ROUGH, h);
            if (pu >= -10 && pu <= greenU + greenRu + 13 && dv <= fw + 14) return new Cell(Surface.DEEP, h);
            return new Cell(Surface.OB, h);
        }

        /** Downhill slope of the green at the given local cell, converted to world direction. */
        public SlopeDir slopeAt(int u, int v, int[] steepOut) {
            double su = tiltU;
            double sv = tiltV;
            for (Mound m : mounds) {
                double du = u - m.u();
                double dv = v - m.v();
                double s2 = m.sigma() * m.sigma();
                double g = m.amp() * Math.exp(-(du * du + dv * dv) / s2) * 2.0 / s2;
                su += g * du * 2.2;
                sv += g * dv * 2.2;
            }
            double cupDist = Math.hypot(u - cupU, v - cupV);
            if (cupDist <= 2.0) {
                su *= 0.35;
                sv *= 0.35;
            }
            double mag = Math.hypot(su, sv);
            if (mag < 0.3) {
                steepOut[0] = 1;
                return SlopeDir.NONE;
            }
            steepOut[0] = mag < 0.75 ? 1 : (mag < 1.25 ? 2 : 3);
            double wx = su * ax + sv * rx;
            double wz = su * az + sv * rz;
            return SlopeDir.fromVector(wx, wz);
        }

        public int worldX(int u, int v) {
            return x0 + ax * u + rx * v;
        }

        public int worldZ(int u, int v) {
            return z0 + az * u + rz * v;
        }

        public BlockPos teePos() {
            return new BlockPos(worldX(0, 0), course.baseY + teeH, worldZ(0, 0));
        }

        public BlockPos cupPos() {
            return new BlockPos(worldX(cupU, cupV), course.baseY + greenH, worldZ(cupU, cupV));
        }
    }
}
