package com.eternalrealm.world.gen;

import com.eternalrealm.world.field.FieldLayout;

/**
 * The ten fields of アストリア around the capital at (0, 0), as the world generator sees them (the gameplay side is
 * {@code world.Areas}; both use {@link FieldLayout}, a test keeps them equal).
 */
public final class AstoriaAreas {
    public static final int COUNT = FieldLayout.FIELD_COUNT;

    /**
     * Slugs used in translation keys ({@code area.eternalrealm.<slug>}), indexed by field id - 1. They are the slugs of
     * the gameplay world module ({@code world.Areas}); the texts live in docs/i18n/world.json.
     */
    private static final String[] SLUGS = {"arcelia", "elder_forest", "sandria_desert", "nordia_snowfield",
            "dragonia_volcano", "celestia", "tower_of_the_end", "fertile_farm", "lakeside", "wind_harbour"};

    private AstoriaAreas() {
    }

    /** @return the field (1..{@value FieldLayout#FIELD_COUNT}) at the horizontal position {@code (x, z)} */
    public static int areaAt(double x, double z) {
        return FieldLayout.areaAt(x, z);
    }

    public static String slug(int area) {
        return SLUGS[checked(area) - 1];
    }

    /** Field title, e.g. {@code area.eternalrealm.arcelia} = はじまりの草原. */
    public static String titleKey(int area) {
        return "area.eternalrealm." + slug(area);
    }

    /** Field description line shown under the title ({@code area.eternalrealm.<slug>.desc}). */
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
