package com.reisatsu.world;

/** The breaker panel's circuits (docs/DESIGN.md §3.9). Owner: world. */
public enum Circuit {
    DINING(false), KITCHEN(false), RESTROOM(true), BACK(true), SIGN(true), CAMERAS(true), DOOR(true);

    private final boolean startOn;

    Circuit(boolean startOn) {
        this.startOn = startOn;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Switch position when the night begins (§3.9): dining and kitchen off, everything else on. */
    public boolean startOn() {
        return startOn;
    }

    public static Circuit byKey(String key) {
        for (Circuit c : values()) if (c.key().equals(key)) return c;
        return null;
    }

    /** The circuit that powers the lights of a room (Layout room key), or null for the sealed cavity. */
    public static Circuit ofRoom(String roomKey) {
        if (roomKey == null) return SIGN;
        return switch (roomKey) {
            case "vestibule", "waiting", "dining" -> DINING;
            case "kitchen", "fridge" -> KITCHEN;
            case "mens", "womens" -> RESTROOM;
            case "corridor", "staff", "office", "storage" -> BACK;
            default -> null;
        };
    }
}
