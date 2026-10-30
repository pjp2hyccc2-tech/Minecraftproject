package com.mcbesiege.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Contact generation between convex shapes. All functions append points to a manifold whose normal convention is
 * "from A to B" (B is pushed along +normal, A along -normal).
 */
public final class Narrowphase {
    private Narrowphase() {}

    /** Oriented box (also used for static world boxes, which are axis aligned and carry face exposure flags). */
    static final class OBB {
        V3 c;
        final V3[] u = new V3[3];
        final double[] e = new double[3];
        int exposed = StaticBox.ALL;
        boolean stat;

        boolean exposedFace(int axis, int sign) {
            if (!stat) return true;
            int bit = 1 << (axis * 2 + (sign > 0 ? 1 : 0));
            return (exposed & bit) != 0;
        }

        V3 toLocal(V3 p) {
            V3 d = p.sub(c);
            return new V3(d.dot(u[0]), d.dot(u[1]), d.dot(u[2]));
        }

        V3 dirToLocal(V3 d) { return new V3(d.dot(u[0]), d.dot(u[1]), d.dot(u[2])); }

        V3 toWorld(V3 l) { return c.add(u[0].mul(l.x)).add(u[1].mul(l.y)).add(u[2].mul(l.z)); }

        V3 dirToWorld(V3 l) { return u[0].mul(l.x).add(u[1].mul(l.y)).add(u[2].mul(l.z)); }
    }

    static OBB obb(Collider.Box b) {
        OBB o = new OBB();
        o.c = b.wPos;
        for (int i = 0; i < 3; i++) o.u[i] = b.wR.col(i);
        o.e[0] = b.half.x;
        o.e[1] = b.half.y;
        o.e[2] = b.half.z;
        return o;
    }

    static OBB obb(StaticBox s) {
        OBB o = new OBB();
        o.c = s.center();
        o.u[0] = V3.X;
        o.u[1] = V3.Y;
        o.u[2] = V3.Z;
        V3 h = s.half();
        o.e[0] = h.x;
        o.e[1] = h.y;
        o.e[2] = h.z;
        o.exposed = s.exposed;
        o.stat = true;
        return o;
    }

    static OBB obbOfCylinder(Collider.Cylinder cy) {
        OBB o = new OBB();
        o.c = cy.wPos;
        for (int i = 0; i < 3; i++) o.u[i] = cy.wR.col(i);
        o.e[0] = cy.radius;
        o.e[1] = cy.halfHeight;
        o.e[2] = cy.radius;
        return o;
    }

    // ------------------------------------------------------------------------------------------------------------
    // dispatch

    public static void collide(Collider a, Collider b, double margin, Contact.Manifold out) {
        switch (a.type) {
            case BOX -> {
                switch (b.type) {
                    case BOX -> boxBox(obb((Collider.Box) a), obb((Collider.Box) b), margin, out);
                    case CYLINDER -> cylinderObb((Collider.Cylinder) b, obb((Collider.Box) a), margin, out, false);
                    case SPHERE -> sphereObb((Collider.Sphere) b, obb((Collider.Box) a), margin, out, false);
                }
            }
            case CYLINDER -> {
                switch (b.type) {
                    case BOX -> cylinderObb((Collider.Cylinder) a, obb((Collider.Box) b), margin, out, true);
                    case CYLINDER -> cylinderObb((Collider.Cylinder) a, obbOfCylinder((Collider.Cylinder) b), margin, out, true);
                    case SPHERE -> sphereCylinder((Collider.Sphere) b, (Collider.Cylinder) a, margin, out, false);
                }
            }
            case SPHERE -> {
                switch (b.type) {
                    case BOX -> sphereObb((Collider.Sphere) a, obb((Collider.Box) b), margin, out, true);
                    case CYLINDER -> sphereCylinder((Collider.Sphere) a, (Collider.Cylinder) b, margin, out, true);
                    case SPHERE -> sphereSphere((Collider.Sphere) a, (Collider.Sphere) b, margin, out);
                }
            }
        }
    }

    public static void collideStatic(Collider a, StaticBox sb, double margin, Contact.Manifold out) {
        OBB b = obb(sb);
        switch (a.type) {
            case BOX -> boxBox(obb((Collider.Box) a), b, margin, out);
            case CYLINDER -> cylinderObb((Collider.Cylinder) a, b, margin, out, true);
            case SPHERE -> sphereObb((Collider.Sphere) a, b, margin, out, true);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // box vs box (SAT + face clipping / edge-edge)

    static void boxBox(OBB A, OBB B, double margin, Contact.Manifold out) {
        V3 d = B.c.sub(A.c);
        double[][] ar = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) ar[i][j] = Math.abs(A.u[i].dot(B.u[j])) + 1e-9;
        }
        double[] dA = {d.dot(A.u[0]), d.dot(A.u[1]), d.dot(A.u[2])};
        double[] dB = {d.dot(B.u[0]), d.dot(B.u[1]), d.dot(B.u[2])};

        double sepA = Double.NEGATIVE_INFINITY;
        int idxA = -1;
        for (int i = 0; i < 3; i++) {
            double rb = B.e[0] * ar[i][0] + B.e[1] * ar[i][1] + B.e[2] * ar[i][2];
            double s = Math.abs(dA[i]) - (A.e[i] + rb);
            if (s > margin) return;
            if (s > sepA) {
                sepA = s;
                idxA = i;
            }
        }
        double sepB = Double.NEGATIVE_INFINITY;
        int idxB = -1;
        for (int j = 0; j < 3; j++) {
            double ra = A.e[0] * ar[0][j] + A.e[1] * ar[1][j] + A.e[2] * ar[2][j];
            double s = Math.abs(dB[j]) - (ra + B.e[j]);
            if (s > margin) return;
            // the face of B that looks at A has outward normal -sign(dB) * uB
            int faceSign = dB[j] > 0 ? -1 : 1;
            if (!B.exposedFace(j, faceSign)) continue;
            if (s > sepB) {
                sepB = s;
                idxB = j;
            }
        }
        double sepE = Double.NEGATIVE_INFINITY;
        int ei = -1, ej = -1;
        V3 nE = null;
        if (!B.stat && !A.stat) {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    V3 n = A.u[i].cross(B.u[j]);
                    double len = n.len();
                    if (len < 1e-4) continue;
                    n = n.mul(1.0 / len);
                    double ra = A.e[0] * Math.abs(A.u[0].dot(n)) + A.e[1] * Math.abs(A.u[1].dot(n)) + A.e[2] * Math.abs(A.u[2].dot(n));
                    double rb = B.e[0] * Math.abs(B.u[0].dot(n)) + B.e[1] * Math.abs(B.u[1].dot(n)) + B.e[2] * Math.abs(B.u[2].dot(n));
                    double dn = d.dot(n);
                    double s = Math.abs(dn) - (ra + rb);
                    if (s > margin) return;
                    if (s > sepE) {
                        sepE = s;
                        ei = i;
                        ej = j;
                        nE = dn > 0 ? n : n.neg();
                    }
                }
            }
        }
        boolean useB = idxB >= 0 && sepB > 0.98 * sepA + 0.001;
        double faceSep = useB ? sepB : sepA;
        if (nE != null && sepE > faceSep && sepE > 0.95 * faceSep + 0.01) {
            edgeContact(A, B, ei, ej, nE, sepE, out);
            return;
        }
        if (useB) {
            V3 nRef = B.u[idxB].mul(dB[idxB] > 0 ? -1 : 1); // outward from B toward A
            faceContact(B, A, idxB, nRef, margin, out, false);
        } else {
            V3 nRef = A.u[idxA].mul(dA[idxA] > 0 ? 1 : -1); // outward from A toward B
            faceContact(A, B, idxA, nRef, margin, out, true);
        }
    }

    private static void faceContact(OBB R, OBB I, int ri, V3 nRef, double margin, Contact.Manifold out, boolean refIsA) {
        int k = 0;
        double best = -1;
        for (int m = 0; m < 3; m++) {
            double v = Math.abs(nRef.dot(I.u[m]));
            if (v > best) {
                best = v;
                k = m;
            }
        }
        double sgn = nRef.dot(I.u[k]) > 0 ? -1 : 1;
        V3 fc = I.c.addScaled(I.u[k], sgn * I.e[k]);
        int k1 = (k + 1) % 3, k2 = (k + 2) % 3;
        V3 a1 = I.u[k1].mul(I.e[k1]), a2 = I.u[k2].mul(I.e[k2]);
        List<V3> poly = new ArrayList<>(8);
        poly.add(fc.add(a1).add(a2));
        poly.add(fc.sub(a1).add(a2));
        poly.add(fc.sub(a1).sub(a2));
        poly.add(fc.add(a1).sub(a2));
        int r1 = (ri + 1) % 3, r2 = (ri + 2) % 3;
        double c1 = R.u[r1].dot(R.c), c2 = R.u[r2].dot(R.c);
        poly = clip(poly, R.u[r1], c1 + R.e[r1]);
        poly = clip(poly, R.u[r1].neg(), -c1 + R.e[r1]);
        poly = clip(poly, R.u[r2], c2 + R.e[r2]);
        poly = clip(poly, R.u[r2].neg(), -c2 + R.e[r2]);
        if (poly.isEmpty()) return;
        double planeOff = nRef.dot(R.c) + R.e[ri];
        V3 normal = refIsA ? nRef : nRef.neg();
        List<V3> pts = new ArrayList<>();
        List<Double> depths = new ArrayList<>();
        for (V3 p : poly) {
            double s = nRef.dot(p) - planeOff;
            if (s > margin) continue;
            pts.add(p.addScaled(nRef, -s * 0.5));
            depths.add(-s);
        }
        reduceAndAdd(pts, depths, normal, out);
    }

    private static List<V3> clip(List<V3> in, V3 n, double off) {
        int size = in.size();
        List<V3> res = new ArrayList<>(size + 2);
        if (size == 0) return res;
        for (int i = 0; i < size; i++) {
            V3 a = in.get(i), b = in.get((i + 1) % size);
            double da = n.dot(a) - off, db = n.dot(b) - off;
            if (da <= 0) res.add(a);
            if ((da <= 0) != (db <= 0)) {
                double t = da / (da - db);
                res.add(a.add(b.sub(a).mul(t)));
            }
        }
        return res;
    }

    private static void reduceAndAdd(List<V3> pts, List<Double> depths, V3 normal, Contact.Manifold out) {
        int n = pts.size();
        if (n <= 4) {
            for (int i = 0; i < n; i++) out.add(pts.get(i), normal, depths.get(i));
            return;
        }
        int i0 = 0;
        for (int i = 1; i < n; i++) if (depths.get(i) > depths.get(i0)) i0 = i;
        V3 p0 = pts.get(i0);
        int i1 = -1;
        double bd = -1;
        for (int i = 0; i < n; i++) {
            double dd = pts.get(i).distSq(p0);
            if (dd > bd) {
                bd = dd;
                i1 = i;
            }
        }
        V3 p1 = pts.get(i1);
        V3 e = p1.sub(p0);
        int i2 = -1;
        double ba = -1;
        for (int i = 0; i < n; i++) {
            double a = e.cross(pts.get(i).sub(p0)).dot(normal);
            if (Math.abs(a) > ba) {
                ba = Math.abs(a);
                i2 = i;
            }
        }
        V3 p2 = pts.get(i2);
        double side = Math.signum(e.cross(p2.sub(p0)).dot(normal));
        int i3 = -1;
        double bo = 0;
        for (int i = 0; i < n; i++) {
            double a = -side * e.cross(pts.get(i).sub(p0)).dot(normal);
            if (a > bo) {
                bo = a;
                i3 = i;
            }
        }
        out.add(p0, normal, depths.get(i0));
        out.add(p1, normal, depths.get(i1));
        if (i2 != i0 && i2 != i1) out.add(p2, normal, depths.get(i2));
        if (i3 >= 0 && i3 != i0 && i3 != i1 && i3 != i2) out.add(pts.get(i3), normal, depths.get(i3));
    }

    private static void edgeContact(OBB A, OBB B, int i, int j, V3 n, double sep, Contact.Manifold out) {
        V3 pA = A.c;
        for (int k = 0; k < 3; k++) {
            if (k == i) continue;
            pA = pA.addScaled(A.u[k], (n.dot(A.u[k]) > 0 ? 1 : -1) * A.e[k]);
        }
        V3 pB = B.c;
        for (int k = 0; k < 3; k++) {
            if (k == j) continue;
            pB = pB.addScaled(B.u[k], (n.dot(B.u[k]) > 0 ? -1 : 1) * B.e[k]);
        }
        V3 ua = A.u[i], ub = B.u[j];
        V3 w0 = pA.sub(pB);
        double b = ua.dot(ub), dd = ua.dot(w0), ee = ub.dot(w0);
        double denom = 1 - b * b;
        double t = 0, s = 0;
        if (denom > 1e-9) {
            t = (b * ee - dd) / denom;
            s = (ee - b * dd) / denom;
        }
        t = Math.max(-A.e[i], Math.min(A.e[i], t));
        s = Math.max(-B.e[j], Math.min(B.e[j], s));
        V3 qA = pA.addScaled(ua, t), qB = pB.addScaled(ub, s);
        out.add(qA.add(qB).mul(0.5), n, -sep);
    }

    // ------------------------------------------------------------------------------------------------------------
    // cylinder vs oriented box

    static void cylinderObb(Collider.Cylinder C, OBB B, double margin, Contact.Manifold out, boolean cylIsA) {
        V3 cL = B.toLocal(C.wPos);
        V3 aL = B.dirToLocal(C.worldAxis());
        double r = C.radius, hh = C.halfHeight;
        double[] h = B.e;
        double bestSep = Double.NEGATIVE_INFINITY;
        int bestK = -1, bestSign = 0;
        for (int k = 0; k < 3; k++) {
            double ak = aL.get(k);
            double ext = hh * Math.abs(ak) + r * Math.sqrt(Math.max(0, 1 - ak * ak));
            double ck = cL.get(k);
            double s = Math.abs(ck) - ext - h[k];
            if (s > margin) return;
            int sign = ck >= 0 ? 1 : -1;
            if (!B.exposedFace(k, sign)) continue;
            if (s > bestSep) {
                bestSep = s;
                bestK = k;
                bestSign = sign;
            }
        }
        double rb = h[0] * Math.abs(aL.x) + h[1] * Math.abs(aL.y) + h[2] * Math.abs(aL.z);
        if (Math.abs(cL.dot(aL)) - hh - rb > margin) return;

        double flip = cylIsA ? 1 : -1;
        V3 faceNormalAB = null;
        if (bestK >= 0) {
            V3 nB = V3.axis(bestK).mul(bestSign);
            double axDot = aL.dot(nB);
            List<V3> cand = new ArrayList<>(6);
            if (Math.abs(axDot) < 0.9) {
                V3 dir = nB.neg();
                V3 t = dir.sub(aL.mul(dir.dot(aL)));
                V3 radial = t.norm().mul(r);
                cand.add(cL.add(radial).addScaled(aL, hh * 0.92));
                cand.add(cL.add(radial).addScaled(aL, -hh * 0.92));
                if (Math.abs(axDot) > 0.25) {
                    double s = dir.dot(aL) > 0 ? 1 : -1;
                    cand.add(cL.add(radial).addScaled(aL, hh * s));
                }
            } else {
                double s = axDot > 0 ? -1 : 1;
                V3 cap = cL.addScaled(aL, hh * s);
                V3 u = aL.anyPerp(), v = aL.cross(u);
                for (int i = 0; i < 6; i++) {
                    double th = i * Math.PI / 3;
                    cand.add(cap.add(u.mul(Math.cos(th) * r * 0.95)).add(v.mul(Math.sin(th) * r * 0.95)));
                }
            }
            V3 nW = B.dirToWorld(nB);
            V3 normal = nW.mul(-flip); // A->B
            boolean any = false;
            for (V3 p : cand) {
                double depth = h[bestK] - nB.dot(p);
                if (depth < -margin || depth > 2 * h[bestK] + r) continue;
                boolean inside = true;
                for (int m = 0; m < 3; m++) {
                    if (m == bestK) continue;
                    if (Math.abs(p.get(m)) > h[m] + 0.01) {
                        inside = false;
                        break;
                    }
                }
                if (!inside) continue;
                V3 pw = B.toWorld(p.addScaled(nB, depth * 0.5));
                out.add(pw, normal, depth);
                any = true;
            }
            if (any) faceNormalAB = normal;
        }
        // box edges poking into the cylinder (steps, corners)
        double reach = r + hh + 0.05;
        List<V3> edgePts = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            int m1 = (k + 1) % 3, m2 = (k + 2) % 3;
            for (int s1 = -1; s1 <= 1; s1 += 2) {
                for (int s2 = -1; s2 <= 1; s2 += 2) {
                    if (!B.exposedFace(m1, s1) || !B.exposedFace(m2, s2)) continue;
                    double[] base = new double[3];
                    base[m1] = s1 * h[m1];
                    base[m2] = s2 * h[m2];
                    // closest param on the edge to the cylinder center
                    double t0 = Math.max(-h[k], Math.min(h[k], cL.get(k)));
                    base[k] = t0;
                    V3 bp = new V3(base[0], base[1], base[2]);
                    if (bp.distSq(cL) > reach * reach) continue;
                    double[] offs = {0, 0.33 * r, -0.33 * r, 0.66 * r, -0.66 * r, h[k] - t0, -h[k] - t0};
                    for (double o : offs) {
                        double t = t0 + o;
                        if (t < -h[k] - 1e-9 || t > h[k] + 1e-9) continue;
                        double[] q = base.clone();
                        q[k] = t;
                        edgePts.add(new V3(q[0], q[1], q[2]));
                    }
                }
            }
        }
        List<V3> used = new ArrayList<>();
        for (V3 p : edgePts) {
            boolean dup = false;
            for (V3 u : used) {
                if (u.distSq(p) < 0.0025) {
                    dup = true;
                    break;
                }
            }
            if (dup) continue;
            V3 rel = p.sub(cL);
            double y = rel.dot(aL);
            if (Math.abs(y) > hh) continue;
            V3 q = rel.sub(aL.mul(y));
            double dist = q.len();
            if (dist > r) continue;
            double radialDepth = r - dist, axialDepth = hh - Math.abs(y);
            V3 nL;
            double depth;
            if (radialDepth < axialDepth && dist > 1e-6) {
                nL = q.mul(1.0 / dist);
                depth = radialDepth;
            } else {
                nL = aL.mul(y >= 0 ? 1 : -1);
                depth = axialDepth;
            }
            V3 normal = B.dirToWorld(nL).mul(flip); // cylinder -> point (A->B when cylinder is A)
            if (faceNormalAB != null && normal.dot(faceNormalAB) > 0.9) continue;
            used.add(p);
            out.add(B.toWorld(p), normal, depth);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // spheres

    static void sphereObb(Collider.Sphere S, OBB B, double margin, Contact.Manifold out, boolean sphereIsA) {
        V3 cL = B.toLocal(S.wPos);
        double r = S.radius;
        double[] h = B.e;
        V3 q = new V3(clamp(cL.x, h[0]), clamp(cL.y, h[1]), clamp(cL.z, h[2]));
        V3 diff = cL.sub(q);
        double d2 = diff.lenSq();
        double flip = sphereIsA ? -1 : 1;
        if (d2 > 1e-12) {
            double dist = Math.sqrt(d2);
            if (dist > r + margin) return;
            // reject if all faces the closest point lies on are internal
            boolean anyExposed = false;
            for (int k = 0; k < 3; k++) {
                double v = cL.get(k);
                if (v > h[k] && B.exposedFace(k, 1)) anyExposed = true;
                if (v < -h[k] && B.exposedFace(k, -1)) anyExposed = true;
            }
            if (!anyExposed) return;
            V3 n = diff.mul(1.0 / dist); // box -> sphere
            out.add(B.toWorld(q), B.dirToWorld(n).mul(flip), r - dist);
        } else {
            int bk = -1, bs = 1;
            double bd = Double.MAX_VALUE;
            for (int k = 0; k < 3; k++) {
                int s = cL.get(k) >= 0 ? 1 : -1;
                if (!B.exposedFace(k, s)) continue;
                double dd = h[k] - Math.abs(cL.get(k));
                if (dd < bd) {
                    bd = dd;
                    bk = k;
                    bs = s;
                }
            }
            if (bk < 0) return;
            V3 n = V3.axis(bk).mul(bs);
            double[] c = {cL.x, cL.y, cL.z};
            c[bk] = bs * h[bk];
            out.add(B.toWorld(new V3(c[0], c[1], c[2])), B.dirToWorld(n).mul(flip), r + bd);
        }
    }

    private static double clamp(double v, double h) { return v < -h ? -h : (v > h ? h : v); }

    static void sphereSphere(Collider.Sphere a, Collider.Sphere b, double margin, Contact.Manifold out) {
        V3 d = b.wPos.sub(a.wPos);
        double dist = d.len();
        double rr = a.radius + b.radius;
        if (dist > rr + margin) return;
        V3 n = dist > 1e-9 ? d.mul(1.0 / dist) : V3.Y;
        out.add(a.wPos.addScaled(n, a.radius - (rr - dist) * 0.5), n, rr - dist);
    }

    static void sphereCylinder(Collider.Sphere S, Collider.Cylinder C, double margin, Contact.Manifold out, boolean sphereIsA) {
        V3 a = C.worldAxis();
        V3 rel = S.wPos.sub(C.wPos);
        double y = rel.dot(a);
        V3 radial = rel.sub(a.mul(y));
        double rd = radial.len();
        double flip = sphereIsA ? -1 : 1;
        double hh = C.halfHeight, r = C.radius;
        double cy = Math.max(-hh, Math.min(hh, y));
        V3 closest;
        boolean inside = Math.abs(y) <= hh && rd <= r;
        if (!inside) {
            V3 rv = rd > r ? radial.mul(r / rd) : radial;
            closest = C.wPos.add(a.mul(cy)).add(rv);
            V3 d = S.wPos.sub(closest);
            double dist = d.len();
            if (dist > S.radius + margin || dist < 1e-9) return;
            V3 n = d.mul(1.0 / dist); // cylinder -> sphere
            out.add(closest, n.mul(flip), S.radius - dist);
        } else {
            double radialDepth = r - rd, axialDepth = hh - Math.abs(y);
            V3 n;
            double depth;
            if (radialDepth < axialDepth && rd > 1e-6) {
                n = radial.mul(1.0 / rd);
                depth = radialDepth + S.radius;
            } else {
                n = a.mul(y >= 0 ? 1 : -1);
                depth = axialDepth + S.radius;
            }
            out.add(S.wPos, n.mul(flip), depth);
        }
    }
}
