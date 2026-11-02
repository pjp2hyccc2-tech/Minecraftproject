package com.birdiecraft.golf.engine;

/**
 * Voxel view of the world used by the ball physics. Implementations must be cheap to query;
 * the physics calls them several times per sub-step.
 */
public interface Terrain {
    /** Axis aligned box inside a block, in block-local coordinates (0..1, may exceed 1 for fences). */
    record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public static final Box FULL = new Box(0, 0, 0, 1, 1, 1);

        public boolean contains(double lx, double ly, double lz) {
            return lx >= minX && lx <= maxX && ly >= minY && ly <= maxY && lz >= minZ && lz <= maxZ;
        }
    }

    Box[] NONE = new Box[0];
    Box[] FULL_BLOCK = new Box[]{Box.FULL};

    /** Collision boxes of the block at the given position. Leaves and fluids must return {@link #NONE}. */
    Box[] boxes(int x, int y, int z);

    boolean isWater(int x, int y, int z);

    boolean isLeaves(int x, int y, int z);

    /** Surface type when the ball touches the top of this block. */
    Lie lieAt(int x, int y, int z);

    /** Downhill direction index 0..7 (N, NE, E, SE, S, SW, W, NW) or -1 when flat. */
    int slopeDir(int x, int y, int z);

    /** Slope steepness 1..3 (only meaningful when {@link #slopeDir} is not -1). */
    int slopeSteep(int x, int y, int z);

    /** True when the block is a golf cup (the hole is at the centre of its top face). */
    boolean isCup(int x, int y, int z);

    int minY();

    /** Unit vectors for the slope directions (x, z). N = -Z, E = +X. */
    double[] SLOPE_DX = {0, 0.70710678, 1, 0.70710678, 0, -0.70710678, -1, -0.70710678};
    double[] SLOPE_DZ = {-1, -0.70710678, 0, 0.70710678, 1, 0.70710678, 0, -0.70710678};
}
