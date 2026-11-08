package com.eternalrealm.world.map;

/**
 * Small pure helpers of the map views: colour arithmetic (RGB ints), the vanilla-map-like terrain shading of the
 * minimap, and the placement of markers on the round minimap.
 */
public final class MapMath {
    /** Minimap shading: columns higher than their northern neighbour are lit, lower ones shaded (like vanilla maps). */
    public static final double LIT = 1.0;
    public static final double FLAT = 0.88;
    public static final double SHADED = 0.72;

    private MapMath() {
    }

    /** {@code rgb} with every channel multiplied by {@code factor} (clamped to 0..255). */
    public static int shade(int rgb, double factor) {
        int r = channel(((rgb >> 16) & 0xFF) * factor);
        int g = channel(((rgb >> 8) & 0xFF) * factor);
        int b = channel((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    /** Linear mix: {@code t = 0} gives {@code from}, {@code t = 1} gives {@code to}. */
    public static int mix(int from, int to, double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        int r = channel(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
        int g = channel(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
        int b = channel((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return (r << 16) | (g << 8) | b;
    }

    /** Opaque ARGB of an RGB colour. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** ARGB to the ABGR layout of Minecraft's native images. */
    public static int argbToAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    /** Brightness of a land column from its height and the height of the column north of it. */
    public static double slope(int height, int northHeight) {
        if (height > northHeight) {
            return LIT;
        }
        return height < northHeight ? SHADED : FLAT;
    }

    /** Brightness of water by its depth (1 = shallow and bright, down to 0.55 for deep water). */
    public static double waterDepth(int depth) {
        int d = Math.max(1, Math.min(10, depth));
        return 1.0 - (d - 1) * 0.05;
    }

    /**
     * Colour of a minimap column: the surface colour shaded by its slope, water by its depth.
     *
     * @param waterDepth 0 for land, otherwise the number of water blocks above the bottom
     */
    public static int column(int rgb, int height, int northHeight, int waterDepth) {
        if (waterDepth > 0) {
            return shade(rgb, waterDepth(waterDepth) * (height > northHeight ? 1.0 : 0.96));
        }
        return shade(rgb, slope(height, northHeight));
    }

    /**
     * Position of a world offset on the minimap, in GUI pixels from its centre (+x right, +y down). North is up; when
     * {@code rotate} is set the map turns with the player so that their facing ({@code yawDegrees}) points up.
     *
     * @param dx            world x minus the player's x
     * @param dz            world z minus the player's z
     * @param pixelsPerBlock zoom
     */
    public static double[] toMinimap(double dx, double dz, double yawDegrees, boolean rotate, double pixelsPerBlock) {
        double x = dx * pixelsPerBlock;
        double y = dz * pixelsPerBlock;
        if (!rotate) {
            return new double[] {x, y};
        }
        // facing direction (yaw: 0 = +z) must end up pointing to screen up (-y): rotate by -(yaw + 180)
        double angle = Math.toRadians(-(yawDegrees + 180.0));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new double[] {x * cos - y * sin, x * sin + y * cos};
    }

    /** Pulls a point outside a circle of {@code radius} onto its edge. @return {x, y, 1 if it was moved else 0} */
    public static double[] clampToCircle(double x, double y, double radius) {
        double length = Math.sqrt(x * x + y * y);
        if (length <= radius || length < 1.0E-9) {
            return new double[] {x, y, 0.0};
        }
        return new double[] {x / length * radius, y / length * radius, 1.0};
    }

    /** Screen angle (radians, 0 = up, clockwise) of an arrow showing the yaw {@code yawDegrees}. */
    public static double arrowAngle(double yawDegrees, double mapRotationDegrees) {
        // yaw 180 (north) points up when the map is not rotated
        return Math.toRadians(yawDegrees + 180.0 - mapRotationDegrees);
    }

    private static int channel(double value) {
        return (int) Math.max(0.0, Math.min(255.0, Math.round(value)));
    }
}
