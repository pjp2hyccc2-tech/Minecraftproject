package com.birdiecraft.golf.engine;

/** Infinite flat ground (top surface at y = 0) used for calibration and tests. */
public final class FlatTerrain implements Terrain {
    private final Lie lie;

    public FlatTerrain(Lie lie) {
        this.lie = lie;
    }

    @Override
    public Box[] boxes(int x, int y, int z) {
        return y < 0 ? FULL_BLOCK : NONE;
    }

    @Override
    public boolean isWater(int x, int y, int z) {
        return false;
    }

    @Override
    public boolean isLeaves(int x, int y, int z) {
        return false;
    }

    @Override
    public Lie lieAt(int x, int y, int z) {
        return lie;
    }

    @Override
    public int slopeDir(int x, int y, int z) {
        return -1;
    }

    @Override
    public int slopeSteep(int x, int y, int z) {
        return 0;
    }

    @Override
    public boolean isCup(int x, int y, int z) {
        return false;
    }

    @Override
    public int minY() {
        return -64;
    }
}
