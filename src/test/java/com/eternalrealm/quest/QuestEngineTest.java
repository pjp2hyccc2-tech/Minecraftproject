package com.eternalrealm.quest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QuestEngineTest {
    private static final long DAY = 20_400;

    private static final QuestReward REWARD = new QuestReward(100, 20, List.of(new ItemReward("minecraft:bread", 1)));
    private static final QuestDefinition INTRO = quest("intro", QuestKind.MAIN, List.of(), 1, 1,
            new Objective.TalkTo("guide"));
    private static final QuestDefinition HUNT = quest("hunt", QuestKind.MAIN, List.of("intro"), 3, 1,
            Objective.KillMonsters.of("meadow_slime", 3), new Objective.CollectItems("eternalrealm:slime_gel", 5));
    private static final QuestDefinition SLIMES = quest("slimes", QuestKind.SIDE, List.of(), 1, 1,
            Objective.KillMonsters.of("meadow_slime", 2));
    private static final QuestDefinition FOREST = quest("forest", QuestKind.SIDE, List.of(), 1, 2,
            new Objective.ReachArea(2), Objective.KillMonsters.anyIn(2, 2));
    private static final QuestDefinition PATROL = quest("patrol", QuestKind.DAILY, List.of(), 1, 1,
            Objective.KillMonsters.anyIn(1, 1));

    private final List<QuestDefinition> fillers = fillers();
    private final QuestEngine engine = new QuestEngine(new QuestCatalog(all()));
    private final QuestLogState state = QuestLogState.fresh();

    private static QuestDefinition quest(String id, QuestKind kind, List<String> requires, int minLevel, int areaId,
                                         Objective... objectives) {
        return new QuestDefinition(id, kind, areaId, new QuestPrerequisites(requires, minLevel, areaId),
                List.of(objectives), REWARD);
    }

    private static List<QuestDefinition> fillers() {
        List<QuestDefinition> list = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            list.add(quest("filler" + i, QuestKind.SIDE, List.of(), 1, 1, new Objective.TalkTo("npc" + i)));
        }
        return list;
    }

    private List<QuestDefinition> all() {
        List<QuestDefinition> list = new ArrayList<>(List.of(INTRO, HUNT, SLIMES, FOREST, PATROL));
        list.addAll(fillers);
        return list;
    }

    private static QuestContext context(int level, long day) {
        return new QuestContext(level, Set.of(1), day, 0);
    }

    private void complete(String questId, GameEvent event) {
        assertEquals(AcceptResult.SUCCESS, engine.accept(state, questId, context(50, DAY)));
        engine.onEvent(state, event);
        assertTrue(engine.turnIn(state, questId, DAY).isPresent());
    }

    @Test
    public void acceptanceChecksEveryRuleWithItsOwnFailure() {
        QuestContext lowLevel = context(1, DAY);
        assertEquals(AcceptResult.UNKNOWN_QUEST, engine.accept(state, "nope", lowLevel));
        assertEquals(AcceptResult.PREREQUISITES_MISSING, engine.accept(state, "hunt", lowLevel));
        assertEquals(AcceptResult.AREA_LOCKED, engine.accept(state, "forest", lowLevel));
        complete("intro", new GameEvent.NpcTalked("guide"));
        assertEquals(AcceptResult.ALREADY_COMPLETED, engine.accept(state, "intro", lowLevel));
        assertEquals(AcceptResult.LEVEL_TOO_LOW, engine.accept(state, "hunt", lowLevel));
        assertEquals(AcceptResult.SUCCESS, engine.accept(state, "hunt", context(3, DAY)));
        assertEquals(AcceptResult.ALREADY_ACTIVE, engine.accept(state, "hunt", context(3, DAY)));
        assertEquals(AcceptResult.SUCCESS, engine.accept(state, "forest", new QuestContext(1, Set.of(1, 2), DAY, 0)));
        assertEquals("message.eternalrealm.quest.accept.level_too_low", AcceptResult.LEVEL_TOO_LOW.messageKey());
        assertTrue(AcceptResult.SUCCESS.isSuccess());
        assertFalse(AcceptResult.AREA_LOCKED.isSuccess());
    }

    @Test
    public void atMostTenQuestsCanBeActive() {
        for (int i = 0; i < QuestEngine.MAX_ACTIVE_QUESTS; i++) {
            assertEquals(AcceptResult.SUCCESS, engine.accept(state, "filler" + i, context(1, DAY)));
        }
        assertEquals(AcceptResult.TOO_MANY_ACTIVE, engine.canAccept(state, "filler10", context(1, DAY)));
        assertEquals(AcceptResult.TOO_MANY_ACTIVE, engine.accept(state, "filler10", context(1, DAY)));
        assertFalse(state.isActive("filler10"));
        assertTrue("the limit does not hide quests", engine.available(state, context(1, DAY)).contains(fillers.get(10)));
        assertTrue(engine.abandon(state, "filler0"));
        assertEquals(AcceptResult.SUCCESS, engine.accept(state, "filler10", context(1, DAY)));
    }

    @Test
    public void eventsAdvanceEveryMatchingQuestAndReportProgressCompletionAndReadiness() {
        complete("intro", new GameEvent.NpcTalked("guide"));
        engine.accept(state, "hunt", context(5, DAY));
        engine.accept(state, "slimes", context(5, DAY));
        GameEvent slime = new GameEvent.MonsterKilled("meadow_slime", 1);

        assertEquals(List.of(new QuestUpdate.ProgressChanged("hunt", 0, 1, 3),
                new QuestUpdate.ProgressChanged("slimes", 0, 1, 2)), engine.onEvent(state, slime));
        assertEquals(List.of(new QuestUpdate.ProgressChanged("hunt", 0, 2, 3),
                new QuestUpdate.ProgressChanged("slimes", 0, 2, 2),
                new QuestUpdate.ObjectiveCompleted("slimes", 0),
                new QuestUpdate.ReadyToTurnIn("slimes")), engine.onEvent(state, slime));
        assertEquals(List.of(new QuestUpdate.ProgressChanged("hunt", 0, 3, 3),
                new QuestUpdate.ObjectiveCompleted("hunt", 0)), engine.onEvent(state, slime));
        assertEquals("counters stop at the required amount", List.of(), engine.onEvent(state, slime));
        assertEquals(List.of(3, 0), state.progress("hunt"));
        assertFalse(engine.isReadyToTurnIn(state, "hunt"));

        assertEquals(List.of(new QuestUpdate.ProgressChanged("hunt", 1, 4, 5)),
                engine.onEvent(state, new GameEvent.ItemCollected("eternalrealm:slime_gel", 4)));
        assertEquals(List.of(new QuestUpdate.ProgressChanged("hunt", 1, 5, 5),
                        new QuestUpdate.ObjectiveCompleted("hunt", 1), new QuestUpdate.ReadyToTurnIn("hunt")),
                engine.onEvent(state, new GameEvent.ItemCollected("eternalrealm:slime_gel", 64)));
        assertTrue(engine.isReadyToTurnIn(state, "hunt"));
        assertEquals(List.of(), engine.onEvent(state, new GameEvent.DungeonCleared("d1")));
    }

    @Test
    public void eventsBeforeAcceptanceDoNotCount() {
        GameEvent slime = new GameEvent.MonsterKilled("meadow_slime", 1);
        assertEquals(List.of(), engine.onEvent(state, slime));
        engine.accept(state, "slimes", context(1, DAY));
        assertEquals(List.of(0), state.progress("slimes"));
        engine.onEvent(state, slime);
        assertEquals(List.of(1), state.progress("slimes"));
    }

    @Test
    public void turnInPaysOnceAndOnlyWhenEveryObjectiveIsDone() {
        assertEquals(Optional.empty(), engine.turnIn(state, "slimes", DAY));
        engine.accept(state, "slimes", context(1, DAY));
        engine.onEvent(state, new GameEvent.MonsterKilled("meadow_slime", 1));
        long revision = state.revision();
        assertEquals(Optional.empty(), engine.turnIn(state, "slimes", DAY));
        assertEquals("a refused turn-in changes nothing", revision, state.revision());
        engine.onEvent(state, new GameEvent.MonsterKilled("meadow_slime", 1));
        assertEquals(Optional.of(REWARD), engine.turnIn(state, "slimes", DAY));
        assertEquals(Optional.empty(), engine.turnIn(state, "slimes", DAY));
        assertFalse(state.isActive("slimes"));
        assertEquals(1, state.completionCount("slimes"));
        assertEquals(AcceptResult.ALREADY_COMPLETED, engine.accept(state, "slimes", context(1, DAY)));
        assertFalse(engine.available(state, context(1, DAY)).contains(SLIMES));
    }

    @Test
    public void abandoningDropsProgress() {
        engine.accept(state, "slimes", context(1, DAY));
        engine.onEvent(state, new GameEvent.MonsterKilled("meadow_slime", 1));
        assertTrue(engine.abandon(state, "slimes"));
        assertFalse(engine.abandon(state, "slimes"));
        engine.accept(state, "slimes", context(1, DAY));
        assertEquals(List.of(0), state.progress("slimes"));
        assertEquals(0, state.completionCount("slimes"));
    }

    @Test
    public void reachAreaIsCreditedOnAcceptanceWhenThePlayerIsAlreadyThere() {
        QuestContext inForest = new QuestContext(5, Set.of(1, 2), DAY, 2);
        assertEquals(AcceptResult.SUCCESS, engine.accept(state, "forest", inForest));
        assertEquals(List.of(1, 0), state.progress("forest"));
        engine.onEvent(state, new GameEvent.MonsterKilled("forest_spider", 2));
        engine.onEvent(state, new GameEvent.MonsterKilled("meadow_slime", 1));
        assertEquals("kills outside area 2 do not count", List.of(1, 1), state.progress("forest"));
        engine.onEvent(state, new GameEvent.MonsterKilled("meadow_slime", 2));
        assertTrue(engine.isReadyToTurnIn(state, "forest"));
    }

    @Test
    public void dailiesCanBeTurnedInOncePerDay() {
        GameEvent kill = new GameEvent.MonsterKilled("meadow_slime", 1);
        complete("patrol", kill);
        assertTrue(state.isDailyDone("patrol"));
        assertEquals(DAY, state.lastDailyResetDay());
        assertEquals(AcceptResult.DAILY_ALREADY_DONE, engine.accept(state, "patrol", context(1, DAY)));
        assertFalse(engine.available(state, context(1, DAY)).contains(PATROL));
        assertTrue("available() sees the next day without changing the state",
                engine.available(state, context(1, DAY + 1)).contains(PATROL));
        assertEquals(DAY, state.lastDailyResetDay());

        assertEquals(AcceptResult.SUCCESS, engine.accept(state, "patrol", context(1, DAY + 1)));
        assertEquals(DAY + 1, state.lastDailyResetDay());
        engine.onEvent(state, kill);
        assertTrue(engine.turnIn(state, "patrol", DAY + 1).isPresent());
        assertEquals(2, state.completionCount("patrol"));
        assertEquals("a clock moving backwards never reopens a daily", AcceptResult.DAILY_ALREADY_DONE,
                engine.accept(state, "patrol", context(1, DAY)));
        assertFalse(engine.resetDailies(state, DAY));
        assertTrue(engine.resetDailies(state, DAY + 2));
        assertFalse(state.isDailyDone("patrol"));
        assertFalse("idempotent", engine.resetDailies(state, DAY + 2));
    }

    @Test
    public void aDailyAcceptedYesterdayIsBookedToTheDayItIsTurnedIn() {
        GameEvent kill = new GameEvent.MonsterKilled("meadow_slime", 1);
        engine.accept(state, "patrol", context(1, DAY));
        engine.onEvent(state, kill);
        assertTrue("active dailies survive the reset", engine.resetDailies(state, DAY + 1) && state.isActive("patrol"));
        assertTrue(engine.turnIn(state, "patrol", DAY + 1).isPresent());
        assertEquals(AcceptResult.DAILY_ALREADY_DONE, engine.accept(state, "patrol", context(1, DAY + 1)));
    }

    @Test
    public void availableListsExactlyTheAcceptableQuests() {
        List<QuestDefinition> fresh = engine.available(state, context(1, DAY));
        assertTrue(fresh.contains(INTRO) && fresh.contains(SLIMES) && fresh.contains(PATROL));
        assertFalse(fresh.contains(HUNT));
        assertFalse(fresh.contains(FOREST));
        for (QuestDefinition quest : fresh) {
            assertEquals(AcceptResult.SUCCESS, engine.canAccept(state, quest.id(), context(1, DAY)));
        }
        assertEquals("available() never changes the state", 0, state.revision());
    }

    @Test
    public void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> engine.resetDailies(state, -1));
        assertThrows(IllegalArgumentException.class, () -> new QuestContext(0, Set.of(1), DAY, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuestContext(1, Set.of(9), DAY, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuestContext(1, Set.of(1), -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuestContext(1, Set.of(1), DAY, 8));
        assertThrows(NullPointerException.class, () -> engine.onEvent(state, null));
        assertThrows(NullPointerException.class, () -> new QuestEngine(null));
    }
}
