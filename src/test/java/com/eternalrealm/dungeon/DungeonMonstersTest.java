package com.eternalrealm.dungeon;

import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertTrue;

/** Every monster a dungeon wave asks for exists, and fits the dungeon's level (otherwise the wave spawns nothing). */
public class DungeonMonstersTest {
    @Test
    public void everyWaveMonsterExistsAtTheDungeonLevel() {
        List<String> problems = new ArrayList<>();
        for (DungeonDefinition dungeon : Dungeons.all()) {
            for (RoomPlan room : dungeon.rooms()) {
                for (Wave wave : room.waves()) {
                    for (MonsterGroup group : wave.groups()) {
                        Optional<MonsterTemplate> template = Monsters.byId(group.templateId());
                        if (template.isEmpty()) {
                            problems.add(dungeon.id() + ": unknown monster " + group.templateId());
                        } else if (template.get().levels().max() < dungeon.recommendedLevel() - 3
                                || template.get().levels().min() > dungeon.recommendedLevel() + 3) {
                            problems.add(dungeon.id() + ": " + group.templateId() + " levels " + template.get().levels()
                                    + " far from " + dungeon.recommendedLevel());
                        }
                    }
                }
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }
}
