package com.eternalrealm.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The dungeons of the initial release and the final raid. Every dungeon uses four monster templates with fixed roles
 * (grunt, swarm, ranged, elite); rooms get harder towards the boss and the last room adds an elite wave. Counts are
 * for one player and grow with the party (see {@link MonsterGroup}).
 */
public final class Dungeons {
    private static final long MINUTE = 20L * 60;

    /** 失われた地下都市 (area 1 アルセリア王国). */
    public static final DungeonDefinition D1 = dungeon("d1", 1, 6, DungeonTheme.UNDERGROUND_CITY, 3, "b1", 30,
            "d1_bandit", "d1_cave_rat", "d1_crossbowman", "d1_ghoul");
    /** 古代樹の迷宮 (area 2 エルダーフォレスト). */
    public static final DungeonDefinition D2 = dungeon("d2", 2, 13, DungeonTheme.FOREST, 4, "b2", 30,
            "d2_spider", "d2_wolf", "d2_dryad", "d2_treant");
    /** 砂塵の神殿 (area 3 サンドリア砂漠). */
    public static final DungeonDefinition D3 = dungeon("d3", 3, 20, DungeonTheme.DESERT_TEMPLE, 4, "b3", 30,
            "d3_mummy", "d3_scorpion", "d3_cultist", "d3_sand_golem");
    /** 凍てつく地下牢 (area 4 ノルディア雪原). */
    public static final DungeonDefinition D4 = dungeon("d4", 4, 26, DungeonTheme.ICE_PRISON, 5, "b4", 35,
            "d4_prisoner", "d4_frost_hound", "d4_ice_wraith", "d4_jailer");
    /** 氷結の城 (area 4 ノルディア雪原). */
    public static final DungeonDefinition D5 = dungeon("d5", 4, 29, DungeonTheme.ICE_CASTLE, 5, "b5", 35,
            "d5_ice_knight", "d5_snow_wolf", "d5_frost_mage", "d5_ice_golem");
    /** 灼熱の火山洞窟 (area 5 ドラゴニア火山). */
    public static final DungeonDefinition D6 = dungeon("d6", 5, 35, DungeonTheme.VOLCANO, 5, "b6", 35,
            "d6_salamander", "d6_magma_slime", "d6_fire_imp", "d6_lava_golem");
    /** 天空の古代遺跡 (area 6 セレスティア天空大陸). */
    public static final DungeonDefinition D7 = dungeon("d7", 6, 42, DungeonTheme.SKY_RUINS, 6, "b7", 40,
            "d7_sky_sentinel", "d7_harpy", "d7_storm_spirit", "d7_ruin_construct");
    /** 終焉の塔 (area 7 終焉の塔). */
    public static final DungeonDefinition D8 = dungeon("d8", 7, 47, DungeonTheme.DARK_TOWER, 6, "b8", 40,
            "d8_shade", "d8_abyss_hound", "d8_void_mage", "d8_dark_knight");

    /** 終焉の王 raid at the top of the tower: 10-20 players recommended, smaller groups may try. */
    public static final RaidDefinition FINAL_RAID =
            new RaidDefinition("final", 7, 50, 1, 20, DungeonTheme.END_THRONE, "final", 30 * MINUTE);

    private static final List<DungeonDefinition> ALL = List.of(D1, D2, D3, D4, D5, D6, D7, D8);

    private Dungeons() {
    }

    public static List<DungeonDefinition> all() {
        return ALL;
    }

    public static Optional<DungeonDefinition> byId(String id) {
        for (DungeonDefinition definition : ALL) {
            if (definition.id().equals(id)) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    /** All dungeons entered from one area (area 4 has two). */
    public static List<DungeonDefinition> inArea(int areaId) {
        return ALL.stream().filter(definition -> definition.areaId() == areaId).toList();
    }

    private static DungeonDefinition dungeon(String id, int area, int level, DungeonTheme theme, int roomCount,
                                             String bossId, int minutes, String grunt, String swarm, String ranged,
                                             String elite) {
        List<RoomPlan> rooms = new ArrayList<>();
        for (int i = 0; i < roomCount; i++) {
            boolean last = i == roomCount - 1;
            List<Wave> waves = new ArrayList<>();
            waves.add(Wave.of(new MonsterGroup(grunt, 2 + i / 2, 0.75), new MonsterGroup(swarm, 2 + i / 3, 1.0)));
            waves.add(Wave.of(new MonsterGroup(grunt, 2, 0.5), new MonsterGroup(ranged, 1 + i / 2, 0.5)));
            if (i >= 1) {
                waves.add(Wave.of(new MonsterGroup(elite, 1, 0.25), new MonsterGroup(swarm, 2, 0.75)));
            }
            if (last) {
                waves.add(Wave.of(new MonsterGroup(elite, 1 + i / 3, 0.5), new MonsterGroup(grunt, 2, 0.5),
                        new MonsterGroup(ranged, 1, 0.5)));
            }
            rooms.add(new RoomPlan(waves));
        }
        return new DungeonDefinition(id, area, level, 1, DungeonDefinition.MAX_PARTY_SIZE, theme, rooms, bossId,
                minutes * MINUTE);
    }
}
