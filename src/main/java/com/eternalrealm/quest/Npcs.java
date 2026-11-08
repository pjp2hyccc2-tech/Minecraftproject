package com.eternalrealm.quest;

import java.util.List;
import java.util.Optional;

/** The quest NPCs of the initial season: the knight captain in the capital and one guide per later area. */
public final class Npcs {
    public static final NpcDefinition KNIGHT_CAPTAIN = new NpcDefinition("arcelia_knight_captain", 1);
    public static final NpcDefinition FOREST_SAGE = new NpcDefinition("elder_forest_sage", 2);
    public static final NpcDefinition CARAVAN_MASTER = new NpcDefinition("sandria_caravan_master", 3);
    public static final NpcDefinition CHIEFTAIN = new NpcDefinition("nordia_chieftain", 4);
    public static final NpcDefinition BLACKSMITH = new NpcDefinition("dragonia_blacksmith", 5);
    public static final NpcDefinition SKY_ORACLE = new NpcDefinition("celestia_oracle", 6);
    public static final NpcDefinition TOWER_WATCHER = new NpcDefinition("tower_watcher", 7);

    private static final List<NpcDefinition> ALL = List.of(KNIGHT_CAPTAIN, FOREST_SAGE, CARAVAN_MASTER,
            CHIEFTAIN, BLACKSMITH, SKY_ORACLE, TOWER_WATCHER);

    private Npcs() {
    }

    public static List<NpcDefinition> all() {
        return ALL;
    }

    public static Optional<NpcDefinition> byId(String id) {
        for (NpcDefinition npc : ALL) {
            if (npc.id().equals(id)) {
                return Optional.of(npc);
            }
        }
        return Optional.empty();
    }
}
