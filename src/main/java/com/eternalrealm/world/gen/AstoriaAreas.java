package com.eternalrealm.world.gen;

/**
 * The seven concentric areas of アストリア around the capital at (0, 0), by horizontal distance from the origin.
 * Area {@code n} (1..6) covers distances {@code [300(n-1), 300n)}; area 7 is everything from 1800 outwards.
 *
 * <p>These exact rings are the gameplay rule (level ranges, quests, messages). Biome borders follow the same rings
 * but jitter by up to {@link AstoriaLayout#MAX_BORDER_JITTER} blocks so the landscape has no hard circles.</p>
 */
public final class AstoriaAreas {
    public static final int COUNT = 7;
    public static final int RING_WIDTH = 300;

    /**
     * Slugs used in translation keys ({@code area.eternalrealm.<slug>}), indexed by area number - 1. They are the slugs of
     * the gameplay world module ({@code world.Areas}); the texts live in docs/i18n/world.json.
     */
    private static final String[] SLUGS = {"arcelia", "elder_forest", "sandria_desert", "nordia_snowfield",
            "dragonia_volcano", "celestia", "tower_of_the_end"};

    private AstoriaAreas() {
    }

    /** @return the area (1..7) at the horizontal position {@code (x, z)} */
    public static int areaAt(double x, double z) {
        return areaAtDistance(Math.sqrt(x * x + z * z));
    }

    /** @return the area (1..7) at a horizontal distance from the capital; negative or NaN distances count as 0 */
    public static int areaAtDistance(double distance) {
        if (!(distance > 0.0)) {
            return 1;
        }
        double ring = Math.floor(distance / RING_WIDTH) + 1.0;
        return ring >= COUNT ? COUNT : (int) ring;
    }

    /** Inclusive inner radius of an area. */
    public static int innerRadius(int area) {
        return (checked(area) - 1) * RING_WIDTH;
    }

    /** Exclusive outer radius of an area; {@link Integer#MAX_VALUE} for the outermost area. */
    public static int outerRadius(int area) {
        return checked(area) == COUNT ? Integer.MAX_VALUE : area * RING_WIDTH;
    }

    public static String slug(int area) {
        return SLUGS[checked(area) - 1];
    }

    /** Area title, e.g. {@code area.eternalrealm.arcelia} = アルセリア王国. */
    public static String titleKey(int area) {
        return "area.eternalrealm." + slug(area);
    }

    /** Area description line shown under the title ({@code area.eternalrealm.<slug>.desc}). */
    public static String subtitleKey(int area) {
        return titleKey(area) + ".desc";
    }

    private static int checked(int area) {
        if (area < 1 || area > COUNT) {
            throw new IllegalArgumentException("area must be 1.." + COUNT + ": " + area);
        }
        return area;
    }
}
