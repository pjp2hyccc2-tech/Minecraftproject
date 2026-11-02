package com.birdiecraft.golf.engine;

/**
 * Surface the ball rests on / bounces off. Holds both the "lie" rules (power range,
 * impact zone width, spin and launch changes) and the physical surface properties.
 */
public enum Lie {
    //            powMin powMax zone  spin  launch  rest  bFric  rollDec  grip  course
    TEE(          1.00, 1.00, 1.00, 1.00, 0.0,  0.32, 0.38, 0.0060, 1.00, true),
    FAIRWAY(      1.00, 1.00, 1.00, 1.00, 0.0,  0.30, 0.40, 0.0060, 1.00, true),
    FRINGE(       0.97, 1.00, 0.95, 0.95, 0.0,  0.26, 0.45, 0.0045, 1.00, true),
    GREEN(        1.00, 1.00, 1.00, 1.00, 0.0,  0.22, 0.42, 0.0028, 1.20, true),
    ROUGH(        0.84, 0.94, 0.85, 0.60, 1.0,  0.16, 0.62, 0.0150, 0.50, true),
    DEEP_ROUGH(   0.65, 0.80, 0.70, 0.35, 2.0,  0.08, 0.82, 0.0320, 0.20, true),
    BUNKER(       0.60, 0.78, 0.72, 0.50, 5.0,  0.03, 0.92, 0.0600, 0.10, true),
    CART_PATH(    0.95, 1.00, 0.90, 0.80, 0.0,  0.58, 0.15, 0.0030, 0.60, true),
    /** Vanilla grass, dirt, moss... (out of bounds when the server option says so). */
    NATURAL(      0.82, 0.94, 0.85, 0.60, 1.0,  0.20, 0.55, 0.0120, 0.50, false),
    /** Stone, concrete and other hard blocks. */
    HARD(         0.90, 1.00, 0.85, 0.80, 0.0,  0.55, 0.20, 0.0035, 0.50, false),
    /** Logs, planks. */
    WOOD(         0.85, 0.95, 0.80, 0.60, 0.0,  0.40, 0.30, 0.0060, 0.50, false),
    /** Snow and similar soft blocks. */
    SOFT(         0.65, 0.80, 0.70, 0.35, 2.0,  0.06, 0.85, 0.0350, 0.20, false),
    /** Vanilla sand / gravel (plays like a bunker but is not a golf surface). */
    NATURAL_SAND( 0.60, 0.80, 0.72, 0.50, 4.0,  0.05, 0.90, 0.0550, 0.10, false),
    /** Ice: the ball skids a long way. Resting on ice counts as a hazard. */
    ICE(          0.90, 1.00, 0.90, 0.80, 0.0,  0.45, 0.05, 0.0008, 0.05, false);

    public static final Lie[] VALUES = values();

    public final double powerMin;
    public final double powerMax;
    public final double zoneFactor;
    public final double spinFactor;
    public final double launchAdd;
    public final double restitution;
    public final double bounceFriction;
    public final double rollDecel;
    public final double spinGrip;
    /** True for the dedicated golf surfaces (always in bounds). */
    public final boolean courseSurface;

    Lie(double powerMin, double powerMax, double zoneFactor, double spinFactor, double launchAdd,
        double restitution, double bounceFriction, double rollDecel, double spinGrip, boolean courseSurface) {
        this.powerMin = powerMin;
        this.powerMax = powerMax;
        this.zoneFactor = zoneFactor;
        this.spinFactor = spinFactor;
        this.launchAdd = launchAdd;
        this.restitution = restitution;
        this.bounceFriction = bounceFriction;
        this.rollDecel = rollDecel;
        this.spinGrip = spinGrip;
        this.courseSurface = courseSurface;
    }

    public static Lie byId(int id) {
        if (id < 0 || id >= VALUES.length) return FAIRWAY;
        return VALUES[id];
    }

    /** Power range of this lie for the given club (sand wedges escape bunkers better). */
    public double minPower(Club club) {
        if (this == BUNKER && (club == Club.SW || club == Club.AW)) return 0.86;
        if (club.isPutter()) return this == GREEN || this == FRINGE ? 1.0 : Math.max(0.9, powerMin);
        return powerMin;
    }

    public double maxPower(Club club) {
        if (this == BUNKER && (club == Club.SW || club == Club.AW)) return 0.96;
        if (club.isPutter()) return 1.0;
        return powerMax;
    }

    public double spinFactor(Club club) {
        if (this == BUNKER && (club == Club.SW || club == Club.AW)) return 0.8;
        return spinFactor;
    }

    public double midPower(Club club) {
        return (minPower(club) + maxPower(club)) * 0.5;
    }

    /** True for lies that are treated like a water hazard when the ball stops there. */
    public boolean isHazard() {
        return this == ICE;
    }

    public boolean isGreen() {
        return this == GREEN;
    }

    public String translationKey() {
        return "lie.birdiecraft." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
