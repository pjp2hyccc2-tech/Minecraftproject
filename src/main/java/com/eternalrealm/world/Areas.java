package com.eternalrealm.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The seven overworld areas, the eight dungeons and the bosses of the initial season (docs/modules/world.md).
 *
 * <p>Areas are rings by horizontal distance from the world spawn. Area {@code N+1} opens for everyone when its
 * {@link AreaDefinition#unlockedByBossId() unlocking boss} is first defeated on the server. Boss {@code bN} waits at the
 * end of dungeon {@code dN}. Area 4 holds two dungeons: {@code b4} unlocks nothing and {@code b5} opens area 5;
 * {@code b8} (the tower) unlocks nothing either. The final boss {@value #FINAL_BOSS_ID} (終焉の王, top of the tower)
 * becomes available once {@code b1}..{@code b8} have all been defeated.</p>
 */
public final class Areas {
    public static final int FIRST_ID = 1;
    public static final int LAST_ID = 7;
    /** 終焉の王, fought at the top of the Tower of the End. */
    public static final String FINAL_BOSS_ID = "final";
    public static final int FINAL_BOSS_AREA_ID = 7;

    /** アルセリア王国: the starting capital and its plains (a lost city lies beneath). */
    public static final AreaDefinition ARCELIA =
            new AreaDefinition(1, "arcelia", 0, 300, 1, 8, null, "arcelia", 0xF2C14E);
    /** エルダーフォレスト. */
    public static final AreaDefinition ELDER_FOREST =
            new AreaDefinition(2, "elder_forest", 300, 600, 8, 15, "b1", "elder_forest", 0x2E7D32);
    /** サンドリア砂漠. */
    public static final AreaDefinition SANDRIA_DESERT =
            new AreaDefinition(3, "sandria_desert", 600, 900, 15, 22, "b2", "sandria_desert", 0xE0A85A);
    /** ノルディア雪原 (two dungeons). */
    public static final AreaDefinition NORDIA_SNOWFIELD =
            new AreaDefinition(4, "nordia_snowfield", 900, 1200, 22, 30, "b3", "nordia_snowfield", 0xA7D8F0);
    /** ドラゴニア火山: opened by the boss of the second snow dungeon (b5). */
    public static final AreaDefinition DRAGONIA_VOLCANO =
            new AreaDefinition(5, "dragonia_volcano", 1200, 1500, 30, 37, "b5", "dragonia_volcano", 0xD84315);
    /** セレスティア天空大陸. */
    public static final AreaDefinition CELESTIA =
            new AreaDefinition(6, "celestia", 1500, 1800, 37, 44, "b6", "celestia", 0x9FA8DA);
    /** 終焉の塔: the tower and the land around it, unbounded outwards. */
    public static final AreaDefinition TOWER_OF_THE_END =
            new AreaDefinition(7, "tower_of_the_end", 1800, AreaDefinition.UNBOUNDED, 44, 50, "b7",
                    "tower_of_the_end", 0x6A1B9A);

    private static final List<AreaDefinition> ALL = List.of(ARCELIA, ELDER_FOREST, SANDRIA_DESERT, NORDIA_SNOWFIELD,
            DRAGONIA_VOLCANO, CELESTIA, TOWER_OF_THE_END);

    private static final List<DungeonSite> DUNGEONS = List.of(
            new DungeonSite("d1", 1, "b1"),  // 失われた地下都市
            new DungeonSite("d2", 2, "b2"),  // 古代樹の迷宮
            new DungeonSite("d3", 3, "b3"),  // 砂塵の神殿
            new DungeonSite("d4", 4, "b4"),  // 凍てつく地下牢
            new DungeonSite("d5", 4, "b5"),  // 氷結の城
            new DungeonSite("d6", 5, "b6"),  // 灼熱の火山洞窟
            new DungeonSite("d7", 6, "b7"),  // 天空の古代遺跡
            new DungeonSite("d8", 7, "b8")); // 終焉の塔

    private static final List<String> REGULAR_BOSS_IDS = DUNGEONS.stream().map(DungeonSite::bossId).toList();
    private static final List<String> BOSS_IDS = withFinal(REGULAR_BOSS_IDS);

    private Areas() {
    }

    /** All areas, ordered by id (= from the spawn outwards). */
    public static List<AreaDefinition> all() {
        return ALL;
    }

    public static Optional<AreaDefinition> byId(int id) {
        for (AreaDefinition area : ALL) {
            if (area.id() == id) {
                return Optional.of(area);
            }
        }
        return Optional.empty();
    }

    /** @throws IllegalArgumentException if there is no such area */
    public static AreaDefinition require(int id) {
        return byId(id).orElseThrow(() -> new IllegalArgumentException("Unknown area id " + id));
    }

    public static boolean exists(int id) {
        return id >= FIRST_ID && id <= LAST_ID;
    }

    public static List<DungeonSite> dungeons() {
        return DUNGEONS;
    }

    public static Optional<DungeonSite> dungeon(String dungeonId) {
        for (DungeonSite site : DUNGEONS) {
            if (site.dungeonId().equals(dungeonId)) {
                return Optional.of(site);
            }
        }
        return Optional.empty();
    }

    public static List<DungeonSite> dungeonsIn(int areaId) {
        return DUNGEONS.stream().filter(site -> site.areaId() == areaId).toList();
    }

    /** {@code b1}..{@code b8}, the dungeon bosses; all of them must fall before the final boss can be fought. */
    public static List<String> regularBossIds() {
        return REGULAR_BOSS_IDS;
    }

    /** The regular bosses followed by {@link #FINAL_BOSS_ID}. */
    public static List<String> bossIds() {
        return BOSS_IDS;
    }

    public static boolean isBossId(String bossId) {
        return bossId != null && BOSS_IDS.contains(bossId);
    }

    /** The area a boss is fought in. */
    public static OptionalInt areaOfBoss(String bossId) {
        if (FINAL_BOSS_ID.equals(bossId)) {
            return OptionalInt.of(FINAL_BOSS_AREA_ID);
        }
        for (DungeonSite site : DUNGEONS) {
            if (site.bossId().equals(bossId)) {
                return OptionalInt.of(site.areaId());
            }
        }
        return OptionalInt.empty();
    }

    private static List<String> withFinal(List<String> regular) {
        List<String> ids = new ArrayList<>(regular);
        ids.add(FINAL_BOSS_ID);
        return List.copyOf(ids);
    }
}
