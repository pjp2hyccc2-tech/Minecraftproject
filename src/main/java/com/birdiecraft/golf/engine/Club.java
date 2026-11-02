package com.birdiecraft.golf.engine;

/**
 * The club set. Carry distances are in yards for a BALANCED golfer at 100% power,
 * NORMAL shot, perfect lie and no wind.
 */
public enum Club {
    DRIVER("1W", 250, 15.0, 0.45, ClubFamily.DRIVER),
    WOOD3("3W", 228, 16.5, 0.55, ClubFamily.WOOD),
    WOOD5("5W", 212, 18.0, 0.65, ClubFamily.WOOD),
    IRON3("3I", 198, 19.5, 0.75, ClubFamily.IRON),
    IRON4("4I", 188, 21.0, 0.82, ClubFamily.IRON),
    IRON5("5I", 177, 23.0, 0.90, ClubFamily.IRON),
    IRON6("6I", 166, 25.5, 0.98, ClubFamily.IRON),
    IRON7("7I", 155, 28.5, 1.06, ClubFamily.IRON),
    IRON8("8I", 143, 32.0, 1.14, ClubFamily.IRON),
    IRON9("9I", 131, 36.0, 1.22, ClubFamily.IRON),
    PW("PW", 117, 41.0, 1.32, ClubFamily.WEDGE),
    AW("AW", 100, 46.0, 1.42, ClubFamily.WEDGE),
    SW("SW", 82, 52.0, 1.52, ClubFamily.WEDGE),
    PUTTER("PT", 0, 0.0, 0.0, ClubFamily.PUTTER);

    public static final Club[] VALUES = values();

    public final String label;
    public final int maxCarryYards;
    public final double launchDeg;
    public final double baseSpin;
    public final ClubFamily family;

    Club(String label, int maxCarryYards, double launchDeg, double baseSpin, ClubFamily family) {
        this.label = label;
        this.maxCarryYards = maxCarryYards;
        this.launchDeg = launchDeg;
        this.baseSpin = baseSpin;
        this.family = family;
    }

    public boolean isPutter() {
        return this == PUTTER;
    }

    public boolean isWedge() {
        return family == ClubFamily.WEDGE;
    }

    public static Club byId(int id) {
        if (id < 0 || id >= VALUES.length) return DRIVER;
        return VALUES[id];
    }

    public Club next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public Club prev() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public String translationKey() {
        return "club.birdiecraft." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
