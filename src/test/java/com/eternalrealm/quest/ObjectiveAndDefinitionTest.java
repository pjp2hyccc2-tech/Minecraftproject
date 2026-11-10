package com.eternalrealm.quest;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ObjectiveAndDefinitionTest {
    private static final GameEvent SLIME_IN_1 = new GameEvent.MonsterKilled("meadow_slime", 1);
    private static final GameEvent SLIME_IN_2 = new GameEvent.MonsterKilled("meadow_slime", 2);
    private static final GameEvent SPIDER_IN_2 = new GameEvent.MonsterKilled("forest_spider", 2);

    @Test
    public void killObjectivesMatchTemplateAndOptionalArea() {
        Objective.KillMonsters slimes = Objective.KillMonsters.of("meadow_slime", 5);
        assertEquals(1, slimes.progressFrom(SLIME_IN_1));
        assertEquals(1, slimes.progressFrom(SLIME_IN_2));
        assertEquals(0, slimes.progressFrom(SPIDER_IN_2));
        assertEquals(0, slimes.progressFrom(new GameEvent.ItemCollected("eternalrealm:slime_gel", 3)));

        Objective.KillMonsters anyInForest = Objective.KillMonsters.anyIn(2, 10);
        assertTrue(anyInForest.anyMonster());
        assertEquals(1, anyInForest.progressFrom(SPIDER_IN_2));
        assertEquals(1, anyInForest.progressFrom(SLIME_IN_2));
        assertEquals("kills elsewhere do not count", 0, anyInForest.progressFrom(SLIME_IN_1));
        assertEquals(0, anyInForest.progressFrom(new GameEvent.MonsterKilled("meadow_slime", 0)));

        Objective.KillMonsters slimesInForest = new Objective.KillMonsters("meadow_slime", 5, 2);
        assertEquals(0, slimesInForest.progressFrom(SLIME_IN_1));
        assertEquals(1, slimesInForest.progressFrom(SLIME_IN_2));
        assertEquals(1, new Objective.KillMonsters(Objective.KillMonsters.ANY, 1, 0).progressFrom(SLIME_IN_1));
    }

    @Test
    public void otherObjectivesMatchTheirEventsOnly() {
        Objective.CollectItems gel = new Objective.CollectItems("eternalrealm:slime_gel", 8);
        assertEquals("pickups count with their stack size", 3,
                gel.progressFrom(new GameEvent.ItemCollected("eternalrealm:slime_gel", 3)));
        assertEquals(0, gel.progressFrom(new GameEvent.ItemCollected("eternalrealm:wolf_fang", 3)));
        Objective.ClearDungeon d1 = new Objective.ClearDungeon("d1", 2);
        assertEquals(1, d1.progressFrom(new GameEvent.DungeonCleared("d1")));
        assertEquals(0, d1.progressFrom(new GameEvent.DungeonCleared("d2")));
        Objective.DefeatBoss b1 = new Objective.DefeatBoss("b1");
        assertEquals(1, b1.progressFrom(new GameEvent.BossDefeated("b1")));
        assertEquals(0, b1.progressFrom(new GameEvent.DungeonCleared("d1")));
        Objective.ReachArea forest = new Objective.ReachArea(2);
        assertEquals(1, forest.progressFrom(new GameEvent.AreaEntered(2)));
        assertEquals(0, forest.progressFrom(new GameEvent.AreaEntered(3)));
        Objective.TalkTo captain = new Objective.TalkTo("arcelia_knight_captain");
        assertEquals(1, captain.progressFrom(new GameEvent.NpcTalked("arcelia_knight_captain")));
        assertEquals(0, captain.progressFrom(new GameEvent.NpcTalked("tower_watcher")));
        for (Objective once : List.<Objective>of(b1, forest, captain)) {
            assertEquals(1, once.required());
        }
        assertEquals(2, d1.required());
    }

    @Test
    public void objectiveLinesHaveOneTranslationKeyPerShape() {
        assertEquals("quest_objective.eternalrealm.kill", Objective.KillMonsters.of("x", 1).translationKey());
        assertEquals("quest_objective.eternalrealm.kill_in_area", new Objective.KillMonsters("x", 1, 3).translationKey());
        assertEquals("quest_objective.eternalrealm.kill_any", new Objective.KillMonsters("any", 1, 0).translationKey());
        assertEquals("quest_objective.eternalrealm.kill_any_in_area", Objective.KillMonsters.anyIn(3, 1).translationKey());
        assertEquals("quest_objective.eternalrealm.collect", new Objective.CollectItems("a:b", 1).translationKey());
        assertEquals("quest_objective.eternalrealm.clear_dungeon", new Objective.ClearDungeon("d1", 1).translationKey());
        assertEquals("quest_objective.eternalrealm.defeat_boss", new Objective.DefeatBoss("b1").translationKey());
        assertEquals("quest_objective.eternalrealm.reach_area", new Objective.ReachArea(1).translationKey());
        assertEquals("quest_objective.eternalrealm.talk_to", new Objective.TalkTo("n").translationKey());
    }

    @Test
    public void invalidObjectivesAndEventsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Objective.KillMonsters.of("x", 0));
        assertThrows(IllegalArgumentException.class, () -> Objective.KillMonsters.of("X", 1));
        assertThrows(IllegalArgumentException.class, () -> Objective.KillMonsters.anyIn(11, 1));
        assertThrows(IllegalArgumentException.class, () -> new Objective.CollectItems("slime_gel", 1));
        assertThrows(IllegalArgumentException.class, () -> new Objective.ClearDungeon("d1", 10_001));
        assertThrows(IllegalArgumentException.class, () -> new Objective.ReachArea(0));
        assertThrows(IllegalArgumentException.class, () -> new Objective.TalkTo(""));
        assertThrows(IllegalArgumentException.class, () -> new GameEvent.MonsterKilled("x", 11));
        assertThrows(IllegalArgumentException.class, () -> new GameEvent.MonsterKilled(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new GameEvent.ItemCollected("a:b", 0));
        assertThrows(IllegalArgumentException.class, () -> new GameEvent.AreaEntered(0));
        assertThrows(IllegalArgumentException.class, () -> new GameEvent.BossDefeated("B1"));
    }

    @Test
    public void definitionsValidateTheirParts() {
        QuestPrerequisites none = new QuestPrerequisites(List.of(), 1, 1);
        QuestReward reward = new QuestReward(10, 5, List.of(new ItemReward("minecraft:bread", 2)));
        List<Objective> one = List.of(new Objective.TalkTo("npc"));
        QuestDefinition quest = new QuestDefinition("q", QuestKind.SIDE, 1, none, one, reward);
        assertEquals("quest.eternalrealm.q", quest.translationKey());
        assertEquals("quest.eternalrealm.q.desc", quest.descriptionKey());
        assertTrue(quest.isComplete(List.of(1)));
        assertFalse(quest.isComplete(List.of(0)));
        assertFalse("wrong length", quest.isComplete(List.of(1, 1)));

        assertThrows(IllegalArgumentException.class, () -> new QuestDefinition("q", QuestKind.SIDE, 1, none, List.of(), reward));
        assertThrows(IllegalArgumentException.class, () -> new QuestDefinition("q", QuestKind.SIDE, 1, none,
                List.of(one.get(0), one.get(0), one.get(0), one.get(0), one.get(0), one.get(0)), reward));
        assertThrows(IllegalArgumentException.class, () -> new QuestDefinition("q", QuestKind.SIDE, 11, none, one, reward));
        assertThrows(IllegalArgumentException.class, () -> new QuestDefinition("q", QuestKind.SIDE, 1,
                new QuestPrerequisites(List.of("q"), 1, 1), one, reward));
        assertThrows(IllegalArgumentException.class, () -> new QuestPrerequisites(List.of("a", "a"), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new QuestPrerequisites(List.of(), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new QuestPrerequisites(List.of(), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuestReward(-1, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ItemReward("minecraft:bread", 65));
        assertThrows(IllegalArgumentException.class, () -> new ItemReward("bread", 1));
        assertEquals("quest_kind.eternalrealm.daily", QuestKind.DAILY.translationKey());
        assertTrue(QuestKind.DAILY.isRepeatable());
        assertFalse(QuestKind.MAIN.isRepeatable());
    }

    @Test
    public void catalogsRejectDuplicatesAndForwardReferences() {
        QuestReward reward = new QuestReward(1, 1, List.of());
        List<Objective> one = List.of(new Objective.TalkTo("npc"));
        QuestDefinition a = new QuestDefinition("a", QuestKind.MAIN, 1, new QuestPrerequisites(List.of(), 1, 1), one, reward);
        QuestDefinition b = new QuestDefinition("b", QuestKind.MAIN, 1, new QuestPrerequisites(List.of("a"), 1, 1), one, reward);
        QuestDefinition daily = new QuestDefinition("c", QuestKind.DAILY, 1, new QuestPrerequisites(List.of(), 1, 1), one, reward);
        QuestCatalog catalog = new QuestCatalog(List.of(a, b, daily));
        assertEquals(List.of(a, b), catalog.mainChain());
        assertEquals(List.of(daily), catalog.ofKind(QuestKind.DAILY));
        assertEquals(3, catalog.size());
        assertTrue(catalog.byId("zzz").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new QuestCatalog(List.of(b, a)));
        assertThrows(IllegalArgumentException.class, () -> new QuestCatalog(List.of(a, a)));
    }
}
