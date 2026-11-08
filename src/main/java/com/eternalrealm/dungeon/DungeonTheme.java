package com.eternalrealm.dungeon;

import java.util.Optional;

/**
 * Visual theme of an instance. The platform owns the palette (theme + {@link BlockRole} -> block); {@link #id()} is the
 * stable key for configs and data packs.
 */
public enum DungeonTheme {
    /** d1 失われた地下都市: old stone streets, cobbled walls, lanterns. */
    UNDERGROUND_CITY("underground_city"),
    /** d2 古代樹の迷宮: wood, moss and roots, glowing mushrooms. */
    FOREST("forest"),
    /** d3 砂塵の神殿: sandstone temple, gold accents. */
    DESERT_TEMPLE("desert_temple"),
    /** d4 凍てつく地下牢: iron bars, packed ice, cold dim light. */
    ICE_PRISON("ice_prison"),
    /** d5 氷結の城: blue ice halls, snow, sea lanterns. */
    ICE_CASTLE("ice_castle"),
    /** d6 灼熱の火山洞窟: basalt and blackstone, magma accents. */
    VOLCANO("volcano"),
    /** d7 天空の古代遺跡: white quartz and prismarine ruins in the sky. */
    SKY_RUINS("sky_ruins"),
    /** d8 終焉の塔: dark tower of deepslate and obsidian, purple light. */
    DARK_TOWER("dark_tower"),
    /** The final raid arena at the top of the tower (終焉の玉座). */
    END_THRONE("end_throne");

    private final String id;

    DungeonTheme(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<DungeonTheme> byId(String id) {
        for (DungeonTheme theme : values()) {
            if (theme.id.equals(id)) {
                return Optional.of(theme);
            }
        }
        return Optional.empty();
    }
}
