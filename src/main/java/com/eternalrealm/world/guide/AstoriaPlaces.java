package com.eternalrealm.world.guide;

import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.Dungeons;
import com.eternalrealm.monster.DropEntry;
import com.eternalrealm.monster.DropTables;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.quest.NpcDefinition;
import com.eternalrealm.quest.Npcs;
import com.eternalrealm.quest.QuestGuide;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.Habitats;
import com.eternalrealm.world.gen.LandmarkSites;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The places of アストリア for the quest guide ({@link QuestGuide.Places}): dungeon entrances ({@link LandmarkSites}),
 * monster haunts ({@link Habitats}), the way into each field ({@link FieldLayout}), and where the quest givers stand
 * (where the server last saw them, else beside their dungeon's entrance, the capital's for the first field). Pure.
 */
public final class AstoriaPlaces implements QuestGuide.Places {
    /**
     * The quest givers of fields 2..7 stand beside the gate of one dungeon of their field (the server places them
     * there, {@code RealmSetup}).
     */
    public static final Map<String, String> GIVER_DUNGEONS = Map.of("elder_forest_sage", "d2",
            "sandria_caravan_master", "d3", "nordia_chieftain", "d4", "dragonia_blacksmith", "d6", "celestia_oracle",
            "d7", "tower_watcher", "d8");
    /** How far into a field the guide leads for "reach the field". */
    static final double ENTRY_DEPTH = 24.0;
    private static final double ENTRY_STEP = 8.0;

    private final Function<String, Optional<QuestGuide.Point>> seen;

    /** @param seen where the server last saw a quest NPC standing, if it has */
    public AstoriaPlaces(Function<String, Optional<QuestGuide.Point>> seen) {
        this.seen = Objects.requireNonNull(seen, "seen");
    }

    @Override
    public Optional<QuestGuide.Point> npc(String npcId) {
        Optional<QuestGuide.Point> known = seen.apply(npcId);
        if (known.isPresent()) {
            return known;
        }
        String dungeon = GIVER_DUNGEONS.get(npcId);
        if (dungeon != null) {
            return dungeon(dungeon);
        }
        if (Npcs.byId(npcId).map(NpcDefinition::areaId).orElse(0) == FieldLayout.MEADOW) {
            return Optional.of(capital());
        }
        return Optional.empty();
    }

    /** The middle of the capital. */
    public static QuestGuide.Point capital() {
        return new QuestGuide.Point((FieldLayout.CAPITAL_MIN_X + FieldLayout.CAPITAL_MAX_X) / 2.0,
                (FieldLayout.CAPITAL_MIN_Z + FieldLayout.CAPITAL_MAX_Z) / 2.0);
    }

    @Override
    public Optional<QuestGuide.Point> dungeon(String dungeonId) {
        String site = Dungeons.FINAL_RAID.id().equals(dungeonId) ? "d8" : dungeonId;
        return LandmarkSites.byId(site).map(found -> new QuestGuide.Point(found.x() + 0.5, found.z() + 0.5));
    }

    @Override
    public Optional<String> dungeonOfBoss(String bossId) {
        if (Dungeons.FINAL_RAID.bossId().equals(bossId)) {
            return Optional.of(Dungeons.FINAL_RAID.id());
        }
        return Dungeons.all().stream().filter(d -> d.bossId().equals(bossId)).map(DungeonDefinition::id).findFirst();
    }

    @Override
    public Optional<QuestGuide.Point> areaEntry(int areaId, QuestGuide.Point from) {
        if (areaId < 1 || areaId > FieldLayout.FIELD_COUNT) {
            return Optional.empty();
        }
        double[] middle = FieldLayout.labelPoint(areaId);
        if (FieldLayout.areaAt(from.x(), from.z()) == areaId) {
            return Optional.of(new QuestGuide.Point(from.x(), from.z()));
        }
        double dx = middle[0] - from.x();
        double dz = middle[1] - from.z();
        double length = Math.hypot(dx, dz);
        if (length < 1e-6) {
            return Optional.of(new QuestGuide.Point(middle[0], middle[1]));
        }
        for (double at = ENTRY_STEP; at < length; at += ENTRY_STEP) {
            double x = from.x() + dx / length * at;
            double z = from.z() + dz / length * at;
            if (FieldLayout.areaAt(x, z) == areaId) {
                double in = Math.min(length, at + ENTRY_DEPTH);
                return Optional.of(new QuestGuide.Point(from.x() + dx / length * in, from.z() + dz / length * in));
            }
        }
        return Optional.of(new QuestGuide.Point(middle[0], middle[1]));
    }

    @Override
    public List<QuestGuide.Point> haunts(String monsterId, int areaId) {
        List<QuestGuide.Point> out = new ArrayList<>();
        for (Habitats.Habitat habitat : Habitats.all()) {
            if ((monsterId == null || habitat.monster().equals(monsterId))
                    && (areaId == 0 || habitat.field() == areaId)) {
                out.add(new QuestGuide.Point(habitat.x() + 0.5, habitat.z() + 0.5));
            }
        }
        return out;
    }

    @Override
    public List<String> droppers(String itemId) {
        List<String> out = new ArrayList<>();
        for (MonsterTemplate template : Monsters.all()) {
            boolean drops = DropTables.byId(template.dropTableId()).map(table -> {
                for (DropEntry entry : table.entries()) {
                    if (entry.itemId().equals(itemId)) {
                        return true;
                    }
                }
                return false;
            }).orElse(false);
            if (drops) {
                out.add(template.id());
            }
        }
        return out;
    }

    @Override
    public Optional<String> giverOf(int areaId) {
        return Npcs.all().stream().filter(npc -> npc.areaId() == areaId).map(NpcDefinition::id).findFirst();
    }
}
