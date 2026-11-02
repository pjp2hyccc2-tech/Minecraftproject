package com.birdiecraft.golf.engine;

/** Golfer play style: trades distance, control (impact zone width) and spin. */
public enum PlayStyle {
    BALANCED(1.00, 1.00, 1.00),
    POWER(1.07, 0.84, 0.90),
    CONTROL(0.95, 1.25, 1.00),
    SPIN(0.98, 1.00, 1.45);

    public static final PlayStyle[] VALUES = values();

    public final double distance;
    public final double control;
    public final double spin;

    PlayStyle(double distance, double control, double spin) {
        this.distance = distance;
        this.control = control;
        this.spin = spin;
    }

    public static PlayStyle byId(int id) {
        if (id < 0 || id >= VALUES.length) return BALANCED;
        return VALUES[id];
    }

    public PlayStyle next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public String translationKey() {
        return "style.birdiecraft." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
