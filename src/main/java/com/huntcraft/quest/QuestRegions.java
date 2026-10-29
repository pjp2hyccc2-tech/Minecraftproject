package com.huntcraft.quest;

import net.minecraft.core.BlockPos;

/**
 * Region layout inside a quest-map dimension. Every running quest gets its own region ("slot"):
 * centres are {@link #SPACING} blocks apart, the playable square is +-{@link #HALF} blocks around the centre,
 * divided into a 3x3 grid of numbered areas (1 = north-west ... 9 = south-east). The base camp lies in area 1.
 */
public final class QuestRegions {
    public static final int SPACING = 2048;
    public static final int HALF = 224;
    public static final int GRID = 16;
    public static final int MAX_SLOTS = GRID * GRID;
    public static final int CAMP_AREA = 1;

    private QuestRegions() {
    }

    public static BlockPos center(int slot) {
        return new BlockPos(1024 + SPACING * (slot % GRID), 0, 1024 + SPACING * (slot / GRID));
    }

    /** Area number 1..9 of a position, or 0 when outside the region. */
    public static int area(int cx, int cz, double x, double z) {
        double size = HALF * 2.0;
        double lx = x - (cx - HALF), lz = z - (cz - HALF);
        if (lx < 0 || lz < 0 || lx >= size || lz >= size) return 0;
        int col = Math.min(2, (int) (lx * 3.0 / size));
        int row = Math.min(2, (int) (lz * 3.0 / size));
        return row * 3 + col + 1;
    }

    public static int areaCenterX(int cx, int area) {
        int col = (area - 1) % 3;
        return (int) Math.round(cx - HALF + (col + 0.5) * HALF * 2.0 / 3.0);
    }

    public static int areaCenterZ(int cz, int area) {
        int row = (area - 1) / 3;
        return (int) Math.round(cz - HALF + (row + 0.5) * HALF * 2.0 / 3.0);
    }

    public static boolean inside(int cx, int cz, double x, double z, double margin) {
        return Math.abs(x - cx) <= HALF - margin && Math.abs(z - cz) <= HALF - margin;
    }

    public static boolean inside(BlockPos center, double x, double z, double margin) {
        return inside(center.getX(), center.getZ(), x, z, margin);
    }
}
