package com.eternalrealm.boss;

/** A position in absolute world coordinates (blocks), Minecraft-independent. */
public record Point(double x, double y, double z) {
    public Point {
        Check.finite("x", x);
        Check.finite("y", y);
        Check.finite("z", z);
    }

    public static Point of(BossPosition position) {
        return new Point(position.x(), position.y(), position.z());
    }

    public static Point of(TargetSnapshot target) {
        return new Point(target.x(), target.y(), target.z());
    }

    /** Squared distance in the XZ plane (height ignored). */
    public double horizontalDistanceSquared(double otherX, double otherZ) {
        double dx = otherX - x;
        double dz = otherZ - z;
        return dx * dx + dz * dz;
    }
}
