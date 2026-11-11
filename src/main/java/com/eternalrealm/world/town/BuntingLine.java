package com.eternalrealm.world.town;

/**
 * The cord of a string of bunting: a catenary from the anchor (the origin) to the far end {@code (dx, dy, dz)} that
 * hangs {@code sag} blocks below the straight chord at mid-span, parametrised by arc length so decorations can be
 * spaced evenly along it. The whole cord swings about its chord like a pendulum (see {@link #swing}); both ends stay
 * where they are and no length changes.
 *
 * <p>Coordinates are blocks, x east, y up, z south, relative to the anchor. A cord that is too short horizontally to
 * sag (nearly vertical), or one with no sag, is a straight line.
 */
public final class BuntingLine {
    /** Longest supported reach of a string along each axis, in blocks. */
    public static final int MAX_REACH = 32;
    /** The deepest sag the cord takes, in blocks (and never deeper than the horizontal span). */
    public static final double MAX_SAG = 8.0;
    private static final double MIN_SAG = 0.01;
    private static final double MIN_SPAN = 0.25;

    private final double dx;
    private final double dy;
    private final double dz;
    private final double span;
    private final double hx;
    private final double hz;
    private final double sag;
    private final boolean straight;
    // catenary y(x) = a * cosh((x - x0) / a) + c over the horizontal distance x in [0, span]
    private final double a;
    private final double x0;
    private final double c;
    private final double sinhStart;
    private final double length;
    // unit chord: the swing axis
    private final double ux;
    private final double uy;
    private final double uz;

    private BuntingLine(double dx, double dy, double dz, double requestedSag) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        this.span = horizontal;
        if (horizontal > 1e-9) {
            this.hx = dx / horizontal;
            this.hz = dz / horizontal;
        } else {
            this.hx = 1.0;
            this.hz = 0.0;
        }
        double chord = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (chord > 1e-9) {
            this.ux = dx / chord;
            this.uy = dy / chord;
            this.uz = dz / chord;
        } else {
            this.ux = 1.0;
            this.uy = 0.0;
            this.uz = 0.0;
        }
        double wanted = Double.isFinite(requestedSag) ? Math.min(Math.max(requestedSag, 0.0), Math.min(MAX_SAG, horizontal))
                : 0.0;
        if (horizontal < MIN_SPAN || wanted < MIN_SAG) {
            this.straight = true;
            this.sag = 0.0;
            this.a = Double.POSITIVE_INFINITY;
            this.x0 = 0.0;
            this.c = 0.0;
            this.sinhStart = 0.0;
            this.length = chord;
            return;
        }
        double param = solve(horizontal, dy, wanted);
        double h = horizontal / (2.0 * param);
        double mu = asinh(dy / (2.0 * param * Math.sinh(h)));
        this.straight = false;
        this.a = param;
        this.x0 = horizontal / 2.0 - param * mu;
        this.c = -param * Math.cosh(x0 / param);
        this.sinhStart = Math.sinh(-x0 / param);
        this.sag = sagOf(horizontal, dy, param);
        this.length = param * (Math.sinh((horizontal - x0) / param) - sinhStart);
    }

    /**
     * The cord from the anchor to {@code (dx, dy, dz)} sagging {@code sag} blocks at mid-span (clamped to
     * 0 .. {@link #MAX_SAG} and to the horizontal span).
     */
    public static BuntingLine of(double dx, double dy, double dz, double sag) {
        return new BuntingLine(dx, dy, dz, sag);
    }

    /** Arc length of the cord in blocks. */
    public double length() {
        return length;
    }

    /** The sag actually used (0 for a straight cord). */
    public double sag() {
        return sag;
    }

    public boolean straight() {
        return straight;
    }

    /** Horizontal distance between the ends. */
    public double span() {
        return span;
    }

    public double dx() {
        return dx;
    }

    public double dy() {
        return dy;
    }

    public double dz() {
        return dz;
    }

    /** Unit vector along the chord (the swing axis): x, y, z for {@code i} = 0, 1, 2. */
    public double axis(int i) {
        return i == 0 ? ux : i == 1 ? uy : uz;
    }

    /**
     * Rest position and tangent at arc length {@code s} (clamped to the cord): {@code out[o..o+2]} = position
     * relative to the anchor, {@code out[o+3..o+5]} = unit tangent pointing towards the far end.
     */
    public void at(double s, double[] out, int o) {
        double t = Math.max(0.0, Math.min(length, s));
        if (straight) {
            out[o] = ux * t;
            out[o + 1] = uy * t;
            out[o + 2] = uz * t;
            out[o + 3] = ux;
            out[o + 4] = uy;
            out[o + 5] = uz;
            return;
        }
        double sh = t / a + sinhStart;
        double x = x0 + a * asinh(sh);
        double y = a * Math.cosh((x - x0) / a) + c;
        // slope dy/dx = sinh((x - x0) / a) = sh, so the tangent is (h, sh) / sqrt(1 + sh^2)
        double norm = 1.0 / Math.sqrt(1.0 + sh * sh);
        out[o] = hx * x;
        out[o + 1] = y;
        out[o + 2] = hz * x;
        out[o + 3] = hx * norm;
        out[o + 4] = sh * norm;
        out[o + 5] = hz * norm;
    }

    /** How far the rest cord hangs below the straight chord at arc length {@code s} (0 at both ends). */
    public double depthAt(double s) {
        if (straight) {
            return 0.0;
        }
        double t = Math.max(0.0, Math.min(length, s));
        double x = x0 + a * asinh(t / a + sinhStart);
        double y = a * Math.cosh((x - x0) / a) + c;
        return Math.max(0.0, dy * x / span - y);
    }

    /**
     * Rotates the vector at {@code v[o..o+2]} in place about the chord by the angle whose cosine and sine are given:
     * the swing of the whole cord about the line through both ends (positive angles carry the low middle of a level
     * cord towards {@code chord x down}). Apply it to positions relative to the anchor and to tangents alike.
     */
    public void swing(double cos, double sin, double[] v, int o) {
        double x = v[o];
        double y = v[o + 1];
        double z = v[o + 2];
        double dot = ux * x + uy * y + uz * z;
        double cx = uy * z - uz * y;
        double cy = uz * x - ux * z;
        double cz = ux * y - uy * x;
        double k = dot * (1.0 - cos);
        v[o] = x * cos + cx * sin + ux * k;
        v[o + 1] = y * cos + cy * sin + uy * k;
        v[o + 2] = z * cos + cz * sin + uz * k;
    }

    /**
     * How much a wind blowing along the unit horizontal vector {@code (windX, windZ)} pushes the cord sideways, -1..1:
     * the component of {@code chord x down} along the wind (a cord running across the wind swings most, one along it
     * not at all; positive values mean a positive {@link #swing} angle moves the cord downwind).
     */
    public double crossWind(double windX, double windZ) {
        // chord x (0, -1, 0) = (uz, 0, -ux)
        return uz * windX - ux * windZ;
    }

    /** Depth of the catenary below its chord at mid-span for the parameter {@code a}. */
    static double sagOf(double span, double rise, double a) {
        double h = span / (2.0 * a);
        double sinhH = Math.sinh(h);
        double q = rise / (2.0 * a * sinhH);
        return a * Math.sqrt(1.0 + q * q) * (Math.cosh(h) - 1.0);
    }

    /** The catenary parameter giving the wanted sag (the sag shrinks as the parameter grows). */
    private static double solve(double span, double rise, double wanted) {
        double lo = Math.log(span * 1e-3);
        double hi = Math.log(span * 1e7);
        for (int i = 0; i < 90; i++) {
            double mid = 0.5 * (lo + hi);
            if (sagOf(span, rise, Math.exp(mid)) > wanted) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return Math.exp(0.5 * (lo + hi));
    }

    private static double asinh(double v) {
        return v >= 0.0 ? Math.log(v + Math.sqrt(v * v + 1.0)) : -Math.log(-v + Math.sqrt(v * v + 1.0));
    }
}
