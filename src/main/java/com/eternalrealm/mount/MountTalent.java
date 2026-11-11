package com.eternalrealm.mount;

import com.eternalrealm.core.ModInfo;

/**
 * A breed's talent (特性, docs/modules/mounts.md): one small thing the breed does that its family otherwise does not,
 * within the mount rules (no breed fights, none is faster than the arcbike on the ground, none flies above the rules'
 * ceiling by more than {@value #HIGH_FLIGHT_EXTRA} blocks). Some talents are only the breed's numbers ({@link
 * #GREAT_LEAP}, {@link #LAND_STRIDER}, {@link #HOVER}, {@link #UPDRAFT}); the others change how it moves ({@link
 * MountMotion}) or where it may be called ({@link MountRules#waterStopsCall}). Ids are texts' keys: never rename them.
 */
public enum MountTalent {
    /** The three first mounts. */
    NONE("none"),
    /** Horses: leaps about four blocks high (its jump number). */
    GREAT_LEAP("great_leap"),
    /** Horses: stands and gallops on still water at full speed, and may be called while standing in water. */
    WAVE_RUNNER("wave_runner"),
    /** Horses: holding jump in the air, it spreads its wings and sinks slowly, keeping its speed. */
    GLIDE("glide"),
    /** Horses: after {@value #SURGE_TICKS} ticks at full gallop its fire flares up: {@value #SURGE_BOOST}x speed. */
    SURGE("surge"),
    /** Horses: jump again in the air (once until it lands). */
    SKY_STEP("sky_step"),
    /** Griffins: runs on the ground almost as fast as it flies (its ground speed). */
    LAND_STRIDER("land_strider"),
    /** Griffins: does not sink without input: it hangs in the air (its glide is nil). */
    HOVER("hover"),
    /** Griffins: sneak is a steep dive that gains {@value #DIVE_BOOST}x speed. */
    DIVE("dive"),
    /** Griffins: flies {@value #HIGH_FLIGHT_EXTRA} blocks higher above the ground. */
    HIGH_FLIGHT("high_flight"),
    /** Griffins: climbs and takes off much faster (its climb and take-off numbers). */
    UPDRAFT("updraft");

    /** Ticks at full gallop before a surge (2 seconds). */
    public static final int SURGE_TICKS = 40;
    /** Share of the top speed that counts as full gallop for a surge. */
    public static final double SURGE_THRESHOLD = 0.9;
    /** A surge's top speed against the breed's own. */
    public static final double SURGE_BOOST = 1.12;
    /** The second jump against the first. */
    public static final double SKY_STEP_FACTOR = 0.85;
    /** How fast a gliding horse sinks (blocks per tick). */
    public static final double GLIDE_SINK = 0.1;
    /** A dive's top speed against the breed's own. */
    public static final double DIVE_BOOST = 1.25;
    /** Extra flight height above the rules' ceiling (blocks). */
    public static final int HIGH_FLIGHT_EXTRA = 16;

    private final String id;

    MountTalent(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** The talent's name (empty texts are never shown: {@link #NONE} has no text). */
    public String translationKey() {
        return "mount." + ModInfo.MOD_ID + ".talent." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    /** The most a talent raises a mount's speed over its top speed (the server's watch allows for it). */
    public double peakFactor() {
        return switch (this) {
            case SURGE -> SURGE_BOOST;
            case DIVE -> DIVE_BOOST;
            default -> 1.0;
        };
    }
}
