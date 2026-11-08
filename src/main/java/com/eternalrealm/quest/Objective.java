package com.eternalrealm.quest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.Areas;

/**
 * One goal of a quest. Progress is a counter from 0 to {@link #required()}, advanced by matching {@link GameEvent}s
 * while the quest is active; events before acceptance never count.
 *
 * <p>{@link #translationKey()} is the format of the objective line in the quest UI; the arguments are listed per
 * variant (names come from the monster, area, boss/dungeon and NPC translation keys; progress and required are
 * numbers).</p>
 */
public sealed interface Objective permits Objective.KillMonsters, Objective.CollectItems, Objective.ClearDungeon,
        Objective.DefeatBoss, Objective.ReachArea, Objective.TalkTo {
    int MAX_COUNT = 10_000;

    /** Progress needed to complete the objective (at least 1). */
    int required();

    /** Progress {@code event} adds to this objective; 0 if it does not match. */
    int progressFrom(GameEvent event);

    String translationKey();

    private static String key(String name) {
        return "quest_objective." + ModInfo.MOD_ID + "." + name;
    }

    /**
     * Kill monsters of one template, or of any template ({@link #ANY}), optionally only inside one area. An area filter
     * keeps "kill N monsters" dailies of high areas from being farmed on weak monsters elsewhere.
     *
     * <p>Format args: {@code kill} [monster, progress, required]; {@code kill_in_area} [monster, area, progress,
     * required]; {@code kill_any} [progress, required]; {@code kill_any_in_area} [area, progress, required].</p>
     *
     * @param target template id or {@link #ANY}
     * @param areaId only kills in this area count; 0 = anywhere
     */
    record KillMonsters(String target, int count, int areaId) implements Objective {
        public static final String ANY = "any";

        public KillMonsters {
            Check.id("target", target);
            Check.range("count", count, 1, MAX_COUNT);
            Check.range("areaId", areaId, 0, Areas.LAST_ID);
        }

        public static KillMonsters of(String templateId, int count) {
            return new KillMonsters(templateId, count, 0);
        }

        public static KillMonsters anyIn(int areaId, int count) {
            return new KillMonsters(ANY, count, areaId);
        }

        public boolean anyMonster() {
            return ANY.equals(target);
        }

        @Override
        public int required() {
            return count;
        }

        @Override
        public int progressFrom(GameEvent event) {
            return event instanceof GameEvent.MonsterKilled kill
                    && (anyMonster() || target.equals(kill.templateId()))
                    && (areaId == 0 || areaId == kill.areaId()) ? 1 : 0;
        }

        @Override
        public String translationKey() {
            return key((anyMonster() ? "kill_any" : "kill") + (areaId == 0 ? "" : "_in_area"));
        }
    }

    /**
     * Pick up items while the quest is active. Pickups count as progress and nothing is taken at turn-in: the materials
     * stay with the player for crafting. Abuse is prevented at the source, which only reports items new to the economy
     * ({@link GameEvent.ItemCollected}). Format args: [item, progress, required].
     */
    record CollectItems(String itemId, int count) implements Objective {
        public CollectItems {
            Check.itemId("itemId", itemId);
            Check.range("count", count, 1, MAX_COUNT);
        }

        @Override
        public int required() {
            return count;
        }

        @Override
        public int progressFrom(GameEvent event) {
            return event instanceof GameEvent.ItemCollected item && itemId.equals(item.itemId()) ? item.count() : 0;
        }

        @Override
        public String translationKey() {
            return key("collect");
        }
    }

    /** Complete runs of a dungeon. Format args: [dungeon, progress, required]. */
    record ClearDungeon(String dungeonId, int count) implements Objective {
        public ClearDungeon {
            Check.id("dungeonId", dungeonId);
            Check.range("count", count, 1, MAX_COUNT);
        }

        @Override
        public int required() {
            return count;
        }

        @Override
        public int progressFrom(GameEvent event) {
            return event instanceof GameEvent.DungeonCleared cleared && dungeonId.equals(cleared.dungeonId()) ? 1 : 0;
        }

        @Override
        public String translationKey() {
            return key("clear_dungeon");
        }
    }

    /** Take part in defeating a boss (any defeat while active, not only the first on the server). Format args: [boss]. */
    record DefeatBoss(String bossId) implements Objective {
        public DefeatBoss {
            Check.id("bossId", bossId);
        }

        @Override
        public int required() {
            return 1;
        }

        @Override
        public int progressFrom(GameEvent event) {
            return event instanceof GameEvent.BossDefeated boss && bossId.equals(boss.bossId()) ? 1 : 0;
        }

        @Override
        public String translationKey() {
            return key("defeat_boss");
        }
    }

    /**
     * Enter an area. Completed on acceptance if the player is already there ({@link QuestContext#currentAreaId()}).
     * Format args: [area].
     */
    record ReachArea(int areaId) implements Objective {
        public ReachArea {
            Check.range("areaId", areaId, Areas.FIRST_ID, Areas.LAST_ID);
        }

        @Override
        public int required() {
            return 1;
        }

        @Override
        public int progressFrom(GameEvent event) {
            return event instanceof GameEvent.AreaEntered entered && entered.areaId() == areaId ? 1 : 0;
        }

        @Override
        public String translationKey() {
            return key("reach_area");
        }
    }

    /** Talk to an NPC ({@link Npcs}). Format args: [npc]. */
    record TalkTo(String npcId) implements Objective {
        public TalkTo {
            Check.id("npcId", npcId);
        }

        @Override
        public int required() {
            return 1;
        }

        @Override
        public int progressFrom(GameEvent event) {
            return event instanceof GameEvent.NpcTalked talked && npcId.equals(talked.npcId()) ? 1 : 0;
        }

        @Override
        public String translationKey() {
            return key("talk_to");
        }
    }
}
