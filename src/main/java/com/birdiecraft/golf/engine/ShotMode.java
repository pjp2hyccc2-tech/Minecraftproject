package com.birdiecraft.golf.engine;

/** How the swing is played. Putts ignore the mode. */
public enum ShotMode {
    /** Regular full swing. */
    NORMAL(1.00, 0.0, 1.00, 1.00),
    /** Consumes one power-shot charge: more distance, faster gauge, narrow impact zone. */
    POWER(1.10, -0.5, 0.62, 1.30),
    /** Half-scale gauge for precise pitches and chips, higher launch. */
    APPROACH(0.50, 3.0, 1.20, 0.85);

    public static final ShotMode[] VALUES = values();

    public final double distanceFactor;
    public final double launchAdd;
    public final double zoneFactor;
    public final double gaugeSpeedFactor;

    ShotMode(double distanceFactor, double launchAdd, double zoneFactor, double gaugeSpeedFactor) {
        this.distanceFactor = distanceFactor;
        this.launchAdd = launchAdd;
        this.zoneFactor = zoneFactor;
        this.gaugeSpeedFactor = gaugeSpeedFactor;
    }

    public static ShotMode byId(int id) {
        if (id < 0 || id >= VALUES.length) return NORMAL;
        return VALUES[id];
    }

    public String translationKey() {
        return "shotmode.birdiecraft." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
