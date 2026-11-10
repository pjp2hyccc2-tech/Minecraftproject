package com.eternalrealm.world.gen;

import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.FieldTerrain;

/**
 * Where each {@link AstoriaBiome} lies on the field map: the field of {@link FieldLayout} at the position, then one of
 * its sub-biomes chosen by low-frequency noise (and, in the peaks, by how high the mountains rise; on the water, by the
 * lake and the sea). Pure and deterministic (fixed seed, independent of the world seed), so every server has the same
 * hand-made land.
 */
public final class AstoriaLayout {
    public static final long SEED = 0x4153_5452_4941L;
    /** Margin around the capital's footprint that keeps the capital meadow biome. */
    public static final int CAPITAL_MARGIN = 24;
    /** Centre of the dark tower (終焉の塔), the d8 entrance. */
    public static final int TOWER_X = FieldTerrain.TOWER_X;
    public static final int TOWER_Z = FieldTerrain.TOWER_Z;
    /** Radius of the darkest ground around the tower. */
    public static final int SPIRE_RADIUS = 120;

    private static final GenNoise PATCH = new GenNoise(SEED + 2);
    private static final GenNoise PATCH_ALT = new GenNoise(SEED + 3);
    private static final GenNoise RARE = new GenNoise(SEED + 4);

    private AstoriaLayout() {
    }

    /** Field (1..10) of the biome at (x, z): the gameplay field. */
    public static int biomeAreaAt(double x, double z) {
        return FieldLayout.areaAt(x, z);
    }

    /** The biome at block (x, z) (biomes do not change with height). */
    public static AstoriaBiome biomeAt(int x, int z) {
        if (FieldLayout.capitalDistance(x, z) <= CAPITAL_MARGIN) {
            return AstoriaBiome.ROYAL_CAPITAL;
        }
        if (FieldLayout.outOfBounds(x, z) > 0.0) {
            boolean wall = z < FieldLayout.MIN_Z || x < FieldLayout.MIN_X;
            return wall ? AstoriaBiome.NORDIA_SNOWFIELD : AstoriaBiome.ASTORIA_SEA;
        }
        int field = FieldLayout.areaAt(x, z);
        double patch = PATCH.fractal(x / 260.0, z / 260.0, 2);
        double alt = PATCH_ALT.fractal(x / 190.0, z / 190.0, 2);
        return switch (field) {
            case FieldLayout.MEADOW -> patch > 0.1 ? AstoriaBiome.ARCELIA_FLOWER_MEADOW : AstoriaBiome.ARCELIA_PLAINS;
            case FieldLayout.MOON_FOREST -> alt > 0.16 ? AstoriaBiome.ELDER_MUSHROOM_GLADE
                    : patch < -0.02 ? AstoriaBiome.ELDER_OLD_GROWTH : AstoriaBiome.ELDER_FOREST;
            case FieldLayout.IRON_MINE -> {
                if (Math.hypot(x - FieldTerrain.QUARRY_X, z - FieldTerrain.QUARRY_Z) < FieldTerrain.QUARRY_R + 20.0) {
                    yield AstoriaBiome.IRON_QUARRY;
                }
                yield RARE.sample(x / 90.0, z / 90.0) > 0.45 ? AstoriaBiome.DRAGONIA_BASALT_FIELDS
                        : AstoriaBiome.IRON_HILLS;
            }
            case FieldLayout.SACRED_FALLS -> alt > 0.08 ? AstoriaBiome.CELESTIA_CHERRY_GROVE
                    : AstoriaBiome.CELESTIA_MEADOW;
            case FieldLayout.ANCIENT_RUINS -> patch > 0.22 ? AstoriaBiome.SANDRIA_MESA : AstoriaBiome.RUIN_HILLS;
            case FieldLayout.SOARING_PEAKS -> peaks(x, z, patch, alt);
            case FieldLayout.END_FOREST -> {
                if (Math.hypot(x - TOWER_X, z - TOWER_Z) < SPIRE_RADIUS) {
                    yield AstoriaBiome.ABYSS_SPIRE;
                }
                yield patch > 0.15 ? AstoriaBiome.ABYSS_VOID_RIFT
                        : patch < -0.2 ? AstoriaBiome.ABYSS_WASTES : AstoriaBiome.ABYSS_DEAD_FOREST;
            }
            case FieldLayout.FARM -> patch > 0.05 ? AstoriaBiome.GOLDEN_FIELDS : AstoriaBiome.ARCELIA_FARMLANDS;
            case FieldLayout.LAKESIDE -> FieldLayout.lakeRadius(x, z) < 0.97 ? AstoriaBiome.ASTORIA_LAKE
                    : AstoriaBiome.LAKESIDE;
            case FieldLayout.HARBOUR -> {
                double coast = FieldLayout.coastDistance(x, z);
                if (coast < -6.0) {
                    yield AstoriaBiome.ASTORIA_SEA;
                }
                yield coast < 40.0 && alt > -0.1 ? AstoriaBiome.SANDRIA_DUNES : AstoriaBiome.HARBOUR_COAST;
            }
            default -> AstoriaBiome.ARCELIA_PLAINS;
        };
    }

    /** The peaks: alpine meadows below, taiga, snowfields and ice above; crystal hollows and white woods here and there. */
    private static AstoriaBiome peaks(int x, int z, double patch, double alt) {
        double height = FieldTerrain.fieldHeight(FieldLayout.SOARING_PEAKS, x, z);
        if (height < 118.0) {
            return alt > 0.2 ? AstoriaBiome.CELESTIA_WHITE_WOODS : AstoriaBiome.ALPINE_MEADOW;
        }
        if (height < 150.0) {
            return AstoriaBiome.NORDIA_TAIGA;
        }
        if (alt > 0.22) {
            return AstoriaBiome.NORDIA_ICE_SPIKES;
        }
        if (alt < -0.25) {
            return AstoriaBiome.NORDIA_CRYSTAL_HOLLOW;
        }
        return AstoriaBiome.NORDIA_SNOWFIELD;
    }
}
