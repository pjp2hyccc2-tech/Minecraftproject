package com.mcbesiege.physics;

/** Immutable unit quaternion (w, x, y, z) for rigid body orientation. */
public final class Quat {
    public static final Quat IDENTITY = new Quat(1, 0, 0, 0);

    public final double w, x, y, z;

    public Quat(double w, double x, double y, double z) {
        this.w = w;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Quat fromAxisAngle(V3 axis, double angle) {
        V3 a = axis.norm();
        double h = angle * 0.5, s = Math.sin(h);
        return new Quat(Math.cos(h), a.x * s, a.y * s, a.z * s);
    }

    /** Shortest rotation taking unit vector a onto unit vector b. */
    public static Quat fromTo(V3 a, V3 b) {
        double d = a.dot(b);
        if (d > 0.999999) return IDENTITY;
        if (d < -0.999999) {
            V3 p = a.anyPerp();
            return new Quat(0, p.x, p.y, p.z);
        }
        V3 c = a.cross(b);
        return new Quat(1 + d, c.x, c.y, c.z).normalize();
    }

    public Quat mul(Quat q) {
        return new Quat(
                w * q.w - x * q.x - y * q.y - z * q.z,
                w * q.x + x * q.w + y * q.z - z * q.y,
                w * q.y - x * q.z + y * q.w + z * q.x,
                w * q.z + x * q.y - y * q.x + z * q.w);
    }

    public Quat conj() { return new Quat(w, -x, -y, -z); }

    public double dot(Quat q) { return w * q.w + x * q.x + y * q.y + z * q.z; }

    public Quat normalize() {
        double l = Math.sqrt(w * w + x * x + y * y + z * z);
        if (l < 1e-12) return IDENTITY;
        return new Quat(w / l, x / l, y / l, z / l);
    }

    public V3 rotate(V3 v) {
        // t = 2 * cross(q.xyz, v); v' = v + w * t + cross(q.xyz, t)
        double tx = 2 * (y * v.z - z * v.y);
        double ty = 2 * (z * v.x - x * v.z);
        double tz = 2 * (x * v.y - y * v.x);
        return new V3(
                v.x + w * tx + (y * tz - z * ty),
                v.y + w * ty + (z * tx - x * tz),
                v.z + w * tz + (x * ty - y * tx));
    }

    public V3 invRotate(V3 v) {
        double tx = 2 * (-y * v.z + z * v.y);
        double ty = 2 * (-z * v.x + x * v.z);
        double tz = 2 * (-x * v.y + y * v.x);
        return new V3(
                v.x + w * tx + (-y * tz + z * ty),
                v.y + w * ty + (-z * tx + x * tz),
                v.z + w * tz + (-x * ty + y * tx));
    }

    /** Integrates angular velocity (world frame) over h seconds. */
    public Quat integrate(V3 omega, double h) {
        double hx = omega.x * h * 0.5, hy = omega.y * h * 0.5, hz = omega.z * h * 0.5;
        // dq = (0, hw) * q
        double dw = -hx * x - hy * y - hz * z;
        double dx = hx * w + hy * z - hz * y;
        double dy = -hx * z + hy * w + hz * x;
        double dz = hx * y - hy * x + hz * w;
        return new Quat(w + dw, x + dx, y + dy, z + dz).normalize();
    }

    /** Small-angle rotation vector (axis * angle) of this quaternion. */
    public V3 toRotationVector() {
        Quat q = w < 0 ? new Quat(-w, -x, -y, -z) : this;
        double s = Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z);
        if (s < 1e-9) return new V3(2 * q.x, 2 * q.y, 2 * q.z);
        double angle = 2 * Math.atan2(s, q.w);
        return new V3(q.x / s * angle, q.y / s * angle, q.z / s * angle);
    }

    public static Quat slerp(Quat a, Quat b, double t) {
        double d = a.dot(b);
        Quat bb = b;
        if (d < 0) {
            d = -d;
            bb = new Quat(-b.w, -b.x, -b.y, -b.z);
        }
        if (d > 0.9995) {
            return new Quat(a.w + (bb.w - a.w) * t, a.x + (bb.x - a.x) * t, a.y + (bb.y - a.y) * t, a.z + (bb.z - a.z) * t).normalize();
        }
        double th = Math.acos(d), s = Math.sin(th);
        double wa = Math.sin((1 - t) * th) / s, wb = Math.sin(t * th) / s;
        return new Quat(a.w * wa + bb.w * wb, a.x * wa + bb.x * wb, a.y * wa + bb.y * wb, a.z * wa + bb.z * wb);
    }

    public boolean isFinite() {
        return Double.isFinite(w) && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    @Override
    public String toString() { return String.format("[%.3f, %.3f, %.3f, %.3f]", w, x, y, z); }
}
