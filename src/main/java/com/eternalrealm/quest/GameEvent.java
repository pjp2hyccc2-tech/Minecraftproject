package com.eternalrealm.quest;

import com.eternalrealm.world.Areas;

/**
 * Something a player did that may advance quests ({@link QuestEngine#onEvent}). The Forge layer reports each event once
 * per player it should count for (for example every party member credited with a kill).
 */
public sealed interface GameEvent permits GameEvent.MonsterKilled, GameEvent.ItemCollected, GameEvent.DungeonCleared,
        GameEvent.BossDefeated, GameEvent.AreaEntered, GameEvent.NpcTalked {

    /**
     * The player killed a templated monster (vanilla mobs are not reported).
     *
     * @param areaId area the kill happened in (a dungeon instance reports its dungeon's area), 0 if none
     */
    record MonsterKilled(String templateId, int areaId) implements GameEvent {
        public MonsterKilled {
            Check.id("templateId", templateId);
            Check.range("areaId", areaId, 0, Areas.LAST_ID);
        }
    }

    /**
     * Items entered the player's inventory from the world (CollectItems counts pickups, see {@link Objective.CollectItems}).
     * Only report items that are new to the economy: monster loot, mined or gathered blocks, fishing. Never report items
     * a player dropped or threw (their item entity has a thrower), items taken from containers, crafted or traded items,
     * or the same items could be counted again and again.
     */
    record ItemCollected(String itemId, int count) implements GameEvent {
        public ItemCollected {
            Check.itemId("itemId", itemId);
            Check.range("count", count, 1, 64 * 64);
        }
    }

    /** The player's party completed a run of the dungeon. */
    record DungeonCleared(String dungeonId) implements GameEvent {
        public DungeonCleared {
            Check.id("dungeonId", dungeonId);
        }
    }

    /** The player took part in defeating the boss (every defeat, not only the first on the server). */
    record BossDefeated(String bossId) implements GameEvent {
        public BossDefeated {
            Check.id("bossId", bossId);
        }
    }

    /** The player entered an area ({@link com.eternalrealm.world.AreaTracker} transition). */
    record AreaEntered(int areaId) implements GameEvent {
        public AreaEntered {
            Check.range("areaId", areaId, Areas.FIRST_ID, Areas.LAST_ID);
        }
    }

    /** The player talked to (right-clicked) a quest NPC ({@link Npcs}). */
    record NpcTalked(String npcId) implements GameEvent {
        public NpcTalked {
            Check.id("npcId", npcId);
        }
    }
}
