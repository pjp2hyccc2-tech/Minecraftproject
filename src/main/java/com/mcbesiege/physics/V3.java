package com.mcbesiege.physics;

/** Immutable double-precision 3D vector used by the physics engine (independent of Minecraft classes). */
public final class V3 {
    public static final V3 ZERO = new V3(0, 0, 0);
    public static final V3 X = new V3(1, 0, 0);
    public static final V3 Y = new V3(0, 1, 0);
    public static final V3 Z = new V3(0, 0, 1);

    public final double x, y, z;

    public V3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public V3 add(V3 o) { return new V3(x + o.x, y + o.y, z + o.z); }

    public V3 add(double ax, double ay, double az) { return new V3(x + ax, y + ay, z + az); }

    public V3 sub(V3 o) { return new V3(x - o.x, y - o.y, z - o.z); }

    public V3 mul(double s) { return new V3(x * s, y * s, z * s); }

    public V3 mul(V3 o) { return new V3(x * o.x, y * o.y, z * o.z); }

    public V3 neg() { return new V3(-x, -y, -z); }

    public V3 addScaled(V3 o, double s) { return new V3(x + o.x * s, y + o.y * s, z + o.z * s); }

    public double dot(V3 o) { return x * o.x + y * o.y + z * o.z; }

    public V3 cross(V3 o) { return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }

    public double lenSq() { return x * x + y * y + z * z; }

    public double len() { return Math.sqrt(x * x + y * y + z * z); }

    public V3 norm() {
        double l = len();
        return l < 1e-12 ? ZERO : new V3(x / l, y / l, z / l);
    }

    public double distSq(V3 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double dist(V3 o) { return Math.sqrt(distSq(o)); }

    public double get(int i) { return i == 0 ? x : (i == 1 ? y : z); }

    public V3 abs() { return new V3(Math.abs(x), Math.abs(y), Math.abs(z)); }

    public V3 min(V3 o) { return new V3(Math.min(x, o.x), Math.min(y, o.y), Math.min(z, o.z)); }

    public V3 max(V3 o) { return new V3(Math.max(x, o.x), Math.max(y, o.y), Math.max(z, o.z)); }

    public boolean isFinite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }

    /** Component of this vector perpendicular to the unit vector n. */
    public V3 perp(V3 n) { return addScaled(n, -dot(n)); }

    /** Any unit vector perpendicular to this (assumed unit) vector. */
    public V3 anyPerp() {
        V3 t = Math.abs(x) < 0.6 ? X : (Math.abs(y) < 0.6 ? Y : Z);
        return cross(t).norm();
    }

    public V3 clampLen(double max) {
        double l2 = lenSq();
        if (l2 <= max * max) return this;
        return mul(max / Math.sqrt(l2));
    }

    public static V3 lerp(V3 a, V3 b, double t) {
        return new V3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    public static V3 axis(int i) { return i == 0 ? X : (i == 1 ? Y : Z); }

    @Override
    public String toString() { return String.format("(%.3f, %.3f, %.3f)", x, y, z); }
}
