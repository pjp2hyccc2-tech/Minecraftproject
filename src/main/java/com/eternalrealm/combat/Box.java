package com.eternalrealm.combat;

/** Axis-aligned bounding box (a target's hitbox), Minecraft-independent. */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public Box {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
            throw new IllegalArgumentException("non-finite box");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("min > max");
        }
    }

    /** Box of a mob standing at {@code (x, y, z)} with Minecraft's width/height convention. */
    public static Box ofEntity(double x, double y, double z, double width, double height) {
        double half = width / 2.0;
        return new Box(x - half, y, z - half, x + half, y + height, z + half);
    }

    public double centerX() {
        return (minX + maxX) / 2.0;
    }

    public double centerY() {
        return (minY + maxY) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ) / 2.0;
    }

    /** Squared distance from a point to the box; 0 inside. */
    public double distanceSquaredTo(double x, double y, double z) {
        double dx = x - clamp(x, minX, maxX);
        double dy = y - clamp(y, minY, maxY);
        double dz = z - clamp(z, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** Squared horizontal (XZ) distance from a point to the box's footprint; 0 inside. */
    public double horizontalDistanceSquaredTo(double x, double z) {
        double dx = x - clamp(x, minX, maxX);
        double dz = z - clamp(z, minZ, maxZ);
        return dx * dx + dz * dz;
    }

    /** Vertical gap between a height and the box; 0 when the height is within the box. Negative = box is above. */
    public double verticalGap(double y) {
        if (y > maxY) {
            return y - maxY;
        }
        if (y < minY) {
            return y - minY;
        }
        return 0.0;
    }

    static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
