package com.eternalrealm.quest;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.character.PlayerCharacter;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.BossDefeatResult;
import com.eternalrealm.world.WorldProgress;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Plays the whole main story with the real catalog, world progress and character rules: every main quest must become
 * available exactly after the previous one, be completable with the events its objectives describe, and unlock the
 * next area through the boss defeats it requires.
 */
public class MainStoryPlaythroughTest {
    private static final SeasonId SEASON = SeasonId.of("s1");
    private static final long DAY = 20_400;
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    private final CharacterRules rules = CharacterRules.defaults();
    private final QuestEngine engine = new QuestEngine(Quests.catalog());
    private final QuestLogState log = QuestLogState.fresh();
    private final WorldProgress world = WorldProgress.fresh(SEASON);
    private final PlayerCharacter hero = PlayerCharacter.createNew(UUID.randomUUID(), SEASON, NOW);

    @Test
    public void theMainChainIsCompletableInOrderAndEndsWithTheFinalBoss() {
        List<QuestDefinition> chain = Quests.catalog().mainChain();
        for (int i = 0; i < chain.size(); i++) {
            QuestDefinition quest = chain.get(i);
            // Grind to the minimum level if the quest rewards so far were not enough.
            while (hero.level() < quest.prerequisites().minLevel()) {
                hero.addExperience(rules.experienceToNext(hero.level()) - hero.experience(), rules);
            }
            QuestContext context = QuestContext.of(hero.level(), world, DAY, 0);
            List<String> available = engine.available(log, context).stream()
                    .filter(q -> q.kind() == QuestKind.MAIN).map(QuestDefinition::id).toList();
            assertEquals("exactly the next main quest is available", List.of(quest.id()), available);
            if (i + 1 < chain.size()) {
                assertEquals(AcceptResult.PREREQUISITES_MISSING,
                        engine.canAccept(log, chain.get(i + 1).id(), QuestContext.of(50, world, DAY, 0)));
            }
            assertEquals(quest.id(), AcceptResult.SUCCESS, engine.accept(log, quest.id(), context));

            for (Objective objective : quest.objectives()) {
                for (int n = 0; n < objective.required(); n++) {
                    perform(objective);
                }
            }
            assertTrue(quest.id(), engine.isReadyToTurnIn(log, quest.id()));
            QuestReward reward = engine.turnIn(log, quest.id(), DAY).orElseThrow();
            if (reward.exp() > 0) {
                hero.addExperience(reward.exp(), rules);
            }
        }
        assertTrue(world.finalBossDefeated());
        assertEquals(chain.size(), chain.stream().filter(q -> log.hasCompleted(q.id())).count());
        assertEquals(List.of(), engine.available(log, QuestContext.of(50, world, DAY, 0)).stream()
                .filter(q -> q.kind() == QuestKind.MAIN).toList());
    }

    @Test
    public void theNextAreaStoryWaitsForTheServerWideUnlock() {
        // A character whose area-1 story is done while the world file lacks the b1 defeat (e.g. reset by an admin).
        QuestLog done = new QuestLog(List.of(), java.util.Map.of("m1_arrival", 1, "m1_hunt", 1, "m1_dungeon", 1,
                "m1_boss", 1), QuestLog.NEVER_RESET, java.util.Set.of());
        QuestLogState restored = QuestLogState.restore(done, Quests.catalog());
        assertEquals(AcceptResult.AREA_LOCKED, engine.accept(restored, "m2_arrival", QuestContext.of(50, world, DAY, 0)));
        assertFalse(engine.available(restored, QuestContext.of(50, world, DAY, 0)).stream()
                .anyMatch(q -> q.id().equals("m2_arrival")));

        BossDefeatResult defeat = world.recordBossDefeat("b1", NOW);
        assertEquals(List.of(2, 8), defeat.newlyUnlockedAreas().stream().map(a -> a.id()).toList());
        assertEquals(AcceptResult.SUCCESS, engine.accept(restored, "m2_arrival", QuestContext.of(50, world, DAY, 2)));
        assertEquals("standing in the forest already counts", List.of(1, 0), restored.progress("m2_arrival"));
        engine.onEvent(restored, new GameEvent.NpcTalked("elder_forest_sage"));
        assertTrue(engine.isReadyToTurnIn(restored, "m2_arrival"));
    }

    /** The events a player generates while doing the objective; boss kills also update the server-wide progress. */
    private void perform(Objective objective) {
        if (objective instanceof Objective.DefeatBoss boss) {
            world.recordBossDefeat(boss.bossId(), NOW);
        }
        engine.onEvent(log, eventFor(objective));
    }

    private static GameEvent eventFor(Objective objective) {
        if (objective instanceof Objective.KillMonsters kill) {
            String template = kill.anyMonster() ? "meadow_slime" : kill.target();
            int area = kill.areaId() != 0 ? kill.areaId()
                    : com.eternalrealm.monster.Monsters.require(template).areaIds().iterator().next();
            return new GameEvent.MonsterKilled(template, area);
        } else if (objective instanceof Objective.CollectItems collect) {
            return new GameEvent.ItemCollected(collect.itemId(), 1);
        } else if (objective instanceof Objective.ClearDungeon clear) {
            return new GameEvent.DungeonCleared(clear.dungeonId());
        } else if (objective instanceof Objective.DefeatBoss boss) {
            return new GameEvent.BossDefeated(boss.bossId());
        } else if (objective instanceof Objective.ReachArea reach) {
            return new GameEvent.AreaEntered(reach.areaId());
        } else if (objective instanceof Objective.TalkTo talk) {
            return new GameEvent.NpcTalked(talk.npcId());
        }
        throw new AssertionError("unhandled objective " + objective);
    }

    @Test
    public void bossKillsForOtherPlayersCountOnlyOnceForTheWorld() {
        Optional<QuestDefinition> boss = Quests.catalog().byId("m1_boss");
        assertTrue(boss.isPresent());
        assertTrue(world.recordBossDefeat("b1", NOW).firstDefeat());
        assertFalse(world.recordBossDefeat("b1", NOW.plusSeconds(60)).firstDefeat());
        assertEquals(Optional.of(NOW), world.firstDefeat("b1"));
        assertTrue(world.isAreaUnlocked(Areas.ELDER_FOREST.id()));
    }
}
