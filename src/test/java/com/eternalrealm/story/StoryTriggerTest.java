package com.eternalrealm.story;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Triggers, story progress and chapters. */
public class StoryTriggerTest {

    @Test
    public void triggersRejectMalformedArguments() {
        assertThrows(IllegalArgumentException.class, () -> new StoryTrigger.FinalPhase(0));
        assertThrows(IllegalArgumentException.class, () -> new StoryTrigger.FinalPhase(5));
        assertThrows(IllegalArgumentException.class, () -> new StoryTrigger.DayStarted(0));
        assertThrows(IllegalArgumentException.class, () -> new StoryTrigger.DayStarted(3, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StoryTrigger.DayStarted(3, 14, -1));
        assertThrows(IllegalArgumentException.class, () -> new StoryTrigger.NpcTalk("item_shop", -1));
        assertThrows(IllegalArgumentException.class, () -> new StoryTrigger.AreaEntered(0));
        assertThrows(IllegalArgumentException.class, () -> new StoryTrigger.AreaUnlocked(-3));
        // an id can never be turned into another dialogue id
        for (String bad : Arrays.asList(null, "", "b1.intro", "B1", "b 1", "../b1")) {
            assertThrows(String.valueOf(bad), IllegalArgumentException.class, () -> new StoryTrigger.BossEncounter(bad));
            assertThrows(String.valueOf(bad), IllegalArgumentException.class, () -> new StoryTrigger.DungeonEntered(bad));
            assertThrows(String.valueOf(bad), IllegalArgumentException.class, () -> new StoryTrigger.NpcTalk(bad, 0));
            assertThrows(String.valueOf(bad), IllegalArgumentException.class,
                    () -> new StoryTrigger.QuestTalk("tower_watcher", bad));
        }
        assertThrows(NullPointerException.class, () -> StoryCatalog.standard().dialogue(null));
    }

    @Test
    public void everyTriggerHasItsOwnKindAndAnAudience() {
        List<StoryTrigger> triggers = List.of(StoryTrigger.FIRST_LOGIN, new StoryTrigger.AreaEntered(1),
                new StoryTrigger.DungeonEntered("d1"), new StoryTrigger.BossEncounter("b1"),
                new StoryTrigger.BossDefeated("b1", true), new StoryTrigger.AreaUnlocked(2),
                new StoryTrigger.NpcTalk("quest_board", 0), new StoryTrigger.QuestTalk("tower_watcher", "m7_arrival"),
                new StoryTrigger.FinalPhase(1), StoryTrigger.ENDING, new StoryTrigger.DayStarted(1),
                new StoryTrigger.SeasonEnded(true));
        Set<StoryTrigger.Kind> kinds = EnumSet.noneOf(StoryTrigger.Kind.class);
        for (StoryTrigger trigger : triggers) {
            assertTrue("two triggers with kind " + trigger.kind(), kinds.add(trigger.kind()));
            assertTrue(trigger.audience() != null);
        }
        assertEquals(EnumSet.allOf(StoryTrigger.Kind.class), kinds);
        assertEquals(StoryTrigger.Audience.PARTY, new StoryTrigger.DungeonEntered("d1").audience());
        assertEquals(StoryTrigger.Audience.PARTY, new StoryTrigger.FinalPhase(2).audience());
        assertEquals(StoryTrigger.Audience.SERVER, new StoryTrigger.AreaUnlocked(2).audience());
        assertEquals(StoryTrigger.Audience.SERVER, new StoryTrigger.DayStarted(2).audience());
        assertEquals(StoryTrigger.Audience.PLAYER, new StoryTrigger.NpcTalk("lilia", 0).audience());
        assertEquals(new StoryTrigger.DayStarted(4, 14, 0), new StoryTrigger.DayStarted(4));
        assertEquals(new StoryTrigger.FirstLogin(), StoryTrigger.FIRST_LOGIN);
    }

    @Test
    public void progressCountsDistinctMainBosses() {
        assertEquals(0, StoryProgress.of(List.of()));
        assertEquals(1, StoryProgress.of(List.of("b1", "b1")));
        assertEquals(2, StoryProgress.of(Arrays.asList("b2", null, "ex_b2", "b5", "final_ex")));
        assertEquals(StoryProgress.MAX, StoryProgress.of(StoryProgress.MAIN_BOSSES));
        assertEquals(StoryProgress.MAX, StoryProgress.MAIN_BOSSES.size());
        assertTrue(StoryProgress.isMainBoss("final"));
        assertFalse(StoryProgress.isMainBoss("b9"));
        assertFalse(StoryProgress.isComplete(8));
        assertTrue(StoryProgress.isComplete(9));
    }

    @Test
    public void chapterFollowsTheProgress() {
        assertEquals(Chapter.CHAPTER_1, Chapter.ofProgress(0));
        assertEquals(Chapter.CHAPTER_5, Chapter.ofProgress(4));
        assertEquals(Chapter.CHAPTER_8, Chapter.ofProgress(7));
        assertEquals(Chapter.FINALE, Chapter.ofProgress(8));
        assertEquals(Chapter.EPILOGUE, Chapter.ofProgress(9));
        assertEquals(Chapter.EPILOGUE, Chapter.ofProgress(1000));
        assertThrows(IllegalArgumentException.class, () -> Chapter.ofProgress(-1));
        for (int progress = 0; progress < StoryProgress.MAX; progress++) {
            assertEquals("the chapter ends with the next main boss", StoryProgress.MAIN_BOSSES.get(progress),
                    Chapter.ofProgress(progress).bossId().orElseThrow());
        }
        assertEquals(Chapter.CHAPTER_6, Chapter.ofBoss("b6").orElseThrow());
        assertTrue(Chapter.ofBoss("b9").isEmpty());
        assertTrue(Chapter.PROLOGUE.bossId().isEmpty() && Chapter.EPILOGUE.dungeonId().isEmpty());
    }

    @Test
    public void areasAndNpcsAreFoundByTheirIds() {
        for (StoryArea area : StoryArea.values()) {
            assertEquals(area, StoryArea.byId(area.id()).orElseThrow());
            assertEquals(area, StoryArea.bySlug(area.slug()).orElseThrow());
        }
        assertTrue(StoryArea.byId(8).isEmpty() && StoryArea.bySlug("moon").isEmpty());
        assertTrue(StoryArea.ARCELIA.unlockedBy().isEmpty());
        assertEquals("b5", StoryArea.DRAGONIA_VOLCANO.unlockedBy().orElseThrow());
        for (StoryNpc npc : StoryNpc.values()) {
            assertEquals(npc, StoryNpc.byId(npc.id()).orElseThrow());
        }
        assertTrue(StoryNpc.byId("story").isEmpty());
    }

    @Test
    public void valueTypesValidateTheirParts() {
        assertThrows(IllegalArgumentException.class, () -> new Dialogue("empty", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DialogueLine("story.eternalrealm.speaker.nox", "Not a key"));
        assertTrue(DialogueLine.of(Speaker.NARRATOR, "story.eternalrealm.x.1").isNarration());
        assertFalse(DialogueLine.of(Speaker.NOX, "story.eternalrealm.x.1").isNarration());
        DialogueLine line = DialogueLine.of(Speaker.NOX, "story.eternalrealm.x.1");
        assertThrows(IllegalArgumentException.class, () -> new EndingSequence(
                List.of(new EndingSequence.Beat(20, line, EndingSequence.Display.CHAT)),
                List.of(new EndingSequence.Credit(EndingSequence.CreditKind.PLAYERS, "story.eternalrealm.staff.x")), 40));
        assertThrows("two beats at once", IllegalArgumentException.class, () -> new EndingSequence(
                List.of(new EndingSequence.Beat(0, line, EndingSequence.Display.CHAT),
                        new EndingSequence.Beat(0, line, EndingSequence.Display.CHAT)),
                List.of(new EndingSequence.Credit(EndingSequence.CreditKind.PLAYERS, "story.eternalrealm.staff.x")), 40));
        assertThrows("no player list", IllegalArgumentException.class, () -> new EndingSequence(
                List.of(new EndingSequence.Beat(0, line, EndingSequence.Display.CHAT)),
                List.of(new EndingSequence.Credit(EndingSequence.CreditKind.CREDIT, "story.eternalrealm.staff.x")), 40));
    }

    @Test
    public void theBuilderRejectsDuplicatesAndMisplacedCharacters() {
        StoryCatalog.Builder builder = new StoryCatalog.Builder().dialogue("a.b", Speaker.NOX);
        assertThrows(IllegalStateException.class, () -> builder.dialogue("a.b", Speaker.LILIA));
        assertThrows(IllegalArgumentException.class, () -> builder.dialogue("a.c"));
        assertThrows("Wolfram is not in town before b4 falls", IllegalArgumentException.class,
                () -> builder.npc(StoryNpc.WOLFRAM, 3, 1));
        builder.bark("b1", 2, Speaker.LASZLO);
        assertThrows(IllegalStateException.class, () -> builder.bark("b1", 2, Speaker.LASZLO));
        assertThrows(IllegalArgumentException.class, () -> builder.bark("b1", 1, Speaker.LASZLO));
        builder.areaCard(StoryArea.CELESTIA);
        assertThrows(IllegalStateException.class, () -> builder.areaCard(StoryArea.CELESTIA));
    }
}
