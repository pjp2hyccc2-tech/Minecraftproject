package com.eternalrealm.world.map;

/**
 * The pan and zoom of the world map: which world point is at the centre of the view and how many blocks one GUI pixel
 * covers. North is up, east is right. Mutable, client thread only.
 */
public final class MapViewport {
    /** Closest zoom: four GUI pixels per block. */
    public static final double MIN_BLOCKS_PER_PIXEL = 0.25;
    /** Farthest zoom: the whole realm on a small screen. */
    public static final double MAX_BLOCKS_PER_PIXEL = 24.0;
    /** How far the centre may be panned from the realm centre. */
    public static final double MAX_PAN = 4000.0;

    private final double minBlocksPerPixel;
    private final double maxBlocksPerPixel;
    private final double minX;
    private final double minZ;
    private final double maxX;
    private final double maxZ;
    private double centerX;
    private double centerZ;
    private double blocksPerPixel;

    /** The realm map's view: the zoom between {@link #MIN_BLOCKS_PER_PIXEL} and {@link #MAX_BLOCKS_PER_PIXEL}. */
    public MapViewport(double centerX, double centerZ, double blocksPerPixel) {
        this(centerX, centerZ, blocksPerPixel, MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL, -MAX_PAN, -MAX_PAN, MAX_PAN,
                MAX_PAN);
    }

    /**
     * A view with its own limits (the capital's map): the zoom between {@code minBlocksPerPixel} and
     * {@code maxBlocksPerPixel}, the centre inside {@code [minX..maxX] × [minZ..maxZ]}.
     */
    public MapViewport(double centerX, double centerZ, double blocksPerPixel, double minBlocksPerPixel,
                       double maxBlocksPerPixel, double minX, double minZ, double maxX, double maxZ) {
        if (!(minBlocksPerPixel > 0.0) || !(maxBlocksPerPixel >= minBlocksPerPixel) || !(maxX >= minX)
                || !(maxZ >= minZ)) {
            throw new IllegalArgumentException("bad limits");
        }
        this.minBlocksPerPixel = minBlocksPerPixel;
        this.maxBlocksPerPixel = maxBlocksPerPixel;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.blocksPerPixel = clampZoom(blocksPerPixel);
        this.centerX = clamp(centerX, minX, maxX);
        this.centerZ = clamp(centerZ, minZ, maxZ);
    }

    public double centerX() {
        return centerX;
    }

    public double centerZ() {
        return centerZ;
    }

    public double blocksPerPixel() {
        return blocksPerPixel;
    }

    public double pixelsPerBlock() {
        return 1.0 / blocksPerPixel;
    }

    /** Screen x of world x for a view whose centre is at screen x {@code screenCenterX}. */
    public double screenX(double worldX, double screenCenterX) {
        return screenCenterX + (worldX - centerX) / blocksPerPixel;
    }

    public double screenY(double worldZ, double screenCenterY) {
        return screenCenterY + (worldZ - centerZ) / blocksPerPixel;
    }

    public double worldX(double screenX, double screenCenterX) {
        return centerX + (screenX - screenCenterX) * blocksPerPixel;
    }

    public double worldZ(double screenY, double screenCenterY) {
        return centerZ + (screenY - screenCenterY) * blocksPerPixel;
    }

    public void centerOn(double worldX, double worldZ) {
        centerX = clamp(worldX, minX, maxX);
        centerZ = clamp(worldZ, minZ, maxZ);
    }

    /** Sets the zoom (clamped to the limits), keeping the centre. */
    public void zoomTo(double blocksPerPixel) {
        this.blocksPerPixel = clampZoom(blocksPerPixel);
    }

    /** Moves the view by a mouse drag of ({@code dx}, {@code dy}) GUI pixels (the map follows the mouse). */
    public void drag(double dx, double dy) {
        centerOn(centerX - dx * blocksPerPixel, centerZ - dy * blocksPerPixel);
    }

    /**
     * Zooms by {@code factor} (> 1 zooms out) keeping the world point under the screen position
     * ({@code anchorX}, {@code anchorY}) in place.
     */
    public void zoom(double factor, double anchorX, double anchorY, double screenCenterX, double screenCenterY) {
        if (!(factor > 0.0) || !Double.isFinite(factor)) {
            return;
        }
        double worldX = worldX(anchorX, screenCenterX);
        double worldZ = worldZ(anchorY, screenCenterY);
        blocksPerPixel = clampZoom(blocksPerPixel * factor);
        centerOn(worldX - (anchorX - screenCenterX) * blocksPerPixel, worldZ - (anchorY - screenCenterY) * blocksPerPixel);
    }

    private double clampZoom(double value) {
        if (!Double.isFinite(value)) {
            return Math.max(minBlocksPerPixel, Math.min(maxBlocksPerPixel, 4.0));
        }
        return Math.max(minBlocksPerPixel, Math.min(maxBlocksPerPixel, value));
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return Math.max(min, Math.min(max, 0.0));
        }
        return Math.max(min, Math.min(max, value));
    }
}
