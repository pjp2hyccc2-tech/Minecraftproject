package com.eternalrealm.dungeon;

/** Axis-aligned box of blocks with inclusive bounds, relative to an instance origin. */
public record BlockBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public BlockBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("min > max: " + minX + "," + minY + "," + minZ + " .. " + maxX + ","
                    + maxY + "," + maxZ);
        }
    }

    public static BlockBox of(BlockPoint point) {
        return new BlockBox(point.x(), point.y(), point.z(), point.x(), point.y(), point.z());
    }

    /** Size along X in blocks. */
    public int sizeX() {
        return maxX - minX + 1;
    }

    /** Size along Y in blocks. */
    public int sizeY() {
        return maxY - minY + 1;
    }

    /** Size along Z in blocks. */
    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(BlockPoint point) {
        return contains(point.x(), point.y(), point.z());
    }

    public boolean contains(BlockBox other) {
        return other.minX >= minX && other.maxX <= maxX && other.minY >= minY && other.maxY <= maxY
                && other.minZ >= minZ && other.maxZ <= maxZ;
    }

    /** Whether the two boxes share at least one block. */
    public boolean intersects(BlockBox other) {
        return other.minX <= maxX && other.maxX >= minX && other.minY <= maxY && other.maxY >= minY
                && other.minZ <= maxZ && other.maxZ >= minZ;
    }

    /** The space inside the walls, floor and ceiling (one block in from every face). */
    public BlockBox interior() {
        return new BlockBox(minX + 1, minY + 1, minZ + 1, maxX - 1, maxY - 1, maxZ - 1);
    }

    /** Center block column at the given height (rounded towards the minimum for even sizes). */
    public BlockPoint center(int y) {
        return new BlockPoint(minX + (sizeX() - 1) / 2, y, minZ + (sizeZ() - 1) / 2);
    }
}
