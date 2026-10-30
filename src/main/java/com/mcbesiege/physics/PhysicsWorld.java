package com.mcbesiege.physics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sequential-impulse rigid body world with substepping and soft constraints (in the spirit of Box2D v3's soft step),
 * colliding bodies against each other and against static world boxes supplied by a {@link WorldCollisionProvider}.
 */
public final class PhysicsWorld {
    public interface PairFilter {
        boolean canCollide(Collider a, Collider b);
    }

    public final List<RigidBody> bodies = new ArrayList<>();
    public final List<Joint> joints = new ArrayList<>();
    /** Static anchor body used for joints to the world. */
    public final RigidBody ground = new RigidBody();

    public V3 gravity = new V3(0, -20, 0);
    public int substeps = 8;
    public int iterations = 4;
    public int relaxIterations = 2;
    public double contactHertz = 30, contactDampingRatio = 10;
    public double jointHertz = 60, jointDampingRatio = 2;
    public double contactPushMaxVel = 3.0;
    public double speculativeDistance = 0.03;
    public double restitutionThreshold = 1.5;
    public double impactEventSpeed = 2.0;
    public double sleepLinear = 0.08, sleepAngular = 0.1;
    public int sleepDelayTicks = 30;

    public WorldCollisionProvider worldGeometry = WorldCollisionProvider.NONE;
    public PhysicsListener listener = new PhysicsListener() {};
    public PairFilter filter;

    Soft contactSoft = Soft.RIGID, jointSoft = Soft.RIGID;

    private Map<Long, Contact.Manifold> prevManifolds = new HashMap<>();
    private final List<Contact.Manifold> manifolds = new ArrayList<>();
    private final Set<Long> connectedPairs = new HashSet<>();
    private final Set<Long> disabledWorldPairs = new HashSet<>();
    private final Map<Integer, Integer> islandSlowTicks = new HashMap<>();
    private boolean jointsDirty = true;
    private double tickDt = 0.05;
    public long stepCount;

    public PhysicsWorld() {
        ground.isStatic = true;
        ground.recomputeMass();
    }

    // ------------------------------------------------------------------------------------------------- management

    public void addBody(RigidBody b) {
        if (!bodies.contains(b)) bodies.add(b);
        b.updateInertiaWorld();
        b.updateColliders();
        b.computeAabb();
    }

    public void removeBody(RigidBody b) {
        bodies.remove(b);
        joints.removeIf(j -> {
            if (j.involves(b)) {
                jointsDirty = true;
                return true;
            }
            return false;
        });
    }

    public void addJoint(Joint j) {
        joints.add(j);
        jointsDirty = true;
        wakeIsland(j.a);
        wakeIsland(j.b);
    }

    public void removeJoint(Joint j) {
        if (joints.remove(j)) jointsDirty = true;
        wakeIsland(j.a);
        wakeIsland(j.b);
    }

    public void markJointsDirty() { jointsDirty = true; }

    /** Moves joint anchors after a body's COM moved by localShift (see RigidBody#recomputeMass). */
    public void notifyBodyShift(RigidBody body, V3 localShift) {
        if (localShift.lenSq() == 0) return;
        for (Joint j : joints) if (j.involves(body)) j.onBodyShift(body, localShift);
    }

    public void wakeIsland(RigidBody b) {
        if (b == null || !b.sleeping) return;
        if (b.island == 0) {
            b.wake();
            gatherStatic(b);
            return;
        }
        for (RigidBody o : bodies) {
            if (o.island == b.island && o.sleeping) {
                o.wake();
                gatherStatic(o);
            }
        }
        islandSlowTicks.remove(b.island);
    }

    private void gatherStatic(RigidBody b) {
        b.staticCache.clear();
        if (b.isStatic || b.frozen) return;
        b.updateColliders();
        b.computeAabb();
        double dt = tickDt;
        double ex = Math.abs(b.vel.x) * dt + 0.6, ey = Math.abs(b.vel.y) * dt + 0.6, ez = Math.abs(b.vel.z) * dt + 0.6;
        worldGeometry.collect(b.aabbMinX - ex, b.aabbMinY - ey, b.aabbMinZ - ez, b.aabbMaxX + ex, b.aabbMaxY + ey, b.aabbMaxZ + ez, b.staticCache);
    }

    private boolean active(RigidBody b) {
        return !b.isStatic && !b.frozen && !b.sleeping && b.invMass > 0;
    }

    // ------------------------------------------------------------------------------------------------- stepping

    public void step(double dt) {
        stepCount++;
        tickDt = dt;
        double h = dt / substeps;
        contactSoft = Soft.of(Math.min(contactHertz, 0.25 / h), contactDampingRatio, h);
        jointSoft = Soft.of(Math.min(jointHertz, 0.25 / h), jointDampingRatio, h);

        joints.removeIf(j -> j.broken);
        if (jointsDirty) {
            connectedPairs.clear();
            for (Joint j : joints) {
                if (!j.collideConnected) connectedPairs.add(bodyPair(j.a, j.b));
            }
            jointsDirty = false;
        }
        // wake islands that are being driven
        for (RigidBody b : bodies) {
            if (b.sleeping && (b.force.lenSq() > 0 || b.torque.lenSq() > 0 || !b.allowSleep)) wakeIsland(b);
        }
        for (Joint j : joints) {
            if (j.wantsAwake()) {
                wakeIsland(j.a);
                wakeIsland(j.b);
            }
            if (j.a.sleeping != j.b.sleeping && !j.a.isStatic && !j.b.isStatic) {
                wakeIsland(j.a);
                wakeIsland(j.b);
            }
        }
        // gather static geometry around every moving body once per tick
        for (RigidBody b : bodies) {
            b.staticCache.clear();
            if (b.isStatic) continue;
            b.frozen = !worldGeometry.isLoaded(b.pos.x, b.pos.y, b.pos.z);
            if (b.frozen || b.sleeping) continue;
            gatherStatic(b);
        }
        disabledWorldPairs.clear();
        for (Joint j : joints) {
            j.tickLin = 0;
            j.tickAng = 0;
        }
        for (int s = 0; s < substeps; s++) substep(h);

        // joint breaking
        for (Joint j : joints) {
            if (!j.enabled) continue;
            j.lastForce = j.tickLin / dt;
            j.lastTorque = j.tickAng / dt;
            if (j.lastForce > j.breakForce || j.lastTorque > j.breakTorque) {
                j.broken = true;
                jointsDirty = true;
                listener.onJointBroken(j);
            }
        }
        joints.removeIf(j -> j.broken);
        for (RigidBody b : bodies) {
            b.force = V3.ZERO;
            b.torque = V3.ZERO;
        }
        updateSleep();
    }

    private static long bodyPair(RigidBody a, RigidBody b) {
        int x = Math.min(a.id, b.id), y = Math.max(a.id, b.id);
        return Contact.pairKey(x, y);
    }

    private void substep(double h) {
        // 1. velocities
        for (RigidBody b : bodies) {
            if (!active(b)) continue;
            V3 acc = gravity.mul(b.gravityScale).add(b.force.mul(b.invMass));
            b.vel = b.vel.addScaled(acc, h);
            if (b.torque.lenSq() > 0) b.angVel = b.angVel.add(b.invInertiaWorld.mul(b.torque).mul(h));
            b.vel = b.vel.mul(1.0 / (1.0 + h * b.linearDamping)).clampLen(b.maxSpeed);
            b.angVel = b.angVel.mul(1.0 / (1.0 + h * b.angularDamping)).clampLen(b.maxAngularSpeed);
        }
        // 2. world transforms
        for (RigidBody b : bodies) {
            if (b.isStatic || b.frozen) continue;
            b.updateColliders();
            b.computeAabb();
        }
        // 3. collision detection
        manifolds.clear();
        Map<Long, Contact.Manifold> current = new HashMap<>();
        double margin = speculativeDistance;
        for (RigidBody b : bodies) {
            if (!active(b)) continue;
            for (Collider c : b.colliders) {
                if (!c.solid) continue;
                for (StaticBox sb : b.staticCache) {
                    if (!c.aabbOverlaps(sb, margin)) continue;
                    long key = Contact.pairKey(c.id, (int) (sb.key ^ (sb.key >>> 32)));
                    if (disabledWorldPairs.contains(key)) continue;
                    Contact.Manifold m = new Contact.Manifold();
                    m.a = b;
                    m.ca = c;
                    m.b = ground;
                    m.sb = sb;
                    m.friction = Math.sqrt(Math.max(0, c.friction * sb.friction));
                    m.restitution = Math.max(c.restitution, sb.restitution);
                    Narrowphase.collideStatic(c, sb, margin, m);
                    if (m.pts.isEmpty()) continue;
                    m.key = key;
                    matchWarmStart(m);
                    boolean keep = true;
                    for (Contact.Point p : m.pts) {
                        if (!p.isNew) continue;
                        V3 rA = p.point.sub(b.pos);
                        double vn = -b.vel.add(b.angVel.cross(rA)).dot(p.normal);
                        if (-vn > impactEventSpeed * 0.5) {
                            V3 rn = rA.cross(p.normal);
                            double k = b.invMass + rn.dot(b.invInertiaWorld.mul(rn));
                            if (!listener.onWorldImpact(b, c, sb, p.point, p.normal, -vn, k > 0 ? 1.0 / k : 0)) {
                                keep = false;
                                break;
                            }
                        }
                    }
                    if (!keep) {
                        disabledWorldPairs.add(key);
                        continue;
                    }
                    manifolds.add(m);
                    current.put(key, m);
                }
            }
        }
        // body vs body (sort and sweep on x)
        List<RigidBody> sorted = new ArrayList<>();
        for (RigidBody b : bodies) {
            if (b.isStatic || b.frozen || b.colliders.isEmpty()) continue;
            sorted.add(b);
        }
        sorted.sort(Comparator.comparingDouble(o -> o.aabbMinX));
        for (int i = 0; i < sorted.size(); i++) {
            RigidBody a = sorted.get(i);
            for (int j = i + 1; j < sorted.size(); j++) {
                RigidBody b = sorted.get(j);
                if (b.aabbMinX > a.aabbMaxX + margin) break;
                if (!active(a) && !active(b)) continue;
                if (b.aabbMinY > a.aabbMaxY + margin || b.aabbMaxY < a.aabbMinY - margin) continue;
                if (b.aabbMinZ > a.aabbMaxZ + margin || b.aabbMaxZ < a.aabbMinZ - margin) continue;
                if (connectedPairs.contains(bodyPair(a, b))) continue;
                for (Collider ca : a.colliders) {
                    if (!ca.solid) continue;
                    for (Collider cb : b.colliders) {
                        if (!cb.solid || !ca.aabbOverlaps(cb, margin)) continue;
                        if (filter != null && !filter.canCollide(ca, cb)) continue;
                        Contact.Manifold m = new Contact.Manifold();
                        m.a = a;
                        m.ca = ca;
                        m.b = b;
                        m.cb = cb;
                        m.friction = Math.sqrt(Math.max(0, ca.friction * cb.friction));
                        m.restitution = Math.max(ca.restitution, cb.restitution);
                        Narrowphase.collide(ca, cb, margin, m);
                        if (m.pts.isEmpty()) continue;
                        if (a.sleeping) wakeIsland(a);
                        if (b.sleeping) wakeIsland(b);
                        m.key = Contact.pairKey(ca.id, cb.id);
                        matchWarmStart(m);
                        manifolds.add(m);
                        current.put(m.key, m);
                    }
                }
            }
        }
        // 4. prepare contacts
        for (Contact.Manifold m : manifolds) prepareManifold(m);
        // 5. prepare joints
        List<Joint> live = new ArrayList<>(joints.size());
        for (Joint j : joints) {
            if (!j.enabled || j.broken) continue;
            if (!active(j.a) && !active(j.b)) continue;
            j.prepare(h, this);
            live.add(j);
        }
        // 6. warm start
        for (Joint j : live) j.warmStart();
        for (Contact.Manifold m : manifolds) warmStart(m);
        // 7. solve
        for (int it = 0; it < iterations; it++) {
            for (Joint j : live) j.solve(h, true, this);
            for (Contact.Manifold m : manifolds) solveContacts(m, h, true);
        }
        // 8. integrate positions
        for (RigidBody b : bodies) {
            if (!active(b)) continue;
            b.pos = b.pos.addScaled(b.vel, h);
            b.rot = b.rot.integrate(b.angVel, h);
            if (b.sanitize()) continue;
            b.updateInertiaWorld();
            b.markSafe();
        }
        // 9. relax
        for (int it = 0; it < relaxIterations; it++) {
            for (Joint j : live) j.solve(h, false, this);
            for (Contact.Manifold m : manifolds) solveContacts(m, h, false);
        }
        for (Joint j : live) j.endSubstep();
        for (Contact.Manifold m : manifolds) for (Contact.Point p : m.pts) p.tickImpulse += p.normalImpulse;
        prevManifolds = current;
    }

    private void matchWarmStart(Contact.Manifold m) {
        Contact.Manifold old = prevManifolds.get(m.key);
        for (Contact.Point p : m.pts) {
            p.localA = m.a.worldToLocal(p.point);
            if (old == null) continue;
            Contact.Point best = null;
            double bd = 0.04 * 0.04;
            for (Contact.Point q : old.pts) {
                if (q.localA == null) continue;
                double d = q.localA.distSq(p.localA);
                if (d < bd && q.normal.dot(p.normal) > 0.9) {
                    bd = d;
                    best = q;
                }
            }
            if (best != null) {
                p.isNew = false;
                p.normalImpulse = best.normalImpulse;
                V3 f = best.t1.mul(best.t1Impulse).add(best.t2.mul(best.t2Impulse));
                p.t1 = f; // temporarily stores the friction impulse vector, resolved in prepare
                p.tickImpulse = best.tickImpulse;
            }
        }
    }

    private void prepareManifold(Contact.Manifold m) {
        RigidBody a = m.a, b = m.b;
        double mA = a.sleeping ? 0 : a.invMass, mB = b.sleeping ? 0 : b.invMass;
        M3 iA = a.sleeping ? M3.ZERO : a.invInertiaWorld, iB = b.sleeping ? M3.ZERO : b.invInertiaWorld;
        for (Contact.Point p : m.pts) {
            V3 n = p.normal;
            p.rA = p.point.sub(a.pos);
            p.rB = p.point.sub(b.pos);
            V3 friction = p.isNew ? V3.ZERO : p.t1;
            V3 dv = Joint.relVel(a, b, p.rA, p.rB);
            p.relVelN0 = dv.dot(n);
            V3 vt = dv.sub(n.mul(p.relVelN0));
            V3 t1 = vt.lenSq() > 1e-4 ? vt.norm() : n.anyPerp();
            V3 t2 = n.cross(t1);
            p.t1 = t1;
            p.t2 = t2;
            p.t1Impulse = friction.dot(t1);
            p.t2Impulse = friction.dot(t2);
            p.normalMass = invMass(mA, mB, iA, iB, p.rA, p.rB, n);
            p.t1Mass = invMass(mA, mB, iA, iB, p.rA, p.rB, t1);
            p.t2Mass = invMass(mA, mB, iA, iB, p.rA, p.rB, t2);
            if (p.isNew && m.cb != null && -p.relVelN0 > impactEventSpeed) {
                listener.onBodyImpact(a, m.ca, b, m.cb, p.point, n, -p.relVelN0, p.normalMass);
            }
        }
    }

    private static double invMass(double mA, double mB, M3 iA, M3 iB, V3 rA, V3 rB, V3 n) {
        V3 ra = rA.cross(n), rb = rB.cross(n);
        double k = mA + mB + ra.dot(iA.mul(ra)) + rb.dot(iB.mul(rb));
        return k > 1e-12 ? 1.0 / k : 0;
    }

    private static void applyContact(RigidBody a, RigidBody b, V3 rA, V3 rB, V3 p) {
        Joint.applyLinear(a, b, rA, rB, p);
    }

    private void warmStart(Contact.Manifold m) {
        for (Contact.Point p : m.pts) {
            V3 imp = p.normal.mul(p.normalImpulse).add(p.t1.mul(p.t1Impulse)).add(p.t2.mul(p.t2Impulse));
            if (imp.lenSq() > 0) applyContact(m.a, m.b, p.rA, p.rB, imp);
        }
    }

    private void solveContacts(Contact.Manifold m, double h, boolean useBias) {
        RigidBody a = m.a, b = m.b;
        for (Contact.Point p : m.pts) {
            V3 n = p.normal;
            V3 dv = Joint.relVel(a, b, p.rA, p.rB);
            double vn = dv.dot(n);
            double s = -p.depth;
            double bias = 0, ms = 1, is = 0;
            if (s > 0) {
                bias = s / h;
            } else if (useBias) {
                bias = Math.max(contactSoft.biasRate * s, -contactPushMaxVel);
                ms = contactSoft.massScale;
                is = contactSoft.impulseScale;
            }
            if (m.restitution > 0 && p.relVelN0 < -restitutionThreshold) {
                bias = Math.min(bias, m.restitution * p.relVelN0);
                ms = 1;
                is = 0;
            }
            double imp = -p.normalMass * ms * (vn + bias) - is * p.normalImpulse;
            double old = p.normalImpulse;
            p.normalImpulse = Math.max(0, old + imp);
            double d = p.normalImpulse - old;
            if (d != 0) applyContact(a, b, p.rA, p.rB, n.mul(d));

            double maxF = m.friction * p.normalImpulse;
            dv = Joint.relVel(a, b, p.rA, p.rB);
            double vt1 = dv.dot(p.t1);
            double o1 = p.t1Impulse;
            p.t1Impulse = Math.max(-maxF, Math.min(maxF, o1 - p.t1Mass * vt1));
            double vt2 = dv.dot(p.t2);
            double o2 = p.t2Impulse;
            p.t2Impulse = Math.max(-maxF, Math.min(maxF, o2 - p.t2Mass * vt2));
            V3 fi = p.t1.mul(p.t1Impulse - o1).add(p.t2.mul(p.t2Impulse - o2));
            if (fi.lenSq() > 0) applyContact(a, b, p.rA, p.rB, fi);
        }
    }

    private void updateSleep() {
        Map<Integer, Boolean> slow = new HashMap<>();
        for (RigidBody b : bodies) {
            if (b.isStatic || b.frozen || b.sleeping) continue;
            int key = b.island != 0 ? b.island : -b.id;
            boolean s = b.allowSleep && b.vel.lenSq() < sleepLinear * sleepLinear && b.angVel.lenSq() < sleepAngular * sleepAngular;
            slow.merge(key, s, (x, y) -> x && y);
        }
        for (Map.Entry<Integer, Boolean> e : slow.entrySet()) {
            int key = e.getKey();
            if (!e.getValue()) {
                islandSlowTicks.remove(key);
                continue;
            }
            int t = islandSlowTicks.merge(key, 1, Integer::sum);
            if (t > sleepDelayTicks) {
                for (RigidBody b : bodies) {
                    int k = b.island != 0 ? b.island : -b.id;
                    if (k == key && !b.isStatic) {
                        b.sleeping = true;
                        b.vel = V3.ZERO;
                        b.angVel = V3.ZERO;
                    }
                }
                islandSlowTicks.remove(key);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- queries

    public List<Contact.Manifold> lastManifolds() { return manifolds; }

    /** Ray cast against body colliders (boxes / cylinders approximated by their boxes, spheres exact). */
    public RayHit raycastBodies(V3 from, V3 to, RigidBody ignoreIsland) {
        V3 dir = to.sub(from);
        double len = dir.len();
        if (len < 1e-9) return null;
        V3 d = dir.mul(1.0 / len);
        RayHit best = null;
        for (RigidBody b : bodies) {
            if (ignoreIsland != null && b.island != 0 && b.island == ignoreIsland.island) continue;
            for (Collider c : b.colliders) {
                double t = rayCollider(from, d, len, c);
                if (t >= 0 && (best == null || t < best.distance)) best = new RayHit(b, c, from.addScaled(d, t), t);
            }
        }
        return best;
    }

    public static double rayCollider(V3 o, V3 d, double maxLen, Collider c) {
        if (c.type == Collider.Type.SPHERE) {
            Collider.Sphere s = (Collider.Sphere) c;
            V3 oc = o.sub(s.wPos);
            double bq = oc.dot(d), cq = oc.lenSq() - s.radius * s.radius;
            double disc = bq * bq - cq;
            if (disc < 0) return -1;
            double t = -bq - Math.sqrt(disc);
            if (t < 0) t = 0;
            return t <= maxLen ? t : -1;
        }
        V3 half = c.type == Collider.Type.BOX ? ((Collider.Box) c).half
                : new V3(((Collider.Cylinder) c).radius, ((Collider.Cylinder) c).halfHeight, ((Collider.Cylinder) c).radius);
        V3 lo = c.wRot.invRotate(o.sub(c.wPos)), ld = c.wRot.invRotate(d);
        double tmin = 0, tmax = maxLen;
        for (int i = 0; i < 3; i++) {
            double oi = lo.get(i), di = ld.get(i), hi = half.get(i);
            if (Math.abs(di) < 1e-12) {
                if (oi < -hi || oi > hi) return -1;
            } else {
                double t1 = (-hi - oi) / di, t2 = (hi - oi) / di;
                if (t1 > t2) {
                    double tt = t1;
                    t1 = t2;
                    t2 = tt;
                }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return -1;
            }
        }
        return tmin;
    }

    public static final class RayHit {
        public final RigidBody body;
        public final Collider collider;
        public final V3 point;
        public final double distance;

        RayHit(RigidBody body, Collider collider, V3 point, double distance) {
            this.body = body;
            this.collider = collider;
            this.point = point;
            this.distance = distance;
        }
    }

    public double tickDt() { return tickDt; }
}
