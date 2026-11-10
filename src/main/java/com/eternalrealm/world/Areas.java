package com.eternalrealm.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The ten fields of アストリア around the royal capital, the eight dungeons and the bosses of the initial season
 * (docs/modules/world.md; where each field lies: {@link com.eternalrealm.world.field.FieldLayout}).
 *
 * <p>The seven story fields keep the ids and slugs of the former concentric areas (their names changed with the field
 * map: アルセリア → はじまりの草原, エルダーフォレスト → 月影の森, サンドリア → 鉄鉱の鉱山, ノルディア → 聖なる滝, ドラゴニア → 古代遺跡,
 * セレスティア → 天翔の峰, 終焉の塔 → 終焉の森) so progress, quests and saves carry over; three side fields (豊穣の農場,
 * 湖畔のほとり, 風の港) were added. A field opens for everyone when its
 * {@link AreaDefinition#unlockedByBossId() unlocking boss} is first defeated on the server. Boss {@code bN} waits at the
 * end of dungeon {@code dN}. Field 4 holds two dungeons: {@code b4} unlocks nothing and {@code b5} opens field 5;
 * {@code b8} (the tower) unlocks nothing either. The final boss {@value #FINAL_BOSS_ID} (終焉の王, top of the tower)
 * becomes available once {@code b1}..{@code b8} have all been defeated.</p>
 */
public final class Areas {
    public static final int FIRST_ID = 1;
    public static final int LAST_ID = 10;
    /** 終焉の王, fought at the top of the Tower of the End. */
    public static final String FINAL_BOSS_ID = "final";
    public static final int FINAL_BOSS_AREA_ID = 7;

    /** はじまりの草原: the meadows south of the capital (the capital itself counts as this field). */
    public static final AreaDefinition ARCELIA = new AreaDefinition(1, "arcelia", 5, 15, false, null, "arcelia",
            0x8BC34A, AreaDefinition.Kind.MAIN, 1);
    /** 月影の森: the moonlit forest west of the capital. */
    public static final AreaDefinition ELDER_FOREST = new AreaDefinition(2, "elder_forest", 10, 20, false, "b1",
            "elder_forest", 0x2E7D5A, AreaDefinition.Kind.MAIN, 2);
    /** 鉄鉱の鉱山: the iron mine and its quarry in the far west (a sun temple was dug up beneath it). */
    public static final AreaDefinition SANDRIA_DESERT = new AreaDefinition(3, "sandria_desert", 15, 25, false, "b2",
            "sandria_desert", 0xA1887F, AreaDefinition.Kind.MAIN, 3);
    /** 聖なる滝: the sacred falls north-east of the capital (two dungeons). */
    public static final AreaDefinition NORDIA_SNOWFIELD = new AreaDefinition(4, "nordia_snowfield", 20, 30, false,
            "b3", "nordia_snowfield", 0x4FC3F7, AreaDefinition.Kind.QUEST, 4);
    /** 古代遺跡: the ruins on their hill in the east; opened by the boss of the second falls dungeon (b5). */
    public static final AreaDefinition DRAGONIA_VOLCANO = new AreaDefinition(5, "dragonia_volcano", 30, 40, false,
            "b5", "dragonia_volcano", 0xD7B377, AreaDefinition.Kind.MAIN, 5);
    /** 天翔の峰: the soaring snowy peaks behind the capital. */
    public static final AreaDefinition CELESTIA = new AreaDefinition(6, "celestia", 40, 50, true, "b6", "celestia",
            0xE3F2FD, AreaDefinition.Kind.QUEST, 6);
    /** 終焉の森: the corrupted forest of the dark tower in the north-west. */
    public static final AreaDefinition TOWER_OF_THE_END = new AreaDefinition(7, "tower_of_the_end", 50, 50, true,
            "b7", "tower_of_the_end", 0x8E24AA, AreaDefinition.Kind.DANGER, 7);
    /** 豊穣の農場: the farms and windmills east of the capital. */
    public static final AreaDefinition FERTILE_FARM = new AreaDefinition(8, "fertile_farm", 10, 20, false, "b1",
            "fertile_farm", 0xFFC94D, AreaDefinition.Kind.MAIN, 2);
    /** 湖畔のほとり: the lake with its wooded island south-east of the meadow. */
    public static final AreaDefinition LAKESIDE = new AreaDefinition(9, "lakeside", 15, 25, false, "b2", "lakeside",
            0x26C6DA, AreaDefinition.Kind.MAIN, 3);
    /** 風の港: the windy harbour town on the south-eastern sea. */
    public static final AreaDefinition WIND_HARBOUR = new AreaDefinition(10, "wind_harbour", 20, 30, false, "b3",
            "wind_harbour", 0x5C8DF6, AreaDefinition.Kind.QUEST, 4);

    private static final List<AreaDefinition> ALL = List.of(ARCELIA, ELDER_FOREST, SANDRIA_DESERT, NORDIA_SNOWFIELD,
            DRAGONIA_VOLCANO, CELESTIA, TOWER_OF_THE_END, FERTILE_FARM, LAKESIDE, WIND_HARBOUR);

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

    /** All fields, ordered by id (the seven story fields first, then the side fields). */
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
