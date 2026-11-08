package com.eternalrealm.world;

/**
 * Where a dungeon is and which boss waits at its end. The ids are shared with the boss/dungeon module, which owns the
 * dungeons' names and logic; the world module only needs the placement (quests, unlock chain).
 */
public record DungeonSite(String dungeonId, int areaId, String bossId) {
    public DungeonSite {
        Check.id("dungeonId", dungeonId);
        Check.range("areaId", areaId, 1, 99);
        Check.id("bossId", bossId);
    }
}
