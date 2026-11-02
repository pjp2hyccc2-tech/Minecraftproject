package com.birdiecraft.golf.engine;

/** Visual / handling family of a club. The ordinal is used as the item model predicate. */
public enum ClubFamily {
    DRIVER(0.86),
    WOOD(0.92),
    IRON(1.0),
    WEDGE(1.10),
    PUTTER(1.35);

    /** Multiplier for the impact zone width on the swing gauge. */
    public final double zoneFactor;

    ClubFamily(double zoneFactor) {
        this.zoneFactor = zoneFactor;
    }

    /** Value used by the item model override predicate (0.0 .. 1.0). */
    public float modelValue() {
        return ordinal() / 4.0f;
    }
}
