package com.eternalrealm.world.map;

import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.FieldTerrain;
import com.eternalrealm.world.gen.AstoriaAreas;
import com.eternalrealm.world.gen.AstoriaBiome;
import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.gen.GenNoise;

import java.util.EnumMap;
import java.util.Map;

/**
 * Paints the stylised atlas of アストリア for the world map, like the hand-painted field map: every biome of the pure
 * layout in a muted, parchment-toned colour, the real relief of {@link FieldTerrain} shaded from the north-west, lakes,
 * rivers and the sea in blue, the roads, the capital, and the field borders as dashed ink lines; beyond the bounds the
 * land fades into clouds. Pure and deterministic, so the client paints it in the background without asking the server.
 * Coordinates are relative to the realm centre (the capital).
 */
public final class AtlasPainter {
    /** Half size of the painted square in blocks (the field map and a margin of sea, mountains and clouds). */
    public static final int EXTENT = 1900;
    private static final int PARCHMENT = 0xE8DCB5;
    private static final int CLOUD = 0xF2F1EC;
    private static final int BORDER_INK = 0x3B2A1A;
    private static final int ROAD = 0x9C7B4E;
    private static final int WATER = 0x4F8FC9;
    private static final int DEEP_WATER = 0x2E6AA8;
    private static final int CAPITAL = 0xE9E2CF;
    private static final GenNoise GRAIN = new GenNoise(AstoriaLayout.SEED + 102);
    private static final Map<AstoriaBiome, Integer> COLORS = new EnumMap<>(AstoriaBiome.class);

    static {
        COLORS.put(AstoriaBiome.ROYAL_CAPITAL, 0xD6C99A);
        COLORS.put(AstoriaBiome.ARCELIA_PLAINS, 0x9DBB68);
        COLORS.put(AstoriaBiome.ARCELIA_FLOWER_MEADOW, 0xB4C46F);
        COLORS.put(AstoriaBiome.ARCELIA_FARMLANDS, 0xC4B867);
        COLORS.put(AstoriaBiome.GOLDEN_FIELDS, 0xD6BE5E);
        COLORS.put(AstoriaBiome.ELDER_FOREST, 0x4E7D3A);
        COLORS.put(AstoriaBiome.ELDER_OLD_GROWTH, 0x39632F);
        COLORS.put(AstoriaBiome.ELDER_MUSHROOM_GLADE, 0x6E6A4A);
        COLORS.put(AstoriaBiome.IRON_HILLS, 0x948A70);
        COLORS.put(AstoriaBiome.IRON_QUARRY, 0x8E8A84);
        COLORS.put(AstoriaBiome.SANDRIA_DUNES, 0xE1C383);
        COLORS.put(AstoriaBiome.SANDRIA_MESA, 0xC88A5A);
        COLORS.put(AstoriaBiome.SANDRIA_OASIS, 0x5FA69E);
        COLORS.put(AstoriaBiome.RUIN_HILLS, 0xB7AE6E);
        COLORS.put(AstoriaBiome.ALPINE_MEADOW, 0x8DB27A);
        COLORS.put(AstoriaBiome.NORDIA_SNOWFIELD, 0xE6ECEF);
        COLORS.put(AstoriaBiome.NORDIA_TAIGA, 0x7E9A8A);
        COLORS.put(AstoriaBiome.NORDIA_ICE_SPIKES, 0xC6E0EE);
        COLORS.put(AstoriaBiome.NORDIA_CRYSTAL_HOLLOW, 0xA6C6E6);
        COLORS.put(AstoriaBiome.DRAGONIA_ASHLANDS, 0x6E625C);
        COLORS.put(AstoriaBiome.DRAGONIA_BASALT_FIELDS, 0x5B5753);
        COLORS.put(AstoriaBiome.DRAGONIA_CALDERA, 0x8E3C25);
        COLORS.put(AstoriaBiome.CELESTIA_MEADOW, 0xA9D8A2);
        COLORS.put(AstoriaBiome.CELESTIA_CHERRY_GROVE, 0xE9C1CF);
        COLORS.put(AstoriaBiome.CELESTIA_WHITE_WOODS, 0xE4E8D6);
        COLORS.put(AstoriaBiome.ABYSS_WASTES, 0x5B4B66);
        COLORS.put(AstoriaBiome.ABYSS_DEAD_FOREST, 0x4A3A5A);
        COLORS.put(AstoriaBiome.ABYSS_VOID_RIFT, 0x3C2752);
        COLORS.put(AstoriaBiome.ABYSS_SPIRE, 0x2A1838);
        COLORS.put(AstoriaBiome.LAKESIDE, 0x8EC07A);
        COLORS.put(AstoriaBiome.ASTORIA_LAKE, WATER);
        COLORS.put(AstoriaBiome.HARBOUR_COAST, 0xA2BE7C);
        COLORS.put(AstoriaBiome.ASTORIA_SEA, DEEP_WATER);
    }

    private AtlasPainter() {
    }

    /** Map colour (RGB) of a biome before toning and relief. */
    public static int biomeColor(AstoriaBiome biome) {
        return COLORS.get(biome);
    }

    /**
     * Paints the atlas square {@code [-EXTENT, EXTENT]²} into {@code size × size} opaque ARGB pixels (row by row,
     * north at the top).
     *
     * @param biomes true for アストリア (biomes and terrain of the pure layout); false paints only the fields in their
     *               colours (realms without the dedicated dimension, whose terrain is not known)
     */
    public static int[] paint(int size, boolean biomes) {
        if (size < 8 || size > 4096) {
            throw new IllegalArgumentException("atlas size " + size);
        }
        double blocksPerPixel = 2.0 * EXTENT / size;
        // heights on a coarser grid, interpolated per pixel (the relief needs no more detail on a map)
        int step = Math.max(1, size / 256);
        int grid = size / step + 2;
        double[] heights = new double[grid * grid];
        if (biomes) {
            for (int row = 0; row < grid; row++) {
                for (int col = 0; col < grid; col++) {
                    heights[row * grid + col] = FieldTerrain.heightAt(-EXTENT + col * step * blocksPerPixel,
                            -EXTENT + row * step * blocksPerPixel);
                }
            }
        }
        int[] pixels = new int[size * size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                double x = -EXTENT + (col + 0.5) * blocksPerPixel;
                double z = -EXTENT + (row + 0.5) * blocksPerPixel;
                double height = 70.0;
                double light = 1.0;
                if (biomes) {
                    double gx = (col + 0.5) / step;
                    double gz = (row + 0.5) / step;
                    height = sample(heights, grid, gx, gz);
                    double northWest = sample(heights, grid, gx - 0.5, gz - 0.5);
                    double southEast = sample(heights, grid, gx + 0.5, gz + 0.5);
                    light = 1.0 + (northWest - southEast) / (blocksPerPixel * step) * 1.6;
                }
                pixels[row * size + col] = MapMath.opaque(color(x, z, blocksPerPixel, biomes, light, height));
            }
        }
        return pixels;
    }

    private static double sample(double[] heights, int grid, double gx, double gz) {
        gx = Math.max(0.0, Math.min(grid - 1.001, gx));
        gz = Math.max(0.0, Math.min(grid - 1.001, gz));
        int x0 = (int) gx;
        int z0 = (int) gz;
        double fx = gx - x0;
        double fz = gz - z0;
        double a = heights[z0 * grid + x0];
        double b = heights[z0 * grid + x0 + 1];
        double c = heights[(z0 + 1) * grid + x0];
        double d = heights[(z0 + 1) * grid + x0 + 1];
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    /** Colour (RGB) of one atlas pixel centred on ({@code x}, {@code z}); {@code height} is the ground there. */
    static int color(double x, double z, double blocksPerPixel, boolean biomes, double light, double height) {
        int base;
        boolean water = false;
        if (biomes) {
            base = biomeColor(AstoriaLayout.biomeAt((int) Math.floor(x), (int) Math.floor(z)));
            int top = FieldTerrain.waterTop(x, z, (int) Math.floor(height));
            if (top != FieldTerrain.NO_WATER && top > height) {
                double depth = Math.min(1.0, (top - height) / 14.0);
                base = MapMath.mix(WATER, DEEP_WATER, depth);
                water = true;
            } else if (height > 150.0) {
                base = MapMath.mix(base, 0xF4F6F8, Math.min(1.0, (height - 150.0) / 30.0));    // snow caps
            }
        } else {
            AreaDefinition area = Areas.require(AstoriaAreas.areaAt(x, z));
            base = MapMath.mix(area.color(), 0x808070, 0.35);
        }
        if (FieldLayout.inCapital(x, z)) {
            base = MapMath.mix(base, CAPITAL, 0.65);
        }
        int toned = MapMath.mix(base, PARCHMENT, water ? 0.08 : 0.16);
        double grain = 1.0 + 0.035 * GRAIN.sample(x / 9.0, z / 9.0);
        int shaded = MapMath.shade(toned, (water ? 1.0 : Math.max(0.68, Math.min(1.3, light))) * grain);
        if (!water && FieldLayout.inBounds(x, z) && !FieldLayout.inCapital(x, z)) {
            double[] road = FieldLayout.nearestRoad(x, z);
            if (road[0] <= Math.max(road[1], blocksPerPixel * 0.6)) {
                shaded = MapMath.mix(shaded, ROAD, 0.75);
            }
        }
        // field borders as dashed ink lines
        if (FieldLayout.inBounds(x, z) && !FieldLayout.inCapital(x, z)
                && FieldLayout.borderDistance(x, z) <= Math.max(1.5, blocksPerPixel * 0.8)) {
            if (Math.floorMod((long) Math.floor((x + z) / 22.0), 3L) != 2L) {
                shaded = MapMath.mix(shaded, BORDER_INK, 0.6);
            }
        }
        // beyond the bounds the map fades into clouds
        double outside = FieldLayout.outOfBounds(x, z) - 30.0 * GRAIN.sample(x / 140.0, z / 140.0);
        if (outside > 0.0) {
            shaded = MapMath.mix(shaded, CLOUD, Math.min(0.85, outside / 220.0));
        }
        return shaded;
    }
}
