package com.eternalrealm.quest;

import com.eternalrealm.world.Areas;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A dungeon's boss falls in every clear of that dungeon, so the quest to defeat it counts the defeat made in the clear
 * its prerequisite asked for (owner, 2026-11-15: 「ダンジョンのクエストがトータル二回ダンジョンに挑まないといけないから
 * それを修正して」). Nothing else from before acceptance counts, and a daily always needs a new defeat.
 */
public class DungeonBossCreditTest {
    private static final long DAY = 20_400;
    private static final QuestReward REWARD = new QuestReward(100, 20, List.of());
    private static final String BOSS = Areas.dungeon("d1").orElseThrow().bossId();
    private static final String OTHER_BOSS = Areas.dungeon("d2").orElseThrow().bossId();

    private final QuestEngine engine = new QuestEngine(new QuestCatalog(List.of(
            quest("dungeon", QuestKind.MAIN, List.of(), new Objective.ClearDungeon("d1", 1)),
            quest("boss", QuestKind.MAIN, List.of("dungeon"), new Objective.DefeatBoss(BOSS)),
            quest("other_boss", QuestKind.SIDE, List.of("dungeon"), new Objective.DefeatBoss(OTHER_BOSS)),
            quest("boss_and_more", QuestKind.SIDE, List.of("dungeon"), new Objective.DefeatBoss(BOSS),
                    Objective.KillMonsters.of("meadow_slime", 2)),
            quest("boss_daily", QuestKind.DAILY, List.of("dungeon"), new Objective.DefeatBoss(BOSS)),
            quest("talk", QuestKind.MAIN, List.of(), new Objective.TalkTo("guide")),
            quest("boss_after_talk", QuestKind.SIDE, List.of("talk"), new Objective.DefeatBoss(BOSS)))));
    private final QuestLogState state = QuestLogState.fresh();

    private static QuestDefinition quest(String id, QuestKind kind, List<String> requires, Objective... objectives) {
        return new QuestDefinition(id, kind, 1, new QuestPrerequisites(requires, 1, 1), List.of(objectives), REWARD);
    }

    private static QuestContext context() {
        return new QuestContext(50, Set.of(1, 2), DAY, 0);
    }

    /** One run of the dungeon: the service reports the clear and the boss's defeat to everyone who was there. */
    private void clearTheDungeon() {
        engine.onEvent(state, new GameEvent.DungeonCleared("d1"));
        engine.onEvent(state, new GameEvent.BossDefeated(BOSS));
    }

    private void doTheDungeonQuest() {
        assertEquals(AcceptResult.SUCCESS, engine.accept(state, "dungeon", context()));
        clearTheDungeon();
        assertTrue(engine.turnIn(state, "dungeon", DAY).isPresent());
    }

    @Test
    public void oneRunOfTheDungeonCarriesItsBossQuestToo() {
        doTheDungeonQuest();
        List<QuestUpdate> counted = new ArrayList<>();
        assertEquals(AcceptResult.SUCCESS, engine.accept(state, "boss", context(), counted));
        assertEquals(List.of(1), state.progress("boss"));
        assertTrue(engine.isReadyToTurnIn(state, "boss"));
        assertEquals(List.of(new QuestUpdate.ProgressChanged("boss", 0, 1, 1),
                new QuestUpdate.ObjectiveCompleted("boss", 0), new QuestUpdate.ReadyToTurnIn("boss")), counted);
        assertTrue(engine.turnIn(state, "boss", DAY).isPresent());
        // the plain overload counts the same, it only does not report
        QuestLogState other = QuestLogState.restore(new QuestLog(List.of(), Map.of("dungeon", 1),
                QuestLog.NEVER_RESET, Set.of()), engine.catalog());
        assertEquals(AcceptResult.SUCCESS, engine.accept(other, "boss", context()));
        assertTrue(engine.isReadyToTurnIn(other, "boss"));
    }

    @Test
    public void onlyTheBossOfThatDungeonIsCountedAndTheRestStillWaits() {
        doTheDungeonQuest();
        engine.accept(state, "other_boss", context());
        assertEquals("another dungeon's boss still has to be beaten", List.of(0), state.progress("other_boss"));
        engine.accept(state, "boss_and_more", context());
        assertEquals(List.of(1, 0), state.progress("boss_and_more"));
        assertFalse(engine.isReadyToTurnIn(state, "boss_and_more"));
    }

    @Test
    public void aDailyAndAQuestWithoutTheDungeonBeforeItNeedANewDefeat() {
        doTheDungeonQuest();
        engine.accept(state, "boss_daily", context());
        assertEquals(List.of(0), state.progress("boss_daily"));
        // the boss fell before, but not in a clear this quest's prerequisite asked for
        engine.accept(state, "talk", context());
        engine.onEvent(state, new GameEvent.NpcTalked("guide"));
        assertTrue(engine.turnIn(state, "talk", DAY).isPresent());
        engine.accept(state, "boss_after_talk", context());
        assertEquals(List.of(0), state.progress("boss_after_talk"));
        engine.onEvent(state, new GameEvent.BossDefeated(BOSS));
        assertEquals(List.of(1), state.progress("boss_after_talk"));
        assertEquals(List.of(1), state.progress("boss_daily"));
    }

    /** A boss quest accepted before the rule (its progress still 0) is credited when the log is loaded, once. */
    @Test
    public void bossQuestsAcceptedBeforeTheRuleAreCreditedOnLoad() {
        QuestLog stored = new QuestLog(List.of(new ActiveQuest("boss", List.of(0)), new ActiveQuest("boss_daily",
                List.of(0))), Map.of("dungeon", 1), QuestLog.NEVER_RESET, Set.of());
        QuestLogState loaded = QuestLogState.restore(stored, engine.catalog());
        List<QuestUpdate> updates = engine.creditClearedBosses(loaded);
        assertEquals(List.of(new QuestUpdate.ProgressChanged("boss", 0, 1, 1),
                new QuestUpdate.ObjectiveCompleted("boss", 0), new QuestUpdate.ReadyToTurnIn("boss")), updates);
        assertTrue(engine.isReadyToTurnIn(loaded, "boss"));
        assertEquals("a daily is never credited", List.of(0), loaded.progress("boss_daily"));
        assertEquals("idempotent", List.of(), engine.creditClearedBosses(loaded));
        // without the dungeon quest done there is nothing to credit
        QuestLogState early = QuestLogState.restore(new QuestLog(List.of(new ActiveQuest("other_boss", List.of(0))),
                Map.of(), QuestLog.NEVER_RESET, Set.of()), engine.catalog());
        assertEquals(List.of(), engine.creditClearedBosses(early));
    }

    /** The real story: every "defeat the boss" quest that follows "clear its dungeon" is credited by that run. */
    @Test
    public void everyStoryBossQuestAfterItsDungeonQuestIsCreditedByTheRun() {
        QuestCatalog catalog = Quests.catalog();
        int pairs = 0;
        for (QuestDefinition quest : catalog.all()) {
            if (quest.kind() != QuestKind.MAIN) {
                continue;
            }
            for (String before : quest.prerequisites().questIds()) {
                QuestDefinition previous = catalog.byId(before).orElseThrow();
                for (Objective objective : previous.objectives()) {
                    if (!(objective instanceof Objective.ClearDungeon clear)) {
                        continue;
                    }
                    String boss = Areas.dungeon(clear.dungeonId()).orElseThrow().bossId();
                    if (quest.objectives().contains(new Objective.DefeatBoss(boss))) {
                        pairs++;
                    }
                }
            }
        }
        assertEquals("the story's eight dungeons, each followed by its boss", 8, pairs);
    }
}
