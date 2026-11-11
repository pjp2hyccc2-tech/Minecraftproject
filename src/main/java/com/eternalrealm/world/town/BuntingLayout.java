package com.eternalrealm.world.town;

/**
 * Where the pieces of a string of bunting sit along its cord: the arc length of each piece's middle, its width and
 * height, what it is ({@code KIND_*}) and which of the string's colours it takes. Computed once when the string's data
 * changes; deterministic for a seed (the anchor position), so every player sees the same laundry on the same line.
 */
public final class BuntingLayout {
    public static final int KIND_PENNANT = 0;
    public static final int KIND_FLAG = 1;
    public static final int KIND_LANTERN = 2;
    public static final int KIND_LEAVES = 3;
    public static final int KIND_FLOWER = 4;
    public static final int KIND_SHIRT = 5;
    public static final int KIND_TOWEL = 6;
    public static final int KIND_SOCKS = 7;
    public static final int KIND_TROUSERS = 8;
    public static final int KIND_DRESS = 9;
    /** Most pieces on one string (a 55-block garland). */
    public static final int MAX_PIECES = 400;
    /** Bare cord kept free next to each hook, blocks. */
    public static final double END_MARGIN = 0.32;

    /** Laundry kinds: width, height. */
    private static final double[][] LAUNDRY = {
            {0.62, 0.6},    // shirt
            {0.48, 0.62},   // towel
            {0.34, 0.36},   // socks (a pair)
            {0.42, 0.76},   // trousers
            {0.5, 0.72}};   // dress

    private final int count;
    private final double[] at;
    private final float[] width;
    private final float[] height;
    private final byte[] kind;
    private final short[] slot;

    private BuntingLayout(int count, double[] at, float[] width, float[] height, byte[] kind, short[] slot) {
        this.count = count;
        this.at = at;
        this.width = width;
        this.height = height;
        this.kind = kind;
        this.slot = slot;
    }

    /** The pieces of a {@code style} string whose cord is {@code length} blocks long. */
    public static BuntingLayout of(BuntingStyle style, double length, long seed) {
        Builder b = new Builder();
        double usable = length - 2.0 * END_MARGIN;
        if (usable > 0.05) {
            switch (style) {
                case PENNANT -> b.even(usable, style.spacing(), 0.46, 0.58, KIND_PENNANT);
                case FLAGS -> b.even(usable, style.spacing(), 0.4, 0.36, KIND_FLAG);
                case LANTERNS -> b.even(usable, style.spacing(), 0.32, 0.52, KIND_LANTERN);
                case GARLAND -> b.garland(usable, style.spacing());
                case LAUNDRY -> b.laundry(usable, seed);
            }
        }
        return b.build();
    }

    public int count() {
        return count;
    }

    /** Arc length of the middle of piece {@code i} (its top edge sits on the cord). */
    public double at(int i) {
        return at[i];
    }

    /** Width of piece {@code i} along the cord. */
    public float width(int i) {
        return width[i];
    }

    /** Height of piece {@code i} below the cord. */
    public float height(int i) {
        return height[i];
    }

    public int kind(int i) {
        return kind[i];
    }

    /** Index of the colour this piece takes (cycle it over the string's colours); -1 for untinted pieces. */
    public int colorSlot(int i) {
        return slot[i];
    }

    /** Size of laundry kind {@code kind} ({@link #KIND_SHIRT} ..): {width, height}. */
    public static double[] laundrySize(int kind) {
        return LAUNDRY[kind - KIND_SHIRT].clone();
    }

    private static final class Builder {
        private final double[] at = new double[MAX_PIECES];
        private final float[] width = new float[MAX_PIECES];
        private final float[] height = new float[MAX_PIECES];
        private final byte[] kind = new byte[MAX_PIECES];
        private final short[] slot = new short[MAX_PIECES];
        private int count;
        private int colored;

        private void add(double s, double w, double h, int k, boolean tinted) {
            if (count >= MAX_PIECES) {
                return;
            }
            at[count] = s;
            width[count] = (float) w;
            height[count] = (float) h;
            kind[count] = (byte) k;
            slot[count] = (short) (tinted ? colored++ : -1);
            count++;
        }

        /** Pieces at a fixed pitch, the leftover split evenly between both ends. */
        void even(double usable, double pitch, double w, double h, int k) {
            if (usable < w) {
                return;
            }
            int n = (int) Math.floor((usable - w) / pitch) + 1;
            double used = (n - 1) * pitch;
            double start = END_MARGIN + (usable - used) / 2.0;
            for (int i = 0; i < n; i++) {
                add(start + i * pitch, w, h, k, true);
            }
        }

        /** Leaf clusters all along the cord, every third one with a flower. */
        void garland(double usable, double pitch) {
            if (usable < 0.26) {
                return;
            }
            int n = (int) Math.floor((usable - 0.26) / pitch) + 1;
            double start = END_MARGIN + (usable - (n - 1) * pitch) / 2.0;
            for (int i = 0; i < n; i++) {
                add(start + i * pitch, 0.26, 0.2, KIND_LEAVES, false);
                if (i % 3 == 1) {
                    add(start + i * pitch, 0.14, 0.14, KIND_FLOWER, true);
                }
            }
        }

        /** A washing line: random pieces with small random gaps, packed from the anchor end. */
        void laundry(double usable, long seed) {
            double s = END_MARGIN + 0.05;
            double end = END_MARGIN + usable;
            for (int i = 0; i < MAX_PIECES; i++) {
                double r = unit(seed, i * 2L);
                int k;
                if (r < 0.3) {
                    k = KIND_SHIRT;
                } else if (r < 0.52) {
                    k = KIND_TOWEL;
                } else if (r < 0.7) {
                    k = KIND_SOCKS;
                } else if (r < 0.86) {
                    k = KIND_TROUSERS;
                } else {
                    k = KIND_DRESS;
                }
                double[] size = LAUNDRY[k - KIND_SHIRT];
                if (s + size[0] > end) {
                    break;
                }
                add(s + size[0] / 2.0, size[0], size[1], k, true);
                s += size[0] + 0.08 + 0.2 * unit(seed, i * 2L + 1L);
            }
        }

        private static double unit(long seed, long i) {
            long h = (seed ^ 0x5DEECE66DL) * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
            h ^= h >>> 29;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 32;
            return (h >>> 11) * 0x1.0p-53;
        }

        BuntingLayout build() {
            double[] a = new double[count];
            float[] w = new float[count];
            float[] h = new float[count];
            byte[] k = new byte[count];
            short[] s = new short[count];
            System.arraycopy(at, 0, a, 0, count);
            System.arraycopy(width, 0, w, 0, count);
            System.arraycopy(height, 0, h, 0, count);
            System.arraycopy(kind, 0, k, 0, count);
            System.arraycopy(slot, 0, s, 0, count);
            return new BuntingLayout(count, a, w, h, k, s);
        }
    }
}
