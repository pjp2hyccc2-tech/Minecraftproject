package com.frostbound.core.weather;

import java.util.Locale;

/** The six weather states (provisional values). */
public enum WeatherState {
    CLEAR(0, 1.00, 1.00),
    SNOW(-3, 0.95, 0.90),
    WIND(-6, 0.90, 0.80),
    BLIZZARD(-12, 0.60, 0.40),
    EXTREME_COLD(-16, 0.75, 0.60),
    GREAT_COLD_WAVE(-21, 0.50, 0.30);

    /** Added to the outside temperature (degrees C). */
    public final double tempOffset;
    /** Output multiplier for outdoor work (hunting, fishing). */
    public final double outdoorWork;
    /** Travel speed multiplier for expeditions. */
    public final double travel;

    WeatherState(double tempOffset, double outdoorWork, double travel) {
        this.tempOffset = tempOffset;
        this.outdoorWork = outdoorWork;
        this.travel = travel;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return "frostbound.weather." + id();
    }

    /** True for states that should look and sound like a storm. */
    public boolean stormy() {
        return this == BLIZZARD || this == GREAT_COLD_WAVE;
    }

    /** Whether vanilla snowfall should be visible. */
    public boolean snowing() {
        return this != CLEAR && this != EXTREME_COLD;
    }
}
