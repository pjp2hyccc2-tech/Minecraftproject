package com.mcbesiege.physics;

import java.util.ArrayList;
import java.util.List;

/** A rigid body made of one or more colliders. Position is the world-space center of mass. */
public final class RigidBody {
    private static int nextId = 1;

    public final int id = nextId++;
    public final List<Collider> colliders = new ArrayList<>();

    public double mass, invMass;
    public M3 inertiaLocal = M3.ZERO;
    public M3 invInertiaLocal = M3.ZERO;
    public M3 invInertiaWorld = M3.ZERO;

    public V3 pos = V3.ZERO;
    public Quat rot = Quat.IDENTITY;
    public V3 vel = V3.ZERO;
    public V3 angVel = V3.ZERO;

    /** Forces/torques applied every substep of the current tick (cleared by the world after each tick). */
    public V3 force = V3.ZERO;
    public V3 torque = V3.ZERO;

    public double linearDamping = 0.05;
    public double angularDamping = 0.1;
    public double gravityScale = 1.0;
    public double maxSpeed = 160.0;
    public double maxAngularSpeed = 90.0;

    public boolean isStatic;
    /** Frozen bodies are skipped completely (e.g. in unloaded chunks). */
    public boolean frozen;
    public boolean sleeping;
    public boolean allowSleep = true;
    public int sleepTicks;

    /** Bodies with the same non-zero island id sleep/wake together (one machine). */
    public int island;
    /** Owner data (machine body record). */
    public Object userData;

    /** World boxes near this body for the current tick. */
    final List<StaticBox> staticCache = new ArrayList<>();
    double aabbMinX, aabbMinY, aabbMinZ, aabbMaxX, aabbMaxY, aabbMaxZ;

    // last valid state for NaN recovery
    private V3 safePos = V3.ZERO;
    private Quat safeRot = Quat.IDENTITY;

    public void addCollider(Collider c) {
        c.body = this;
        colliders.add(c);
    }

    public void removeCollider(Collider c) {
        colliders.remove(c);
        if (c.body == this) c.body = null;
    }

    /**
     * Recomputes mass, center of mass and inertia from the colliders. Collider local positions are re-expressed relative to the
     * new COM, the world position moves to the new COM and the linear velocity is carried over to it.
     *
     * @return shift of the COM in the old local frame (callers move local joint anchors by -shift)
     */
    public V3 recomputeMass() {
        double m = 0;
        V3 com = V3.ZERO;
        for (Collider c : colliders) {
            m += c.mass;
            com = com.addScaled(c.localPos, c.mass);
        }
        if (m <= 1e-9) {
            mass = 0;
            invMass = 0;
            inertiaLocal = M3.ZERO;
            invInertiaLocal = M3.ZERO;
            updateInertiaWorld();
            return V3.ZERO;
        }
        com = com.mul(1.0 / m);
        M3 inertia = M3.ZERO;
        for (Collider c : colliders) {
            c.localPos = c.localPos.sub(com);
            M3 r = M3.fromQuat(c.localRot);
            inertia = inertia.add(c.shapeInertia().rotated(r)).add(M3.parallelAxis(c.localPos, c.mass));
        }
        V3 worldShift = rot.rotate(com);
        pos = pos.add(worldShift);
        vel = vel.add(angVel.cross(worldShift));
        mass = m;
        invMass = isStatic ? 0 : 1.0 / m;
        // guard against degenerate (thin) inertia
        double tr = inertia.m00 + inertia.m11 + inertia.m22;
        double floor = Math.max(1e-6, tr * 0.02);
        inertia = new M3(Math.max(inertia.m00, floor), inertia.m01, inertia.m02,
                inertia.m10, Math.max(inertia.m11, floor), inertia.m12,
                inertia.m20, inertia.m21, Math.max(inertia.m22, floor));
        inertiaLocal = inertia;
        invInertiaLocal = isStatic ? M3.ZERO : inertia.inverse();
        updateInertiaWorld();
        return com;
    }

    public void updateInertiaWorld() {
        if (invMass == 0) {
            invInertiaWorld = M3.ZERO;
            return;
        }
        M3 r = M3.fromQuat(rot);
        invInertiaWorld = invInertiaLocal.rotated(r);
    }

    public M3 inertiaWorld() {
        return inertiaLocal.rotated(M3.fromQuat(rot));
    }

    public void updateColliders() {
        for (Collider c : colliders) c.updateWorld();
    }

    public V3 pointVelocity(V3 worldPoint) {
        return vel.add(angVel.cross(worldPoint.sub(pos)));
    }

    public void applyImpulse(V3 impulse, V3 worldPoint) {
        if (invMass == 0) return;
        vel = vel.addScaled(impulse, invMass);
        angVel = angVel.add(invInertiaWorld.mul(worldPoint.sub(pos).cross(impulse)));
    }

    public void applyAngularImpulse(V3 l) {
        if (invMass == 0) return;
        angVel = angVel.add(invInertiaWorld.mul(l));
    }

    /** Adds a force (applied during every substep of this tick) at a world point. */
    public void addForce(V3 f, V3 worldPoint) {
        force = force.add(f);
        torque = torque.add(worldPoint.sub(pos).cross(f));
    }

    public void addForce(V3 f) {
        force = force.add(f);
    }

    public void addTorque(V3 t) {
        torque = torque.add(t);
    }

    public V3 localToWorld(V3 local) { return pos.add(rot.rotate(local)); }

    public V3 worldToLocal(V3 world) { return rot.invRotate(world.sub(pos)); }

    public V3 dirToWorld(V3 local) { return rot.rotate(local); }

    public V3 dirToLocal(V3 world) { return rot.invRotate(world); }

    public boolean isDynamic() { return invMass > 0 && !frozen; }

    public void setTransform(V3 p, Quat q) {
        pos = p;
        rot = q.normalize();
        updateInertiaWorld();
        updateColliders();
        safePos = pos;
        safeRot = rot;
    }

    void markSafe() {
        safePos = pos;
        safeRot = rot;
    }

    /** Restores the last finite state if the solver produced NaNs. */
    boolean sanitize() {
        if (pos.isFinite() && rot.isFinite() && vel.isFinite() && angVel.isFinite()) return false;
        pos = safePos;
        rot = safeRot;
        vel = V3.ZERO;
        angVel = V3.ZERO;
        updateInertiaWorld();
        return true;
    }

    public void computeAabb() {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (Collider c : colliders) {
            x0 = Math.min(x0, c.minX);
            y0 = Math.min(y0, c.minY);
            z0 = Math.min(z0, c.minZ);
            x1 = Math.max(x1, c.maxX);
            y1 = Math.max(y1, c.maxY);
            z1 = Math.max(z1, c.maxZ);
        }
        if (colliders.isEmpty()) {
            x0 = x1 = pos.x;
            y0 = y1 = pos.y;
            z0 = z1 = pos.z;
        }
        aabbMinX = x0;
        aabbMinY = y0;
        aabbMinZ = z0;
        aabbMaxX = x1;
        aabbMaxY = y1;
        aabbMaxZ = z1;
    }

    public double[] aabb() { return new double[]{aabbMinX, aabbMinY, aabbMinZ, aabbMaxX, aabbMaxY, aabbMaxZ}; }

    public void wake() {
        sleeping = false;
        sleepTicks = 0;
    }
}
