package com.frostbound.core;

/** Difficulty presets chosen when the city is founded (provisional balance values). */
public enum Difficulty {
    EASY(8.0, 1.6, 0.85, 0.6, 0.7),
    NORMAL(0.0, 1.0, 1.0, 1.0, 1.0),
    HARD(-6.0, 0.7, 1.15, 1.4, 1.35);

    /** Added to every outside temperature (degrees C). */
    public final double tempOffset;
    /** Multiplier for the starting stockpile. */
    public final double startResources;
    /** Multiplier for generator coal burn. */
    public final double coalBurn;
    /** Multiplier for harmful event outcomes. */
    public final double eventSeverity;
    /** Multiplier for illness and cold damage. */
    public final double healthRisk;

    Difficulty(double tempOffset, double startResources, double coalBurn, double eventSeverity, double healthRisk) {
        this.tempOffset = tempOffset;
        this.startResources = startResources;
        this.coalBurn = coalBurn;
        this.eventSeverity = eventSeverity;
        this.healthRisk = healthRisk;
    }

    public String key() {
        return "frostbound.difficulty." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
