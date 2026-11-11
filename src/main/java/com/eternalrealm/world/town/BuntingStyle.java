package com.eternalrealm.world.town;

import java.util.Locale;

/**
 * What a string of bunting carries: triangular pennants, small square flags, paper lanterns (glowing at night),
 * laundry pegged to a washing line, or a leafy garland with small flowers. The numbers (blocks) are shared by the
 * renderer, the render bounds and the capital build's keep-free check ({@code tools/capital}).
 */
public enum BuntingStyle {
    /** Triangular pennants side by side. */
    PENNANT("pennant", 0.56, 0.62, new int[]{14, 4, 11, 0, 13}),
    /** Small rectangular flags. */
    FLAGS("flags", 0.52, 0.45, new int[]{11, 0, 14}),
    /** Paper lanterns on short cords, lit from inside after dusk. */
    LANTERNS("lanterns", 1.3, 0.6, new int[]{1, 14, 4, 6}),
    /** Shirts, cloths, socks and trousers pegged to a washing line. */
    LAUNDRY("laundry", 0.0, 0.8, new int[]{0, 3, 8, 4, 6, 11}),
    /** Leaves wound round the cord, small flowers among them (the colours are the flowers'). */
    GARLAND("garland", 0.2, 0.18, new int[]{6, 0, 4, 3});

    private final String id;
    private final double spacing;
    private final double hang;
    private final int[] defaultColors;

    BuntingStyle(String id, double spacing, double hang, int[] defaultColors) {
        this.id = id;
        this.spacing = spacing;
        this.hang = hang;
        this.defaultColors = defaultColors;
    }

    public String id() {
        return id;
    }

    /** Distance between neighbouring pieces along the cord (laundry is spaced piece by piece). */
    public double spacing() {
        return spacing;
    }

    /** How far below the cord the pieces reach at rest, in blocks. */
    public double hang() {
        return hang;
    }

    /** Dye ids used when the data names no colours. */
    public int[] defaultColors() {
        return defaultColors.clone();
    }

    /** The default colour for piece number {@code slot} (they go round in turn). */
    public int defaultColor(int slot) {
        return defaultColors[Math.floorMod(slot, defaultColors.length)];
    }

    /** The style with this id (case-insensitive); {@link #PENNANT} for anything unknown. */
    public static BuntingStyle of(String id) {
        if (id != null) {
            String key = id.trim().toLowerCase(Locale.ROOT);
            for (BuntingStyle style : values()) {
                if (style.id.equals(key)) {
                    return style;
                }
            }
        }
        return PENNANT;
    }

    // ------------------------------------------------------------------------------------------- colours

    /**
     * Cloth colours for the 16 dye ids (Minecraft's dye order: white, orange, magenta, light blue, yellow, lime, pink,
     * gray, light gray, cyan, purple, blue, brown, green, red, black), a little brighter and warmer than the vanilla
     * wool so festive strings read against plaster and stone.
     */
    private static final int[] DYE_RGB = {
            0xF4EFE2, 0xEC842B, 0xC24BB4, 0x63B5E8, 0xF6CD3B, 0x8DC63F, 0xF092B6, 0x6F6F72,
            0xB7B4AD, 0x2C9EA8, 0x7D4CB9, 0x3559C4, 0x8B5B35, 0x4F8D3B, 0xD43A30, 0x2B2B31};

    /** 0xRRGGBB of a dye id (wrapped into 0..15). */
    public static int dyeRgb(int dye) {
        return DYE_RGB[Math.floorMod(dye, DYE_RGB.length)];
    }

    /** Keeps the valid dye ids (0..15) of {@code raw}, at most {@code max}; empty input gives an empty array. */
    public static int[] cleanColors(int[] raw, int max) {
        if (raw == null) {
            return new int[0];
        }
        int n = 0;
        int[] out = new int[Math.min(raw.length, max)];
        for (int value : raw) {
            if (n >= out.length) {
                break;
            }
            if (value >= 0 && value < DYE_RGB.length) {
                out[n++] = value;
            }
        }
        if (n == out.length) {
            return out;
        }
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }
}
