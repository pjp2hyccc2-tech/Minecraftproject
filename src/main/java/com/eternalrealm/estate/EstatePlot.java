package com.eternalrealm.estate;

import java.util.Optional;

/**
 * A plot of the estates dimension: a {@value #SPACING}-block square cell of a region's grid. Region {@code r} covers
 * z from {@code r × }{@value #REGION_DEPTH} on; inside it plot {@code i} sits in column {@code i % }{@value #COLUMNS},
 * row {@code i / }{@value #COLUMNS}. The plot's origin (where a blueprint's local 0/0 goes) is the cell's centre
 * point {@link #originX()}/{@link #originZ()}; an estate may use {@value #HALF_EXTENT} blocks around it, so
 * neighbouring estates are at least {@code SPACING - 2 × HALF_EXTENT} blocks apart and never see each other.
 */
public record EstatePlot(int region, int index) {
    public static final int SPACING = 1024;
    /** Usable distance around the origin in x and z (a blueprint spans at most ±176/208). */
    public static final int HALF_EXTENT = 256;
    public static final int COLUMNS = 64;
    public static final int REGION_DEPTH = 1 << 21;
    public static final int ROWS = REGION_DEPTH / SPACING;
    /** Plots per region. */
    public static final int CAPACITY = COLUMNS * ROWS;
    /** Regions 0..7: z stays below 8 × 2 097 152 (inside the world border). */
    public static final int REGIONS = 8;

    public EstatePlot {
        if (region < 0 || region >= REGIONS) {
            throw new IllegalArgumentException("region " + region);
        }
        if (index < 0 || index >= CAPACITY) {
            throw new IllegalArgumentException("plot index " + index);
        }
    }

    public int originX() {
        return (index % COLUMNS) * SPACING + SPACING / 2;
    }

    public int originZ() {
        return region * REGION_DEPTH + (index / COLUMNS) * SPACING + SPACING / 2;
    }

    /** Whether the block column (x, z) belongs to this plot's usable square. */
    public boolean contains(int x, int z) {
        return Math.abs(x - originX()) <= HALF_EXTENT && Math.abs(z - originZ()) <= HALF_EXTENT;
    }

    /** The plot whose usable square holds the block column (x, z), if any. */
    public static Optional<EstatePlot> at(int x, int z) {
        if (x < 0 || z < 0) {
            return Optional.empty();
        }
        int region = z / REGION_DEPTH;
        int column = x / SPACING;
        int row = (z - region * REGION_DEPTH) / SPACING;
        if (region >= REGIONS || column >= COLUMNS || row >= ROWS) {
            return Optional.empty();
        }
        EstatePlot plot = new EstatePlot(region, row * COLUMNS + column);
        return plot.contains(x, z) ? Optional.of(plot) : Optional.empty();
    }

    /** A stable single-number form ({@code region × CAPACITY + index}). */
    public long key() {
        return (long) region * CAPACITY + index;
    }

    public static EstatePlot ofKey(long key) {
        return new EstatePlot((int) (key / CAPACITY), (int) (key % CAPACITY));
    }

    @Override
    public String toString() {
        return "plot " + region + ":" + index;
    }
}
