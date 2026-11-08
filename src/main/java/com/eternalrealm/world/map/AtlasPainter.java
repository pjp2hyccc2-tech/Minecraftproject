package com.eternalrealm.world.map;

import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.gen.AstoriaAreas;
import com.eternalrealm.world.gen.AstoriaBiome;
import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.gen.GenNoise;

import java.util.EnumMap;
import java.util.Map;

/**
 * Paints the stylised atlas of アストリア for the world map: every biome of the pure layout in a muted, parchment-toned
 * colour, a hand-drawn looking relief, the exact area rings as dashed borders, and the realm fading into the void at its
 * edge. Pure and deterministic (fixed seeds, independent of the world), so the client can paint it in the background
 * without asking the server. Coordinates are relative to the realm centre (the capital).
 */
public final class AtlasPainter {
    /** Half size of the painted square in blocks: the tower (z = -2100) and the void beyond it fit. */
    public static final int EXTENT = 2600;
    /** Beyond this distance the land fades into the void. */
    public static final int LAND_RADIUS = 2380;
    private static final int PARCHMENT = 0xE8DCB5;
    private static final int VOID = 0x15101C;
    private static final int RING_INK = 0x3B2A1A;
    private static final GenNoise RELIEF = new GenNoise(AstoriaLayout.SEED + 101);
    private static final GenNoise GRAIN = new GenNoise(AstoriaLayout.SEED + 102);
    private static final Map<AstoriaBiome, Integer> COLORS = new EnumMap<>(AstoriaBiome.class);

    static {
        COLORS.put(AstoriaBiome.ROYAL_CAPITAL, 0xD6C99A);
        COLORS.put(AstoriaBiome.ARCELIA_PLAINS, 0x9DBB68);
        COLORS.put(AstoriaBiome.ARCELIA_FLOWER_MEADOW, 0xB4C46F);
        COLORS.put(AstoriaBiome.ARCELIA_FARMLANDS, 0xC4B867);
        COLORS.put(AstoriaBiome.ELDER_FOREST, 0x4E7D3A);
        COLORS.put(AstoriaBiome.ELDER_OLD_GROWTH, 0x39632F);
        COLORS.put(AstoriaBiome.ELDER_MUSHROOM_GLADE, 0x6E6A4A);
        COLORS.put(AstoriaBiome.SANDRIA_DUNES, 0xE1C383);
        COLORS.put(AstoriaBiome.SANDRIA_MESA, 0xC88A5A);
        COLORS.put(AstoriaBiome.SANDRIA_OASIS, 0x5FA69E);
        COLORS.put(AstoriaBiome.NORDIA_SNOWFIELD, 0xE6ECEF);
        COLORS.put(AstoriaBiome.NORDIA_TAIGA, 0x9AB0A2);
        COLORS.put(AstoriaBiome.NORDIA_ICE_SPIKES, 0xC6E0EE);
        COLORS.put(AstoriaBiome.NORDIA_CRYSTAL_HOLLOW, 0xA6C6E6);
        COLORS.put(AstoriaBiome.DRAGONIA_ASHLANDS, 0x6E625C);
        COLORS.put(AstoriaBiome.DRAGONIA_BASALT_FIELDS, 0x4B4744);
        COLORS.put(AstoriaBiome.DRAGONIA_CALDERA, 0x8E3C25);
        COLORS.put(AstoriaBiome.CELESTIA_MEADOW, 0xC7E4C2);
        COLORS.put(AstoriaBiome.CELESTIA_CHERRY_GROVE, 0xE9C1CF);
        COLORS.put(AstoriaBiome.CELESTIA_WHITE_WOODS, 0xE4E8D6);
        COLORS.put(AstoriaBiome.ABYSS_WASTES, 0x4B3F55);
        COLORS.put(AstoriaBiome.ABYSS_DEAD_FOREST, 0x3F3A3B);
        COLORS.put(AstoriaBiome.ABYSS_VOID_RIFT, 0x2E2140);
        COLORS.put(AstoriaBiome.ABYSS_SPIRE, 0x1F1529);
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
     * @param biomes true for アストリア (biomes of the pure layout); false paints only the area rings in their colours
     *               (realms without the dedicated dimension, whose terrain is not known)
     */
    public static int[] paint(int size, boolean biomes) {
        if (size < 8 || size > 4096) {
            throw new IllegalArgumentException("atlas size " + size);
        }
        double blocksPerPixel = 2.0 * EXTENT / size;
        // relief sampled at pixel corners, shaded by its gradient (light from the north-west)
        double[] relief = new double[(size + 1) * (size + 1)];
        for (int row = 0; row <= size; row++) {
            for (int col = 0; col <= size; col++) {
                double x = -EXTENT + col * blocksPerPixel;
                double z = -EXTENT + row * blocksPerPixel;
                relief[row * (size + 1) + col] = RELIEF.fractal(x / 420.0, z / 420.0, 3);
            }
        }
        int[] pixels = new int[size * size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                double x = -EXTENT + (col + 0.5) * blocksPerPixel;
                double z = -EXTENT + (row + 0.5) * blocksPerPixel;
                double northWest = relief[row * (size + 1) + col];
                double southEast = relief[(row + 1) * (size + 1) + col + 1];
                double light = 1.0 + (northWest - southEast) * 2.2;
                pixels[row * size + col] = MapMath.opaque(color(x, z, blocksPerPixel, biomes, light));
            }
        }
        return pixels;
    }

    /** Colour (RGB) of one atlas pixel centred on ({@code x}, {@code z}). */
    static int color(double x, double z, double blocksPerPixel, boolean biomes, double light) {
        double distance = Math.sqrt(x * x + z * z);
        int base;
        if (biomes) {
            base = biomeColor(AstoriaLayout.biomeAt((int) Math.floor(x), (int) Math.floor(z)));
        } else {
            AreaDefinition area = Areas.require(AstoriaAreas.areaAt(x, z));
            base = MapMath.mix(area.color(), 0x808070, 0.35);
        }
        int toned = MapMath.mix(base, PARCHMENT, 0.16);
        double grain = 1.0 + 0.035 * GRAIN.sample(x / 9.0, z / 9.0);
        int shaded = MapMath.shade(toned, Math.max(0.7, Math.min(1.25, light)) * grain);
        // the exact area rings as dashed ink lines
        for (int ring = 1; ring < AstoriaAreas.COUNT; ring++) {
            double radius = ring * (double) AstoriaAreas.RING_WIDTH;
            if (Math.abs(distance - radius) <= Math.max(1.5, blocksPerPixel * 0.75)) {
                double angle = Math.atan2(z, x) * radius;
                if (Math.floorMod((long) Math.floor(angle / 18.0), 3L) != 2L) {
                    shaded = MapMath.mix(shaded, RING_INK, 0.55);
                }
                break;
            }
        }
        // the land ends in the void (the tower's surroundings reach a little further)
        double towerDx = x - AstoriaLayout.TOWER_X;
        double towerDz = z - AstoriaLayout.TOWER_Z;
        double towerReach = AstoriaLayout.SPIRE_RADIUS + 140.0 - Math.sqrt(towerDx * towerDx + towerDz * towerDz);
        double edge = distance - LAND_RADIUS - Math.max(0.0, towerReach) - 30.0 * GRAIN.sample(x / 140.0, z / 140.0);
        if (edge > 0.0) {
            shaded = MapMath.mix(shaded, VOID, Math.min(1.0, edge / 160.0));
        }
        return shaded;
    }
}
