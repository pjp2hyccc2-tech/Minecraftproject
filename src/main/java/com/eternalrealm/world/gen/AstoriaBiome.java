package com.eternalrealm.world.gen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every biome of the アストリア dimension ({@code eternalrealm:<id>}), grouped by the field they are placed in
 * ({@link AstoriaLayout}). The JSON definitions live in {@code data/eternalrealm/worldgen/biome/<id>.json}; names are
 * {@code biome.eternalrealm.<id>}. Biomes of field 0 are still registered (old saves, the dungeons' look) but no
 * longer placed on the field map.
 */
public enum AstoriaBiome {
    /** The meadows inside and around the walls of the capital (no trees, no rocks). */
    ROYAL_CAPITAL("royal_capital", 1),
    // ---- 1 はじまりの草原
    ARCELIA_PLAINS("arcelia_plains", 1),
    ARCELIA_FLOWER_MEADOW("arcelia_flower_meadow", 1),
    // ---- 2 月影の森
    ELDER_FOREST("elder_forest", 2),
    ELDER_OLD_GROWTH("elder_old_growth", 2),
    ELDER_MUSHROOM_GLADE("elder_mushroom_glade", 2),
    // ---- 3 鉄鉱の鉱山
    IRON_HILLS("iron_hills", 3),
    IRON_QUARRY("iron_quarry", 3),
    DRAGONIA_BASALT_FIELDS("dragonia_basalt_fields", 3),
    // ---- 4 聖なる滝
    CELESTIA_MEADOW("celestia_meadow", 4),
    CELESTIA_CHERRY_GROVE("celestia_cherry_grove", 4),
    // ---- 5 古代遺跡
    RUIN_HILLS("ruin_hills", 5),
    SANDRIA_MESA("sandria_mesa", 5),
    // ---- 6 天翔の峰
    ALPINE_MEADOW("alpine_meadow", 6),
    NORDIA_TAIGA("nordia_taiga", 6),
    NORDIA_SNOWFIELD("nordia_snowfield", 6),
    NORDIA_ICE_SPIKES("nordia_ice_spikes", 6),
    NORDIA_CRYSTAL_HOLLOW("nordia_crystal_hollow", 6),
    CELESTIA_WHITE_WOODS("celestia_white_woods", 6),
    // ---- 7 終焉の森
    ABYSS_DEAD_FOREST("abyss_dead_forest", 7),
    ABYSS_WASTES("abyss_wastes", 7),
    ABYSS_VOID_RIFT("abyss_void_rift", 7),
    /** The darkest ground around the dark tower. */
    ABYSS_SPIRE("abyss_spire", 7),
    // ---- 8 豊穣の農場
    ARCELIA_FARMLANDS("arcelia_farmlands", 8),
    GOLDEN_FIELDS("golden_fields", 8),
    // ---- 9 湖畔のほとり
    LAKESIDE("lakeside", 9),
    ASTORIA_LAKE("astoria_lake", 9),
    // ---- 10 風の港
    HARBOUR_COAST("harbour_coast", 10),
    SANDRIA_DUNES("sandria_dunes", 10),
    ASTORIA_SEA("astoria_sea", 10),
    // ---- no longer placed
    SANDRIA_OASIS("sandria_oasis", 0),
    DRAGONIA_ASHLANDS("dragonia_ashlands", 0),
    DRAGONIA_CALDERA("dragonia_caldera", 0);

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

    /** Field 1..10 it is placed in (see {@link AstoriaAreas}); 0 if it is no longer placed. */
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
