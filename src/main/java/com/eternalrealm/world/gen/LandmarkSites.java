package com.eternalrealm.world.gen;

import com.eternalrealm.dungeon.DungeonTheme;

import java.util.List;
import java.util.Optional;

/**
 * The dungeon entrances of アストリア, one per dungeon of {@code dungeon.Dungeons}, at fixed coordinates inside their
 * fields of the field map (field 4, the sacred falls, has two). Front-facing local positions: the gate is at the local
 * origin, players arrive from +z.
 */
public final class LandmarkSites {
    /** Standard gate: entrance just in front of the gate, NPC to its side, waypoint pad further out. */
    private static final LandmarkSite.Local ENTRANCE = new LandmarkSite.Local(0, 1, 4, 180.0F);
    private static final LandmarkSite.Local NPC = new LandmarkSite.Local(4, 1, 6, 0.0F);
    private static final LandmarkSite.Local WAYPOINT = new LandmarkSite.Local(0, 1, 10, 180.0F);

    /** 失われた地下都市: a ruined stair gate into the ground at the far end of the meadow. */
    public static final LandmarkSite D1 = standard("d1", 1, -640, 930, 14, DungeonTheme.UNDERGROUND_CITY);
    /** 古代樹の迷宮: a gate grown into the roots of an ancient tree deep in the moonshadow forest. */
    public static final LandmarkSite D2 = standard("d2", 2, -1010, -60, 15, DungeonTheme.FOREST);
    /** 砂塵の神殿: the front of a sun temple the miners laid bare in the quarry. */
    public static final LandmarkSite D3 = standard("d3", 3, -1330, 790, 16, DungeonTheme.DESERT_TEMPLE);
    /** 凍てつく地下牢: a barred prison gate in the frozen cliff beside the falls. */
    public static final LandmarkSite D4 = standard("d4", 4, 651, -362, 15, DungeonTheme.ICE_PRISON);
    /** 氷結の城: the gatehouse of an ice castle on the plateau above the falls. */
    public static final LandmarkSite D5 = standard("d5", 4, 830, -540, 17, DungeonTheme.ICE_CASTLE);
    /** 灼熱の火山洞窟: a cave mouth beneath the temple ruins, framed by lava falls. */
    public static final LandmarkSite D6 = standard("d6", 5, 1300, -170, 16, DungeonTheme.VOLCANO);
    /** 天空の古代遺跡: a quartz arch with floating stones near the summit of the peaks. */
    public static final LandmarkSite D7 = standard("d7", 6, 200, -1230, 16, DungeonTheme.SKY_RUINS);
    /** 終焉の塔: the base of the dark tower; its gate is in the tower wall (radius 13), all spots on its plinth (22). */
    public static final LandmarkSite D8 = new LandmarkSite("d8", 7, AstoriaLayout.TOWER_X, AstoriaLayout.TOWER_Z, 30,
            DungeonTheme.DARK_TOWER, new LandmarkSite.Local(0, 1, 17, 180.0F),
            new LandmarkSite.Local(5, 1, 19, 0.0F), new LandmarkSite.Local(0, 1, 21, 180.0F));

    private static final List<LandmarkSite> ALL = List.of(D1, D2, D3, D4, D5, D6, D7, D8);

    private LandmarkSites() {
    }

    public static List<LandmarkSite> all() {
        return ALL;
    }

    public static Optional<LandmarkSite> byId(String dungeonId) {
        for (LandmarkSite site : ALL) {
            if (site.dungeonId().equals(dungeonId)) {
                return Optional.of(site);
            }
        }
        return Optional.empty();
    }

    private static LandmarkSite standard(String id, int area, int x, int z, int padRadius, DungeonTheme theme) {
        return new LandmarkSite(id, area, x, z, padRadius, theme, ENTRANCE, NPC, WAYPOINT);
    }
}
