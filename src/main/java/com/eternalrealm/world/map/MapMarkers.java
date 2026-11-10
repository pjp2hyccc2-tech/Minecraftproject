package com.eternalrealm.world.map;

import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.world.gen.AstoriaAreas;
import com.eternalrealm.world.gen.AstoriaLayout;
import com.eternalrealm.world.gen.LandmarkSite;
import com.eternalrealm.world.gen.LandmarkSites;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The fixed places of アストリア shown on the maps, from the pure layout (the client knows them without asking the
 * server): the capital, the Tower of the End and the entrance of every dungeon with its recommended level.
 */
public final class MapMarkers {
    /** Translation key of the capital's label. */
    public static final String CAPITAL_KEY = "map.eternalrealm.capital";
    /** Translation key of the tower's label. */
    public static final String TOWER_KEY = "map.eternalrealm.tower";

    /** What a marker stands for. */
    public enum Kind {
        CAPITAL, TOWER, DUNGEON, WAYSTONE
    }

    /**
     * One place.
     *
     * @param dungeonId "d1".."d8" for dungeon entrances (and the tower, whose gate is d8), the waystone id for
     *                  waystones, otherwise empty
     * @param x         world position (アストリア); dungeon markers sit on the entrance in front of the gate
     * @param level     recommended level of the dungeon (0 for the capital)
     */
    public record Marker(Kind kind, String dungeonId, double x, double z, String nameKey, int areaId, int level) {
        public Marker {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(dungeonId, "dungeonId");
            Objects.requireNonNull(nameKey, "nameKey");
        }
    }

    private static final List<Marker> ASTORIA = build();

    private MapMarkers() {
    }

    /** Capital first, then the dungeon entrances d1..d7, then the tower (d8), then the waystones. */
    public static List<Marker> astoria() {
        return ASTORIA;
    }

    private static List<Marker> build() {
        List<Marker> markers = new ArrayList<>();
        markers.add(new Marker(Kind.CAPITAL, "", 0.5, 0.5, CAPITAL_KEY, 1, 0));
        for (LandmarkSite site : LandmarkSites.all()) {
            DungeonDefinition dungeon = Dungeons.byId(site.dungeonId()).orElse(null);
            int level = dungeon == null ? 0 : dungeon.recommendedLevel();
            String nameKey = dungeon == null ? "dungeon.eternalrealm." + site.dungeonId() : dungeon.translationKey();
            if (site.x() == AstoriaLayout.TOWER_X && site.z() == AstoriaLayout.TOWER_Z) {
                markers.add(new Marker(Kind.TOWER, site.dungeonId(), site.x() + 0.5, site.z() + 0.5, TOWER_KEY,
                        site.area(), level));
                continue;
            }
            LandmarkSite.Local entrance = site.entrance();
            markers.add(new Marker(Kind.DUNGEON, site.dungeonId(), site.worldX(entrance.dx(), entrance.dz()) + 0.5,
                    site.worldZ(entrance.dx(), entrance.dz()) + 0.5, nameKey, AstoriaAreas.areaAt(site.x(), site.z()),
                    level));
        }
        for (com.eternalrealm.world.field.FieldLayout.Waystone waystone
                : com.eternalrealm.world.field.FieldLayout.waystones()) {
            markers.add(new Marker(Kind.WAYSTONE, waystone.id(), waystone.x() + 0.5, waystone.z() + 0.5,
                    com.eternalrealm.world.field.Waystones.translationKey(waystone.id()), waystone.field(), 0));
        }
        return List.copyOf(markers);
    }
}
