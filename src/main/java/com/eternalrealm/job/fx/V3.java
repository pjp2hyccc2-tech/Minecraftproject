package com.eternalrealm.job.fx;

/**
 * A small immutable vector of the effects engine (world blocks; y is up). The engine is pure (no Minecraft), so the
 * client, the tests and the preview renderer draw exactly the same light.
 */
public record V3(double x, double y, double z) {
    public static final V3 ZERO = new V3(0.0, 0.0, 0.0);
    public static final V3 UP = new V3(0.0, 1.0, 0.0);

    public V3 add(V3 o) {
        return new V3(x + o.x, y + o.y, z + o.z);
    }

    public V3 add(double dx, double dy, double dz) {
        return new V3(x + dx, y + dy, z + dz);
    }

    public V3 sub(V3 o) {
        return new V3(x - o.x, y - o.y, z - o.z);
    }

    public V3 scale(double s) {
        return new V3(x * s, y * s, z * s);
    }

    /** {@code this + o * s}. */
    public V3 plus(V3 o, double s) {
        return new V3(x + o.x * s, y + o.y * s, z + o.z * s);
    }

    public double dot(V3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public V3 cross(V3 o) {
        return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double lengthSqr() {
        return x * x + y * y + z * z;
    }

    /** Unit length (or {@code fallback} for a vector too short to have a direction). */
    public V3 unit(V3 fallback) {
        double l = length();
        return l < 1.0E-9 ? fallback : new V3(x / l, y / l, z / l);
    }

    public V3 unit() {
        return unit(new V3(0.0, 0.0, 1.0));
    }

    public V3 lerp(V3 o, double t) {
        return new V3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    public double distance(V3 o) {
        return sub(o).length();
    }

    /** Turned around the vertical axis by {@code radians} (counter-clockwise seen from above). */
    public V3 yaw(double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new V3(x * c - z * s, y, x * s + z * c);
    }

    /** Rotated around the unit axis {@code k} by {@code radians} (Rodrigues). */
    public V3 rotate(V3 k, double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        V3 kxv = k.cross(this);
        double kdv = k.dot(this);
        return new V3(x * c + kxv.x * s + k.x * kdv * (1.0 - c), y * c + kxv.y * s + k.y * kdv * (1.0 - c),
                z * c + kxv.z * s + k.z * kdv * (1.0 - c));
    }

    /** Horizontal part. */
    public V3 flat() {
        return new V3(x, 0.0, z);
    }

    public boolean finite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    /** Two unit vectors perpendicular to {@code dir} and to each other. */
    public static V3[] basis(V3 dir) {
        V3 d = dir.unit();
        V3 ref = Math.abs(d.y) > 0.95 ? new V3(1.0, 0.0, 0.0) : UP;
        V3 u = d.cross(ref).unit();
        return new V3[]{u, u.cross(d).unit()};
    }
}
