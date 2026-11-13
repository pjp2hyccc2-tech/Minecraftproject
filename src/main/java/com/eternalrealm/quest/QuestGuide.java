package com.eternalrealm.quest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the guided quest wants the player to go next (the yellow dotted path, docs/modules/map.md "クエストの道案内"):
 * the first unfinished objective in order, or the quest's giver once it is ready to turn in. Pure: the places come
 * from {@link Places} (the Forge side knows where NPCs stand and dungeons open).
 *
 * <ul>
 *   <li>talk to an NPC, turn in: the NPC</li>
 *   <li>reach an area: a point just inside it, on the way from the player</li>
 *   <li>kill monsters: the nearest haunt of that monster (of any monster in the area, for "any")</li>
 *   <li>collect items: the nearest haunt of a monster that drops it</li>
 *   <li>clear a dungeon, defeat a boss: the dungeon's entrance (the boss's dungeon)</li>
 * </ul>
 */
public final class QuestGuide {
    private QuestGuide() {
    }

    public enum Kind {
        TALK, AREA, HUNT, COLLECT, DUNGEON, BOSS, TURN_IN
    }

    public record Point(double x, double z) {
        public double distanceTo(double px, double pz) {
            return Math.hypot(x - px, z - pz);
        }
    }

    /**
     * The next place.
     *
     * @param objective the objective it is for (-1 for turning in)
     * @param subject   what it is about: the NPC, area ("1".."7"), monster, item, dungeon or boss id
     * @param areaId    the area of a hunt or of the place, 0 when none
     */
    public record Target(String questId, Kind kind, Point point, int objective, String subject, int areaId) {
        public Target {
            Objects.requireNonNull(questId, "questId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(subject, "subject");
        }
    }

    /** What the world knows about places. */
    public interface Places {
        /** Where a quest NPC (a giver of {@link Npcs}) stands. */
        Optional<Point> npc(String npcId);

        /** The entrance of a dungeon ("d1".."d8", "final"). */
        Optional<Point> dungeon(String dungeonId);

        /** The dungeon whose boss this is, if it lives in one. */
        Optional<String> dungeonOfBoss(String bossId);

        /** A point just inside {@code areaId}, on the way from {@code from}. */
        Optional<Point> areaEntry(int areaId, Point from);

        /** Centres of the haunts of a monster ({@code null}: any monster), only in {@code areaId} unless it is 0. */
        List<Point> haunts(String monsterId, int areaId);

        /** Monsters whose drops include the item. */
        List<String> droppers(String itemId);

        /** The quest giver of an area (who takes the quest back). */
        Optional<String> giverOf(int areaId);
    }

    /**
     * @param progress     progress of each objective (missing entries count as 0)
     * @param readyToTurnIn every objective is done
     */
    public static Optional<Target> target(QuestDefinition quest, List<Integer> progress, boolean readyToTurnIn,
                                          Point player, Places places) {
        Objects.requireNonNull(quest, "quest");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(places, "places");
        if (readyToTurnIn) {
            Optional<String> giver = places.giverOf(quest.areaId());
            return giver.flatMap(npc -> places.npc(npc).map(point ->
                    new Target(quest.id(), Kind.TURN_IN, point, -1, npc, quest.areaId())));
        }
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            int done = i < progress.size() ? progress.get(i) : 0;
            if (done >= objective.required()) {
                continue;
            }
            Optional<Target> target = place(quest.id(), i, objective, player, places);
            if (target.isPresent()) {
                return target;
            }
        }
        return Optional.empty();
    }

    private static Optional<Target> place(String questId, int index, Objective objective, Point player,
                                          Places places) {
        if (objective instanceof Objective.TalkTo talk) {
            return places.npc(talk.npcId()).map(point -> new Target(questId, Kind.TALK, point, index, talk.npcId(),
                    Npcs.byId(talk.npcId()).map(NpcDefinition::areaId).orElse(0)));
        }
        if (objective instanceof Objective.ReachArea reach) {
            return places.areaEntry(reach.areaId(), player).map(point -> new Target(questId, Kind.AREA, point, index,
                    Integer.toString(reach.areaId()), reach.areaId()));
        }
        if (objective instanceof Objective.KillMonsters kill) {
            String monster = kill.anyMonster() ? null : kill.target();
            return nearest(places.haunts(monster, kill.areaId()), player).map(point -> new Target(questId, Kind.HUNT,
                    point, index, kill.anyMonster() ? Objective.KillMonsters.ANY : kill.target(), kill.areaId()));
        }
        if (objective instanceof Objective.CollectItems collect) {
            Point best = null;
            for (String monster : places.droppers(collect.itemId())) {
                Optional<Point> haunt = nearest(places.haunts(monster, 0), player);
                if (haunt.isPresent() && (best == null
                        || haunt.get().distanceTo(player.x(), player.z()) < best.distanceTo(player.x(), player.z()))) {
                    best = haunt.get();
                }
            }
            return Optional.ofNullable(best).map(point -> new Target(questId, Kind.COLLECT, point, index,
                    collect.itemId(), 0));
        }
        if (objective instanceof Objective.ClearDungeon clear) {
            return places.dungeon(clear.dungeonId()).map(point -> new Target(questId, Kind.DUNGEON, point, index,
                    clear.dungeonId(), 0));
        }
        if (objective instanceof Objective.DefeatBoss boss) {
            return places.dungeonOfBoss(boss.bossId()).flatMap(places::dungeon).map(point ->
                    new Target(questId, Kind.BOSS, point, index, boss.bossId(), 0));
        }
        return Optional.empty();
    }

    /** The point of {@code points} nearest to {@code from}. */
    public static Optional<Point> nearest(List<Point> points, Point from) {
        Point best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Point point : points) {
            double distance = point.distanceTo(from.x(), from.z());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = point;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Which active quest to guide when the player has not chosen: the main quest, else the first active one.
     *
     * @param chosen the player's choice, if any (kept only while it is active)
     */
    public static Optional<String> guided(List<String> activeQuestIds, String chosen, QuestCatalog catalog) {
        if (chosen != null && activeQuestIds.contains(chosen)) {
            return Optional.of(chosen);
        }
        for (String id : activeQuestIds) {
            if (catalog.byId(id).map(quest -> quest.kind() == QuestKind.MAIN).orElse(false)) {
                return Optional.of(id);
            }
        }
        return activeQuestIds.isEmpty() ? Optional.empty() : Optional.of(activeQuestIds.get(0));
    }
}
