package com.mcbesiege.physics;

/** A convex collision shape attached to a rigid body. Shapes are defined in body-local space relative to the body COM. */
public abstract class Collider {
    public enum Type { BOX, CYLINDER, SPHERE }

    private static int nextId = 1;

    public final int id = nextId++;
    public final Type type;
    public RigidBody body;
    /** Position of the shape center in body-local coordinates (relative to the body's center of mass). */
    public V3 localPos;
    public Quat localRot;
    public double mass;
    public double friction = 0.6;
    public double restitution = 0.05;
    /** Continuous collision against the world (for fast small projectiles). */
    public boolean ccd;
    /** If false the shape takes part in mass properties but never generates contacts. */
    public boolean solid = true;
    /** Arbitrary owner data (machine part). */
    public Object userData;

    // world-space cache, refreshed by updateWorld()
    public V3 wPos = V3.ZERO;
    public Quat wRot = Quat.IDENTITY;
    public M3 wR = M3.IDENTITY;
    public double minX, minY, minZ, maxX, maxY, maxZ;

    protected Collider(Type type, V3 localPos, Quat localRot, double mass) {
        this.type = type;
        this.localPos = localPos;
        this.localRot = localRot;
        this.mass = mass;
    }

    public void updateWorld() {
        wRot = body.rot.mul(localRot);
        wPos = body.pos.add(body.rot.rotate(localPos));
        wR = M3.fromQuat(wRot);
        computeAabb();
    }

    protected abstract void computeAabb();

    /** Inertia tensor about the shape center, in the shape's own frame. */
    public abstract M3 shapeInertia();

    /** Approximate volume, used for buoyancy. */
    public abstract double volume();

    public boolean aabbOverlaps(Collider o, double margin) {
        return minX - margin <= o.maxX && maxX + margin >= o.minX
                && minY - margin <= o.maxY && maxY + margin >= o.minY
                && minZ - margin <= o.maxZ && maxZ + margin >= o.minZ;
    }

    public boolean aabbOverlaps(StaticBox b, double margin) {
        return minX - margin <= b.maxX && maxX + margin >= b.minX
                && minY - margin <= b.maxY && maxY + margin >= b.minY
                && minZ - margin <= b.maxZ && maxZ + margin >= b.minZ;
    }

    // ------------------------------------------------------------------------------------------------------------

    public static final class Box extends Collider {
        public V3 half;

        public Box(V3 half, V3 localPos, Quat localRot, double mass) {
            super(Type.BOX, localPos, localRot, mass);
            this.half = half;
        }

        @Override
        protected void computeAabb() {
            double ex = Math.abs(wR.m00) * half.x + Math.abs(wR.m01) * half.y + Math.abs(wR.m02) * half.z;
            double ey = Math.abs(wR.m10) * half.x + Math.abs(wR.m11) * half.y + Math.abs(wR.m12) * half.z;
            double ez = Math.abs(wR.m20) * half.x + Math.abs(wR.m21) * half.y + Math.abs(wR.m22) * half.z;
            minX = wPos.x - ex;
            maxX = wPos.x + ex;
            minY = wPos.y - ey;
            maxY = wPos.y + ey;
            minZ = wPos.z - ez;
            maxZ = wPos.z + ez;
        }

        @Override
        public M3 shapeInertia() {
            double hx = half.x, hy = half.y, hz = half.z;
            return M3.diag(mass / 3.0 * (hy * hy + hz * hz), mass / 3.0 * (hx * hx + hz * hz), mass / 3.0 * (hx * hx + hy * hy));
        }

        @Override
        public double volume() { return 8 * half.x * half.y * half.z; }
    }

    /** Cylinder whose axis is the local Y axis. */
    public static final class Cylinder extends Collider {
        public double radius, halfHeight;

        public Cylinder(double radius, double halfHeight, V3 localPos, Quat localRot, double mass) {
            super(Type.CYLINDER, localPos, localRot, mass);
            this.radius = radius;
            this.halfHeight = halfHeight;
        }

        public V3 worldAxis() { return wR.col(1); }

        @Override
        protected void computeAabb() {
            V3 a = worldAxis();
            double ex = halfHeight * Math.abs(a.x) + radius * Math.sqrt(Math.max(0, 1 - a.x * a.x));
            double ey = halfHeight * Math.abs(a.y) + radius * Math.sqrt(Math.max(0, 1 - a.y * a.y));
            double ez = halfHeight * Math.abs(a.z) + radius * Math.sqrt(Math.max(0, 1 - a.z * a.z));
            minX = wPos.x - ex;
            maxX = wPos.x + ex;
            minY = wPos.y - ey;
            maxY = wPos.y + ey;
            minZ = wPos.z - ez;
            maxZ = wPos.z + ez;
        }

        @Override
        public M3 shapeInertia() {
            double r2 = radius * radius, h = 2 * halfHeight;
            double side = mass * (3 * r2 + h * h) / 12.0;
            return M3.diag(side, 0.5 * mass * r2, side);
        }

        @Override
        public double volume() { return Math.PI * radius * radius * 2 * halfHeight; }
    }

    public static final class Sphere extends Collider {
        public double radius;

        public Sphere(double radius, V3 localPos, double mass) {
            super(Type.SPHERE, localPos, Quat.IDENTITY, mass);
            this.radius = radius;
        }

        @Override
        protected void computeAabb() {
            minX = wPos.x - radius;
            maxX = wPos.x + radius;
            minY = wPos.y - radius;
            maxY = wPos.y + radius;
            minZ = wPos.z - radius;
            maxZ = wPos.z + radius;
        }

        @Override
        public M3 shapeInertia() {
            double i = 0.4 * mass * radius * radius;
            return M3.diag(i, i, i);
        }

        @Override
        public double volume() { return 4.0 / 3.0 * Math.PI * radius * radius * radius; }
    }
}
