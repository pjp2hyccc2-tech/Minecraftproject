package com.birdiecraft.golf.engine;

import java.util.Random;

/**
 * Deterministic golf ball simulation on a voxel {@link Terrain}.
 * Units: blocks and ticks. The ball is treated as a point; positions are its contact point.
 */
public final class BallPhysics {
    public static final double GRAVITY = 0.016;
    public static final double DRAG = 0.0055;
    public static final double LIFT = 0.0080;
    public static final double CURVE = 0.0030;
    /** How strongly the wind pushes the ball (per tick, applied to the wind velocity). */
    public static final double WIND_K = 0.016;
    public static final double SPIN_DECAY = 0.006;
    public static final int SUBSTEPS = 8;
    public static final double CUP_RADIUS = 0.15;
    public static final double CUP_CAPTURE_SPEED = 0.10;
    public static final double DUNK_MAX_HSPEED = 0.35;
    public static final double STOP_SPEED = 0.0025;
    public static final double SLOPE_ACCEL = 0.00055;
    public static final int MAX_TICKS = 900;
    /** The ball floats this far above a surface when resting on it. */
    public static final double REST_OFFSET = 0.0005;

    private BallPhysics() {
    }

    public static Trajectory simulate(Terrain terrain, ShotLaunch launch, Wind wind, int maxTicks) {
        return new Sim(terrain, launch, wind, false).run(maxTicks);
    }

    /** Stops as soon as the ball first touches the ground (for the landing preview). */
    public static Trajectory simulateCarry(Terrain terrain, ShotLaunch launch, Wind wind, int maxTicks) {
        return new Sim(terrain, launch, wind, true).run(maxTicks);
    }

    private static final class Sim {
        private final Terrain t;
        private final boolean stopAtLanding;
        private final double wx, wz;
        private final double sx, sy, sz;
        private final Random rng;

        double x, y, z, vx, vy, vz, back, side, topBounce;
        boolean rolling;
        boolean finished;
        Trajectory.Result result;
        int tick;
        byte ev;
        int firstLand = -1;
        double carry = -1;
        double apex;
        boolean inLeaves;
        boolean touchedTree;
        int lipCooldown;
        double captureSpeed;

        Sim(Terrain t, ShotLaunch l, Wind w, boolean stopAtLanding) {
            this.t = t;
            this.stopAtLanding = stopAtLanding;
            this.wx = w.vx;
            this.wz = w.vz;
            this.sx = l.x;
            this.sy = l.y;
            this.sz = l.z;
            this.x = l.x;
            this.y = l.y;
            this.z = l.z;
            this.vx = l.vx;
            this.vy = l.vy;
            this.vz = l.vz;
            this.back = l.back;
            this.side = l.side;
            this.topBounce = l.topBounce;
            this.rolling = l.rolling;
            this.rng = new Random(l.seed);
        }

        Trajectory run(int maxTicks) {
            int cap = Math.max(1, Math.min(maxTicks, MAX_TICKS));
            float[] xs = new float[cap + 1];
            float[] ys = new float[cap + 1];
            float[] zs = new float[cap + 1];
            byte[] evs = new byte[cap + 1];
            xs[0] = (float) x;
            ys[0] = (float) y;
            zs[0] = (float) z;
            int n = 1;
            double h = 1.0 / SUBSTEPS;
            for (tick = 1; tick <= cap && !finished; tick++) {
                ev = 0;
                for (int s = 0; s < SUBSTEPS && !finished; s++) {
                    if (rolling) stepRoll(h);
                    else stepAir(h);
                    if (lipCooldown > 0) lipCooldown--;
                }
                apex = Math.max(apex, y - sy);
                xs[n] = (float) x;
                ys[n] = (float) y;
                zs[n] = (float) z;
                evs[n] = ev;
                n++;
            }
            if (!finished) {
                result = Trajectory.Result.TIMEOUT;
            }
            Lie restLie = Lie.FAIRWAY;
            if (result == Trajectory.Result.REST || result == Trajectory.Result.TIMEOUT || result == Trajectory.Result.LANDED) {
                restLie = t.lieAt(floor(x), floor(y - 0.02), floor(z));
            } else if (result == Trajectory.Result.IN_CUP) {
                restLie = Lie.GREEN;
            }
            if (result == Trajectory.Result.IN_CUP) {
                // show the ball dropping into the hole for a few frames
                int extra = Math.min(4, xs.length - n);
                for (int i = 0; i < extra; i++) {
                    xs[n] = (float) x;
                    ys[n] = (float) (y - 0.08 * (i + 1));
                    zs[n] = (float) z;
                    evs[n] = 0;
                    n++;
                }
                y = ys[n - 1];
            }
            float[] fx = new float[n];
            float[] fy = new float[n];
            float[] fz = new float[n];
            byte[] fe = new byte[n];
            System.arraycopy(xs, 0, fx, 0, n);
            System.arraycopy(ys, 0, fy, 0, n);
            System.arraycopy(zs, 0, fz, 0, n);
            System.arraycopy(evs, 0, fe, 0, n);
            return new Trajectory(fx, fy, fz, fe, result, x, y, z, restLie, carry, firstLand, apex, touchedTree, captureSpeed);
        }

        private void finish(Trajectory.Result r) {
            finished = true;
            result = r;
        }

        private void stepAir(double h) {
            double hs = Math.sqrt(vx * vx + vz * vz);
            double ax = -DRAG * vx + WIND_K * wx;
            double ay = -GRAVITY - DRAG * vy;
            double az = -DRAG * vz + WIND_K * wz;
            double lift = LIFT * back * hs;
            if (lift > 0.85 * GRAVITY) lift = 0.85 * GRAVITY;
            if (lift < -0.5 * GRAVITY) lift = -0.5 * GRAVITY;
            ay += lift;
            if (hs > 1.0e-6) {
                double rx = -vz / hs;
                double rz = vx / hs;
                double c = CURVE * side * hs;
                ax += rx * c;
                az += rz * c;
            }
            vx += ax * h;
            vy += ay * h;
            vz += az * h;
            double decay = 1.0 - SPIN_DECAY * h;
            back *= decay;
            side *= decay;

            // ---- vertical move
            double ny = y + vy * h;
            if (ny < t.minY() - 4) {
                y = ny;
                finish(Trajectory.Result.OUT_OF_WORLD);
                return;
            }
            int bx = floor(x);
            int bz = floor(z);
            int by = floor(ny);
            Terrain.Box hit = containing(t.boxes(bx, by, bz), x - bx, ny - by, z - bz);
            if (hit != null && vy < 0) {
                if (landOn(bx, by, bz, hit, hs)) return;
            } else if (hit != null) {
                vy = -vy * 0.3;
                y = by + hit.minY() - 1.0e-4;
                ev |= Trajectory.EV_WALL;
            } else {
                y = ny;
            }

            // ---- horizontal moves (x then z)
            double nx = x + vx * h;
            int nbx = floor(nx);
            int cby = floor(y);
            Terrain.Box wx1 = containing(t.boxes(nbx, cby, bz), nx - nbx, y - cby, z - bz);
            if (wx1 != null) {
                wallBounce(nbx, cby, bz, true, nbx > bx || (nbx == bx && vx > 0));
            } else {
                x = nx;
            }
            bx = floor(x);
            double nz = z + vz * h;
            int nbz = floor(nz);
            Terrain.Box wz1 = containing(t.boxes(bx, cby, nbz), x - bx, y - cby, nz - nbz);
            if (wz1 != null) {
                wallBounce(bx, cby, nbz, false, nbz > bz || (nbz == bz && vz > 0));
            } else {
                z = nz;
            }

            // ---- volumes: water and leaves at the new position
            int fx = floor(x);
            int fy = floor(y);
            int fz = floor(z);
            if (t.isWater(fx, fy, fz)) {
                ev |= Trajectory.EV_SPLASH;
                finish(Trajectory.Result.WATER);
                return;
            }
            if (t.isLeaves(fx, fy, fz)) {
                if (!inLeaves) {
                    inLeaves = true;
                    touchedTree = true;
                    ev |= Trajectory.EV_TREE;
                    double k = 0.36;
                    double ang = (rng.nextDouble() - 0.5) * 1.6;
                    double cos = Math.cos(ang);
                    double sin = Math.sin(ang);
                    double nvx = (vx * cos - vz * sin) * k;
                    double nvz = (vx * sin + vz * cos) * k;
                    vx = nvx;
                    vz = nvz;
                    vy = vy > 0 ? vy * 0.25 : vy * 0.5;
                    back *= 0.3;
                    side *= 0.3;
                } else {
                    vx *= 0.93;
                    vz *= 0.93;
                    if (vy > 0) vy *= 0.85;
                }
            } else {
                inLeaves = false;
            }
        }

        /** Ball comes down onto the top of a block. @return true when the simulation finished. */
        private boolean landOn(int bx, int by, int bz, Terrain.Box hit, double hs) {
            double surfaceY = by + hit.maxY();
            double impactV = -vy;
            if (firstLand < 0) {
                firstLand = tick;
                carry = GolfUnits.horizontalDistance(sx, sz, x, z);
                ev |= Trajectory.EV_LAND;
                if (stopAtLanding) {
                    y = surfaceY + REST_OFFSET;
                    finish(Trajectory.Result.LANDED);
                    return true;
                }
            }
            if (t.isCup(bx, by, bz) && hit.maxY() >= 0.99) {
                double cx = bx + 0.5;
                double cz = bz + 0.5;
                double d = GolfUnits.horizontalDistance(x, z, cx, cz);
                if (d < CUP_RADIUS && hs < DUNK_MAX_HSPEED) {
                    captureSpeed = hs;
                    x = cx;
                    z = cz;
                    y = surfaceY - 0.12;
                    ev |= Trajectory.EV_CUP;
                    finish(Trajectory.Result.IN_CUP);
                    return true;
                }
            }
            Lie lie = t.lieAt(bx, by, bz);
            double newVy = impactV * lie.restitution;
            double newHs = hs * (1.0 - lie.bounceFriction);
            double impactScale = Math.min(1.0, impactV / 0.35);
            if (back > 0) {
                newHs -= lie.spinGrip * back * 0.20 * impactScale;
            } else {
                newHs += lie.spinGrip * (-back) * 0.10 * impactScale;
            }
            newHs += topBounce * lie.spinGrip * impactScale;
            if (newHs < -0.12) newHs = -0.12;
            if (hs > 1.0e-6) {
                vx = vx / hs * newHs;
                vz = vz / hs * newHs;
            } else {
                vx = 0;
                vz = 0;
            }
            vy = newVy;
            back *= 0.45;
            side *= 0.40;
            topBounce *= 0.5;
            y = surfaceY + REST_OFFSET;
            if (impactV > 0.05) ev |= Trajectory.EV_BOUNCE;
            if (vy < 0.035) {
                vy = 0;
                rolling = true;
            }
            return false;
        }

        private void wallBounce(int bx, int by, int bz, boolean xAxis, boolean positiveDir) {
            Lie lie = t.lieAt(bx, by, bz);
            double wallRest = Math.max(0.2, Math.min(0.6, lie.restitution + 0.1));
            if (xAxis) vx = (positiveDir ? -Math.abs(vx) : Math.abs(vx)) * wallRest;
            else vz = (positiveDir ? -Math.abs(vz) : Math.abs(vz)) * wallRest;
            if (!rolling) vy *= 0.8;
            back *= 0.5;
            side *= 0.5;
            ev |= Trajectory.EV_WALL;
        }

        private void stepRoll(double h) {
            int bx = floor(x);
            int bz = floor(z);
            int by = floor(y - 0.02);
            Terrain.Box sup = containing(t.boxes(bx, by, bz), x - bx, (y - 0.02) - by, z - bz);
            if (sup == null) {
                // rolled off an edge (or over water): fall
                rolling = false;
                vy = 0;
                stepAir(h);
                return;
            }
            double groundY = by + sup.maxY();
            y = groundY + REST_OFFSET;
            Lie lie = t.lieAt(bx, by, bz);

            double hs = Math.sqrt(vx * vx + vz * vz);
            if (t.isCup(bx, by, bz) && lipCooldown == 0) {
                double cx = bx + 0.5;
                double cz = bz + 0.5;
                double ex = x + vx * h;
                double ez = z + vz * h;
                double d = segmentPointDistance(x, z, ex, ez, cx, cz);
                if (d < CUP_RADIUS) {
                    double rel = d / CUP_RADIUS;
                    double capture = CUP_CAPTURE_SPEED * (1.0 - 0.55 * rel * rel);
                    if (hs < capture) {
                        captureSpeed = hs;
                        x = cx;
                        z = cz;
                        y = groundY - 0.12;
                        ev |= Trajectory.EV_CUP;
                        finish(Trajectory.Result.IN_CUP);
                        return;
                    }
                    // lip out: the rim grabs the ball and swings it around
                    double cross = vx * (cz - z) - vz * (cx - x);
                    double turn = Math.toRadians(28.0) * (1.0 - rel) * (cross >= 0 ? -1 : 1);
                    double cos = Math.cos(turn);
                    double sin = Math.sin(turn);
                    double nvx = (vx * cos - vz * sin) * 0.78;
                    double nvz = (vx * sin + vz * cos) * 0.78;
                    vx = nvx;
                    vz = nvz;
                    lipCooldown = SUBSTEPS * 2;
                    ev |= Trajectory.EV_LIP;
                }
            }

            double slopeAx = 0;
            double slopeAz = 0;
            int sd = t.slopeDir(bx, by, bz);
            if (sd >= 0) {
                int steep = t.slopeSteep(bx, by, bz);
                slopeAx = Terrain.SLOPE_DX[sd] * SLOPE_ACCEL * steep;
                slopeAz = Terrain.SLOPE_DZ[sd] * SLOPE_ACCEL * steep;
            }
            vx += slopeAx * h;
            vz += slopeAz * h;
            hs = Math.sqrt(vx * vx + vz * vz);
            double dec = lie.rollDecel * h;
            if (hs <= dec) {
                vx = 0;
                vz = 0;
                hs = 0;
            } else {
                double k = (hs - dec) / hs;
                vx *= k;
                vz *= k;
                hs -= dec;
            }
            double slopeMag = Math.sqrt(slopeAx * slopeAx + slopeAz * slopeAz);
            if (hs < STOP_SPEED && slopeMag <= lie.rollDecel) {
                vx = 0;
                vz = 0;
                finish(Trajectory.Result.REST);
                return;
            }

            double probeY = y + 0.05;
            int pby = floor(probeY);
            double nx = x + vx * h;
            int nbx = floor(nx);
            Terrain.Box wallX = containing(t.boxes(nbx, pby, bz), nx - nbx, probeY - pby, z - bz);
            if (wallX != null) {
                double top = pby + wallX.maxY();
                if (top - y <= 0.13) {
                    x = nx;
                    y = top + REST_OFFSET;
                } else {
                    vx = -vx * 0.35;
                    ev |= Trajectory.EV_WALL;
                }
            } else {
                x = nx;
            }
            int cbx = floor(x);
            double nz = z + vz * h;
            int nbz = floor(nz);
            Terrain.Box wallZ = containing(t.boxes(cbx, pby, nbz), x - cbx, probeY - pby, nz - nbz);
            if (wallZ != null) {
                double top = pby + wallZ.maxY();
                if (top - y <= 0.13) {
                    z = nz;
                    y = top + REST_OFFSET;
                } else {
                    vz = -vz * 0.35;
                    ev |= Trajectory.EV_WALL;
                }
            } else {
                z = nz;
            }
            if (t.isWater(floor(x), floor(y), floor(z))) {
                ev |= Trajectory.EV_SPLASH;
                finish(Trajectory.Result.WATER);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    static Terrain.Box containing(Terrain.Box[] boxes, double lx, double ly, double lz) {
        for (Terrain.Box b : boxes) {
            if (b.contains(lx, ly, lz)) return b;
        }
        return null;
    }

    static double segmentPointDistance(double ax, double az, double bx, double bz, double px, double pz) {
        double dx = bx - ax;
        double dz = bz - az;
        double len2 = dx * dx + dz * dz;
        double tt = len2 < 1.0e-12 ? 0 : ((px - ax) * dx + (pz - az) * dz) / len2;
        if (tt < 0) tt = 0;
        if (tt > 1) tt = 1;
        double qx = ax + dx * tt - px;
        double qz = az + dz * tt - pz;
        return Math.sqrt(qx * qx + qz * qz);
    }

    /**
     * Finds where to drop a ball after a water hazard: walks the trajectory backwards from the
     * splash and returns the first point above a dry, in-bounds surface. Falls back to the start.
     *
     * @return {x, y, z} of the drop spot (on top of the surface)
     */
    public static double[] findDropSpot(Terrain t, Trajectory traj, java.util.function.Predicate<Lie> inBounds) {
        for (int i = traj.length() - 1; i >= 0; i--) {
            int px = floor(traj.xs[i]);
            int pz = floor(traj.zs[i]);
            int py = floor(traj.ys[i]);
            for (int dy = 0; dy < 40; dy++) {
                int yy = py - dy;
                if (yy < t.minY()) break;
                if (t.isWater(px, yy, pz)) break;
                Terrain.Box[] bs = t.boxes(px, yy, pz);
                if (bs.length > 0) {
                    Lie lie = t.lieAt(px, yy, pz);
                    if (inBounds.test(lie) && !t.isCup(px, yy, pz)) {
                        double top = 0;
                        for (Terrain.Box b : bs) top = Math.max(top, b.maxY());
                        return new double[]{px + 0.5, yy + top + REST_OFFSET, pz + 0.5};
                    }
                    break;
                }
            }
        }
        return new double[]{traj.xs[0], traj.ys[0], traj.zs[0]};
    }
}
