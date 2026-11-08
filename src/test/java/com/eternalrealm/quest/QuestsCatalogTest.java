package com.eternalrealm.quest;

import com.eternalrealm.monster.Materials;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.DungeonSite;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuestsCatalogTest {
    private final QuestCatalog catalog = Quests.catalog();

    @Test
    public void theCatalogHasTheMainStorySideQuestsAndDailies() {
        assertEquals(41, catalog.size());
        assertEquals(31, catalog.mainChain().size());
        assertEquals(6, catalog.ofKind(QuestKind.SIDE).size());
        assertEquals(4, catalog.ofKind(QuestKind.DAILY).size());
    }

    @Test
    public void everyReferencedIdExists() {
        assertEquals(List.of(), Quests.referenceProblems(catalog));
        Set<String> npcsUsed = new HashSet<>();
        for (QuestDefinition quest : catalog.all()) {
            for (Objective objective : quest.objectives()) {
                if (objective instanceof Objective.TalkTo talk) {
                    assertEquals(quest.id() + " talks to someone in its own area",
                            quest.areaId(), Npcs.byId(talk.npcId()).orElseThrow().areaId());
                    npcsUsed.add(talk.npcId());
                }
            }
        }
        assertEquals("every NPC has a role in a quest", Npcs.all().size(), npcsUsed.size());
        assertEquals("npc.eternalrealm.tower_watcher", Npcs.TOWER_WATCHER.translationKey());
        assertTrue(Npcs.byId("nobody").isEmpty());
    }

    @Test
    public void brokenReferencesAreReported() {
        QuestPrerequisites none = new QuestPrerequisites(List.of(), 1, 1);
        QuestReward reward = new QuestReward(1, 1, List.of(new ItemReward("eternalrealm:no_such_item", 1)));
        QuestCatalog broken = new QuestCatalog(List.of(new QuestDefinition("broken", QuestKind.SIDE, 1, none, List.of(
                Objective.KillMonsters.of("dragon", 1),
                new Objective.KillMonsters("meadow_slime", 1, 7),
                new Objective.CollectItems("minecraft:diamond", 1),
                new Objective.ClearDungeon("d9", 1),
                new Objective.DefeatBoss("b9")), reward),
                new QuestDefinition("broken2", QuestKind.SIDE, 1, none, List.of(new Objective.TalkTo("nobody")),
                        new QuestReward(1, 1, List.of(new ItemReward("minecraft:bread", 1))))));
        List<String> problems = Quests.referenceProblems(broken);
        assertEquals(problems.toString(), 7, problems.size());
    }

    @Test
    public void theMainStoryIsALinearChainThroughEveryAreaDungeonAndBoss() {
        List<QuestDefinition> chain = catalog.mainChain();
        List<String> dungeons = new ArrayList<>();
        List<String> bosses = new ArrayList<>();
        int previousArea = 1;
        for (int i = 0; i < chain.size(); i++) {
            QuestDefinition quest = chain.get(i);
            List<String> expected = i == 0 ? List.of() : List.of(chain.get(i - 1).id());
            assertEquals(quest.id(), expected, quest.prerequisites().questIds());
            assertEquals(quest.id(), quest.areaId(), quest.prerequisites().requiredAreaId());
            assertTrue("areas only move forwards", quest.areaId() >= previousArea);
            previousArea = quest.areaId();
            AreaDefinition area = Areas.require(quest.areaId());
            assertTrue(quest.id() + " level fits the area",
                    quest.prerequisites().minLevel() >= area.minLevel() - 1
                            && quest.prerequisites().minLevel() <= area.maxLevel());
            for (Objective objective : quest.objectives()) {
                if (objective instanceof Objective.ClearDungeon clear) {
                    dungeons.add(clear.dungeonId());
                    assertEquals(quest.areaId(), Areas.dungeon(clear.dungeonId()).orElseThrow().areaId());
                } else if (objective instanceof Objective.DefeatBoss boss) {
                    bosses.add(boss.bossId());
                }
            }
        }
        assertEquals(Areas.dungeons().stream().map(DungeonSite::dungeonId).toList(), dungeons);
        assertEquals(Areas.bossIds(), bosses);
        QuestDefinition last = chain.get(chain.size() - 1);
        assertEquals(List.of(new Objective.DefeatBoss(Areas.FINAL_BOSS_ID)), last.objectives());
        assertEquals(7, last.areaId());
    }

    @Test
    public void eachAreaStoryStartsByArrivingAndDungeonsComeBeforeTheirBoss() {
        List<QuestDefinition> chain = catalog.mainChain();
        Set<Integer> arrived = new HashSet<>();
        Set<String> cleared = new HashSet<>();
        for (QuestDefinition quest : chain) {
            if (arrived.add(quest.areaId()) && quest.areaId() > 1) {
                assertTrue(quest.id() + " arrives first", quest.objectives().contains(new Objective.ReachArea(quest.areaId())));
            }
            for (Objective objective : quest.objectives()) {
                if (objective instanceof Objective.ClearDungeon clear) {
                    cleared.add(clear.dungeonId());
                } else if (objective instanceof Objective.DefeatBoss boss && !boss.bossId().equals(Areas.FINAL_BOSS_ID)) {
                    assertTrue(boss.bossId() + " after its dungeon", cleared.contains("d" + boss.bossId().substring(1)));
                }
            }
        }
        assertEquals(7, arrived.size());
    }

    @Test
    public void collectQuestsAskForMaterialsOfTheirAreaOrEarlier() {
        for (QuestDefinition quest : catalog.all()) {
            for (Objective objective : quest.objectives()) {
                if (objective instanceof Objective.CollectItems collect) {
                    int materialArea = Materials.areaOf(collect.itemId()).orElseThrow();
                    assertTrue(quest.id(), materialArea <= quest.areaId());
                }
            }
        }
    }

    @Test
    public void sidesAndDailiesNeedTheirAreaStoryStartedAndRewardsGrow() {
        for (QuestDefinition quest : catalog.all()) {
            assertTrue(quest.id(), quest.reward().exp() > 0 && quest.reward().gold() >= 10);
            if (quest.kind() != QuestKind.MAIN) {
                String required = quest.prerequisites().questIds().get(0);
                assertEquals(quest.id(), "m" + quest.areaId() + "_arrival", required);
            }
        }
        long previous = 0;
        for (QuestDefinition quest : catalog.mainChain()) {
            if (quest.id().endsWith("_boss") || quest.id().endsWith("_boss2") || quest.id().equals("m7_final")) {
                assertTrue(quest.id() + " pays more than the previous boss", quest.reward().exp() > previous);
                previous = quest.reward().exp();
            }
        }
    }
}
