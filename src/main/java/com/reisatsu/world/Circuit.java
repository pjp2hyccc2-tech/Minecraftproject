package com.reisatsu.world;

/** The breaker panel's circuits (docs/DESIGN.md §3.9). Owner: world. */
public enum Circuit {
    DINING, KITCHEN, RESTROOM, BACK, SIGN, CAMERAS, DOOR;

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
