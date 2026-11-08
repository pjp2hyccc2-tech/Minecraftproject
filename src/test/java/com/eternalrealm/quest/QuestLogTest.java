package com.eternalrealm.quest;

import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.IncompatibleDataException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QuestLogTest {
    private static final long DAY = 20_400;
    private final QuestCatalog catalog = Quests.catalog();
    private final QuestEngine engine = new QuestEngine(catalog);

    private QuestLogState playedState() {
        QuestLogState state = QuestLogState.fresh();
        QuestContext context = new QuestContext(5, Set.of(1), DAY, 1);
        engine.accept(state, "m1_arrival", context);
        engine.onEvent(state, new GameEvent.NpcTalked("arcelia_knight_captain"));
        engine.turnIn(state, "m1_arrival", DAY);
        engine.accept(state, "m1_hunt", context);
        engine.accept(state, "daily_plains_patrol", context);
        engine.onEvent(state, new GameEvent.MonsterKilled("meadow_slime", 1));
        return state;
    }

    @Test
    public void snapshotsRoundTripThroughTheCodec() throws Exception {
        QuestLogState state = playedState();
        QuestLog snapshot = state.snapshot();
        assertEquals(List.of(new ActiveQuest("m1_hunt", List.of(1, 0)), new ActiveQuest("daily_plains_patrol", List.of(1))),
                snapshot.active());
        assertEquals(Map.of("m1_arrival", 1), snapshot.completions());
        assertEquals(DAY, snapshot.lastDailyResetDay());

        JsonObject json = QuestLogCodec.toJson(snapshot);
        assertEquals(1, json.get("schema").getAsInt());
        QuestLog decoded = QuestLogCodec.fromJson(JsonParser.parseString(json.toString()));
        assertEquals(snapshot, decoded);
        QuestLogState restored = QuestLogState.restore(decoded, catalog);
        assertEquals(snapshot, restored.snapshot());
        assertEquals("a consistent log needs no adjustment", 0, restored.revision());
        assertEquals(QuestLog.EMPTY, QuestLogCodec.fromJson(QuestLogCodec.toJson(QuestLog.EMPTY)));
        assertEquals(QuestLog.EMPTY, QuestLogState.fresh().snapshot());
    }

    @Test
    public void dailyMarkersRoundTrip() throws Exception {
        QuestLogState state = playedState();
        for (int i = 1; i < 15; i++) {
            engine.onEvent(state, new GameEvent.MonsterKilled("wandering_dead", 1));
        }
        assertTrue(engine.turnIn(state, "daily_plains_patrol", DAY).isPresent());
        QuestLog decoded = QuestLogCodec.fromJson(QuestLogCodec.toJson(state.snapshot()));
        assertEquals(Set.of("daily_plains_patrol"), decoded.dailyDone());
        QuestLogState restored = QuestLogState.restore(decoded, catalog);
        assertEquals(AcceptResult.DAILY_ALREADY_DONE,
                engine.accept(restored, "daily_plains_patrol", new QuestContext(5, Set.of(1), DAY, 0)));
    }

    @Test
    public void restoringReconcilesWithTheCurrentCatalog() {
        QuestLog stored = new QuestLog(List.of(
                new ActiveQuest("removed_quest", List.of(1)),
                new ActiveQuest("m1_arrival", List.of(1)),
                new ActiveQuest("m1_hunt", List.of(99, 2)),
                new ActiveQuest("m1_dungeon", List.of(0, 0, 0))),
                Map.of("m1_arrival", 1, "old_quest", 3), DAY, Set.of("m1_hunt", "daily_plains_patrol"));
        QuestLogState state = QuestLogState.restore(stored, catalog);
        assertTrue("adjustments make the state dirty", state.revision() > 0);
        assertEquals("unknown and already completed quests are dropped", List.of("m1_hunt", "m1_dungeon"),
                state.activeQuestIds());
        assertEquals("progress is clamped", List.of(6, 2), state.progress("m1_hunt"));
        assertEquals("a changed objective count resets progress", List.of(0), state.progress("m1_dungeon"));
        assertEquals("history is kept", 3, state.completionCount("old_quest"));
        assertFalse("only dailies keep daily markers", state.isDailyDone("m1_hunt"));
        assertTrue(state.isDailyDone("daily_plains_patrol"));
        assertFalse(state.reconcile(catalog));
    }

    @Test
    public void invalidSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ActiveQuest("q", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ActiveQuest("q", List.of(-1)));
        assertThrows(IllegalArgumentException.class, () -> new ActiveQuest("Q", List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> new QuestLog(
                List.of(new ActiveQuest("q", List.of(0)), new ActiveQuest("q", List.of(1))), Map.of(), -1, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new QuestLog(List.of(), Map.of("q", 0), -1, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new QuestLog(List.of(), Map.of(), -2, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new QuestLog(List.of(), Map.of(), -1, Set.of("Bad")));
    }

    @Test
    public void corruptJsonIsRejectedAndNewerSchemasAreIncompatible() {
        String valid = QuestLogCodec.toJson(playedState().snapshot()).toString();
        for (String json : new String[]{
                valid.replace("\"schema\":1", "\"schema\":0"),
                valid.replace("\"lastDailyReset\":" + DAY, "\"lastDailyReset\":\"x\""),
                valid.replace("\"progress\":[1,0]", "\"progress\":[1.5,0]"),
                valid.replace("\"progress\":[1,0]", "\"progress\":[]"),
                valid.replace("\"quest\":\"m1_hunt\"", "\"quest\":\"M1 HUNT\""),
                valid.replace("\"m1_arrival\":1", "\"m1_arrival\":0"),
                valid.replace("\"dailyDone\":[]", "\"dailyDone\":[\"a\",\"a\"]"),
                valid.replace(",\"dailyDone\":[]", ""),
                "[]", "{}"}) {
            assertThrows(json, CorruptDataException.class, () -> QuestLogCodec.fromJson(JsonParser.parseString(json)));
        }
        String newer = valid.replace("\"schema\":1", "\"schema\":2");
        assertThrows(IncompatibleDataException.class, () -> QuestLogCodec.fromJson(JsonParser.parseString(newer)));
    }

    @Test
    public void theDayIndexStartsAtTheConfiguredResetTime() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        LocalTime five = DailyReset.DEFAULT_RESET_TIME;
        long nov9 = java.time.LocalDate.of(2026, 11, 9).toEpochDay();
        assertEquals(nov9, DailyReset.dayIndex(Instant.parse("2026-11-08T20:00:00Z"), tokyo, five)); // 05:00 JST
        assertEquals(nov9 - 1, DailyReset.dayIndex(Instant.parse("2026-11-08T19:59:59Z"), tokyo, five)); // 04:59 JST
        assertEquals(nov9, DailyReset.dayIndex(Instant.parse("2026-11-09T19:59:59Z"), tokyo, five));
        assertEquals(nov9, DailyReset.dayIndex(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC, LocalTime.MIDNIGHT));
        assertThrows(NullPointerException.class, () -> DailyReset.dayIndex(null, tokyo, five));
    }
}
