package com.eternalrealm.world.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every biome of the アストリア dimension ({@code eternalrealm:<id>}), grouped by area. The JSON definitions live in
 * {@code data/eternalrealm/worldgen/biome/<id>.json}; names are {@code biome.eternalrealm.<id>}.
 */
public enum AstoriaBiome {
    /** The meadows inside and around the walls of the capital アルセリア王都 (no trees, no rocks). */
    ROYAL_CAPITAL("royal_capital", 1),
    ARCELIA_PLAINS("arcelia_plains", 1),
    ARCELIA_FLOWER_MEADOW("arcelia_flower_meadow", 1),
    ARCELIA_FARMLANDS("arcelia_farmlands", 1),

    ELDER_FOREST("elder_forest", 2),
    ELDER_OLD_GROWTH("elder_old_growth", 2),
    ELDER_MUSHROOM_GLADE("elder_mushroom_glade", 2),

    SANDRIA_DUNES("sandria_dunes", 3),
    SANDRIA_MESA("sandria_mesa", 3),
    SANDRIA_OASIS("sandria_oasis", 3),

    NORDIA_SNOWFIELD("nordia_snowfield", 4),
    NORDIA_TAIGA("nordia_taiga", 4),
    NORDIA_ICE_SPIKES("nordia_ice_spikes", 4),
    NORDIA_CRYSTAL_HOLLOW("nordia_crystal_hollow", 4),

    DRAGONIA_ASHLANDS("dragonia_ashlands", 5),
    DRAGONIA_BASALT_FIELDS("dragonia_basalt_fields", 5),
    DRAGONIA_CALDERA("dragonia_caldera", 5),

    CELESTIA_MEADOW("celestia_meadow", 6),
    CELESTIA_CHERRY_GROVE("celestia_cherry_grove", 6),
    CELESTIA_WHITE_WOODS("celestia_white_woods", 6),

    ABYSS_WASTES("abyss_wastes", 7),
    ABYSS_DEAD_FOREST("abyss_dead_forest", 7),
    ABYSS_VOID_RIFT("abyss_void_rift", 7),
    /** The darkest ground around the dark tower (終焉の塔) itself. */
    ABYSS_SPIRE("abyss_spire", 7);

    private static final Map<String, AstoriaBiome> BY_ID = new HashMap<>();

    static {
        for (AstoriaBiome biome : values()) {
            BY_ID.put(biome.id, biome);
        }
    }

    private final String id;
    private final int area;

    AstoriaBiome(String id, int area) {
        this.id = id;
        this.area = area;
    }

    /** Registry path, e.g. {@code arcelia_plains}. */
    public String id() {
        return id;
    }

    /** Area 1..7 (see {@link AstoriaAreas}). */
    public int area() {
        return area;
    }

    public String translationKey() {
        return "biome.eternalrealm." + id;
    }

    public static Optional<AstoriaBiome> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /** All biomes of one area, in declaration order. */
    public static List<AstoriaBiome> inArea(int area) {
        List<AstoriaBiome> result = new ArrayList<>();
        for (AstoriaBiome biome : values()) {
            if (biome.area == area) {
                result.add(biome);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
