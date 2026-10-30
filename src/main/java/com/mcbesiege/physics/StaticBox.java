package com.mcbesiege.physics;

/** Axis aligned, immovable world box (one block shape or a greedily merged run of full blocks). */
public final class StaticBox {
    public static final int NEG_X = 1, POS_X = 2, NEG_Y = 4, POS_Y = 8, NEG_Z = 16, POS_Z = 32, ALL = 63;

    public final double minX, minY, minZ, maxX, maxY, maxZ;
    public final double friction, restitution;
    /** Bit mask of faces that border open space; contacts are never pushed through an unexposed face. */
    public final int exposed;
    public final long key;
    /** Free slot for the world adapter (e.g. "is fluid"/material info). */
    public Object userData;

    public StaticBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double friction, double restitution, int exposed) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.friction = friction;
        this.restitution = restitution;
        this.exposed = exposed;
        long h = 1469598103934665603L;
        h = (h ^ Double.doubleToLongBits(minX)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(minY)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(minZ)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(maxX)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(maxY)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(maxZ)) * 1099511628211L;
        this.key = h;
    }

    public V3 center() { return new V3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5); }

    public V3 half() { return new V3((maxX - minX) * 0.5, (maxY - minY) * 0.5, (maxZ - minZ) * 0.5); }

    public boolean isExposed(int axis, int sign) {
        int bit = 1 << (axis * 2 + (sign > 0 ? 1 : 0));
        return (exposed & bit) != 0;
    }

    public boolean overlaps(double x0, double y0, double z0, double x1, double y1, double z1) {
        return minX <= x1 && maxX >= x0 && minY <= y1 && maxY >= y0 && minZ <= z1 && maxZ >= z0;
    }
}
