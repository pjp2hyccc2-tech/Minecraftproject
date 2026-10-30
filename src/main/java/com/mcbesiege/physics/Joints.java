package com.mcbesiege.physics;

/** Concrete joint types. */
public final class Joints {
    private Joints() {}

    static V3 localOf(RigidBody body, V3 worldPoint) { return body.worldToLocal(worldPoint); }

    /** Soft coefficients for a physical spring k (N/m) and damper c (N*s/m) acting on effective mass m. */
    static Soft physicalSoft(double k, double c, double m, double h) {
        if (m <= 0 || k <= 0) return Soft.RIGID;
        double omega = Math.sqrt(k / m);
        double zeta = c / (2 * Math.sqrt(k * m));
        return Soft.of(omega / (2 * Math.PI), zeta, h);
    }

    // ==================================================================================================================

    /** Ball and socket. */
    public static final class Ball extends Joint {
        final PointPart point;

        public Ball(RigidBody a, RigidBody b, V3 worldAnchor) {
            super(a, b);
            point = new PointPart(localOf(a, worldAnchor), localOf(b, worldAnchor));
        }

        @Override
        void prepare(double h, PhysicsWorld w) { point.prepare(a, b); }

        @Override
        void warmStart() { applyLinear(a, b, point.rA, point.rB, point.impulse); }

        @Override
        void solve(double h, boolean useBias, PhysicsWorld w) { point.solve(a, b, useBias, w.jointSoft); }

        @Override
        void endSubstep() { tickLin += point.impulse.len(); }

        @Override
        void onBodyShift(RigidBody body, V3 s) {
            if (body == a) point.localA = point.localA.sub(s);
            if (body == b) point.localB = point.localB.sub(s);
        }

        public V3 worldAnchor() { return a.localToWorld(point.localA); }
    }

    // ==================================================================================================================

    /** Weld: removes all relative motion. */
    public static final class Fixed extends Joint {
        final PointPart point;
        final AngularLock ang;

        public Fixed(RigidBody a, RigidBody b, V3 worldAnchor) {
            super(a, b);
            point = new PointPart(localOf(a, worldAnchor), localOf(b, worldAnchor));
            ang = new AngularLock(a, b);
        }

        @Override
        void prepare(double h, PhysicsWorld w) {
            point.prepare(a, b);
            ang.prepare(a, b);
        }

        @Override
        void warmStart() {
            applyLinear(a, b, point.rA, point.rB, point.impulse);
            applyAngular(a, b, ang.impulse);
        }

        @Override
        void solve(double h, boolean useBias, PhysicsWorld w) {
            ang.solve(a, b, useBias, w.jointSoft);
            point.solve(a, b, useBias, w.jointSoft);
        }

        @Override
        void endSubstep() {
            tickLin += point.impulse.len();
            tickAng += ang.impulse.len();
        }

        @Override
        void onBodyShift(RigidBody body, V3 s) {
            if (body == a) point.localA = point.localA.sub(s);
            if (body == b) point.localB = point.localB.sub(s);
        }

        public V3 worldAnchor() { return a.localToWorld(point.localA); }
    }

    // ==================================================================================================================

    /** Revolute joint with optional motor, position servo and angle limits. */
    public static final class Hinge extends Joint {
        final PointPart point;
        final HingeAxisPart axis;
        final V3 refA, refB;

        public boolean motorEnabled;
        public double motorSpeed;      // rad/s, B relative to A around the axis
        public double maxMotorTorque;
        double motorImpulse;

        public boolean servoEnabled;
        public double servoTarget;     // rad
        public double servoMaxSpeed = 6;
        public double servoGain = 25;  // 1/s : target speed = gain * error
        public double maxServoTorque = 1e5;
        double servoImpulse, servoSpeed;

        public boolean limitEnabled;
        public double lower = -Math.PI, upper = Math.PI;
        double lowerImpulse, upperImpulse;

        /** Passive friction torque (always on). */
        public double frictionTorque;
        double frictionImpulse;

        double angle, prevRawAngle;
        boolean angleInit;
        double axialMass;
        V3 a1 = V3.Y;

        public Hinge(RigidBody a, RigidBody b, V3 worldAnchor, V3 worldAxis) {
            super(a, b);
            V3 ax = worldAxis.norm();
            point = new PointPart(localOf(a, worldAnchor), localOf(b, worldAnchor));
            axis = new HingeAxisPart(a.dirToLocal(ax), b.dirToLocal(ax));
            V3 ref = ax.anyPerp();
            refA = a.dirToLocal(ref);
            refB = b.dirToLocal(ref);
        }

        /** Current (unwrapped) joint angle of B relative to A. */
        public double angle() { return angle; }

        @Override
        public boolean wantsAwake() {
            return (motorEnabled && !servoEnabled && Math.abs(motorSpeed) > 1e-4) || (servoEnabled && Math.abs(angle - servoTarget) > 0.005);
        }

        public V3 worldAxis() { return a.dirToWorld(axis.axisA).norm(); }

        public V3 worldAnchor() { return a.localToWorld(point.localA); }

        double rawAngle() {
            V3 ax = a.rot.rotate(axis.axisA).norm();
            V3 ra = a.rot.rotate(refA).perp(ax);
            V3 rb = b.rot.rotate(refB).perp(ax);
            return Math.atan2(ra.cross(rb).dot(ax), ra.dot(rb));
        }

        void updateAngle() {
            double raw = rawAngle();
            if (!angleInit) {
                angle = raw;
                angleInit = true;
            } else {
                double d = raw - prevRawAngle;
                while (d > Math.PI) d -= 2 * Math.PI;
                while (d < -Math.PI) d += 2 * Math.PI;
                angle += d;
            }
            prevRawAngle = raw;
        }

        @Override
        void prepare(double h, PhysicsWorld w) {
            point.prepare(a, b);
            axis.prepare(a, b);
            a1 = axis.a1;
            double k = a1.dot(invIA(a).add(invIA(b)).mul(a1));
            axialMass = k > 1e-12 ? 1.0 / k : 0;
            updateAngle();
            if (!motorEnabled) motorImpulse = 0;
            if (!servoEnabled) servoImpulse = 0;
            servoSpeed = Math.max(-servoMaxSpeed, Math.min(servoMaxSpeed, servoGain * (servoTarget - angle)));
            if (!limitEnabled) {
                lowerImpulse = 0;
                upperImpulse = 0;
            }
        }

        @Override
        void warmStart() {
            applyLinear(a, b, point.rA, point.rB, point.impulse);
            double ax = motorImpulse + servoImpulse + lowerImpulse - upperImpulse + frictionImpulse;
            applyAngular(a, b, axis.impulse.add(a1.mul(ax)));
        }

        @Override
        void solve(double h, boolean useBias, PhysicsWorld w) {
            double cdot;
            if (frictionTorque > 0) {
                cdot = b.angVel.sub(a.angVel).dot(a1);
                double imp = -axialMass * cdot;
                double old = frictionImpulse, max = frictionTorque * h;
                frictionImpulse = Math.max(-max, Math.min(max, old + imp));
                applyAngular(a, b, a1.mul(frictionImpulse - old));
            }
            if (motorEnabled && !servoEnabled) {
                cdot = b.angVel.sub(a.angVel).dot(a1);
                double imp = axialMass * (motorSpeed - cdot);
                double old = motorImpulse, max = maxMotorTorque * h;
                motorImpulse = Math.max(-max, Math.min(max, old + imp));
                applyAngular(a, b, a1.mul(motorImpulse - old));
            }
            if (servoEnabled) {
                cdot = b.angVel.sub(a.angVel).dot(a1);
                double imp = axialMass * (servoSpeed - cdot);
                double old = servoImpulse, max = maxServoTorque * h;
                servoImpulse = Math.max(-max, Math.min(max, old + imp));
                applyAngular(a, b, a1.mul(servoImpulse - old));
            }
            if (limitEnabled) {
                Soft js = w.jointSoft;
                // lower: C = angle - lower >= 0
                {
                    double c = angle - lower;
                    cdot = b.angVel.sub(a.angVel).dot(a1);
                    double bias = 0, ms = 1, is = 0;
                    if (c > 0) bias = c / h;
                    else if (useBias) {
                        bias = js.biasRate * c;
                        ms = js.massScale;
                        is = js.impulseScale;
                    }
                    double imp = -axialMass * ms * (cdot + bias) - is * lowerImpulse;
                    double old = lowerImpulse;
                    lowerImpulse = Math.max(0, old + imp);
                    applyAngular(a, b, a1.mul(lowerImpulse - old));
                }
                // upper: C = upper - angle >= 0
                {
                    double c = upper - angle;
                    cdot = -b.angVel.sub(a.angVel).dot(a1);
                    double bias = 0, ms = 1, is = 0;
                    if (c > 0) bias = c / h;
                    else if (useBias) {
                        bias = js.biasRate * c;
                        ms = js.massScale;
                        is = js.impulseScale;
                    }
                    double imp = -axialMass * ms * (cdot + bias) - is * upperImpulse;
                    double old = upperImpulse;
                    upperImpulse = Math.max(0, old + imp);
                    applyAngular(a, b, a1.mul(-(upperImpulse - old)));
                }
            }
            axis.solve(a, b, useBias, w.jointSoft);
            point.solve(a, b, useBias, w.jointSoft);
        }

        @Override
        void endSubstep() {
            tickLin += point.impulse.len();
            tickAng += axis.impulse.len() + Math.abs(lowerImpulse) + Math.abs(upperImpulse);
        }

        @Override
        void onBodyShift(RigidBody body, V3 s) {
            if (body == a) point.localA = point.localA.sub(s);
            if (body == b) point.localB = point.localB.sub(s);
        }

        /** Relative angular speed of B around the axis. */
        public double angularSpeed() { return b.angVel.sub(a.angVel).dot(worldAxis()); }
    }

    // ==================================================================================================================

    /** Prismatic joint along an axis fixed in A, with motor, position servo / spring and limits. */
    public static final class Slider extends Joint {
        final AngularLock ang;
        V3 localA, localB, axisLocal;
        V3 rA = V3.ZERO, rB = V3.ZERO, d = V3.ZERO, ax = V3.Y, p1 = V3.X, p2 = V3.Z;
        V3 perpImpulse = V3.ZERO;
        double kp11, kp12, kp22, axialMass;

        public boolean limitEnabled = true;
        public double lower = 0, upper = 1;
        double lowerImpulse, upperImpulse;

        public boolean motorEnabled;
        public double motorSpeed, maxMotorForce;
        double motorImpulse;

        /** Rigid position servo (velocity motor with position feedback), e.g. pistons. */
        public boolean servoEnabled;
        public double servoTarget, servoMaxSpeed = 2, servoGain = 20, maxServoForce = 1e5;
        double servoImpulse, servoSpeed;

        /** Physical spring toward a target position (N/m, N*s/m), e.g. suspension. */
        public boolean springEnabled;
        public double springTarget, springStiffness = 200, springDampingCoeff = 10, maxSpringForce = 1e5;
        double springImpulse;
        Soft springSoft = Soft.RIGID;

        public Slider(RigidBody a, RigidBody b, V3 worldAnchor, V3 worldAxis) {
            super(a, b);
            localA = localOf(a, worldAnchor);
            localB = localOf(b, worldAnchor);
            axisLocal = a.dirToLocal(worldAxis.norm());
            ang = new AngularLock(a, b);
        }

        public double translation() {
            V3 pa = a.localToWorld(localA), pb = b.localToWorld(localB);
            return pb.sub(pa).dot(a.dirToWorld(axisLocal));
        }

        public V3 worldAxis() { return a.dirToWorld(axisLocal).norm(); }

        @Override
        public boolean wantsAwake() {
            return (motorEnabled && Math.abs(motorSpeed) > 1e-4) || (servoEnabled && Math.abs(translation() - servoTarget) > 0.005)
                    || (springEnabled && Math.abs(translation() - springTarget) > 0.01);
        }

        V3 jA(V3 dir) { return rA.add(d).cross(dir); }

        V3 jB(V3 dir) { return rB.cross(dir); }

        double effMass(V3 dir) {
            V3 ja = jA(dir), jb = jB(dir);
            return invMassA(a) + invMassA(b) + ja.dot(invIA(a).mul(ja)) + jb.dot(invIA(b).mul(jb));
        }

        void applyDir(V3 dir, double lambda) {
            V3 p = dir.mul(lambda);
            if (a.invMass > 0 && !a.sleeping) {
                a.vel = a.vel.addScaled(p, -a.invMass);
                a.angVel = a.angVel.sub(a.invInertiaWorld.mul(jA(p)));
            }
            if (b.invMass > 0 && !b.sleeping) {
                b.vel = b.vel.addScaled(p, b.invMass);
                b.angVel = b.angVel.add(b.invInertiaWorld.mul(jB(p)));
            }
        }

        double cdotDir(V3 dir) {
            return b.vel.dot(dir) + b.angVel.dot(jB(dir)) - a.vel.dot(dir) - a.angVel.dot(jA(dir));
        }

        @Override
        void prepare(double h, PhysicsWorld w) {
            rA = a.rot.rotate(localA);
            rB = b.rot.rotate(localB);
            d = b.pos.add(rB).sub(a.pos.add(rA));
            ax = a.rot.rotate(axisLocal).norm();
            p1 = ax.anyPerp();
            p2 = ax.cross(p1);
            kp11 = effMass(p1);
            kp22 = effMass(p2);
            V3 ja1 = jA(p1), jb1 = jB(p1), ja2 = jA(p2), jb2 = jB(p2);
            kp12 = ja1.dot(invIA(a).mul(ja2)) + jb1.dot(invIA(b).mul(jb2));
            double k = effMass(ax);
            axialMass = k > 1e-12 ? 1.0 / k : 0;
            perpImpulse = perpImpulse.perp(ax);
            ang.prepare(a, b);
            if (!motorEnabled) motorImpulse = 0;
            if (!springEnabled) springImpulse = 0;
            if (!servoEnabled) servoImpulse = 0;
            double x0 = d.dot(ax);
            servoSpeed = Math.max(-servoMaxSpeed, Math.min(servoMaxSpeed, servoGain * (servoTarget - x0)));
            springSoft = physicalSoft(springStiffness, springDampingCoeff, axialMass, h);
            if (!limitEnabled) {
                lowerImpulse = 0;
                upperImpulse = 0;
            }
        }

        @Override
        void warmStart() {
            applyAngular(a, b, ang.impulse);
            V3 p = perpImpulse.add(ax.mul(motorImpulse + servoImpulse + springImpulse + lowerImpulse - upperImpulse));
            double l = p.len();
            if (l > 0) applyDir(p.mul(1.0 / l), l);
        }

        @Override
        void solve(double h, boolean useBias, PhysicsWorld w) {
            double x = d.dot(ax);
            Soft js = w.jointSoft;
            if (motorEnabled) {
                double cdot = cdotDir(ax);
                double imp = axialMass * (motorSpeed - cdot);
                double old = motorImpulse, max = maxMotorForce * h;
                motorImpulse = Math.max(-max, Math.min(max, old + imp));
                applyDir(ax, motorImpulse - old);
            }
            if (servoEnabled) {
                double cdot = cdotDir(ax);
                double imp = axialMass * (servoSpeed - cdot);
                double old = servoImpulse, max = maxServoForce * h;
                servoImpulse = Math.max(-max, Math.min(max, old + imp));
                applyDir(ax, servoImpulse - old);
            }
            if (springEnabled) {
                double cdot = cdotDir(ax);
                Soft s = springSoft;
                double imp = -axialMass * s.massScale * (cdot + s.biasRate * (x - springTarget)) - s.impulseScale * springImpulse;
                double old = springImpulse, max = maxSpringForce * h;
                springImpulse = Math.max(-max, Math.min(max, old + imp));
                applyDir(ax, springImpulse - old);
            }
            if (limitEnabled) {
                {
                    double c = x - lower;
                    double cdot = cdotDir(ax);
                    double bias = 0, ms = 1, is = 0;
                    if (c > 0) bias = c / h;
                    else if (useBias) {
                        bias = js.biasRate * c;
                        ms = js.massScale;
                        is = js.impulseScale;
                    }
                    double imp = -axialMass * ms * (cdot + bias) - is * lowerImpulse;
                    double old = lowerImpulse;
                    lowerImpulse = Math.max(0, old + imp);
                    applyDir(ax, lowerImpulse - old);
                }
                {
                    double c = upper - x;
                    double cdot = -cdotDir(ax);
                    double bias = 0, ms = 1, is = 0;
                    if (c > 0) bias = c / h;
                    else if (useBias) {
                        bias = js.biasRate * c;
                        ms = js.massScale;
                        is = js.impulseScale;
                    }
                    double imp = -axialMass * ms * (cdot + bias) - is * upperImpulse;
                    double old = upperImpulse;
                    upperImpulse = Math.max(0, old + imp);
                    applyDir(ax, -(upperImpulse - old));
                }
            }
            ang.solve(a, b, useBias, js);
            // perpendicular linear rows (2x2 block)
            double c1 = cdotDir(p1), c2 = cdotDir(p2);
            double ms = 1, is = 0;
            if (useBias) {
                c1 += js.biasRate * d.dot(p1);
                c2 += js.biasRate * d.dot(p2);
                ms = js.massScale;
                is = js.impulseScale;
            }
            double det = kp11 * kp22 - kp12 * kp12;
            if (Math.abs(det) > 1e-14) {
                double inv = 1.0 / det;
                double x1 = (kp22 * c1 - kp12 * c2) * inv;
                double x2 = (-kp12 * c1 + kp11 * c2) * inv;
                double i1 = -ms * x1 - is * perpImpulse.dot(p1);
                double i2 = -ms * x2 - is * perpImpulse.dot(p2);
                perpImpulse = perpImpulse.add(p1.mul(i1)).add(p2.mul(i2));
                applyDir(p1, i1);
                applyDir(p2, i2);
            }
        }

        @Override
        void endSubstep() {
            tickLin += perpImpulse.len() + Math.abs(lowerImpulse) + Math.abs(upperImpulse);
            tickAng += ang.impulse.len();
        }

        @Override
        void onBodyShift(RigidBody body, V3 s) {
            if (body == a) localA = localA.sub(s);
            if (body == b) localB = localB.sub(s);
        }
    }

    // ==================================================================================================================

    /** Distance constraint between two anchor points: rigid rod, rope (max length) or spring. */
    public static final class Distance extends Joint {
        public enum Mode { ROD, ROPE, SPRING }

        public Mode mode;
        V3 localA, localB;
        public double length;
        public double minLength = 0.05;
        public double maxLength = Double.POSITIVE_INFINITY;
        public double springStiffness = 100, springDampingCoeff = 5;
        V3 rA = V3.ZERO, rB = V3.ZERO, u = V3.Y;
        double currentLength, mass;
        double impulse, maxImpulse;

        public Distance(RigidBody a, RigidBody b, V3 worldA, V3 worldB, Mode mode) {
            super(a, b);
            this.mode = mode;
            localA = localOf(a, worldA);
            localB = localOf(b, worldB);
            length = worldB.sub(worldA).len();
            collideConnected = true;
        }

        public V3 worldA() { return a.localToWorld(localA); }

        public V3 worldB() { return b.localToWorld(localB); }

        public double currentLength() { return worldB().sub(worldA()).len(); }

        @Override
        void prepare(double h, PhysicsWorld w) {
            rA = a.rot.rotate(localA);
            rB = b.rot.rotate(localB);
            V3 dd = b.pos.add(rB).sub(a.pos.add(rA));
            currentLength = dd.len();
            u = currentLength > 1e-6 ? dd.mul(1.0 / currentLength) : V3.Y;
            V3 ja = rA.cross(u), jb = rB.cross(u);
            double k = invMassA(a) + invMassA(b) + ja.dot(invIA(a).mul(ja)) + jb.dot(invIA(b).mul(jb));
            mass = k > 1e-12 ? 1.0 / k : 0;
            stopImpulse = 0;
            if (mode == Mode.ROPE && currentLength < Math.max(minLength, Math.min(maxLength, length)) - 0.05) impulse = 0;
        }

        @Override
        void warmStart() { applyLinear(a, b, rA, rB, u.mul(impulse)); }

        @Override
        void solve(double h, boolean useBias, PhysicsWorld w) {
            double cdot = relVel(a, b, rA, rB).dot(u);
            double target = Math.max(minLength, Math.min(maxLength, length));
            double c = currentLength - target;
            double old = impulse;
            switch (mode) {
                case ROD -> {
                    Soft s = w.jointSoft;
                    double imp = useBias ? -mass * s.massScale * (cdot + s.biasRate * c) - s.impulseScale * impulse : -mass * cdot;
                    impulse += imp;
                }
                case ROPE -> {
                    // only resists stretching beyond target: C = L - target <= 0
                    Soft s = w.jointSoft;
                    double bias = 0, ms = 1, is = 0;
                    if (c < 0) bias = c / h;
                    else if (useBias) {
                        bias = s.biasRate * c;
                        ms = s.massScale;
                        is = s.impulseScale;
                    }
                    double imp = -mass * ms * (cdot + bias) - is * impulse;
                    impulse = Math.min(0, impulse + imp);
                }
                case SPRING -> {
                    // a real spring: applied in the relax pass too
                    Soft s = physicalSoft(springStiffness, springDampingCoeff, mass, h);
                    double springC = currentLength - Math.max(minLength, length);
                    double imp = -mass * s.massScale * (cdot + s.biasRate * springC) - s.impulseScale * impulse;
                    impulse += imp;
                }
            }
            applyLinear(a, b, rA, rB, u.mul(impulse - old));
            if (mode == Mode.SPRING && maxLength < Double.POSITIVE_INFINITY) {
                // hard stop at max length (rope behaviour on top of the spring)
                double cd = relVel(a, b, rA, rB).dot(u);
                double c2 = currentLength - maxLength;
                double bias = c2 < 0 ? c2 / h : (useBias ? w.jointSoft.biasRate * c2 : 0);
                double imp = -mass * (cd + bias);
                double o2 = stopImpulse;
                stopImpulse = Math.min(0, stopImpulse + imp);
                applyLinear(a, b, rA, rB, u.mul(stopImpulse - o2));
            }
        }

        double stopImpulse;

        @Override
        void endSubstep() { tickLin += Math.abs(impulse) + Math.abs(stopImpulse); }

        @Override
        void onBodyShift(RigidBody body, V3 s) {
            if (body == a) localA = localA.sub(s);
            if (body == b) localB = localB.sub(s);
        }
    }
}
