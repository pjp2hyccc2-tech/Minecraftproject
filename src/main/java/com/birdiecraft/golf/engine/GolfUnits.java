package com.birdiecraft.golf.engine;

/** Scale conversions. The whole mod uses 1 block = 2 yards. */
public final class GolfUnits {
    public static final double YARDS_PER_BLOCK = 2.0;
    public static final double TICKS_PER_SECOND = 20.0;

    private GolfUnits() {
    }

    public static double toYards(double blocks) {
        return blocks * YARDS_PER_BLOCK;
    }

    public static double toBlocks(double yards) {
        return yards / YARDS_PER_BLOCK;
    }

    public static int yardsRounded(double blocks) {
        return (int) Math.round(blocks * YARDS_PER_BLOCK);
    }

    public static double horizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Minecraft yaw (degrees) that looks from (x1,z1) toward (x2,z2). */
    public static double yawTo(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static double wrapDegrees(double deg) {
        double d = deg % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}
