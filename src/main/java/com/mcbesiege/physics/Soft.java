package com.mcbesiege.physics;

/** Soft constraint coefficients (Box2D v3 "soft step" formulation). */
public final class Soft {
    public static final Soft RIGID = new Soft(0, 1, 0);

    public final double biasRate, massScale, impulseScale;

    public Soft(double biasRate, double massScale, double impulseScale) {
        this.biasRate = biasRate;
        this.massScale = massScale;
        this.impulseScale = impulseScale;
    }

    public static Soft of(double hertz, double zeta, double h) {
        if (hertz <= 0) return RIGID;
        double omega = 2.0 * Math.PI * hertz;
        double a1 = 2.0 * zeta + h * omega;
        double a2 = h * omega * a1;
        double a3 = 1.0 / (1.0 + a2);
        return new Soft(omega / a1, a2 * a3, a3);
    }
}
