package com.mcbesiege.physics;

/** Base class of all joints. Body b may be the world's static ground body. */
public abstract class Joint {
    public RigidBody a, b;
    public boolean broken;
    public boolean enabled = true;
    public double breakForce = Double.POSITIVE_INFINITY;
    public double breakTorque = Double.POSITIVE_INFINITY;
    public boolean collideConnected;
    public Object userData;

    double tickLin, tickAng;
    public double lastForce, lastTorque;

    protected Joint(RigidBody a, RigidBody b) {
        this.a = a;
        this.b = b;
    }

    abstract void prepare(double h, PhysicsWorld w);

    abstract void warmStart();

    abstract void solve(double h, boolean useBias, PhysicsWorld w);

    /** Called once after the relax pass of each substep: accumulate constraint load. */
    abstract void endSubstep();

    abstract void onBodyShift(RigidBody body, V3 localShift);

    public boolean involves(RigidBody r) { return a == r || b == r; }

    /**
     * Moves this joint from body {@code from} to body {@code to}. Both bodies must share the same orientation; localShift is the
     * offset of {@code to}'s COM relative to {@code from}'s COM in their (common) local frame.
     */
    public void retarget(RigidBody from, RigidBody to, V3 localShift) {
        if (a == from) {
            a = to;
            onBodyShift(to, localShift);
        }
        if (b == from) {
            b = to;
            onBodyShift(to, localShift);
        }
    }

    /** True while a motor/servo is driving the joint (keeps the bodies awake). */
    public boolean wantsAwake() { return false; }

    public RigidBody other(RigidBody r) { return a == r ? b : a; }

    // ---------------------------------------------------------------------------------------------------------- helpers

    static void applyLinear(RigidBody a, RigidBody b, V3 rA, V3 rB, V3 p) {
        if (a.invMass > 0 && !a.sleeping) {
            a.vel = a.vel.addScaled(p, -a.invMass);
            a.angVel = a.angVel.sub(a.invInertiaWorld.mul(rA.cross(p)));
        }
        if (b.invMass > 0 && !b.sleeping) {
            b.vel = b.vel.addScaled(p, b.invMass);
            b.angVel = b.angVel.add(b.invInertiaWorld.mul(rB.cross(p)));
        }
    }

    static void applyAngular(RigidBody a, RigidBody b, V3 l) {
        if (a.invMass > 0 && !a.sleeping) a.angVel = a.angVel.sub(a.invInertiaWorld.mul(l));
        if (b.invMass > 0 && !b.sleeping) b.angVel = b.angVel.add(b.invInertiaWorld.mul(l));
    }

    static V3 relVel(RigidBody a, RigidBody b, V3 rA, V3 rB) {
        return b.vel.add(b.angVel.cross(rB)).sub(a.vel.add(a.angVel.cross(rA)));
    }

    static double invMassA(RigidBody a) { return a.sleeping ? 0 : a.invMass; }

    static M3 invIA(RigidBody a) { return a.sleeping ? M3.ZERO : a.invInertiaWorld; }

    /** 3-DOF point-to-point constraint. */
    static final class PointPart {
        V3 localA, localB;
        V3 rA = V3.ZERO, rB = V3.ZERO;
        M3 invK = M3.ZERO;
        V3 impulse = V3.ZERO;

        PointPart(V3 localA, V3 localB) {
            this.localA = localA;
            this.localB = localB;
        }

        void prepare(RigidBody a, RigidBody b) {
            rA = a.rot.rotate(localA);
            rB = b.rot.rotate(localB);
            double m = invMassA(a) + invMassA(b);
            M3 sa = M3.skew(rA), sb = M3.skew(rB);
            M3 k = M3.diag(m, m, m).add(sa.mul(invIA(a)).mul(sa.transpose())).add(sb.mul(invIA(b)).mul(sb.transpose()));
            invK = k.inverse();
        }

        V3 error(RigidBody a, RigidBody b) {
            return b.pos.add(rB).sub(a.pos.add(rA));
        }

        void solve(RigidBody a, RigidBody b, boolean useBias, Soft soft) {
            V3 cdot = relVel(a, b, rA, rB);
            V3 imp;
            if (useBias) {
                V3 bias = error(a, b).mul(soft.biasRate);
                imp = invK.mul(cdot.add(bias)).mul(-soft.massScale).sub(impulse.mul(soft.impulseScale));
            } else {
                imp = invK.mul(cdot).neg();
            }
            impulse = impulse.add(imp);
            applyLinear(a, b, rA, rB, imp);
        }
    }

    /** 3-DOF relative orientation lock. */
    static final class AngularLock {
        Quat relRest;
        M3 invK = M3.ZERO;
        V3 impulse = V3.ZERO;

        AngularLock(RigidBody a, RigidBody b) {
            relRest = a.rot.conj().mul(b.rot);
        }

        void prepare(RigidBody a, RigidBody b) {
            invK = invIA(a).add(invIA(b)).inverse();
        }

        V3 error(RigidBody a, RigidBody b) {
            Quat target = a.rot.mul(relRest);
            return b.rot.mul(target.conj()).toRotationVector();
        }

        void solve(RigidBody a, RigidBody b, boolean useBias, Soft soft) {
            V3 cdot = b.angVel.sub(a.angVel);
            V3 imp;
            if (useBias) {
                V3 bias = error(a, b).mul(soft.biasRate);
                imp = invK.mul(cdot.add(bias)).mul(-soft.massScale).sub(impulse.mul(soft.impulseScale));
            } else {
                imp = invK.mul(cdot).neg();
            }
            impulse = impulse.add(imp);
            applyAngular(a, b, imp);
        }
    }

    /** Locks the two rotational DOF perpendicular to a hinge axis. */
    static final class HingeAxisPart {
        V3 axisA, axisB;
        V3 a1 = V3.Y, p1 = V3.X, p2 = V3.Z;
        double k11, k12, k22;
        V3 impulse = V3.ZERO; // world vector perpendicular to axis

        HingeAxisPart(V3 axisA, V3 axisB) {
            this.axisA = axisA;
            this.axisB = axisB;
        }

        void prepare(RigidBody a, RigidBody b) {
            a1 = a.rot.rotate(axisA).norm();
            p1 = a1.anyPerp();
            p2 = a1.cross(p1);
            M3 inv = invIA(a).add(invIA(b));
            k11 = p1.dot(inv.mul(p1));
            k12 = p1.dot(inv.mul(p2));
            k22 = p2.dot(inv.mul(p2));
            impulse = impulse.perp(a1);
        }

        void solve(RigidBody a, RigidBody b, boolean useBias, Soft soft) {
            V3 cdot = b.angVel.sub(a.angVel);
            double c1 = cdot.dot(p1), c2 = cdot.dot(p2);
            double ms = 1, is = 0;
            if (useBias) {
                V3 a2 = b.rot.rotate(axisB);
                V3 err = a1.cross(a2);
                c1 += soft.biasRate * err.dot(p1);
                c2 += soft.biasRate * err.dot(p2);
                ms = soft.massScale;
                is = soft.impulseScale;
            }
            double det = k11 * k22 - k12 * k12;
            if (Math.abs(det) < 1e-14) return;
            double inv = 1.0 / det;
            double x1 = (k22 * c1 - k12 * c2) * inv;
            double x2 = (-k12 * c1 + k11 * c2) * inv;
            double acc1 = impulse.dot(p1), acc2 = impulse.dot(p2);
            double i1 = -ms * x1 - is * acc1;
            double i2 = -ms * x2 - is * acc2;
            V3 l = p1.mul(i1).add(p2.mul(i2));
            impulse = impulse.add(l);
            applyAngular(a, b, l);
        }
    }
}
