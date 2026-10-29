package com.huntcraft.quest;

import com.huntcraft.quest.world.QuestMapData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Region layout inside a quest-map dimension. The designed map tile ({@link QuestMapData}, 512x512) repeats every
 * {@link #SPACING} blocks; every running quest gets its own copy ("slot"). The playable square is +-{@link #HALF}
 * blocks around the region centre and is divided into the map's numbered areas (zone plane of the tile).
 */
public final class QuestRegions {
    public static final int SPACING = 2048;
    public static final int HALF = 224;
    public static final int TILE = QuestMapData.SIZE;
    public static final int TILE_HALF = QuestMapData.HALF;
    public static final int GRID = 16;
    public static final int MAX_SLOTS = GRID * GRID;

    private QuestRegions() {
    }

    /** Centre of a region slot (slot 0 is the origin). */
    public static BlockPos center(int slot) {
        return new BlockPos(SPACING * (slot % GRID), 0, SPACING * (slot / GRID));
    }

    /** Tile column of a world x (0..511) or -1 between regions. */
    public static int tileU(int x) {
        int m = Math.floorMod(x + TILE_HALF, SPACING);
        return m < TILE ? m : -1;
    }

    /** Tile row of a world z (0..511) or -1 between regions. */
    public static int tileV(int z) {
        return tileU(z);
    }

    public static int tileU(double x) {
        return tileU(Mth.floor(x));
    }

    public static int tileV(double z) {
        return tileU(Mth.floor(z));
    }

    /** World position of a tile position in the region around {@code center}. */
    public static BlockPos toWorld(BlockPos center, BlockPos tile) {
        return new BlockPos(center.getX() - TILE_HALF + tile.getX(), tile.getY(), center.getZ() - TILE_HALF + tile.getZ());
    }

    public static int worldX(BlockPos center, int u) {
        return center.getX() - TILE_HALF + u;
    }

    public static int worldZ(BlockPos center, int v) {
        return center.getZ() - TILE_HALF + v;
    }

    /** Area number (1..9) of a world position on the given map, 0 outside the playable areas. */
    public static int area(QuestMap map, double x, double z) {
        int u = tileU(x), v = tileV(z);
        if (u < 0 || v < 0) return 0;
        return map.data().zone(u, v);
    }

    public static boolean inside(int cx, int cz, double x, double z, double margin) {
        return Math.abs(x - cx) <= HALF - margin && Math.abs(z - cz) <= HALF - margin;
    }

    public static boolean inside(BlockPos center, double x, double z, double margin) {
        return inside(center.getX(), center.getZ(), x, z, margin);
    }
}
