package com.birdiecraft.course;

/** The ten built-in original course designs. Each one is deterministic (fixed seed). */
public enum CoursePreset {
    SUNRISE_MEADOW("sunrise_meadow", CourseTheme.MEADOW, 1, 0x5A11_0001L, 0.92, new int[]{4, 3, 4, 5, 4, 3, 4, 4, 5}),
    CORAL_BAY("coral_bay", CourseTheme.COAST, 2, 0x5A11_0002L, 1.0, new int[]{4, 4, 3, 5, 4, 4, 3, 5, 4}),
    MAPLE_RIDGE("maple_ridge", CourseTheme.AUTUMN, 2, 0x5A11_0003L, 0.97, new int[]{4, 4, 3, 4, 5, 3, 4, 4, 5}),
    SAKURA_VALLEY("sakura_valley", CourseTheme.SAKURA, 2, 0x5A11_0004L, 0.96, new int[]{4, 3, 5, 4, 4, 3, 4, 5, 4}),
    DESERT_MIRAGE("desert_mirage", CourseTheme.DESERT, 3, 0x5A11_0005L, 1.02, new int[]{4, 5, 3, 4, 4, 3, 5, 4, 4}),
    FROST_PEAK("frost_peak", CourseTheme.SNOW, 3, 0x5A11_0006L, 1.0, new int[]{4, 3, 4, 4, 5, 4, 3, 4, 5}),
    VOLCANO_ISLE("volcano_isle", CourseTheme.VOLCANIC, 4, 0x5A11_0007L, 1.02, new int[]{4, 4, 3, 5, 4, 3, 4, 5, 4}),
    MISTWOOD("mistwood", CourseTheme.MYSTIC, 4, 0x5A11_0008L, 1.0, new int[]{4, 4, 3, 4, 4, 5, 3, 4, 5}),
    CANYON_CROSS("canyon_cross", CourseTheme.CANYON, 4, 0x5A11_0009L, 1.05, new int[]{4, 3, 5, 4, 4, 3, 4, 5, 4}),
    ROYAL_CROWN("royal_crown", CourseTheme.ROYAL, 5, 0x5A11_000AL, 1.10, new int[]{4, 4, 3, 5, 4, 4, 3, 5, 4});

    public static final CoursePreset[] VALUES = values();

    public final String id;
    public final CourseTheme theme;
    public final int stars;
    public final long seed;
    public final double lengthScale;
    public final int[] pars;

    CoursePreset(String id, CourseTheme theme, int stars, long seed, double lengthScale, int[] pars) {
        this.id = id;
        this.theme = theme;
        this.stars = stars;
        this.seed = seed;
        this.lengthScale = lengthScale;
        this.pars = pars;
    }

    public int totalPar() {
        int p = 0;
        for (int x : pars) p += x;
        return p;
    }

    public static CoursePreset byId(String id) {
        for (CoursePreset p : VALUES) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    public String translationKey() {
        return "preset.birdiecraft." + id;
    }

    public String descriptionKey() {
        return "preset.birdiecraft." + id + ".desc";
    }
}
