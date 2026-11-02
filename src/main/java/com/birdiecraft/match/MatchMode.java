package com.birdiecraft.match;

public enum MatchMode {
    /** One player, stroke play. */
    SINGLE,
    /** One player against CPU golfers. */
    VS_CPU,
    /** Several players (plus optional CPUs) from a lobby. */
    MULTI;

    public static MatchMode byId(int id) {
        if (id < 0 || id >= values().length) return SINGLE;
        return values()[id];
    }

    public String translationKey() {
        return "mode.birdiecraft." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
