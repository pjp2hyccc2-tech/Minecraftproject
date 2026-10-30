package com.mcbesiege.physics;

/** Immutable 3x3 matrix (row-major) for inertia tensors and rotations. */
public final class M3 {
    public static final M3 ZERO = new M3(0, 0, 0, 0, 0, 0, 0, 0, 0);
    public static final M3 IDENTITY = new M3(1, 0, 0, 0, 1, 0, 0, 0, 1);

    public final double m00, m01, m02, m10, m11, m12, m20, m21, m22;

    public M3(double m00, double m01, double m02, double m10, double m11, double m12, double m20, double m21, double m22) {
        this.m00 = m00;
        this.m01 = m01;
        this.m02 = m02;
        this.m10 = m10;
        this.m11 = m11;
        this.m12 = m12;
        this.m20 = m20;
        this.m21 = m21;
        this.m22 = m22;
    }

    public static M3 diag(double a, double b, double c) { return new M3(a, 0, 0, 0, b, 0, 0, 0, c); }

    public static M3 fromQuat(Quat q) {
        double xx = q.x * q.x, yy = q.y * q.y, zz = q.z * q.z;
        double xy = q.x * q.y, xz = q.x * q.z, yz = q.y * q.z;
        double wx = q.w * q.x, wy = q.w * q.y, wz = q.w * q.z;
        return new M3(
                1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy),
                2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx),
                2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy));
    }

    /** Matrix with the given vectors as columns. */
    public static M3 cols(V3 a, V3 b, V3 c) { return new M3(a.x, b.x, c.x, a.y, b.y, c.y, a.z, b.z, c.z); }

    public V3 col(int i) {
        return i == 0 ? new V3(m00, m10, m20) : (i == 1 ? new V3(m01, m11, m21) : new V3(m02, m12, m22));
    }

    public V3 mul(V3 v) {
        return new V3(m00 * v.x + m01 * v.y + m02 * v.z, m10 * v.x + m11 * v.y + m12 * v.z, m20 * v.x + m21 * v.y + m22 * v.z);
    }

    /** Transposed multiply: M^T v. */
    public V3 mulT(V3 v) {
        return new V3(m00 * v.x + m10 * v.y + m20 * v.z, m01 * v.x + m11 * v.y + m21 * v.z, m02 * v.x + m12 * v.y + m22 * v.z);
    }

    public M3 mul(M3 o) {
        return new M3(
                m00 * o.m00 + m01 * o.m10 + m02 * o.m20, m00 * o.m01 + m01 * o.m11 + m02 * o.m21, m00 * o.m02 + m01 * o.m12 + m02 * o.m22,
                m10 * o.m00 + m11 * o.m10 + m12 * o.m20, m10 * o.m01 + m11 * o.m11 + m12 * o.m21, m10 * o.m02 + m11 * o.m12 + m12 * o.m22,
                m20 * o.m00 + m21 * o.m10 + m22 * o.m20, m20 * o.m01 + m21 * o.m11 + m22 * o.m21, m20 * o.m02 + m21 * o.m12 + m22 * o.m22);
    }

    public M3 transpose() { return new M3(m00, m10, m20, m01, m11, m21, m02, m12, m22); }

    public M3 add(M3 o) {
        return new M3(m00 + o.m00, m01 + o.m01, m02 + o.m02, m10 + o.m10, m11 + o.m11, m12 + o.m12, m20 + o.m20, m21 + o.m21, m22 + o.m22);
    }

    public M3 scale(double s) { return new M3(m00 * s, m01 * s, m02 * s, m10 * s, m11 * s, m12 * s, m20 * s, m21 * s, m22 * s); }

    public double det() {
        return m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20);
    }

    /** Inverse, or a zero matrix if singular. */
    public M3 inverse() {
        double d = det();
        if (Math.abs(d) < 1e-14) return ZERO;
        double id = 1.0 / d;
        return new M3(
                (m11 * m22 - m12 * m21) * id, (m02 * m21 - m01 * m22) * id, (m01 * m12 - m02 * m11) * id,
                (m12 * m20 - m10 * m22) * id, (m00 * m22 - m02 * m20) * id, (m02 * m10 - m00 * m12) * id,
                (m10 * m21 - m11 * m20) * id, (m01 * m20 - m00 * m21) * id, (m00 * m11 - m01 * m10) * id);
    }

    /** R * this * R^T (rotating a tensor). */
    public M3 rotated(M3 r) { return r.mul(this).mul(r.transpose()); }

    /** Skew-symmetric cross product matrix [v]x. */
    public static M3 skew(V3 v) { return new M3(0, -v.z, v.y, v.z, 0, -v.x, -v.y, v.x, 0); }

    /** Inertia contribution of point mass m at offset d: m (|d|^2 E - d d^T). */
    public static M3 parallelAxis(V3 d, double m) {
        double dd = d.lenSq();
        return new M3(
                m * (dd - d.x * d.x), -m * d.x * d.y, -m * d.x * d.z,
                -m * d.y * d.x, m * (dd - d.y * d.y), -m * d.y * d.z,
                -m * d.z * d.x, -m * d.z * d.y, m * (dd - d.z * d.z));
    }
}
