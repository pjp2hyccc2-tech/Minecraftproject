package com.eternalrealm.world.gen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry names of the world generation objects (namespace {@code eternalrealm}). Shared by the Forge registration and
 * the data tests, so every {@code configured_feature} JSON can be checked against the registered feature types and
 * their variants without starting Minecraft.
 */
public final class WorldgenIds {
    /** The dimension, its dimension type and the biome source type. */
    public static final String DIMENSION = "astoria";
    public static final String BIOME_SOURCE = "astoria";

    public static final String AREA_SURFACE = "area_surface";
    public static final String DARK_TOWER = "dark_tower";
    public static final String ROCK_SPIRE = "rock_spire";
    public static final String BOULDER = "boulder";
    public static final String CRYSTAL_CLUSTER = "crystal_cluster";
    public static final String FALLEN_PILLAR = "fallen_pillar";
    public static final String RUIN = "ruin";
    public static final String TREE = "astoria_tree";
    public static final String POOL = "pool";
    public static final String MAGMA_VENT = "magma_vent";
    public static final String FLOATING_ISLAND = "floating_island";
    public static final String VOID_CRACK = "void_crack";
    public static final String FARM_PLOT = "farm_plot";
    public static final String MUSHROOM_RING = "mushroom_ring";

    /**
     * Feature type -> allowed values of its {@code variant} config; an empty list means the feature takes no config
     * ({@code "config": {}}).
     */
    public static final Map<String, List<String>> FEATURE_VARIANTS;

    static {
        Map<String, List<String>> variants = new LinkedHashMap<>();
        variants.put(AREA_SURFACE, List.of());
        variants.put(DARK_TOWER, List.of());
        variants.put(ROCK_SPIRE, List.of("stone", "sandstone", "terracotta", "ice", "basalt", "obsidian", "calcite",
                "dark"));
        variants.put(BOULDER, List.of("mossy", "sandstone", "snowy", "basalt", "calcite", "deepslate"));
        variants.put(CRYSTAL_CLUSTER, List.of("amethyst", "ice", "magma", "quartz", "void"));
        variants.put(FALLEN_PILLAR, List.of("stone", "sandstone", "quartz", "blackstone", "log"));
        variants.put(RUIN, List.of("mossy", "sandstone", "frost", "basalt", "quartz", "abyss"));
        variants.put(TREE, List.of("giant", "palm", "white", "cherry", "charred", "ashen", "bleached"));
        variants.put(POOL, List.of("oasis", "lava", "frozen"));
        variants.put(MAGMA_VENT, List.of());
        variants.put(FLOATING_ISLAND, List.of("meadow", "cherry", "crystal"));
        variants.put(VOID_CRACK, List.of());
        variants.put(FARM_PLOT, List.of());
        variants.put(MUSHROOM_RING, List.of());
        FEATURE_VARIANTS = java.util.Collections.unmodifiableMap(variants);
    }

    private WorldgenIds() {
    }
}
