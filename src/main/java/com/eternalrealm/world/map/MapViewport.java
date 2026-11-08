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

    private double centerX;
    private double centerZ;
    private double blocksPerPixel;

    public MapViewport(double centerX, double centerZ, double blocksPerPixel) {
        this.blocksPerPixel = clampZoom(blocksPerPixel);
        this.centerX = clampPan(centerX);
        this.centerZ = clampPan(centerZ);
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
        centerX = clampPan(worldX);
        centerZ = clampPan(worldZ);
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

    private static double clampZoom(double value) {
        if (!Double.isFinite(value)) {
            return 4.0;
        }
        return Math.max(MIN_BLOCKS_PER_PIXEL, Math.min(MAX_BLOCKS_PER_PIXEL, value));
    }

    private static double clampPan(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(-MAX_PAN, Math.min(MAX_PAN, value));
    }
}
